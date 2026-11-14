package fi.hospitalos.handsfree

/** Read-only browsing. Stable IDs, never list positions, identify an action's target. */
class WardNavigator(private val me: String, private val bedside: BedsideController) {
  enum class Section { PATIENTS, STAFF, REQUESTS, MESSAGES }
  sealed interface Result {
    data object Changed : Result
    data object Exit : Result
    data class SelectPatient(val id: String) : Result
    data class Compose(val id: String) : Result
    data class ReadMessage(val id: String) : Result
    data class ReviewRequest(val id: String) : Result
  }
  private sealed interface View
  private data class Listing(val section: Section, val id: String?, val filter: String = "pending") : View
  private data class Details(val kind: String, val id: String, val page: Int = 0, val back: View? = null, val reviewOnBack: Boolean = false) : View
  private data class Item(val id: String, val text: String)
  private var view: View? = null
  private var staff = emptyList<Colleague>()
  private var tasks = emptyList<Request>()
  private var messages = emptyList<WardMessage>()
  val active get() = view != null
  val canOpen get() = (view as? Listing)?.let { v -> items(v).any { it.id == v.id } } == true
  val selectedRequestId get() = when (val v = view) {
    is Listing -> v.id.takeIf { v.section == Section.REQUESTS }
    is Details -> v.id.takeIf { v.kind == "request" }
    else -> null
  }
  fun snapshot(people: List<Colleague>, requests: List<Request>, mail: List<WardMessage>) {
    staff = people; tasks = requests; messages = mail
  }
  fun clear() { view = null }
  fun list(section: Section, filter: String = "pending") {
    val initial = Listing(section, null, filter)
    view = initial.copy(id = items(initial).firstOrNull()?.id)
  }
  fun request(id: String, reviewOnBack: Boolean = false) {
    val previous = view
    val back = if (previous is Details && previous.kind == "request" && previous.id == id) previous.back else previous.takeIf { it is Listing }
    view = Details("request", id, back = back, reviewOnBack = reviewOnBack)
  }
  fun patient(kind: String, id: String) {
    val previous = view as? Details
    val back = if (previous?.id == id) { if (previous.kind == kind) previous.back else previous } else Listing(Section.PATIENTS, id)
    view = Details(kind, id, back = back)
  }
  fun help() { if ((view as? Details)?.kind != "help") view = Details("help", "help", back = view) }

  fun command(words: String): Result? {
    val v = view ?: return null
    when (words) {
      "back" -> {
        if (v is Details && v.reviewOnBack) { view = null; return Result.ReviewRequest(v.id) }
        view = (v as? Details)?.back
        return if (view == null) Result.Exit else Result.Changed
      }
      "next", "previous" -> {
        val direction = if (words == "next") 1 else -1
        view = when (v) {
          is Listing -> {
            val rows = items(v)
            val index = rows.indexOfFirst { it.id == v.id }
            v.copy(id = if (rows.isEmpty()) null else rows[if (index < 0) 0 else (index + direction).coerceIn(0, rows.lastIndex)].id)
          }
          is Details -> v.copy(page = (v.page + direction).coerceIn(0, pages(v).lastIndex.coerceAtLeast(0)))
        }
        return Result.Changed
      }
      "open" -> {
        if (v !is Listing || items(v).none { it.id == v.id }) return Result.Changed
        val id = v.id ?: return Result.Changed
        return when (v.section) {
          Section.PATIENTS -> Result.SelectPatient(id)
          Section.STAFF -> Result.Compose(id)
          Section.MESSAGES -> Result.ReadMessage(id)
          Section.REQUESTS -> { request(id); Result.Changed }
        }
      }
    }
    return null
  }

  fun card(): Card? = when (val v = view) {
    null -> null
    is Listing -> {
      val rows = items(v)
      val selected = rows.indexOfFirst { it.id == v.id }
      val title = when (v.section) {
        Section.PATIENTS -> "Assigned patients"
        Section.STAFF -> "Available colleagues"
        Section.REQUESTS -> "${v.filter.replaceFirstChar { it.uppercase() }} requests"
        Section.MESSAGES -> "Messages"
      }
      val body = when {
        rows.isEmpty() -> "No ${title.lowercase()}"
        selected < 0 -> "List changed · say next to choose again"
        else -> "${selected + 1}/${rows.size}\n${rows[selected].text}"
      }
      Card("browse", title, body, "Say next, previous, open or back", true)
    }
    is Details -> {
      val pages = pages(v)
      val index = v.page.coerceAtMost(pages.lastIndex)
      val title = when (v.kind) {
        "request" -> "Request details"
        "patient" -> "Patient overview"
        "note" -> "Latest permitted note"
        "reading" -> "Latest saved reading"
        else -> "What you can say"
      }
      val task = tasks.find { it.id == v.id }.takeIf { v.kind == "request" }
      val action = when {
        task?.to == me && task.status in setOf("sent", "delivered") -> " · review request"
        task?.to == me && task.status == "accepted" -> " · complete request"
        task?.from == me && task.status in setOf("sent", "delivered") -> " · cancel request"
        else -> ""
      }
      Card("details", "$title · ${index + 1}/${pages.size}", pages[index], "Say next, previous or back$action", true)
    }
  }

