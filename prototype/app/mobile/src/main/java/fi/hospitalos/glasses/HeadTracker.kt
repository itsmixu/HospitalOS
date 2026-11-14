package fi.hospitalos.glasses

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.motion.Motion
import com.meta.wearable.dat.motion.addMotion
import com.meta.wearable.dat.motion.removeMotion
import com.meta.wearable.dat.motion.types.MotionConfiguration
import com.meta.wearable.dat.motion.types.MotionSample
import com.meta.wearable.dat.motion.types.MotionSamplingRate
import com.meta.wearable.dat.motion.types.MotionSource
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HeadingSource(val label: String) {
  NONE("No head tracking"),
  GLASSES("Glasses gyroscope"),
  PHONE("Phone sensors (fallback)"),
}

/**
 * How far the wearer has turned since [reset], in degrees. Positive = turned left.
 *
 * Ray-Ban glasses report no magnetometer through DAT, so there's no true compass: we integrate
 * the gyroscope around the gravity axis (from the accelerometer). That's frame-independent and
 * fine for "turn left / straight ahead" over a short walk; it drifts slowly, so [reset] when an
 * alert opens. If glasses Motion isn't available (it's a beta capability), the phone's own
 * rotation sensor is used instead — only meaningful if the phone is held facing forward.
 */
class HeadTracker(private val context: Context, private val scope: CoroutineScope) {
  private val _turn = MutableStateFlow(0f)
  val turnDegrees: StateFlow<Float> = _turn.asStateFlow()

  private val _source = MutableStateFlow(HeadingSource.NONE)
  val source: StateFlow<HeadingSource> = _source.asStateFlow()

  private var motion: Motion? = null
  private var job: Job? = null

  // Glasses integration state
  private var yawRad = 0.0
  private var lastNs = 0L
  private val up = DoubleArray(3)
  private var upReady = false

  // Phone fallback state
  private var phoneZero: Float? = null
  private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
  private val phoneListener =
      object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
          val r = FloatArray(9)
          SensorManager.getRotationMatrixFromVector(r, e.values)
          val o = FloatArray(3)
          SensorManager.getOrientation(r, o)
          val azimuth = Math.toDegrees(o[0].toDouble()).toFloat() // clockwise from north
          val zero = phoneZero ?: azimuth.also { phoneZero = it }
          _turn.value = normalize(zero - azimuth) // turning left = positive
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
      }

  /** Call once the glasses session is STARTED. Falls back to phone sensors on failure. */
  fun attach(session: DeviceSession, onMessage: (String) -> Unit) {
    detach(session)
    session
        .addMotion(MotionConfiguration(samplingRate = MotionSamplingRate.HZ_30))
        .onSuccess { m ->
          motion = m
          job = scope.launch { m.samples.collect { onSample(it) } }
          m.start()
          _source.value = HeadingSource.GLASSES
        }
        .onFailure { error, _ ->
          onMessage("Glasses head tracking unavailable (${error.description}); using phone sensors")
          startPhone()
        }
  }

  fun detach(session: DeviceSession?) {
    job?.cancel()
    job = null
    motion?.let { m ->
      runCatching { m.stop() }
      session?.let { runCatching { it.removeMotion() } }
    }
    motion = null
    stopPhone()
    _source.value = HeadingSource.NONE
  }

  /** "I'm facing up the map now." */
  fun reset() {
    yawRad = 0.0
    phoneZero = null
    _turn.value = 0f
  }

  private fun onSample(s: MotionSample) {
    if (s.source == MotionSource.NEURAL_BAND) return // wrist motion isn't head motion
    s.accelerometer?.let { a ->
      // Low-pass the accelerometer to get gravity. At rest it points up (opposes gravity).
      val k = if (upReady) 0.1 else 1.0
      up[0] += k * (a.x - up[0])
      up[1] += k * (a.y - up[1])
      up[2] += k * (a.z - up[2])
      upReady = true
    }
    val g = s.gyroscope
    if (g != null && upReady && lastNs != 0L) {
      val dt = (s.timestampNs - lastNs) / 1e9
      val n = sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2])
      if (dt in 0.0..0.5 && n > 1e-3) {
        // Angular velocity around "up" (right-hand rule: positive = turning left).
        val yawRate = (g.x * up[0] + g.y * up[1] + g.z * up[2]) / n
        yawRad += yawRate * dt
        _turn.value = normalize(Math.toDegrees(yawRad).toFloat())
      }
    }
    lastNs = s.timestampNs
  }

  private fun startPhone() {
    val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
    phoneZero = null
    sensors.registerListener(phoneListener, sensor, SensorManager.SENSOR_DELAY_UI)
    _source.value = HeadingSource.PHONE
  }

  private fun stopPhone() = sensors.unregisterListener(phoneListener)

  private fun normalize(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
  }
}
