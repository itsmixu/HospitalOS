package fi.hospitalos.handsfree
import kotlin.math.abs
/** Relative turn and return; dismissal only, never authorizes a ward mutation. */
class PhysicalShakeDetector {
 private var key="";private var last=0L;private var began=0L;private var axis=0;private var angle=0.0;private var peak=0.0
 fun reset(){key="";last=0;axis=0;angle=0.0;peak=0.0}
 fun sample(cardKey:String,t:Long,x:Double,y:Double,z:Double):Boolean {
  if(!x.isFinite()||!y.isFinite()||!z.isFinite()||abs(x)>1.2||abs(y)>3.5||abs(z)>3.5){reset();return false}
  if(cardKey!=key){reset();key=cardKey;last=t;began=t;return false}
  val dt=(t-last)/1e9;last=t
  if(dt<=0||dt>0.2||(axis!=0 && t-began>3_000_000_000L)){reset();return false}
  if(axis==0){if(maxOf(abs(y),abs(z))<0.25)return false;axis=if(abs(y)>abs(z))1 else 2;began=t}
  angle+=(if(axis==1)y else z)*dt*180/Math.PI
  if(abs(angle)>abs(peak))peak=angle
  if(abs(peak)>55){reset();return false}
  if(abs(peak)>=12 && (peak-angle)*(if(peak>0)1 else -1)>=12 && t-began>=250_000_000L){reset();return true}
  return false
 }
}
