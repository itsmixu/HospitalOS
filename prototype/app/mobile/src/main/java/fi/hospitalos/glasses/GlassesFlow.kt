package fi.hospitalos.glasses

import fi.hospitalos.ward.Link
import fi.hospitalos.ward.Snapshot
import fi.hospitalos.ward.Task
import fi.hospitalos.ward.WardClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * What the wearer sees, driven by live ward-server state plus the wearer's own actions.
 *
 * The server is the source of truth for patients, requests, encounters and notes. This class only
 * decides which screen to show and turns glasses/phone actions into server commands.
 * Demo-only pieces (staff map, fall alert, scripted encounter text, Ask answers) are labelled.
 */
class GlassesFlow(
    private val scope: CoroutineScope,
    private val ward: WardClient,
    private val glasses: GlassesLink,
) {
  companion object {
    const val WARD_NAME = "Ward 4B"
    const val ASK_QUESTION = "What's the latest on this patient?"
    /** Deterministic stand-in for transcription until real speech capture lands (README). */
    const val DEMO_PLANNED = "Help patient walk to the bathroom."
  }

  private val _screen = MutableStateFlow<Screen>(Screen.Home)
  val screen: StateFlow<Screen> = _screen.asStateFlow()

  /** Patient the wearer has confirmed; null until chosen. */
  private val _selected = MutableStateFlow<String?>(null)
  val selected: StateFlow<String?> = _selected.asStateFlow()

  private var recordingStartedAt = 0L
  private val seenIncoming = mutableSetOf<String>()
  private val acknowledged = mutableSetOf<String>()
  private var timed: Job? = null
  private val renderer = Renderer(this)

  val snapshot: Snapshot?
    get() = ward.snapshot.value

  init {
    scope.launch { ward.snapshot.collect { s -> if (s != null) onSnapshot(s) } }
    scope.launch {
      var wasOnline = false
      ward.link.collect { link ->
        when {
          link == Link.OFFLINE && wasOnline -> show(Screen.Offline)
          link == Link.ONLINE && !wasOnline && _screen.value == Screen.Offline ->
              show(Screen.Toast("Back online", ok = true, next = base()))
        }
        wasOnline = link == Link.ONLINE
      }
    }
  }

  // ---------------------------------------------------------------- core

  fun show(screen: Screen) {
    timed?.cancel()
    _screen.value = screen
    render()
    timed = scope.launch { afterShown(screen) }
  }

  /** Re-send the current screen (data changed, or the display restarted). */
  fun render() {
    val s = snapshot
    val current = _screen.value
    glasses.show { with(renderer) { render(current, s) } }
    if (current is Screen.Incoming) acknowledge(current.taskId)
  }

  /** Where to return after a toast or a finished step. */
  fun base(): Screen {
    val s = snapshot ?: return Screen.Home
    val open = s.drafts.firstOrNull { it.status == "open" }
    if (open != null) return Screen.Recording(open.patientId, recordingStartedAt)
    val review = s.drafts.firstOrNull { it.status == "review" }
    if (review != null) return Screen.NoteReview(review.id)
    return _selected.value?.let { Screen.PatientCard(it) } ?: Screen.Home
  }

  private suspend fun afterShown(screen: Screen) {
    when (screen) {
      is Screen.Toast -> {
        delay(screen.millis)
        show(screen.next)
      }
      is Screen.Recording -> {
        while (true) {
          delay(1000)
          render()
        }
      }
      is Screen.AskListening -> {
        if (screen.heard.length < ASK_QUESTION.length) {
          delay(200)
          val next = ASK_QUESTION.indexOf(' ', screen.heard.length + 1).let { if (it < 0) ASK_QUESTION.length else it }
          show(screen.copy(heard = ASK_QUESTION.substring(0, next)))
        } else {
          delay(500)
          show(answer(screen.patientId))
        }
      }
      is Screen.Outgoing -> {
        val t = task(screen.taskId)
        if (t?.status == "accepted") {
          delay(5000)
          show(base())
        }
      }
      else -> Unit
    }
  }

  private fun onSnapshot(s: Snapshot) {
    // A new request for me: show it straight away (this is the "notification").
    val fresh = s.incoming.firstOrNull { it.status == "sent" && it.id !in seenIncoming }
    if (fresh != null) {
      seenIncoming += fresh.id
      show(Screen.Incoming(fresh.id))
      return
    }
    when (val current = _screen.value) {
      is Screen.Outgoing -> {
        val t = s.tasks.firstOrNull { it.id == current.taskId }
        when (t?.status) {
          "completed" -> show(Screen.Toast("${s.name(t.to)} finished: ${t.summary ?: "request"}", true, base(), 4000))
          "accepted" -> show(current) // restart the timer that returns to base
          else -> render()
        }
      }
      is Screen.Drafting -> {
        val review = s.drafts.firstOrNull { it.status == "review" && it.patientId == current.patientId }
        if (review != null) show(Screen.NoteReview(review.id)) else render()
      }
      is Screen.Briefing -> {
        val t = s.tasks.firstOrNull { it.id == current.taskId }
        if (t == null || t.status == "cancelled") show(Screen.Toast("Request was cancelled", false, base())) else render()
      }
      is Screen.Incoming -> {
        val t = s.tasks.firstOrNull { it.id == current.taskId }
        if (t == null || t.status == "cancelled") show(Screen.Toast("Request was cancelled", false, base())) else render()
      }
      else -> render()
    }
  }

  fun task(id: String): Task? = snapshot?.tasks?.firstOrNull { it.id == id }

  private fun acknowledge(taskId: String) {
    if (!glasses.isDisplayOn || taskId in acknowledged) return
    val t = task(taskId) ?: return
    if (t.status == "sent" && ward.send("message.delivered", JSONObject().put("taskId", taskId))) {
      acknowledged += taskId
    }
  }

  // ---------------------------------------------------------------- actions

  fun home() = show(Screen.Home)

  fun clear() = show(Screen.Clear)

  fun openPatient(patientId: String) {
    _selected.value = patientId
    ward.send("patient.select", JSONObject().put("patientId", patientId))
    show(Screen.PatientCard(patientId))
  }

  fun startConsent(patientId: String) {
    if (snapshot?.drafts?.isNotEmpty() == true) {
      show(Screen.Toast("Finish the open note first", false, base()))
      return
    }
    show(Screen.Consent(patientId))
  }

  fun consentGiven(patientId: String) {
    if (ward.send("log.start", JSONObject().put("patientId", patientId).put("consent", true))) {
      recordingStartedAt = System.currentTimeMillis()
      show(Screen.Recording(patientId, recordingStartedAt))
    }
  }

  fun consentRefused(patientId: String) =
      show(Screen.Toast("Not recording", false, Screen.PatientCard(patientId)))

  fun recordingMenu(patientId: String) = show(Screen.RecordingMenu(patientId, recordingStartedAt))

  fun keepRecording(patientId: String) = show(Screen.Recording(patientId, recordingStartedAt))

  /** Stop capture and prepare a draft. Uses deterministic demo text until real transcription exists. */
  fun stopRecording(patientId: String) {
    val s = snapshot ?: return
    val draft = s.drafts.firstOrNull { it.status == "open" && it.patientId == patientId } ?: return
    val reported = s.patient(patientId)?.reported.orEmpty()
    if (ward.send("log.stop", JSONObject().put("encounterId", draft.id).put("reported", reported).put("planned", DEMO_PLANNED))) {
      show(Screen.Drafting(patientId))
    }
  }

  fun saveNote(draftId: String) {
    val d = snapshot?.drafts?.firstOrNull { it.id == draftId } ?: return
    if (ward.send("log.confirm", JSONObject().put("encounterId", d.id).put("reported", d.reported).put("planned", d.planned))) {
      show(Screen.Toast("Note saved to the record", true, Screen.PatientCard(d.patientId)))
    }
  }

  fun discardNote(draftId: String) {
    val d = snapshot?.drafts?.firstOrNull { it.id == draftId } ?: return
    if (ward.send("log.discard", JSONObject().put("encounterId", d.id))) {
      show(Screen.Toast("Note discarded", false, Screen.PatientCard(d.patientId)))
    }
  }

  /** Ask a colleague for help with this patient. */
  fun requestHelp(patientId: String, to: String, summary: String = "Help repositioning the patient") {
    val before = snapshot?.tasks?.map { it.id }?.toSet().orEmpty()
    if (!ward.send("help.request", JSONObject().put("patientId", patientId).put("to", to).put("summary", summary))) return
    // The new task arrives in the next snapshot; follow it once it does.
    timed?.cancel()
    timed =
        scope.launch {
          repeat(20) {
            delay(150)
            val created = snapshot?.tasks?.lastOrNull { it.id !in before && it.to == to }
            if (created != null) {
              show(Screen.Outgoing(created.id))
              return@launch
            }
          }
        }
  }

  fun cancelRequest(taskId: String) {
    if (ward.send("help.cancel", JSONObject().put("taskId", taskId))) show(Screen.Toast("Request cancelled", false, base()))
  }

  fun acceptRequest(taskId: String) {
    if (ward.send("help.response", JSONObject().put("taskId", taskId).put("accept", true))) show(Screen.Briefing(taskId))
  }

  fun declineRequest(taskId: String) {
    if (ward.send("help.response", JSONObject().put("taskId", taskId).put("accept", false))) {
      show(Screen.Toast("Declined. The sender can ask someone else", false, base()))
    }
  }

  fun completeRequest(taskId: String) {
    if (ward.send("help.complete", JSONObject().put("taskId", taskId))) show(Screen.Toast("Marked as done", true, base()))
  }

  fun ask(patientId: String) = show(Screen.AskListening(patientId, ""))

  /** Demo answerer: answers only from this patient's own record, and always names the source. */
  private fun answer(patientId: String): Screen {
    val s = snapshot
    val p = s?.patient(patientId) ?: return Screen.AskAnswer(patientId, "Not found", "No record for this patient", "")
    val note = s.notesFor(patientId).firstOrNull()
    return if (note != null) {
      Screen.AskAnswer(
          patientId,
          "Latest reviewed note",
          note.planned.ifBlank { note.reported },
          "Note, ${note.time}, ${s.name(note.author)}",
      )
    } else {
      Screen.AskAnswer(patientId, "Patient reported", p.reported, "Patient record")
    }
  }

  fun staffMap() = show(Screen.StaffMap)

  fun fallAlert() = show(Screen.FallAlert)

  fun acceptFall() = show(Screen.FallBriefing)

  fun passFall() = show(Screen.Toast("Passed to the next free nurse", false, base()))
}
