package fi.hospitalos.handsfree

/** Collect finalized SDK segments until a short quiet gap; partial text can never be committed. */
class UtteranceBuffer(private val settleMs:Long=850) {
  var text="";private set
  var confidence=-1f;private set
  private var lastUpdate=0L
  private var pendingPartial=false
  var overflow=false;private set
  fun accept(value:String,final:Boolean,score:Float,now:Long){
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
  fun complete(now:Long)=!overflow && !pendingPartial && text.isNotBlank() && now-lastUpdate>=settleMs
}
