package fi.hospitalos.handsfree
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class TiltFeedbackTest {
 private var t=1_000_000_000L
 private var now=1000L
 private val card=Card("review","Confirm patient","Demo patient","Tilt right to confirm",true,7)
 private fun step(f:TiltFeedback,roll:Double,c:Card=card,speed:Double=0.0):TiltAction?{t+=33_000_000;now+=33;val r=roll*PI/180;return f.sample(c,t,now,9.81*sin(r),9.81*cos(r),0.0,speed)}
 private fun arm(f:TiltFeedback){repeat(15){assertNull(step(f,0.0))}}
 @Test fun partialAngleFillsWithoutTimerOrActionAndRetreats(){val f=TiltFeedback();arm(f);step(f,-12.0);assertEquals(.4,f.progress.right,0.001);repeat(50){assertNull(step(f,-12.0));assertEquals(.4,f.progress.right,0.001)};step(f,-10.0);assertEquals(.2,f.progress.right,0.001);step(f,-8.0);assertEquals(TiltProgress(),f.progress)}
 @Test fun reachingThresholdDoesNotBypassStabilityHold(){val f=TiltFeedback();arm(f);repeat(5){assertNull(step(f,-22.0));assertEquals(1.0,f.progress.right,0.001)};step(f,-12.0);assertEquals(.4,f.progress.right,0.001);repeat(15){assertNull(step(f,0.0))}}
 @Test fun heldGestureFiresOnceAndRequiresUprightAgain(){val f=TiltFeedback();arm(f);var hits=0;repeat(60){if(step(f,-22.0)==TiltAction.ACCEPT)hits++};assertEquals(1,hits);assertEquals(TiltProgress(),f.progress);arm(f);repeat(15){if(step(f,22.0)==TiltAction.BACK)hits++};assertEquals(2,hits)}
 @Test fun changingOnlyBodyStillResetsFeedbackAndPendingAction(){val f=TiltFeedback();arm(f);repeat(5){step(f,22.0)};val changed=card.copy(body="Different patient");f.sync(changed,true,now);assertEquals(TiltProgress(),f.progress);repeat(30){assertNull(step(f,22.0,changed))}}
 @Test fun disconnectAndStaleMotionResetWithoutDispatch(){val f=TiltFeedback();arm(f);step(f,-14.0);f.sync(card,false,now);assertEquals(TiltProgress(),f.progress);repeat(15){assertNull(step(f,-22.0))};arm(f);step(f,12.0);f.sync(card,true,now+251);assertEquals(TiltProgress(),f.progress)}
 @Test fun movementAndInvalidSamplesNeverShowProgress(){val f=TiltFeedback();arm(f);step(f,-12.0);assertNull(step(f,-22.0,speed=1.0));assertEquals(TiltProgress(),f.progress);assertNull(f.sample(card,t+33000000,now+33,Double.NaN,9.81,0.0,0.0));assertEquals(TiltProgress(),f.progress)}
 @Test fun unavailableRightActionDoesNotOfferGreenConfirmation(){val f=TiltFeedback();arm(f);step(f,-12.0);assertEquals(.4,f.visible(true).right,.001);assertEquals(0.0,f.visible(false).right,.001)}
 @Test fun railUpdatesDoNotChangePromptOrCreateDuplicateEngineActions(){val sent=mutableListOf<Action>();val engine=HandsFreeEngine("aino",{now}){_,a->sent.add(a)};engine.quietMode=true;engine.connection(true);engine.snapshot(listOf(Colleague("aino","Aino",true),Colleague("ben","Ben",true)),listOf(Request("r","ben","aino","12","Help","delivered",1)));engine.offerIncoming();val c=engine.card;engine.reviewDisplayed(c.promptId);val f=TiltFeedback();repeat(15){step(f,0.0,c)};repeat(50){step(f,-12.0,c);assertEquals(c,engine.card)};repeat(60){val action=step(f,-22.0,c);if(action==TiltAction.ACCEPT)engine.physicalAccept(c.promptId)};assertEquals(1,sent.size);assertEquals("request.accept",sent.single().type)}
 @Test fun declineLabelIsContextual(){val e=HandsFreeEngine("aino",{now}){_,_->};e.connection(true);e.snapshot(listOf(Colleague("ben","Ben",true)),listOf(Request("r","ben","aino","12","Help","delivered",1)));e.offerIncoming();assertEquals("decline",e.tiltBackLabel);e.dismissScreen();e.wake();assertEquals("back / later",e.tiltBackLabel);e.snapshot(emptyList(),emptyList(),emergencies=listOf(Emergency("e","ben","8","active",1,"2026-11-14T12:00:00Z","2026-11-14T12:00:00Z","pending")));assertEquals("decline",e.tiltBackLabel)}
}
