package fi.hospitalos.handsfree

import android.os.SystemClock
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.speech.addSpeech
import com.meta.wearable.dat.speech.removeSpeech
import com.meta.wearable.dat.speech.types.SpeechState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

/** Explicit opt-in foreground listening until stopped. No transcript logging or phone fallback. */
class DatSpeechInput(
  private val scope:CoroutineScope,
  private val window:()->SpeechWindow,
  private val result:(SpeechRoute,String)->Unit,
  private val status:(Boolean,String)->Unit,
  private val listening:(SpeechWindow?)->Unit,
) {
  private var job:Job?=null
  val active get()=job?.isActive==true
  fun start(session:DeviceSession) {
    if(job!=null)return
    job=scope.launch {
      var attached=false
      try {
        val granted=Wearables.checkPermissionStatus(Permission.MICROPHONE).fold({it==PermissionStatus.Granted},{_,_->false})
        if(!granted)throw IllegalStateException("Allow glasses microphone in setup first")
        val speech=session.addSpeech().fold({it},{_,_->throw IllegalStateException("Glasses speech unavailable; use computer voice explicitly")})
        attached=true
        status(true,"Glasses voice on · say Hey Lumi · stop anytime")
        coroutineScope {
          coroutineScope {
            val errors=launch {speech.errors.drop(1).collect { if(it!=null)throw IllegalStateException("Glasses speech stopped; reconnect or use computer voice") }}
            try {
              while(isActive) {
                val stamp=window()
                if(!stamp.ready)break
                if(!stamp.rendered || stamp.card.phase in setOf("saving","sending","reconciling")){delay(50);continue}
                // Install collector before start; ignore the previous StateFlow value.
                val buffer=UtteranceBuffer()
                val collector=launch(start=CoroutineStart.UNDISPATCHED) {speech.transcriptions.drop(1).collect{r->if(r!=null)buffer.accept(r.text,r.isFinal,r.confidence,SystemClock.elapsedRealtime())}}
                try {
                  speech.start().onFailure {_,_->throw IllegalStateException("Glasses speech could not start")}
                  withTimeout(2000){speech.state.first{it==SpeechState.STARTED}}
                  listening(stamp)
                  val heard=withTimeoutOrNull(if(stamp.card.phase.startsWith("dictating-"))20_000 else 12_000) {
                    while(!buffer.complete(SystemClock.elapsedRealtime()) && !buffer.overflow && window()==stamp)delay(25)
                    if(buffer.complete(SystemClock.elapsedRealtime()))buffer else null
                  }
                  speech.stop()
                  withTimeout(2000){speech.state.first{it==SpeechState.STOPPED}}
                  if(heard!=null) {
                    val route=SpeechGate.route(heard.text,stamp,window(),heard.confidence)
                    if(route==SpeechRoute.STOP)break
                    result(route,SpeechGate.normalize(heard.text))
                  }
                } finally {listening(null);collector.cancel();speech.stop()}
                delay(100)
              }
            } finally {errors.cancel()}
          }
        }
        status(false,"Glasses microphone off · enable again when needed")
      } catch(e:CancellationException){throw e}
      catch(e:Exception){status(false,e.message?:"Glasses speech unavailable")}
      finally {listening(null);if(attached)runCatching{session.removeSpeech()};job=null;status(false,"")}
    }
  }
  fun stop(){job?.cancel();status(false,"Glasses microphone off")}
}
