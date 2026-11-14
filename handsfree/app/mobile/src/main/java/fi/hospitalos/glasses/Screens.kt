package fi.hospitalos.glasses

/**
 * Every screen on the glasses (glasses-first UX):
 * Home → Patients / Message / Requests / Assistant, with your free/busy status on Home.
 * Interruptions (incoming help, new message, fall alert) return you to where you were.
 */
sealed interface Screen {
  /** Nothing on the lens (see-through). */
  data object Clear : Screen

  data object Home : Screen

  // Patients
  data object Patients : Screen

  data class PatientCard(val patientId: String) : Screen

  data class Consent(val patientId: String) : Screen

  /** Listening for a short spoken care note (encounter already started with consent). */
  data class Dictating(val patientId: String) : Screen

  data class Drafting(val patientId: String) : Screen

  data class NoteReview(val draftId: String) : Screen

  // Messages
  data object PickColleague : Screen

  data class SpeakMessage(val to: String) : Screen

  data class ConfirmMessage(val to: String, val text: String) : Screen

  // Requests
  data object Requests : Screen

  /** A request I sent; content follows the task's live status. */
  data class Outgoing(val taskId: String) : Screen

  /** Help I asked for with "Get help": shows who was asked and their status. */
  data class HelpSent(val patientId: String, val taskIds: List<String>) : Screen

  // Voice assistant
  /** Listening; [patientId] is the context ("my room") if a patient was open. */
  data class AssistantListening(val patientId: String?) : Screen

  data class AssistantThinking(val heard: String) : Screen

  data class AssistantAnswer(val heard: String, val say: String) : Screen

  // Interruptions — shown over anything, then [GlassesFlow.resume] returns to the saved screen.
  data class Incoming(val taskId: String) : Screen

  /** Briefing after I accept a request (SPEC F2). */
  data class Briefing(val taskId: String) : Screen

  data class MessageIn(val messageId: String) : Screen

  data object FallAlert : Screen

  data object FallBriefing : Screen

  // Demo extras / states
  data object StaffMap : Screen

  data object Offline : Screen

  data class Toast(val text: String, val ok: Boolean, val next: Screen?, val millis: Long = 2500) : Screen
}

/** Screens that pop up over whatever you were doing. */
val Screen.isInterruption: Boolean
  get() =
      this is Screen.Incoming ||
          this is Screen.Briefing ||
          this is Screen.MessageIn ||
          this is Screen.FallAlert ||
          this is Screen.FallBriefing
