package fi.hospitalos.glasses

import fi.hospitalos.assistant.LocalAction
import fi.hospitalos.assistant.LocalAssistant
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * What the wearer sees and does. The ward server is the source of truth; this class decides the
 * screen, turns glasses actions into server commands, and runs voice input + the assistant.
 */
class GlassesFlow(
    private val scope: CoroutineScope,
    private val ward: WardClient,
    private val glasses: GlassesLink,
    /** On-phone Gemma. When it isn't loaded (or doesn't understand), the server assistant answers. */
    private val local: LocalAssistant,
) {
  /** Who answered the last request, for the stage remote ("Gemma on phone, 850 ms"). */
  private val _lastBrain = MutableStateFlow<String?>(null)
  val lastBrain: StateFlow<String?> = _lastBrain.asStateFlow()

  companion object {
    const val WARD_NAME = "Ward 4B"
  }

  private val _screen = MutableStateFlow<Screen>(Screen.Home)
  val screen: StateFlow<Screen> = _screen.asStateFlow()

  /** Patient whose card is open — "my room" for Get help and the assistant. */
  private val _selected = MutableStateFlow<String?>(null)
  val selected: StateFlow<String?> = _selected.asStateFlow()

  /** Where to go back to after an interruption or the assistant. */
  private var saved: Screen? = null

  private val seenIncoming = mutableSetOf<String>()
  private val seenMessages = mutableSetOf<String>()
  private val acknowledged = mutableSetOf<String>()
  private var timed: Job? = null
  private val renderer = Renderer(this)

  val snapshot: Snapshot?
    get() = ward.snapshot.value

  /** How far the wearer has turned left since the fall alert opened (degrees). */
  val turnDegrees: Float
    get() = glasses.head.turnDegrees.value

  /** Live words while listening, for the lens and the stage mirror. */
  val heardSoFar: StateFlow<String>
    get() = glasses.voice.partial

  init {
    scope.launch { ward.snapshot.collect { s -> if (s != null) onSnapshot(s) } }
    scope.launch {
      var wasOnline = false
      ward.link.collect { link ->
        when {
          link == Link.OFFLINE && wasOnline -> show(Screen.Offline)
          link == Link.ONLINE && !wasOnline && _screen.value == Screen.Offline ->
              show(Screen.Toast("Back online", ok = true, next = Screen.Home))
        }
        wasOnline = link == Link.ONLINE
      }
    }
    // Live transcript on the lens while listening (only re-sent when the words change).
    scope.launch {
      glasses.voice.partial.collect {
        val s = _screen.value
        if (s is Screen.Dictating || s is Screen.SpeakMessage || s is Screen.AssistantListening) render()
      }
    }
    // Head-turning map for the fall alert; GlassesLink keeps only the newest pending send.
    scope.launch {
      var sentTurn = 0f
      glasses.head.turnDegrees.collect { turn ->
        val s = _screen.value
        if (s != Screen.FallAlert && s != Screen.FallBriefing) return@collect
        if (kotlin.math.abs(turn - sentTurn) >= 5f) {
          sentTurn = turn
          render()
        }
      }
    }
  }

  // ---------------------------------------------------------------- core

  fun show(screen: Screen) {
    timed?.cancel()
    val current = _screen.value
    // Remember where we were when something pops up over it.
    if (screen.isInterruption && !current.isInterruption && current !is Screen.Toast) saved = current
    _screen.value = screen
    render()
    timed = scope.launch { afterShown(screen) }
  }

  /** Back to where you were before an interruption / the assistant. */
  fun resume() {
    val back = saved ?: Screen.Home
    saved = null
    show(back)
  }

  fun render() {
    val s = snapshot
    val current = _screen.value
    glasses.show { with(renderer) { render(current, s) } }
    if (current is Screen.Incoming) acknowledge(current.taskId)
  }

  private suspend fun afterShown(screen: Screen) {
    when (screen) {
      is Screen.Toast -> {
        delay(screen.millis)
        if (screen.next != null) show(screen.next) else resume()
      }
      is Screen.AssistantAnswer -> {
        delay(7000)
        resume()
      }
      is Screen.Outgoing -> if (task(screen.taskId)?.status == "accepted") {
        delay(5000)
        resume()
      }
      else -> Unit
    }
  }

  private fun onSnapshot(s: Snapshot) {
    // New request for me → pop up (and acknowledge delivery once it's on the lens).
    s.incoming.firstOrNull { it.status == "sent" && it.id !in seenIncoming }?.let {
      seenIncoming += it.id
      show(Screen.Incoming(it.id))
      return
    }
    // New message for me → pop up.
    s.unreadMessages.firstOrNull { it.id !in seenMessages }?.let {
      seenMessages += it.id
      show(Screen.MessageIn(it.id))
      return
    }
    when (val current = _screen.value) {
      is Screen.Outgoing -> {
        val t = s.tasks.firstOrNull { it.id == current.taskId }
        when (t?.status) {
          "completed" -> show(Screen.Toast("${s.name(t.to)} finished: ${t.summary ?: "request"}", true, null, 4000))
          "accepted" -> show(current)
          else -> render()
        }
      }
      is Screen.HelpSent -> {
        val tasks = current.taskIds.mapNotNull { id -> s.tasks.firstOrNull { it.id == id } }
        if (tasks.isNotEmpty() && tasks.all { it.status == "completed" || it.status == "cancelled" }) {
          val done = tasks.filter { it.status == "completed" }.joinToString(" and ") { s.name(it.to) }
          show(Screen.Toast(if (done.isBlank()) "Nobody could come" else "$done finished", done.isNotBlank(), Screen.PatientCard(current.patientId), 4000))
        } else render()
      }
      is Screen.Drafting -> {
        val review = s.drafts.firstOrNull { it.status == "review" && it.patientId == current.patientId }
        if (review != null) show(Screen.NoteReview(review.id)) else render()
      }
      is Screen.Briefing, is Screen.Incoming -> {
        val id = if (current is Screen.Briefing) current.taskId else (current as Screen.Incoming).taskId
        val t = s.tasks.firstOrNull { it.id == id }
        if (t == null || t.status == "cancelled") show(Screen.Toast("Request was cancelled", false, null)) else render()
      }
      else -> render()
    }
  }

  fun task(id: String): Task? = snapshot?.tasks?.firstOrNull { it.id == id }

  private fun acknowledge(taskId: String) {
    if (!glasses.isDisplayOn || taskId in acknowledged) return
    val t = task(taskId) ?: return
    if (t.status == "sent" && ward.send("message.delivered", JSONObject().put("taskId", taskId))) acknowledged += taskId
  }

  // ---------------------------------------------------------------- home & status

  fun home() {
    saved = null
    show(Screen.Home)
  }

  fun clear() = show(Screen.Clear)

  fun toggleStatus() {
    val next = if (snapshot?.me?.status == "busy") "free" else "busy"
    ward.send("presence.update", JSONObject().put("status", next))
  }

  // ---------------------------------------------------------------- patients & notes

  fun patients() = show(Screen.Patients)

  fun openPatient(patientId: String) {
    _selected.value = patientId
    ward.send("patient.select", JSONObject().put("patientId", patientId))
    show(Screen.PatientCard(patientId))
  }

  fun startNote(patientId: String) {
    if (snapshot?.drafts?.isNotEmpty() == true) {
      show(Screen.Toast("Finish the open note first", false, Screen.PatientCard(patientId)))
      return
    }
    show(Screen.Consent(patientId))
  }

  /** Consent given: start the encounter, then listen for a short spoken note. */
  fun consentGiven(patientId: String) {
    if (!ward.send("log.start", JSONObject().put("patientId", patientId).put("consent", true))) return
    show(Screen.Dictating(patientId))
    glasses.voice.listen(
        onFinal = { text -> noteHeard(patientId, text) },
        onFail = { reason -> show(Screen.Toast("$reason. Tap Speak again", false, Screen.Dictating(patientId))) },
    )
  }

  fun dictateAgain(patientId: String) {
    show(Screen.Dictating(patientId))
    glasses.voice.listen(onFinal = { noteHeard(patientId, it) }, onFail = { show(Screen.Toast("$it", false, Screen.Dictating(patientId))) })
  }

  private fun noteHeard(patientId: String, text: String) {
    scope.launch {
      // The open encounter arrives in a snapshot right after log.start.
      val draft =
          withTimeoutOrNull(3000) {
            ward.snapshot.first { s -> s?.drafts?.any { it.patientId == patientId && it.status == "open" } == true }
          }?.drafts?.first { it.patientId == patientId && it.status == "open" }
      if (draft == null) {
        show(Screen.Toast("Note couldn't start", false, Screen.PatientCard(patientId)))
        return@launch
      }
      // Spoken notes are plans/observations; completed care is only what colleagues confirm (SPEC F3).
      if (ward.send("log.stop", JSONObject().put("encounterId", draft.id).put("reported", "").put("planned", text))) {
        show(Screen.Drafting(patientId))
      }
    }
  }

  fun consentRefused(patientId: String) = show(Screen.Toast("Not recording", false, Screen.PatientCard(patientId)))

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

  /** "Get help": the server picks a free colleague assigned to this patient. */
  fun getHelp(patientId: String, count: Int = 1) {
    val room = snapshot?.patient(patientId)?.room ?: "?"
    val before = snapshot?.tasks?.map { it.id }?.toSet().orEmpty()
    if (!ward.send("help.auto", JSONObject().put("patientId", patientId).put("count", count).put("summary", "Help needed in Room $room"))) return
    show(Screen.Toast("Finding a free colleague…", true, null, 3000))
    followNewTasks(before) { ids -> show(Screen.HelpSent(patientId, ids)) }
  }

  private fun followNewTasks(before: Set<String>, then: (List<String>) -> Unit) {
    timed?.cancel()
    timed =
        scope.launch {
          repeat(20) {
            delay(150)
            val created = snapshot?.tasks?.filter { it.id !in before && it.from == snapshot?.me?.id }.orEmpty()
            if (created.isNotEmpty()) {
              then(created.map { it.id })
              return@launch
            }
          }
          // No task appeared: the server said why (e.g. "Nobody free") via the error notice.
          show(Screen.Toast("Nobody free right now", false, null))
        }
  }

  // ---------------------------------------------------------------- messages

  fun pickColleague() = show(Screen.PickColleague)

  fun speakMessage(to: String) {
    show(Screen.SpeakMessage(to))
    glasses.voice.listen(
        onFinal = { text -> show(Screen.ConfirmMessage(to, text)) },
        onFail = { reason -> show(Screen.Toast(reason, false, Screen.PickColleague)) },
    )
  }

  fun sendMessage(to: String, text: String) {
    if (ward.send("msg.send", JSONObject().put("to", to).put("text", text))) {
      show(Screen.Toast("Sent to ${snapshot?.name(to) ?: to}", true, Screen.Home))
    }
  }

  fun markRead(messageId: String) {
    ward.send("msg.read", JSONObject().put("messageId", messageId))
    resume()
  }

  fun reply(messageId: String) {
    val m = snapshot?.messages?.firstOrNull { it.id == messageId } ?: return resume()
    ward.send("msg.read", JSONObject().put("messageId", messageId))
    saved = null
    speakMessage(m.from)
  }

  // ---------------------------------------------------------------- requests

  fun requests() = show(Screen.Requests)

  fun showOutgoing(taskId: String) = show(Screen.Outgoing(taskId))

  fun showIncoming(taskId: String) =
      show(if (task(taskId)?.status == "accepted") Screen.Briefing(taskId) else Screen.Incoming(taskId))

  fun cancelRequest(taskId: String) {
    if (ward.send("help.cancel", JSONObject().put("taskId", taskId))) show(Screen.Toast("Request cancelled", false, null))
  }

  fun acceptRequest(taskId: String) {
    if (ward.send("help.response", JSONObject().put("taskId", taskId).put("accept", true))) show(Screen.Briefing(taskId))
  }

  fun declineRequest(taskId: String) {
    if (ward.send("help.response", JSONObject().put("taskId", taskId).put("accept", false))) {
      show(Screen.Toast("Declined. They'll ask someone else", false, null))
    }
  }

  fun completeRequest(taskId: String) {
    if (ward.send("help.complete", JSONObject().put("taskId", taskId))) show(Screen.Toast("Marked as done", true, null))
  }

  // ---------------------------------------------------------------- voice assistant

  /** "Two nurses to my room now", "tell Ben…", "set me busy", "what did they report?" */
  fun assistant() {
    val current = _screen.value
    if (current !is Screen.AssistantListening && current !is Screen.AssistantAnswer) saved = current
    val context = (current as? Screen.PatientCard)?.patientId ?: _selected.value.takeIf { current is Screen.PatientCard }
    timed?.cancel()
    _screen.value = Screen.AssistantListening(context)
    render()
    glasses.voice.listen(
        onFinal = { text -> askAssistant(text, context) },
        onFail = { reason -> show(Screen.Toast(reason, false, null)) },
    )
  }

  /** Also used by the phone's typed fallback during the demo. */
  fun askAssistant(text: String, patientId: String? = _selected.value) {
    if (_screen.value !is Screen.AssistantListening && _screen.value !is Screen.AssistantAnswer) saved = _screen.value
    timed?.cancel()
    _screen.value = Screen.AssistantThinking(text)
    render()
    if (local.isReady) {
      timed = scope.launch { askLocal(text, patientId) }
      return
    }
    askServer(text, patientId)
  }

  /** On-phone Gemma proposes one action; the phone sends it as a normal (permission-checked) command. */
  private suspend fun askLocal(text: String, patientId: String?) {
    val s = snapshot ?: return askServer(text, patientId)
    val action = local.interpret(text, s, patientId)
    _lastBrain.value = "Gemma on phone, ${local.lastMs.value ?: "?"} ms"
    val say: String =
        when (action) {
          is LocalAction.RequestHelp -> {
            if (patientId == null) "Which patient? Open their card first."
            else {
              getHelp(patientId, action.count)
              return
            }
          }
          is LocalAction.SendMessage ->
              if (ward.send("msg.send", JSONObject().put("to", action.to).put("text", action.text))) "Sent to ${s.name(action.to)}: \"${action.text}\"" else "Not sent"
          is LocalAction.SetStatus ->
              if (ward.send("presence.update", JSONObject().put("status", action.status))) "You're now ${action.status}." else "Not changed"
          is LocalAction.OpenPatient -> {
            _screen.value = Screen.AssistantAnswer(text, "Opening ${s.patient(action.patientId)?.name}")
            render()
            delay(1500)
            openPatient(action.patientId)
            return
          }
          is LocalAction.Answer -> action.text
          LocalAction.Unknown -> {
            // The small model didn't get it: let the server assistant (rules / Claude) try.
            askServer(text, patientId)
            return
          }
        }
    _screen.value = Screen.AssistantAnswer(text, say)
    render()
    delay(7000)
    if (_screen.value is Screen.AssistantAnswer) resume()
  }

  private fun askServer(text: String, patientId: String?) {
    if (!ward.ask(text, patientId)) return
    timed?.cancel()
    _screen.value = Screen.AssistantThinking(text)
    render()
    timed =
        scope.launch {
          val reply = withTimeoutOrNull(20_000) { ward.replies.first() }
          if (reply == null) {
            show(Screen.Toast("The assistant didn't answer", false, null))
            return@launch
          }
          _lastBrain.value = if (reply.usedClaude) "Claude on the server" else "Rule parser on the server"
          _screen.value = Screen.AssistantAnswer(reply.heard, reply.say)
          render()
          delay(if (reply.openPatientId != null) 2500 else 7000)
          if (reply.openPatientId != null) openPatient(reply.openPatientId) else resume()
        }
  }

  fun cancelListening() {
    glasses.voice.cancel()
    resume()
  }

  // ---------------------------------------------------------------- demo extras

  fun staffMap() = show(Screen.StaffMap)

  /** The wearer is assumed to face map-up when the alert opens, so start counting turns now. */
  fun fallAlert() {
    glasses.head.reset()
    show(Screen.FallAlert)
  }

  fun resetDirection() {
    glasses.head.reset()
    render()
  }

  fun acceptFall() = show(Screen.FallBriefing)

  fun passFall() = show(Screen.Toast("Passed to the next free nurse", false, null))
}
