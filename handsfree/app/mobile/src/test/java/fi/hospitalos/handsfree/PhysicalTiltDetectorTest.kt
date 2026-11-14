package fi.hospitalos.handsfree
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
class PhysicalTiltDetectorTest {
 private var t=1_000_000_000L
 private fun step(d:PhysicalTiltDetector,deg:Double,key:String="message",speed:Double=0.0):TiltAction?{t+=33_000_000L;return d.sample(key,t,9.81*sin(deg*PI/180),9.81*cos(deg*PI/180),0.0,speed)}
 private fun arm(d:PhysicalTiltDetector){repeat(15){assertNull(step(d,0.0))}}
 @Test fun leftHoldClosesAfterReadingPause(){val d=PhysicalTiltDetector();arm(d);repeat(150){assertNull(step(d,0.0))};var hit=false;repeat(14){hit=(step(d,22.0)==TiltAction.BACK)||hit};assertTrue(hit)}
 @Test fun rightAndBriefTiltDoNotClose(){val d=PhysicalTiltDetector();arm(d);repeat(5){assertNull(step(d,-22.0))};assertNull(step(d,0.0));repeat(5){assertNull(step(d,22.0))};assertNull(step(d,0.0))}
 @Test fun requiresNeutralAndCannotCarryAcrossCards(){val d=PhysicalTiltDetector();repeat(30){assertNull(step(d,22.0))};arm(d);repeat(5){assertNull(step(d,22.0))};repeat(30){assertNull(step(d,22.0,"other"))}}
 @Test fun rightAcceptRequiresNeutralAgain(){val d=PhysicalTiltDetector();arm(d);var hits=0;repeat(50){if(step(d,-22.0)==TiltAction.ACCEPT)hits++};assertEquals(1,hits);repeat(30){assertNull(step(d,-22.0,"next"))};arm(d);var accepted=false;repeat(14){accepted=(step(d,-22.0)==TiltAction.ACCEPT)||accepted};assertTrue(accepted)}
 @Test fun movingDoesNotClose(){val d=PhysicalTiltDetector();arm(d);repeat(30){assertNull(step(d,22.0,speed=1.0))}}
}
