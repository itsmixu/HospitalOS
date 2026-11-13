package fi.hospitalos.assistant

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import fi.hospitalos.ward.Snapshot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What the on-phone model understood. The phone executes it as a normal ward command. */
sealed interface LocalAction {
  data class RequestHelp(val count: Int) : LocalAction

  data class SendMessage(val to: String, val text: String) : LocalAction

  data class SetStatus(val status: String) : LocalAction

  data class OpenPatient(val patientId: String) : LocalAction

  data class Answer(val text: String) : LocalAction

  data object Unknown : LocalAction
}

sealed interface ModelState {
  data object Missing : ModelState

  data object Loading : ModelState

  data class Ready(val name: String) : ModelState

  data class Failed(val reason: String) : ModelState
}

/**
 * Voice assistant brain running **on the phone**: Gemma via Google's MediaPipe LLM Inference.
 *
 * Nothing leaves the phone except the resulting ward command, and that command still goes through
 * the server's permission checks (SPEC F5) — the model only *proposes* one action as JSON, which is
 * validated against ids the user is actually allowed to see before anything is sent.
 *
 * The model file isn't in the APK (license + size). Put a MediaPipe `.task` model, e.g.
 * `gemma3-1b-it-int4.task`, in the app's files folder:
 *   adb push gemma3-1b-it-int4.task /sdcard/Android/data/fi.hospitalos.demo/files/
 */
class LocalAssistant(private val context: Context, private val scope: CoroutineScope) {
  private companion object {
    const val TAG = "HospitalOSGemma"
  }

  private val _state = MutableStateFlow<ModelState>(ModelState.Missing)
  val state: StateFlow<ModelState> = _state.asStateFlow()

  /** Last inference time, shown on the stage remote. */
  private val _lastMs = MutableStateFlow<Long?>(null)
  val lastMs: StateFlow<Long?> = _lastMs.asStateFlow()

  private var llm: LlmInference? = null
  private val lock = Mutex()

  val isReady: Boolean
    get() = _state.value is ModelState.Ready

  fun modelFolder(): File = context.getExternalFilesDir(null) ?: context.filesDir

  /** Find and load a `.task` model from the app's files folder. Safe to call again. */
  fun load() {
    if (_state.value is ModelState.Loading || _state.value is ModelState.Ready) return
    val file = modelFolder().listFiles()?.filter { it.name.endsWith(".task") }?.maxByOrNull { it.length() }
    if (file == null) {
      _state.value = ModelState.Missing
      return
    }
    _state.value = ModelState.Loading
    scope.launch(Dispatchers.Default) {
      try {
        val opts = LlmInference.LlmInferenceOptions.builder().setModelPath(file.absolutePath).setMaxTokens(1024).build()
        llm = LlmInference.createFromOptions(context, opts)
        _state.value = ModelState.Ready(file.name)
        Log.i(TAG, "loaded ${file.name}")
      } catch (t: Throwable) {
        Log.e(TAG, "load failed", t)
        _state.value = ModelState.Failed(t.message ?: "Couldn't load ${file.name}")
      }
    }
  }

  /** Turn what the nurse said into one action, or Unknown. Runs off the main thread. */
  suspend fun interpret(heard: String, s: Snapshot, patientId: String?): LocalAction =
      withContext(Dispatchers.Default) {
        val engine = llm ?: return@withContext LocalAction.Unknown
        lock.withLock {
          val t0 = System.currentTimeMillis()
          val raw =
              try {
                // Fresh low-temperature session per request: deterministic, no cross-talk.
                val session =
                    LlmInferenceSession.createFromOptions(
                        engine,
                        LlmInferenceSession.LlmInferenceSessionOptions.builder().setTemperature(0.0f).setTopK(1).build(),
                    )
                // Gemma instruct turn format; without it the small model tends to echo the first example.
                session.addQueryChunk("<start_of_turn>user\n" + prompt(heard, s, patientId) + "<end_of_turn>\n<start_of_turn>model\n")
                val out = session.generateResponse()
                session.close()
                out
              } catch (t: Throwable) {
                Log.e(TAG, "inference failed", t)
                ""
              }
          _lastMs.value = System.currentTimeMillis() - t0
          Log.i(TAG, "${_lastMs.value} ms: \"$heard\" -> $raw")
          parse(raw, s)
        }
      }

