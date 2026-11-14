package fi.hospitalos.handsfree

/** Collect finalized SDK segments until a short quiet gap; partial text can never be committed. */
class UtteranceBuffer(private val settleMs:Long=850) {
  var text="";private set
  var confidence=-1f;private set
  private var lastUpdate=0L
  private var pendingPartial=false
  private var partialText=""
  private var partialConfidence=-1f
  private var partialChangedAt=0L
  var overflow=false;private set
  fun accept(value:String,final:Boolean,score:Float,now:Long){
    // Empty SDK updates contain no speech and must not postpone completed words.
    if(value.isBlank())return
    if(!final){
      if(value.trim()!=partialText)partialChangedAt=now
      partialText=value.trim();partialConfidence=score
    }else {partialText="";partialConfidence=-1f}
    lastUpdate=now
    if(final && value.isBlank())return
    pendingPartial=!final
    if(!final)return
    val next=value.trim()
    // SDKs may send cumulative finals or separate finalized segments.
    text=when{ text.isEmpty()->next;next==text->text;next.startsWith(text+" ")->next;else->"$text $next" }
    if(text.length>200){overflow=true;text=""}
    if(!score.isFinite() || (score != -1f && score !in 0f..1f))confidence=Float.NaN
    else if(score>=0 && confidence.isFinite())confidence=if(confidence<0)score else minOf(confidence,score)
  }
  fun stableDraft(now:Long):String? = partialText.takeIf {
    pendingPartial && text.isEmpty() && !overflow && it.length in 1..200 &&
      partialConfidence.isFinite() && partialConfidence in 0.6f..1f && now-partialChangedAt>=2000
  }
  fun complete(now:Long)=!overflow && !pendingPartial && text.isNotBlank() && now-lastUpdate>=settleMs
}
