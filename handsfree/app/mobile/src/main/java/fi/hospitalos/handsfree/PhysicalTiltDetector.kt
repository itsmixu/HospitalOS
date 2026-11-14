package fi.hospitalos.handsfree
import kotlin.math.*
enum class TiltAction { BACK, ACCEPT }
/** Held, card-bound tilt with mandatory neutral rearming. */
class PhysicalTiltDetector {
 companion object { const val NEUTRAL_DEGREES=8.0; const val TRIGGER_DEGREES=18.0 }
 var progress=TiltProgress();private set
 private var key="";private var last=0L;private var neutralSince=0L;private var heldSince=0L;private var armed=false;private var direction=0
 var degrees=0.0; private set
 var status="Waiting for upright"; private set
 fun reset(){progress=TiltProgress();key="";last=0;neutralSince=0;heldSince=0;armed=false;direction=0}
 fun sample(cardKey:String,t:Long,x:Double,y:Double,z:Double,speed:Double):TiltAction? {
  progress=TiltProgress()
  val norm=sqrt(x*x+y*y+z*z)
  if(!norm.isFinite() || norm !in 8.0..11.5 || !speed.isFinite()){status="Invalid acceleration or speed";reset();return null}
  if(cardKey!=key || (last!=0L && (t<=last || t-last>200_000_000L))){status="New card or sample gap";reset();key=cardKey}
  last=t
  // DAT worn frame: +Y up, X lateral, -Z forward. Confirm left sign on hardware.
  degrees=atan2(x,y)*180/PI
  if(abs(z)/norm>0.65 || speed>0.6){status="Moving or pitched too far";heldSince=0;neutralSince=0;return null}
  if(!armed){
   status="Return upright: ${degrees.toInt()} degrees"
   if(abs(degrees)<=NEUTRAL_DEGREES){if(neutralSince==0L)neutralSince=t;if(t-neutralSince>=300_000_000L){armed=true;status="Armed"}}else neutralSince=0
   return null
  }
  if(abs(degrees)<=40.0){
   val fill=((abs(degrees)-NEUTRAL_DEGREES)/(TRIGGER_DEGREES-NEUTRAL_DEGREES)).coerceIn(0.0,1.0)
   progress=if(degrees>0)TiltProgress(left=fill)else TiltProgress(right=fill)
  }
  status="Armed: ${degrees.toInt()} degrees"
  if(abs(degrees) in TRIGGER_DEGREES..40.0){
   status="Holding tilt"
   val side=if(degrees>0)1 else -1
   if(direction!=side){direction=side;heldSince=0}
   if(heldSince==0L)heldSince=t
   if(t-heldSince>=350_000_000L){val result=if(degrees>0)TiltAction.BACK else TiltAction.ACCEPT;reset();return result}
  }else heldSince=0
  return null
 }
}

/** Measured displacement only. The detector's existing hold still controls activation. */
data class TiltProgress(val left:Double=0.0,val right:Double=0.0) {
 fun quantized()=TiltProgress(kotlin.math.round(left*20)/20,kotlin.math.round(right*20)/20)
}
