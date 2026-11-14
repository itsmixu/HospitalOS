package fi.hospitalos.ward

import org.json.JSONArray
import org.json.JSONObject

/** What the ward server sends this user (already permission-filtered server-side). */
data class Snapshot(
    val me: StaffRef,
    val staff: List<StaffRef>,
    val patients: List<Patient>,
    val tasks: List<Task>,
    val drafts: List<Draft>,
    val notes: List<Note>,
    val messages: List<Message> = emptyList(),
) {
  fun patient(id: String?) = patients.firstOrNull { it.id == id }

  fun name(staffId: String) = staff.firstOrNull { it.id == staffId }?.name ?: staffId

  /** Messages sent to me that I haven't read, oldest first. */
  val unreadMessages: List<Message>
    get() = messages.filter { it.to == me.id && it.status == "sent" }

  /** Colleagues I can ask for help (not me, not the ward overview account). */
  val colleagues: List<StaffRef>
    get() = staff.filter { it.id != me.id && it.id != "observer" }

  /** Requests other people sent to me that still need something from me. */
  val incoming: List<Task>
    get() = tasks.filter { it.to == me.id && it.status in setOf("sent", "delivered", "accepted") }

  /** Requests I sent, newest first. */
  val outgoing: List<Task>
    get() = tasks.filter { it.from == me.id }.reversed()

  fun notesFor(patientId: String) = notes.filter { it.patientId == patientId }.reversed()

  companion object {
    fun parse(o: JSONObject): Snapshot {
      val me = o.optJSONObject("me")?.let(::staffRef) ?: StaffRef("", "Unknown")
      return Snapshot(
          me = me,
          staff = o.optJSONArray("staff").objects().map(::staffRef),
          patients = o.optJSONArray("patients").objects().map(::patient),
          tasks = o.optJSONArray("tasks").objects().map(::task),
          drafts = o.optJSONArray("drafts").objects().map(::draft),
          notes = o.optJSONArray("notes").objects().map(::note),
          messages = o.optJSONArray("messages").objects().map(::message),
      )
    }

    private fun staffRef(o: JSONObject) =
        StaffRef(o.optString("id"), o.optString("name"), o.optString("status").ifBlank { null }.takeIf { !o.isNull("status") })

    private fun message(o: JSONObject) =
        Message(o.optString("id"), o.optString("from"), o.optString("to"), o.optString("text"), o.optString("at"), o.optString("status"))

    private fun patient(o: JSONObject) =
        Patient(
            id = o.optString("id"),
            name = o.optString("name"),
            room = o.optString("room"),
            age = o.optInt("age").takeIf { it > 0 },
            warning = o.optString("warning").ifBlank { null },
            summary = o.optString("summary").ifBlank { null },
            reported = o.optString("reported"),
        )

    private fun task(o: JSONObject) =
        Task(
            id = o.optString("id"),
            patientId = o.optString("patientId").ifBlank { null },
            room = o.optString("room").ifBlank { null },
            from = o.optString("from"),
            to = o.optString("to"),
            status = o.optString("status"),
            summary = o.optString("summary").ifBlank { null },
        )

    private fun draft(o: JSONObject) =
        Draft(
            id = o.optString("id"),
            patientId = o.optString("patientId"),
            status = o.optString("status"),
            reported = o.optString("reported"),
            planned = o.optString("planned"),
        )

    private fun note(o: JSONObject) =
        Note(
            patientId = o.optString("patientId"),
            author = o.optString("author"),
            reported = o.optString("reported"),
            planned = o.optString("planned"),
            completed = o.optJSONArray("completed")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            at = o.optString("at"),
        )

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
  }
}

/** A colleague. [status] is "free" / "busy", or null for the ward overview account. */
data class StaffRef(val id: String, val name: String, val status: String? = null) {
  val isFree: Boolean
    get() = status == "free"
}

/** Direct message between staff (not patient-linked). */
data class Message(val id: String, val from: String, val to: String, val text: String, val at: String, val status: String)

data class Patient(
    val id: String,
    val name: String,
    val room: String,
    val age: Int?,
    val warning: String?,
    val summary: String?,
    val reported: String,
) {
  val title: String
    get() = if (age != null) "$name, $age" else name
}

/** A help request. Tasks for patients I'm not assigned to arrive without patientId/summary. */
data class Task(
    val id: String,
    val patientId: String?,
    val room: String?,
    val from: String,
    val to: String,
    val status: String,
    val summary: String?,
)

data class Draft(
    val id: String,
    val patientId: String,
    val status: String,
    val reported: String,
    val planned: String,
)

data class Note(
    val patientId: String,
    val author: String,
    val reported: String,
    val planned: String,
    val completed: List<String>,
    val at: String,
) {
  /** "HH:MM" from the ISO timestamp. */
  val time: String
    get() = at.substringAfter('T', "").take(5).ifBlank { "Now" }
}
