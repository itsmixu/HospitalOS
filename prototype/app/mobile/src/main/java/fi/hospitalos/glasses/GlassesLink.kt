package fi.hospitalos.glasses

import android.app.Activity
import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.removeDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.ContentScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the glasses connection is, in the order the user moves through it. */
enum class GlassesStep(val label: String) {
  NEEDS_PERMISSION("Bluetooth permission needed"),
  NOT_REGISTERED("Not connected to Meta AI"),
  REGISTERING("Waiting for Meta AI…"),
  REGISTERED("Ready to start display"),
  STARTING("Starting display…"),
  ON("Display on"),
  PAUSED("Paused: glasses off or folded"),
}

/**
 * Owns the Meta Wearables SDK: init, registration with Meta AI, and one Display session.
 * [onDisplayReady] fires every time the display (re)starts so the current screen can be re-sent.
 */
class GlassesLink(
    private val scope: CoroutineScope,
    private val onDisplayReady: () -> Unit,
) {
  private companion object {
    const val TAG = "HospitalOSGlasses"
  }

  private var initialized = false
  private var session: DeviceSession? = null
  private var display: Display? = null
  private val jobs = mutableListOf<Job>()
  private val sendLock = Mutex()

  private val _step = MutableStateFlow(GlassesStep.NEEDS_PERMISSION)
  val step: StateFlow<GlassesStep> = _step.asStateFlow()

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  val isDisplayOn: Boolean
    get() = _step.value == GlassesStep.ON

  /** Call after BLUETOOTH_CONNECT is granted. Safe to call more than once. */
  fun initialize(context: Context) {
    if (initialized) return
    Wearables.initialize(context)
    initialized = true
    scope.launch {
      Wearables.registrationState.collect { state ->
        if (session != null) return@collect
        _step.value =
            when (state) {
              RegistrationState.REGISTERED -> GlassesStep.REGISTERED
              RegistrationState.REGISTERING -> GlassesStep.REGISTERING
              else -> GlassesStep.NOT_REGISTERED
            }
      }
    }
  }

  fun register(activity: Activity) {
    if (!initialized) {
      _messages.tryEmit("Allow Bluetooth first")
      return
    }
    Wearables.startRegistration(activity)
  }

  fun unregister(activity: Activity) {
    stopDisplay()
    if (initialized) Wearables.startUnregistration(activity)
  }

  fun startDisplay() {
    if (!initialized || Wearables.registrationState.value != RegistrationState.REGISTERED) {
      _messages.tryEmit("Connect to Meta AI first")
      return
    }
    stopDisplay()
    _step.value = GlassesStep.STARTING
    Wearables.createSession(AutoDeviceSelector(filter = { it.isDisplayCapable() }))
        .onSuccess { s ->
          session = s
          jobs += scope.launch { s.errors.collect { _messages.tryEmit("Glasses: ${it.description}") } }
          jobs +=
              scope.launch {
                s.state.collect { state ->
                  Log.i(TAG, "Session $state")
                  when (state) {
                    DeviceSessionState.STARTED -> if (display == null) attachDisplay(s) else _step.value = GlassesStep.ON
                    DeviceSessionState.PAUSED -> _step.value = GlassesStep.PAUSED
                    DeviceSessionState.STOPPED -> {
                      display = null
                      session = null
                      _step.value = GlassesStep.REGISTERED
                    }
                    else -> Unit
                  }
                }
              }
          s.start()
        }
        .onFailure { error, _ ->
          _step.value = GlassesStep.REGISTERED
          _messages.tryEmit("Couldn't start display: ${error.description}")
        }
  }

  private fun attachDisplay(s: DeviceSession) {
    s.addDisplay()
        .onSuccess { d ->
          display = d
          jobs +=
              scope.launch {
                d.state.collect { state ->
                  Log.i(TAG, "Display $state")
                  if (state == DisplayState.STARTED) {
                    _step.value = GlassesStep.ON
                    onDisplayReady()
                  }
                }
              }
        }
        .onFailure { error, _ -> _messages.tryEmit("Couldn't attach display: ${error.description}") }
  }

  fun stopDisplay() {
    jobs.forEach { it.cancel() }
    jobs.clear()
    session?.let { s ->
      runCatching { s.removeDisplay() }
      s.stop()
    }
    session = null
    display = null
    if (initialized) {
      _step.value =
          if (Wearables.registrationState.value == RegistrationState.REGISTERED) GlassesStep.REGISTERED
          else GlassesStep.NOT_REGISTERED
    }
  }

  /** Replace what's on the glasses. Sends are serialized so screens never arrive out of order. */
  fun show(content: ContentScope.() -> Unit) {
    val d = display ?: return
    scope.launch(Dispatchers.IO) {
      sendLock.withLock {
        d.sendContent(content).onFailure { error, _ -> _messages.tryEmit("Display: ${error.description}") }
      }
    }
  }
}
