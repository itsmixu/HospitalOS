package fi.hospitalos.handsfree

import kotlin.math.abs

/** Calibrated orientation samples, NOT raw DAT IMU. Physical adapter/calibration is a hardware gate. */
data class HeadSample(val promptId: Int, val time: Long, val pitch: Double, val yaw: Double, val source: String = "glasses", val moving: Boolean = false)
class HeadGestureDetector {
  private var prompt = 0
  private var expires = 0L
  private var last = -1L
  private var neutralSince = -1L
  private var ready = false
  private var phase = 0
  private var axis = ""
  private var began = 0L
  private var previousPitch = 0.0
  private var previousYaw = 0.0
  fun arm(id: Int, now: Long, until: Long) { disarm(); prompt=id; expires=until; last=-1 }
  fun disarm() { prompt=0; expires=0; last=-1; neutralSince=-1; ready=false; phase=0; axis=""; began=0; previousPitch=0.0; previousYaw=0.0 }
  fun sample(s: HeadSample, now: Long): Boolean? {
    if(prompt == 0 || s.promptId != prompt) return null
    if(now >= expires || s.time > now+50 || now-s.time > 250 || s.source !in setOf("glasses", "simulation") || s.moving || !s.pitch.isFinite() || !s.yaw.isFinite() || s.time <= last || (last>=0 && s.time-last>250) || abs(s.pitch)>40 || abs(s.yaw)>40) { disarm(); return null }
    val dt = (s.time-last).coerceAtLeast(1)
    if(last >= 0 && (abs(s.pitch-previousPitch)*1000/dt>180 || abs(s.yaw-previousYaw)*1000/dt>180)) { disarm(); return null }
    last=s.time; previousPitch=s.pitch; previousYaw=s.yaw
    val neutral = abs(s.pitch)<5 && abs(s.yaw)<5
    if(!ready) {
      if(!neutral) { neutralSince=-1; return null }
      if(neutralSince<0) neutralSince=s.time
      if(s.time-neutralSince>=300) ready=true
      return null
    }
    if(axis.isEmpty()) {
      if(s.pitch>=14 && abs(s.yaw)<8) { axis="pitch"; phase=1; began=s.time }
      else if(s.yaw>=14 && abs(s.pitch)<8) { axis="yaw"; phase=1; began=s.time }
      else if(abs(s.yaw)>=8 && abs(s.pitch)>=8) disarm()
      return null
    }
    if(s.time-began>2500 || (axis=="pitch" && abs(s.yaw)>8) || (axis=="yaw" && abs(s.pitch)>8)) { disarm(); return null }
    if(axis=="pitch") {
      if(phase==1 && neutral) phase=2
      else if(phase==2 && s.pitch>=14) phase=3
      else if(phase==3 && neutral && s.time-began>=450) { disarm(); return true }
    } else {
      if(phase==1 && s.yaw<=-14) phase=2
      else if(phase==2 && neutral && s.time-began>=350) { disarm(); return false }
    }
    return null
  }
}
