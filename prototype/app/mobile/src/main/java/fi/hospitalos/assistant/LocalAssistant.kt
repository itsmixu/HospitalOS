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
      // GPU first (much faster prefill on phones), CPU if the GPU delegate isn't available.
      for (backend in listOf(LlmInference.Backend.GPU, LlmInference.Backend.CPU)) {
        try {
          val opts =
              LlmInference.LlmInferenceOptions.builder()
                  .setModelPath(file.absolutePath)
                  .setMaxTokens(1536)
                  .setPreferredBackend(backend)
                  .build()
          llm = LlmInference.createFromOptions(context, opts)
          this@LocalAssistant.backend = backend.name
          Log.i(TAG, "loaded ${file.name} on $backend")
          break
        } catch (t: Throwable) {
          Log.w(TAG, "load on $backend failed", t)
        }
      }
      if (llm == null) {
        _state.value = ModelState.Failed("Couldn't load ${file.name}")
        return@launch
      }
      // Warm-up: the first inference pays one-off setup costs; do it now, not during the demo.
      runCatching { generate(emptyList(), "Request: hello") }
      _state.value = ModelState.Ready("${file.name} ($backend)")
    }
  }

  private var backend = ""

  /** Whether this model continues a prefilled `{"action":"` (Gemma 3n does; Gemma 3 1B doesn't). */
  @Volatile private var prefillWorks = true

  /** Turn what the nurse said into one action, or Unknown. Runs off the main thread. */
  suspend fun interpret(heard: String, s: Snapshot, patientId: String?): LocalAction =
      withContext(Dispatchers.Default) {
        if (llm == null) return@withContext LocalAction.Unknown
        lock.withLock {
          val t0 = System.currentTimeMillis()
          val raw =
              try {
                val user = context(s, patientId) + "\nRequest: " + heard.trim()
                // Some models (Gemma 3 1B) end their turn right after a prefilled answer: then retry
                // without the prefill, and remember it so later requests don't pay twice.
                val first = generate(examples(s), user, usePrefill = prefillWorks)
                if (first.indexOf('}') >= 0) first
                else if (prefillWorks) {
                  prefillWorks = false
                  Log.i(TAG, "prefill ignored by this model; continuing without it")
                  generate(examples(s), user, usePrefill = false)
                } else first
              } catch (t: Throwable) {
                Log.e(TAG, "inference failed", t)
                ""
              }
          _lastMs.value = System.currentTimeMillis() - t0
          Log.i(TAG, "${_lastMs.value} ms: \"$heard\" -> $raw")
          parse(raw, s)
        }
      }

  /**
   * Fixed test set (none of these phrases are in the prompt's examples). Runs every phrase through
   * the model as if spoken with Patient A's card open; logs and returns accuracy + timing.
   */
  suspend fun runEval(s: Snapshot): String {
    val pid = s.patients.firstOrNull()?.id
    val ben = s.colleagues.firstOrNull { it.name == "Ben" }?.id ?: "ben"
    val sara = s.colleagues.firstOrNull { it.name == "Sara" }?.id ?: "sara"
    val room7 = s.patients.firstOrNull { it.room == "7" }?.id ?: "p2"
    val cases: List<Pair<String, (LocalAction) -> Boolean>> =
        listOf(
            "two nurses to my room now" to { a -> a == LocalAction.RequestHelp(2) },
            "I need a hand in here" to { a -> a == LocalAction.RequestHelp(1) },
            "get three people here asap" to { a -> a == LocalAction.RequestHelp(3) },
            "tell Sara the hoist is in room 7" to { a -> a is LocalAction.SendMessage && a.to == sara },
            "message Ben that I'm going for lunch" to { a -> a is LocalAction.SendMessage && a.to == ben },
            "set me busy" to { a -> a == LocalAction.SetStatus("busy") },
            "I'm free now" to { a -> a == LocalAction.SetStatus("free") },
            "open the patient in room 7" to { a -> a == LocalAction.OpenPatient(room7) },
            "what did this patient report" to { a -> a is LocalAction.Answer },
            "play some music" to { a -> a == LocalAction.Unknown },
        )
    var ok = 0
    var totalMs = 0L
    val lines = mutableListOf<String>()
    for ((phrase, expect) in cases) {
      val a = interpret(phrase, s, pid)
      val ms = _lastMs.value ?: 0
      totalMs += ms
      val pass = expect(a)
      if (pass) ok++
      lines += "${if (pass) "✓" else "✗"} ${ms}ms \"$phrase\" → $a"
    }
    val summary = "Gemma ${_state.value.let { (it as? ModelState.Ready)?.name ?: "?" }}: $ok/${cases.size} right, avg ${totalMs / cases.size} ms"
    Log.i(TAG, "EVAL $summary\n" + lines.joinToString("\n"))
    return summary + "\n" + lines.joinToString("\n")
  }

  /** One worked example, shown to Gemma as a real user/model exchange. */
  private data class Turn(val request: String, val json: String?)

  /**
   * Build a Gemma chat transcript (system rules + worked examples as real turns), then **prefill**
   * the model's reply with `{"action":"` so it can only complete one JSON action. Streams the
   * output and stops as soon as the JSON object closes — no rambling, less decode time.
   */
  private fun generate(examples: List<Turn>, finalUser: String, usePrefill: Boolean = true): String {
    val engine = llm ?: return ""
    val prefill = if (usePrefill) "{\"action\":\"" else ""
    // Fixed part: rules + worked examples + the opening of the final user turn. Same every time.
    val prefix = StringBuilder()
    prefix.append("<start_of_turn>user\n").append(Prompt.RULES)
    examples.forEachIndexed { i, ex ->
      if (i > 0) prefix.append("<start_of_turn>user\n")
      prefix.append("\nRequest: ").append(ex.request).append("<end_of_turn>\n")
      prefix.append("<start_of_turn>model\n").append(ex.json).append("<end_of_turn>\n")
    }
    prefix.append("<start_of_turn>user\n")
    // Variable part: today's context + the request, then the prefilled start of Gemma's answer.
    val suffix = finalUser + "<end_of_turn>\n<start_of_turn>model\n" + prefill

    val session = sessionWithPrefix(engine, prefix.toString())
    try {
      session.addQueryChunk(suffix)
      // With the prefill Gemma finishes right after the JSON, so a plain blocking call is enough.
      // (Cancelling mid-stream left the engine busy and broke the next request.)
      return prefill + session.generateResponse()
    } finally {
      runCatching { session.close() }
    }
  }

  private fun newSession(engine: LlmInference) =
      LlmInferenceSession.createFromOptions(
          engine,
          LlmInferenceSession.LlmInferenceSessionOptions.builder().setTemperature(0.0f).setTopK(1).build(),
      )

  /** Base session holding the fixed prompt, kept between requests and cloned for each one. */
  private var base: LlmInferenceSession? = null
  private var baseKey: String? = null

  /**
   * Prefix caching: the rules + examples are processed once into a base session; each request
   * works on a clone of it, so only the short variable part is processed per request.
   * Falls back to a fresh session if cloning isn't supported.
   */
  private fun sessionWithPrefix(engine: LlmInference, prefix: String): LlmInferenceSession {
    if (baseKey != prefix || base == null) {
      runCatching { base?.close() }
      base = newSession(engine).also { it.addQueryChunk(prefix) }
      baseKey = prefix
    }
    return runCatching { base!!.cloneSession() }.getOrElse {
      Log.w(TAG, "cloneSession unavailable, using a fresh session", it)
      newSession(engine).also { s -> s.addQueryChunk(prefix) }
    }
  }

  private fun jsonComplete(sb: CharSequence): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (c in sb) {
      when {
        escaped -> escaped = false
        c == '\\' -> escaped = true
        c == '"' -> inString = !inString
        !inString && c == '{' -> depth++
        !inString && c == '}' -> if (--depth == 0) return true
      }
    }
    return false
  }

  /** The context Gemma needs, kept short: prefill time is most of the latency on a phone. */
  private fun context(s: Snapshot, patientId: String?): String {
    val colleagues = s.colleagues.joinToString(", ") { "${it.id} (${it.name})" }
    val patients = s.patients.joinToString(", ") { "${it.id} (${it.name}, room ${it.room})" }
    val current = patientId?.let { s.patient(it) }
    val record =
        current?.let { p ->
          val notes = s.notesFor(p.id).take(2).joinToString("; ") { n -> "note ${n.time}: ${n.planned.ifBlank { n.reported }}" }
          "patient reported: ${p.reported}" + (p.warning?.let { "; warning: $it" } ?: "") + (if (notes.isNotBlank()) "; $notes" else "")
        } ?: "none"
    return "Colleagues: $colleagues\nPatients: $patients\nCurrent patient: ${current?.let { "${it.id}, room ${it.room}" } ?: "none"}\nRecord: $record"
  }

  /** Worked examples covering every action, with varied wording and numbers. */
  private fun examples(s: Snapshot): List<Turn> {
    val c1 = s.colleagues.getOrNull(0)
    val c2 = s.colleagues.getOrNull(1) ?: c1
    val other = s.patients.lastOrNull()
    return listOfNotNull(
        Turn("I need two nurses in here now", """{"action":"request_help","count":2}"""),
        c1?.let { Turn("tell ${it.name} the hoist is free", """{"action":"send_message","to":"${it.id}","text":"The hoist is free"}""") },
        Turn("put me on busy", """{"action":"set_status","status":"busy"}"""),
        Turn("can someone give me a hand", """{"action":"request_help","count":1}"""),
        other?.let { Turn("show me room ${it.room}", """{"action":"open_patient","patient":"${it.id}"}""") },
        Turn("I'm available again", """{"action":"set_status","status":"free"}"""),
        c2?.let { Turn("message ${it.name} that I'm going on break", """{"action":"send_message","to":"${it.id}","text":"I'm going on break"}""") },
        Turn("what's the weather like", """{"action":"unknown"}"""),
    )
  }

  private object Prompt {
    const val RULES =
        "You are the voice assistant in a hospital nurse's smart glasses. Turn each spoken request into exactly one JSON action.\n" +
            "Actions:\n" +
            "- request_help {count}: the nurse wants that many colleagues to come to the current patient (\"two nurses\", \"a hand\", \"someone\" = 1).\n" +
            "- send_message {to, text}: tell or message one colleague; \"to\" is the colleague id; \"text\" is the message itself.\n" +
            "- set_status {status}: \"free\" or \"busy\" for the nurse themself (break, busy, available, free).\n" +
            "- open_patient {patient}: show a patient; use the patient id from the list.\n" +
            "- answer {text}: answer a question about the current patient in one short sentence using ONLY the Record.\n" +
            "- unknown: anything else.\n" +
            "Only use ids from the lists. Reply with JSON only.\n"
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
