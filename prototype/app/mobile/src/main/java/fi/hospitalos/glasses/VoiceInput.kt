package fi.hospitalos.glasses

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.speech.Speech
import com.meta.wearable.dat.speech.addSpeech
import com.meta.wearable.dat.speech.removeSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VoiceSource(val label: String) {
  NONE("No voice input"),
  GLASSES("Glasses speech (Meta, on-device)"),
  PHONE("Phone speech recognizer"),
}

/**
 * Short spoken input for messages, notes and the assistant.
 *
 * Primary: Meta's on-glasses speech recognition (DAT Speech, beta — needs the MICROPHONE
 * permission granted in the Meta AI app; stops after ~1 minute, which is fine for short input).
 * Fallback: Android's SpeechRecognizer on the phone (needs RECORD_AUDIO), used when glasses
 * speech isn't available. Meta doesn't provide a language model, so understanding happens on
 * the ward server (Claude) — this class only turns speech into text.
 */
class VoiceInput(private val context: Context, private val scope: CoroutineScope) {
  private companion object {
    const val TAG = "HospitalOSVoice"
    const val SILENCE_TIMEOUT_MS = 12_000L
  }

  private val _partial = MutableStateFlow("")
  val partial: StateFlow<String> = _partial.asStateFlow()

  private val _listening = MutableStateFlow(false)
  val listening: StateFlow<Boolean> = _listening.asStateFlow()

  private val _source = MutableStateFlow(VoiceSource.NONE)
  val source: StateFlow<VoiceSource> = _source.asStateFlow()

  private var speech: Speech? = null
  private var job: Job? = null
  private var timeout: Job? = null
  private var onFinal: ((String) -> Unit)? = null
  private var onFail: ((String) -> Unit)? = null

  private val main = Handler(Looper.getMainLooper())
  private var phone: SpeechRecognizer? = null

  /** Attach glasses speech once the session is STARTED. */
  fun attach(session: DeviceSession, onMessage: (String) -> Unit) {
    detach(session)
    session
        .addSpeech()
        .onSuccess { s ->
          speech = s
          job =
              scope.launch {
                s.transcriptions.collect { r ->
                  r ?: return@collect
                  if (!_listening.value) return@collect
                  _partial.value = r.text
                  if (r.isFinal && r.text.isNotBlank()) finish(r.text)
                }
              }
          _source.value = VoiceSource.GLASSES
        }
        .onFailure { error, _ ->
          onMessage("Glasses speech unavailable (${error.description}); using the phone microphone")
          _source.value = if (SpeechRecognizer.isRecognitionAvailable(context)) VoiceSource.PHONE else VoiceSource.NONE
        }
  }

  fun detach(session: DeviceSession?) {
    cancel()
    job?.cancel()
    job = null
    speech?.let { s ->
      runCatching { s.stop() }
      session?.let { runCatching { it.removeSpeech() } }
    }
    speech = null
    _source.value = VoiceSource.NONE
  }

  /** Listen for one utterance. [onFinal] gets the text; [onFail] a short reason. */
  fun listen(onFinal: (String) -> Unit, onFail: (String) -> Unit) {
    cancel()
    this.onFinal = onFinal
    this.onFail = onFail
    _partial.value = ""
    _listening.value = true
    val s = speech
    if (s != null) {
      s.start().onFailure { error, _ ->
        Log.w(TAG, "glasses speech start failed: ${error.description}")
        // Most often the MICROPHONE permission hasn't been granted in Meta AI yet.
        startPhone()
      }
    } else {
      startPhone()
    }
    timeout =
        scope.launch {
          delay(SILENCE_TIMEOUT_MS)
          if (_listening.value) {
            val heard = _partial.value
            if (heard.isNotBlank()) finish(heard) else fail("Didn't hear anything")
          }
        }
  }

  fun cancel() {
    timeout?.cancel()
    _listening.value = false
    runCatching { speech?.stop() }
    main.post { runCatching { phone?.cancel() } }
    onFinal = null
    onFail = null
  }

  private fun finish(text: String) {
    val cb = onFinal
    cancel()
    _partial.value = text
    cb?.invoke(text.trim())
  }

  private fun fail(reason: String) {
    val cb = onFail
    cancel()
    cb?.invoke(reason)
  }

  private fun startPhone() {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      fail("No speech recognition available")
      return
    }
    main.post {
      val r = phone ?: SpeechRecognizer.createSpeechRecognizer(context).also { phone = it }
      r.setRecognitionListener(
          object : RecognitionListener {
            override fun onPartialResults(b: Bundle?) {
              b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { _partial.value = it }
            }

            override fun onResults(b: Bundle?) {
              val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
              if (text.isNotBlank()) finish(text) else fail("Didn't catch that")
            }

            override fun onError(error: Int) {
              if (_listening.value) fail(if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "Allow the microphone under Connect" else "Didn't catch that")
            }

            override fun onReadyForSpeech(p: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(v: Float) = Unit
            override fun onBufferReceived(b: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(t: Int, p: Bundle?) = Unit
          },
      )
      r.startListening(
          Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
              .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
              .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
      )
      if (_source.value == VoiceSource.NONE) _source.value = VoiceSource.PHONE
    }
  }
}
