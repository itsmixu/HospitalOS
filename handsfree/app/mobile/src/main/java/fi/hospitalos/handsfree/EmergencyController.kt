package fi.hospitalos.handsfree

data class Isbar(val identify:String,val situation:String,val background:String,val assessment:String,val recommendation:String)
data class Emergency(val id:String,val from:String,val room:String,val status:String,val revision:Int,val createdAt:String,val updatedAt:String,val response:String,val isbar:Isbar?=null,val briefingUnavailable:Boolean=false)

/** Separate priority surface. A response never grants general patient-record access. */
class EmergencyController(private val me:String,private val clock:()->Long,private val busy:()->Boolean,private val interrupt:()->Unit,private val close:()->Unit,private val send:(Action)->Unit){
 var card:Card?=null;private set
 val active get()=card!=null
 private var events=emptyList<Emergency>();private var selected:String?=null;private val hidden=mutableSetOf<String>();private var page=0;private var sequence=800000;private var visiblePrompt=0;private var waiting=false;private var deferredInterrupt=false;private var statusUntil=0L
 fun connection(online:Boolean){if(!online){events=emptyList();selected=null;card=null;waiting=false;visiblePrompt=0;deferredInterrupt=false}}
 fun snapshot(list:List<Emergency>){if(events.find{it.id==selected}?.revision!=list.find{it.id==selected}?.revision){visiblePrompt=0;card=null};events=list;if(events.find{it.id==selected}?.response in setOf("acknowledged","declined"))waiting=false;refresh()}
 fun refresh(){
  if(deferredInterrupt&&!busy()){deferredInterrupt=false;interrupt()}
  val current=events.find{it.id==selected}
  if(selected!=null&&(current==null||current.status!="active"||current.briefingUnavailable)){
   hidden.add(selected!!);selected=null;waiting=false;statusUntil=clock()+8000;show(Card("emergency-status",if(current?.briefingUnavailable==true)"Handover unavailable" else "Code Blue resolved",current?.let{"Room ${it.room}"}?:"Event no longer available","Tilt left to close",true));return
  }
  if(current!=null&&current.response=="declined"&&!waiting){hidden.add(current.id);selected=null;statusUntil=clock()+5000;show(Card("emergency-status","Response recorded","Room ${current.room} · declined","Tilt left to close",true));return}
  if(selected==null){
   val next=events.filter{it.status=="active"&&it.from!=me&&it.response=="pending"&&!it.briefingUnavailable&&it.id !in hidden}.minByOrNull{it.createdAt}
   if(next!=null){selected=next.id;page=0;waiting=false;statusUntil=0;deferredInterrupt=busy();if(!deferredInterrupt)interrupt()}
   else {if(statusUntil>0&&clock()>=statusUntil){statusUntil=0;card=null;close()};return}
  }
  val e=events.find{it.id==selected}?:return
  if(e.response=="acknowledged"){
   val report=e.isbar
   if(report==null){show(Card("emergency-briefing","ISBAR unavailable","Room ${e.room} · response acknowledged","Tilt left to close",true));return}
   val pages=pages(e,report);page=page.coerceIn(0,pages.lastIndex)
   show(Card("emergency-briefing","Room ${e.room} · ISBAR ${page+1}/${pages.size}",pages[page],"Tilt right / say next · left to close",true))
  }else show(Card("emergency-alert","CODE BLUE · Room ${e.room}","Response requested\nReported by ${e.from}\n${e.createdAt}",if(waiting)"Waiting for ward acknowledgement" else if(busy())"Prior action unresolved · say check outcome" else "Tilt right to respond · left to decline",!waiting))
 }
 private fun pages(e:Emergency,r:Isbar):List<String>{
  val sections=listOf("I" to r.identify,"S" to r.situation,"B" to r.background,"A" to r.assessment,"R" to r.recommendation)
  val result=mutableListOf<String>();var packed=""
  for((label,text) in sections)for(part in text.chunked(150)){
   val line="$label · $part"
   if(packed.isNotEmpty() && packed.length+line.length+1>180){result.add(packed);packed=""}
   packed=if(packed.isEmpty())line else "$packed\n$line"
  }
  if(packed.isNotEmpty())result.add(packed)
  return result+"Team report · ${e.from}\nUpdated ${e.updatedAt}\nNo automatic treatment advice"
 }
 private fun show(value:Card){if(card?.copy(promptId=0)==value.copy(promptId=0))return;visiblePrompt=0;card=value.copy(promptId=++sequence)}
 fun displayed(prompt:Int){if(card?.promptId==prompt)visiblePrompt=prompt}
 fun right(prompt:Int){if(card?.promptId!=prompt||visiblePrompt!=prompt)return;if(card?.phase=="emergency-briefing"){page++;refresh();return};respond(true)}
 fun left(prompt:Int){if(card?.promptId!=prompt||visiblePrompt!=prompt)return;if(card?.phase=="emergency-alert")respond(false)else hide()}
 private fun respond(accept:Boolean){val e=events.find{it.id==selected}?:return;if(waiting||busy()||e.status!="active"||e.briefingUnavailable||e.response!="pending")return;waiting=true;refresh();send(Action(if(accept)"emergency.acknowledge" else "emergency.decline",fields=mapOf("emergencyId" to e.id,"revision" to e.revision)))}
 fun outcomeUnknown(){waiting=false;refresh()}
 fun acknowledged(ok:Boolean){if(!ok || events.find{it.id==selected}?.response in setOf("acknowledged","declined"))waiting=false;if(!ok){visiblePrompt=0;card=null};refresh()}
 fun open(id:String?=null):Boolean{val e=events.filter{it.status=="active"&&!it.briefingUnavailable&&it.from!=me&&(id==null||it.id==id)}.lastOrNull()?:return false;selected=e.id;hidden.remove(e.id);page=0;statusUntil=0;deferredInterrupt=busy();if(!deferredInterrupt)interrupt();refresh();return true}
 fun command(words:String):Boolean {if(!active)return false;when(words){"next"->{if(card?.phase=="emergency-briefing"){page++;refresh()}};"previous"->{if(card?.phase=="emergency-briefing"){page=(page-1).coerceAtLeast(0);refresh()}};"accept emergency","accept request","respond"->right(card!!.promptId);"decline emergency","decline request"->left(card!!.promptId);"back","cancel","close briefing"->hide()};return true}
 private fun hide(){if(waiting||busy())return;selected?.let{hidden.add(it)};selected=null;card=null;statusUntil=0;visiblePrompt=0;close()}
}