  private fun prompt(heard: String, s: Snapshot, patientId: String?): String {
    val colleagues = s.colleagues.joinToString(", ") { "${it.id}=${it.name}" }
    val patients = s.patients.joinToString(", ") { "${it.id}=${it.name} (room ${it.room})" }
    val current = patientId?.let { s.patient(it) }
    val record =
        current?.let { p ->
          val notes = s.notesFor(p.id).take(2).joinToString("; ") { n -> "note ${n.time} by ${s.name(n.author)}: ${n.planned.ifBlank { n.reported }}" }
          "Patient reported: ${p.reported}" + (p.warning?.let { "; warning: $it" } ?: "") + (if (notes.isNotBlank()) "; $notes" else "")
        } ?: "none"
    return """
You turn a hospital nurse's spoken request into ONE JSON action. Reply with the JSON only, nothing else.
Actions:
{"action":"request_help","count":N}   get N colleagues to come to the current patient's room
{"action":"send_message","to":"<colleague id>","text":"<message>"}
{"action":"set_status","status":"free"}   or "busy"
{"action":"open_patient","patient":"<patient id>"}
{"action":"answer","text":"<one short sentence using ONLY the record below>"}
{"action":"unknown"}
Colleagues: $colleagues
Patients: $patients
Current patient: ${current?.let { "${it.id} (room ${it.room})" } ?: "none"}
Record for current patient: $record
Examples:
Nurse: "two nurses to my room now" -> {"action":"request_help","count":2}
Nurse: "I need a hand here" -> {"action":"request_help","count":1}
Nurse: "tell Sara I'm on break" -> {"action":"send_message","to":"sara","text":"I'm on break"}
Nurse: "message Ben the bed in room 12 is free" -> {"action":"send_message","to":"ben","text":"The bed in room 12 is free"}
Nurse: "get someone to help me lift" -> {"action":"request_help","count":1}
Nurse: "set me busy" -> {"action":"set_status","status":"busy"}
Nurse: "open the patient in room 7" -> {"action":"open_patient","patient":"p2"}
Nurse: "what did the patient report" -> {"action":"answer","text":"Patient reported dizziness earlier today."}
Nurse: "${heard.replace("\"", "'")}" ->
""".trimIndent()
  }

  /** Take the first JSON object and validate it against what this user may actually see. */
  private fun parse(raw: String, s: Snapshot): LocalAction {
    val start = raw.indexOf('{')
    val end = raw.indexOf('}', start + 1)
    if (start < 0 || end < 0) return LocalAction.Unknown
    val o = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return LocalAction.Unknown
    return when (o.optString("action")) {
      "request_help" -> LocalAction.RequestHelp(o.optInt("count", 1).coerceIn(1, 4))
      "send_message" -> {
        val to = o.optString("to").lowercase()
        val who = s.colleagues.firstOrNull { it.id == to || it.name.lowercase() == to }
        val text = o.optString("text").trim()
        if (who != null && text.isNotBlank()) LocalAction.SendMessage(who.id, text) else LocalAction.Unknown
      }
      "set_status" -> o.optString("status").takeIf { it == "free" || it == "busy" }?.let { LocalAction.SetStatus(it) } ?: LocalAction.Unknown
      "open_patient" -> o.optString("patient").takeIf { id -> s.patients.any { it.id == id } }?.let { LocalAction.OpenPatient(it) } ?: LocalAction.Unknown
      "answer" -> o.optString("text").trim().takeIf { it.isNotBlank() }?.let { LocalAction.Answer(it) } ?: LocalAction.Unknown
      else -> LocalAction.Unknown
    }
  }

  fun close() {
    runCatching { llm?.close() }
    llm = null
  }
}
