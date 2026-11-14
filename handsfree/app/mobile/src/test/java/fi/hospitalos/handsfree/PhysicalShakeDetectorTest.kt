package fi.hospitalos.handsfree
import org.junit.Assert.*
import org.junit.Test
class PhysicalShakeDetectorTest {
 @Test fun turnAndReturnEitherDirectionAndAxis(){for(axis in 1..2)for(sign in listOf(-1,1)){val d=PhysicalShakeDetector();var t=1_000_000_000L;var hit=false;for(v in List(5){0.0}+List(8){0.9*sign}+List(8){-0.9*sign}){t+=33_000_000;hit=hit||d.sample("card",t,0.0,if(axis==1)v else 0.0,if(axis==2)v else 0.0)};assertTrue(hit)}}
 @Test fun nodAndSingleTurnDoNotDismiss(){for(mode in 0..1){val d=PhysicalShakeDetector();for(i in 0..15)assertFalse(d.sample("card",1_000_000_000L+i*33_000_000L,if(mode==0)0.9 else 0.0,if(mode==1)0.9 else 0.0,0.0))}}
 @Test fun readingPauseDoesNotCutOffShake(){
  val d=PhysicalShakeDetector();var t=1_000_000_000L
  repeat(88){assertFalse(d.sample("message",t,0.0,0.0,0.0));t+=33_000_000L}
  var hit=false
  for(v in List(8){0.9}+List(8){-0.9}){hit=d.sample("message",t,0.0,v,0.0)||hit;t+=33_000_000L}
  assertTrue(hit)
 }
 @Test fun changedCardsCannotCombineMotion(){val d=PhysicalShakeDetector();for(i in 0..20)assertFalse(d.sample("card$i",1_000_000_000L+i*33_000_000L,0.0,if(i<10)0.9 else -0.9,0.0))}
}
