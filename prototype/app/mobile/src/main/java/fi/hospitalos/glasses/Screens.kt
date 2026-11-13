package fi.hospitalos.glasses

/** Every screen the glasses can show. Mirrors mockups/glasses-scenes.html on the feat/glasses-app branch. */
sealed interface Screen {
  /** Nothing on the lens (see-through). */
  data object Clear : Screen

  /** Shift overview + my patients. */
  data object Home : Screen

  data class PatientCard(val patientId: String) : Screen

  data class Consent(val patientId: String) : Screen

  /** Small corner pill while an encounter is being captured. */
  data class Recording(val patientId: String, val startedAt: Long) : Screen

  /** Tapping the recording pill: stop, ask for help, or keep going. */
  data class RecordingMenu(val patientId: String, val startedAt: Long) : Screen

  data class Drafting(val patientId: String) : Screen

  data class NoteReview(val draftId: String) : Screen

  data class AskListening(val patientId: String, val heard: String) : Screen

  data class AskAnswer(val patientId: String, val title: String, val answer: String, val source: String) : Screen

  /** A request I sent; content follows the task's live status. */
  data class Outgoing(val taskId: String) : Screen

  /** A request sent to me. */
  data class Incoming(val taskId: String) : Screen

  /** Patient briefing after I accept a request (SPEC F2). */
  data class Briefing(val taskId: String) : Screen

  // Demo extras (P1, labelled simulated)
  data object StaffMap : Screen

  data object FallAlert : Screen

  data object FallBriefing : Screen

  data object Offline : Screen

  data class Toast(val text: String, val ok: Boolean, val next: Screen, val millis: Long = 2500) : Screen
}