  private fun items(v: Listing): List<Item> = when (v.section) {
    Section.PATIENTS -> bedside.assignedPatients.map { Item(it.id, "${it.name} · ${it.id}\nRoom ${it.room}") }
    Section.STAFF -> staff.filter { it.id != me && it.reachable && it.status == "available" }.map { Item(it.id, "${it.name}\nAvailable and connected") }
    Section.REQUESTS -> tasks.filter {
      when (v.filter) {
        "pending" -> it.status in setOf("sent", "delivered")
        "accepted" -> it.status == "accepted"
        "history" -> it.status in setOf("completed", "declined", "cancelled", "expired", "filled")
        else -> true
      }
    }.map { Item(it.id, "Room ${it.room} · ${it.status}\n${if (it.to == me) "From ${name(it.from)}" else "To ${name(it.to)}"}\n${it.text}") }
    Section.MESSAGES -> messages.map { Item(it.id, "${if (it.to == me) "From ${name(it.from)}" else "To ${name(it.to)}"}\n${if (it.read) "Opened" else "Not marked opened"}") }
  }

  private fun pages(v: Details): List<String> {
    if (v.kind == "help") return listOf(
      "Show patients\nWho's available?\nShow requests · show messages",
      "Show accepted requests\nShow request history\nNext · previous · open · back",
      "Message Ben\nAsk Ben for help in room twelve\nGet help here (confirmed patient)",
      "Show patient details\nShow latest note · show latest reading\nCapture reading · add patient note",
    )
    if (v.kind == "request") {
      val task = tasks.find { it.id == v.id } ?: return listOf("Request no longer available")
      val result = mutableListOf("Room ${task.room} · ${task.status}\nFrom ${name(task.from)}\n${task.text}")
      val a = task.attachment
      if (task.attachmentUnavailable) result += "Patient attachment unavailable\nAccess or encounter may have changed"
      else if (a != null) {
        result += "${a.patientName} · ${a.patientId}\n${a.readingText()}"
        result += "${a.patientName} · ${a.patientId}\nCaptured ${a.capturedAt}\nMeasurement freshness unknown\n${if (a.corrected.isEmpty()) "Reviewed extraction" else "Manual correction: ${a.corrected.joinToString()}"}\n${if (a.source == "injected-camera") "Simulated camera input" else "DAT camera input"}"
        bedside.assignedPatients.find { it.id == a.patientId && it.encounterId == a.encounterId }?.warning?.let {
          result += textPages("${a.patientName} · ${a.patientId}\nWarning", it)
        }
      } else result += "No patient reading attached"
      return result
    }
    val p = bedside.currentPatient?.takeIf { it.id == v.id } ?: return listOf("Confirm this patient again to view details")
    val header = "${p.name} · ${p.id}"
    return when (v.kind) {
      "patient" -> listOf("$header\nRoom ${p.room} · synthetic"+(p.admissionReason?.let{"\nAdmitted: $it"}?:"")) +
        (if(p.allergies.isNotEmpty())textPages("$header\nAllergies",p.allergies.joinToString("\n"))else emptyList()) +
        p.medications.flatMap{m->textPages("$header\nPrescribed medication","${m.name} ${m.dose} · ${m.route}\n${m.schedule}\n${m.prescribedBy} · ${m.prescribedAt}")} +
        p.careHistory.sortedByDescending{it.at}.flatMap{e->textPages("$header\n${e.kind.replaceFirstChar{it.uppercase()}}","${e.description}\n${e.at} · ${e.author}")} +
        textPages("$header\nWarning", p.warning ?: "No warning supplied") +
        textPages("$header\nPatient reported", p.reported ?: "No reported information supplied") +
        (bedside.latestNote(p.id)?.let { textPages("$header\nLatest author-reported note", it.text) + "$header\nRecorded ${it.savedAt}\nAuthor: ${it.author}\nEvent time not verified" } ?: listOf("$header\nNo permitted saved note"))
      "note" -> bedside.latestNote(p.id)?.let {
        textPages("$header\nAuthor-reported note", it.text) + "$header\nRecorded ${it.savedAt}\nAuthor: ${it.author}\nEvent time not verified"
      } ?: listOf("$header\nNo permitted saved note")
      "reading" -> bedside.latestReading(p.id)?.let {
        listOf("$header\n${it.values.display()}", "$header\nCaptured ${it.capturedAt}\nFreshness unknown\n${if (it.corrected) "Manually corrected" else "Reviewed extraction"}")
      } ?: listOf("$header\nNo permitted saved reading")
      else -> listOf("Information unavailable")
    }
  }

  private fun name(id: String) = staff.find { it.id == id }?.name ?: id
  private fun textPages(header: String, text: String) = text.chunked(150).ifEmpty { listOf("No information supplied") }.map { "$header\n$it" }
}
