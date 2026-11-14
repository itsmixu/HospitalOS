package fi.hospitalos.handsfree

/** Feedback never changes a Card, prompt identity, confirmation deadline or action. */
class TiltFeedback {
 private val detector=PhysicalTiltDetector()
 private var card:Card?=null
 private var sampledAt=0L
 var progress=TiltProgress();private set
 val status get()=detector.status
 val degrees get()=detector.degrees
 fun reset(){detector.reset();card=null;sampledAt=0;progress=TiltProgress()}
 fun sync(current:Card,eligible:Boolean,now:Long){
  if(!eligible || card!=current || (sampledAt!=0L && now-sampledAt>250)){reset();card=if(eligible)current else null}
 }
 fun sample(current:Card,t:Long,now:Long,x:Double,y:Double,z:Double,speed:Double):TiltAction?{
  sync(current,true,now);sampledAt=now
  val result=detector.sample(current.toString(),t,x,y,z,speed)
  progress=detector.progress.quantized();return result
 }
 fun visible(rightAvailable:Boolean)=progress.copy(right=if(rightAvailable)progress.right else 0.0)
}
