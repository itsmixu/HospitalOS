package fi.hospitalos.handsfree

import fi.hospitalos.handsfree.capture.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Medication(val name:String,val dose:String,val route:String,val schedule:String,val prescribedBy:String,val prescribedAt:String)
data class CareEvent(val kind:String,val description:String,val at:String,val author:String)
data class Patient(val id:String,val name:String,val encounterId:String,val room:String,val warning:String?=null,val reported:String?=null,val admissionReason:String?=null,val allergies:List<String> = emptyList(),val medications:List<Medication> = emptyList(),val careHistory:List<CareEvent> = emptyList())
data class PatientContext(val patientId:String,val encounterId:String,val contextRevision:Int)
data class CaptureTicket(val id:String,val context:PatientContext,val started:Long,val capturedAt:String,val source:String)
data class SavedReading(val id:String,val patientId:String,val values:Reading,val capturedAt:String,val corrected:Boolean)
data class SavedNote(val id:String,val patientId:String,val text:String,val savedAt:String,val author:String="Not supplied")
data class Candidate(val ticket:CaptureTicket,val original:Reading,val values:Reading)

/** Pure local bedside state. Display acceptance gates both camera activation and every mutation. */
class BedsideController(private val clock:()->Long,private val wall:()->Long,private val send:(String,Action)->Unit) {
  var card:Card?=null;private set
  var ticket:CaptureTicket?=null;private set
  var qrScanId:String?=null;private set
  var context:PatientContext?=null;private set
  var candidate:Candidate?=null;private set
  var savedId:String?=null;private set
  private var patients=emptyList<Patient>();private var people=emptyList<Colleague>();private var records=emptyList<SavedReading>()
  private var online=false;private var visible=false;private var deadline=0L;private var sequence=100000
  private var draft:Action?=null;private var pending:String?=null;private var pendingAction:Action?=null
  private var savedNotes=emptyList<SavedNote>()
  private var noteText:String?=null
  private var noteDraftId:String?=null
  private var noteSaved=false
  private var selected:Patient?=null;private val gesture=HeadGestureDetector()
  val assignedPatients get() = patients
  val currentPatient get() = context?.let { c -> patients.singleOrNull { it.id == c.patientId && it.encounterId == c.encounterId } }
  fun latestNote(patientId:String) = savedNotes.filter { it.patientId == patientId }.maxByOrNull { it.savedAt }
  fun latestReading(patientId:String) = records.filter { it.patientId == patientId }.maxByOrNull { it.capturedAt }
  var cameraSource="injected-camera"
  private var lastCaptureIssue="No stable complete reading"
  val hasPending get()=pending!=null
  fun interruptForEmergency(){if(pending==null){invalidate("Interrupted by emergency",false);card=null}}
  val blocksCommunication get()=card?.phase in setOf("review","camera","camera-qr","saving","dictating-note")
  fun snapshot(list:List<Patient>,staff:List<Colleague>,active:PatientContext?,saved:List<SavedReading> = records, savedNotes:List<SavedNote> = this.savedNotes) {
    this.savedNotes=savedNotes
    records=saved
    patients=list;people=staff
    if(context!=null && (active!=context || list.none{it.id==context?.patientId && it.encounterId==context?.encounterId})) invalidate("Patient context expired")
    val to=draft?.to
    if(!to.isNullOrEmpty() && staff.none{it.id==to && it.reachable}) {draft=null;gesture.disarm();visible=false;show("Recipient unavailable","Saved observation remains private");}
  }
  fun connection(value:Boolean) {online=value;if(!value)invalidate(if(pending!=null)"Outcome unknown; check saved readings" else "Connection lost; confirm patient again",false)}
  fun suspend(){invalidate("Capture stopped; confirm patient again")}
  private fun binding(c:PatientContext)=mapOf<String,Any>("patientId" to c.patientId,"encounterId" to c.encounterId,"contextRevision" to c.contextRevision)
  private fun clearContext(){noteText=null;noteDraftId=null;noteSaved=false;context=null;selected=null;savedId=null;candidate=null;ticket=null;if(online)send(UUID.randomUUID().toString(),Action("context.clear"))}
  private fun invalidate(reason:String,notify:Boolean=true){qrScanId=null;noteText=null;noteDraftId=null;noteSaved=false;ticket=null;candidate=null;savedId=null;context=null;selected=null;draft=null;pending=null;pendingAction=null;gesture.disarm();deadline=0;visible=false;if(notify && online)send(UUID.randomUUID().toString(),Action("context.clear"));show("Stopped",reason)}
  fun dismiss(){qrScanId=null;noteText=null;noteDraftId=null;noteSaved=false;card=null;ticket=null;candidate=null;draft=null;deadline=0;gesture.disarm()}
  fun wake(){tick();if(!online || pending!=null || blocksCommunication)return;deadline=clock()+12000;card=(card?:Card("ready","Select a patient","Say select patient demo-a","",true)).copy(microphone=true,hint="Say capture reading, select patient, or done")}
  fun handles(raw:String):Boolean {val s=raw.lowercase().trim().replace(Regex("[.!?]+$"),"");return blocksCommunication || s in setOf("scan patient","scan patient qr","scan qr code") || s.startsWith("save to patient records that ") || s.startsWith("add note ") || s.startsWith("add patient note ") || s.startsWith("add patient note.") || s.startsWith("add patient note:") || s in setOf("add patient note","save note","check saved notes") || s.startsWith("select patient ") || s in setOf("capture reading","retry","save reading","confirm patient","share reading","send attachment","done","check saved readings") || s.startsWith("share reading with ") || s.startsWith("correct ") || (card!=null && s in setOf("cancel","stop"))}
  fun transcript(raw:String,final:Boolean=true) {
    tick();if(!online || !final || card?.microphone==false || pending!=null)return
    val s=raw.trim().replace(Regex("[.!?]+$"),"").lowercase()
    if(s=="cancel" || s=="stop"){invalidate("Unsubmitted reading discarded; camera off");return}
    if(card?.phase=="dictating-note"){reviewNote(raw.trim());return}
    if(s=="what can i say"){
      if(draft!=null){
        val hint=when(draft?.type){
          "patient.confirm"->"Say confirm patient or cancel"
          "note.save"->"Say save note, edit note or cancel"
          "observation.save"->"Say save reading, correct field digits, retry or cancel"
          "observation.share"->"Say send attachment or cancel"
          else->card?.hint.orEmpty()
        }
        visible=false;gesture.disarm();card=card?.copy(hint="Commands: $hint",promptId=++sequence)
      }
      return
    }
    if(s in setOf("scan patient","scan patient qr","scan qr code")){
      clearContext();ticket=null;draft=null;gesture.disarm();visible=false;qrScanId=UUID.randomUUID().toString();deadline=clock()+8000
      card=Card("camera-qr","Scan patient QR","Look at one synthetic patient code","Camera on · tilt left to cancel",true,++sequence);return
    }
    // Patient switch is permitted during capture/review and invalidates the former prompt immediately.
    if(s.startsWith("select patient ")){
      qrScanId=null;
      val key=s.removePrefix("select patient ");clearContext();draft=null;gesture.disarm()
      val found=patients.filter{it.id.equals(key,true)||it.name.equals(key,true)}
      if(found.size!=1){show("Patient not identified","Use full synthetic name or ID; a room is not identity");return}
      selected=found.single();val p=selected!!
      review(Action("patient.confirm",fields=mapOf("patientId" to p.id,"encounterId" to p.encounterId)),"Confirm patient", "${p.name} · ID ${p.id}\nRoom ${p.room} · synthetic", "confirm patient");return
    }
    if(card?.phase in setOf("camera","camera-qr"))return
    if(draft!=null){
      if(draft?.type=="note.save") {
        when {s=="save note"->confirm(true);s=="edit note"->beginNote();s.startsWith("replace note ")->reviewNote(raw.trim().substring(13).trim())}
        return
      }
      if(s=="confirm patient" && draft?.type=="patient.confirm")confirm(true)
      else if(s=="save reading" && draft?.type=="observation.save")confirm(true)
      else if(s=="send attachment" && draft?.type=="observation.share")confirm(true)
      else if(s=="retry" && draft?.type=="observation.save")capture()
      else if(s.startsWith("correct ") && draft?.type=="observation.save"){
        val m=Regex("correct (systolic|diastolic|pulse) (\\d{1,3})").matchEntire(s)?:return
        candidate=candidate?.let{it.copy(values=it.values.corrected(m.groupValues[1],m.groupValues[2].toInt()))};reviewReading()
      };return
    }
    if(s=="save note" && noteSaved)return
    if(s=="add patient note"){beginNote();return}
    Regex("(?:save to patient records that|add (?:patient )?note)[ .:]+(.+)",RegexOption.IGNORE_CASE).matchEntire(raw.trim())?.let{reviewNote(it.groupValues[1]);return}
    if(s=="check saved notes"){
      val last=savedNotes.lastOrNull{context==null || it.patientId==context?.patientId}
      show("Saved manual note · synthetic",last?.let{"${it.patientId}\n${it.text}\nRecorded ${it.savedAt}"}?:"No saved notes loaded",false,"Author-reported · not clinically verified");return
    }
    if(s=="save reading" && savedId!=null)return
    if(s=="done"){ticket=null;candidate=null;dismiss();return}
    if(s=="capture reading" || s=="retry"){capture();return}
    Regex("share reading with ([a-z]+)").matchEntire(s)?.let { m->
      val c=context;val reading=candidate;val id=savedId;val to=people.filter{it.name.equals(m.groupValues[1],true)&&it.reachable}
      if(c==null||reading==null||id==null||to.size!=1){show("Nothing to share","Save a reading and choose a connected assigned colleague");return}
      val who=to.single()
      review(Action("observation.share",to=who.id,fields=binding(c)+mapOf("observationId" to id,"to" to who.id,"text" to "Review bedside reading")),"Send to ${who.name}?", summary(reading)+"\nRequest: Review bedside reading", "send attachment");return
    }
    if(s=="check saved readings"){
      val last=records.lastOrNull();show("Latest saved · synthetic",if(last==null)"No saved observations" else "${patients.find{it.id==last.patientId}?.name?:last.patientId} · ${last.patientId}\n${last.values.display()}\nCaptured ${last.capturedAt}\n${if(last.corrected)"Manually corrected" else "Reviewed extraction"}",false,"Capture time only · nothing resent");return
    }
    show("Patient required","Say select patient demo-a, then confirm patient")
  }
  private fun beginNote(){
    if(context==null || selected==null){show("Confirm patient first","Say select patient demo-a, then confirm patient");return}
    draft=null;ticket=null;candidate=null;savedId=null;noteText=null;noteSaved=false;visible=false;gesture.disarm();deadline=clock()+30000
    card=Card("dictating-note","Note for ${selected!!.name}","${context!!.patientId} · say your short note exactly as it should be recorded","Up to 160 characters · say cancel",true,++sequence)
  }
  private fun reviewNote(text:String){
    val c=context
    if(c==null || selected==null){show("Confirm patient first","No note retained. Confirm the patient, then dictate again.");return}
    if(text.length !in 1..160 || text.any{it.code<32 || it.code==127}){draft=null;noteText=null;noteDraftId=null;show("Note needs rephrasing","Use one short note, up to 160 characters. Nothing saved.");return}
    ticket=null;candidate=null;savedId=null;noteSaved=false;noteText=text
    if(noteDraftId==null)noteDraftId=UUID.randomUUID().toString()
    review(Action("note.save",fields=binding(c)+mapOf("draftId" to noteDraftId!!,"text" to text,"source" to "manual-dictation")),"Review patient note","${selected!!.name} · ${c.patientId}\n$text","save note")
    deadline=clock()+20000
    card=card!!.copy(hint="Author-reported · say save note, edit note, or cancel")
  }
  private fun capture(){
    noteText=null;noteDraftId=null;noteSaved=false
    val c=context
    if(c==null || selected==null){show("Confirm patient first","Say select patient demo-a");return}
    draft=null;candidate=null;savedId=null;gesture.disarm();visible=false;lastCaptureIssue="No stable complete reading"
    ticket=CaptureTicket(UUID.randomUUID().toString(),c,clock(),Instant.ofEpochMilli(wall()).toString(),cameraSource)
    deadline=clock()+8000
    card=Card("camera",if(cameraSource=="injected-camera")"Camera active · simulated" else "Camera active · hold steady","${selected!!.name} · ${c.patientId}\nAlign whole HOS-BP-1 panel", "Stops within 5 seconds · say cancel",true,++sequence)
  }
  fun displayAccepted(prompt:Int) {
    if(card?.promptId!=prompt || clock()>=deadline)return
    if(!visible && card?.phase in setOf("camera","camera-qr")) deadline=clock()+5000
    visible=true
    if(draft!=null)gesture.arm(prompt,clock(),deadline)
  }
  fun qrReady(id:String)=online&&visible&&qrScanId==id&&card?.phase=="camera-qr"&&clock()<deadline
  fun qrResult(id:String,code:PatientQr){
    if(!qrReady(id))return
    val p=patients.singleOrNull{it.id==code.patientId&&it.encounterId==code.encounterId}
    qrScanId=null
    if(p==null){show("Patient QR unavailable","Code is not for a current assigned patient. Select by voice or rescan.");return}
    transcript("select patient ${p.id}")
  }
  fun qrError(id:String,reason:String){if(qrScanId==id){qrScanId=null;show("QR scan stopped",reason+" · say scan patient to retry",true)}}
  fun cameraReady(id:String)=online && visible && ticket?.id==id && card?.phase=="camera" && clock()<deadline
  fun captureResult(id:String,result:Extraction){
    tick();val t=ticket?:return
    if(t.id!=id || !cameraReady(id) || t.context!=context)return
    when(result){
      is Extraction.Accepted->{ticket=null;candidate=Candidate(t,result.reading,result.reading);reviewReading()}
      is Extraction.Rejected->{lastCaptureIssue=result.reason}
    }
  }
  fun cameraError(id:String,reason:String){if(ticket?.id!=id)return;ticket=null;candidate=null;draft=null;gesture.disarm();show("Camera stopped",reason+" · say retry or cancel",true)}
  private fun summary(c:Candidate):String {
    val time=DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'").withZone(ZoneId.of("UTC")).format(Instant.parse(c.ticket.capturedAt))
    return "${selected?.name} · ${c.ticket.context.patientId}\n${c.values.display()}\nCaptured $time · freshness unknown"+if(c.values!=c.original)"\nManually corrected" else ""
  }
  private fun reviewReading(){val c=candidate?:return
    review(Action("observation.save",fields=binding(c.ticket.context)+mapOf("captureId" to c.ticket.id,"capturedAt" to c.ticket.capturedAt,"profile" to MonitorReader.PROFILE,"values" to values(c.values),"original" to values(c.original),"units" to mapOf("pressure" to "mmHg","pulse" to "bpm"),"corrected" to c.values.changes(c.original),"source" to c.ticket.source)),"Review reading",summary(c),"save reading")
  }
  private fun values(r:Reading)=mapOf("systolic" to r.systolic,"diastolic" to r.diastolic,"pulse" to r.pulse)
  private fun review(a:Action,title:String,body:String,phrase:String){draft=a;visible=false;gesture.disarm();deadline=clock()+8000;card=Card("review",title,body,if(a.type=="observation.save")"Say $phrase · correct field digits · retry · cancel" else "Tilt right or say $phrase · left to cancel",true,++sequence)}
  val tiltActionLabel:String? get()=if(card?.phase=="review")when(draft?.type){"patient.confirm"->"confirm patient";"observation.save"->"save reading";"observation.share"->"send attachment";"note.save"->"save note";else->null}else null
  fun physicalAccept(prompt:Int){tick();if(card?.promptId==prompt && card?.phase=="review")confirm(true)}
  fun motion(sample:HeadSample){tick();gesture.sample(sample,clock())?.let{confirm(it)}}
  private fun confirm(yes:Boolean){val a=draft?:return;if(!online||!visible||clock()>=deadline)return;if(!yes){invalidate("Cancelled; nothing submitted");return}
    val execute=if(a.type in setOf("observation.save","note.save"))a.copy(fields=a.fields+mapOf("reviewedAt" to Instant.ofEpochMilli(wall()).toString()))else a
    draft=null;gesture.disarm();visible=false;val id=UUID.randomUUID().toString();pending=id;pendingAction=execute;deadline=clock()+5000
    card=Card("saving","Waiting for acknowledgement","${if(a.type in setOf("observation.save","note.save"))"Saving to synthetic store" else "Submitting reviewed action"}","No automatic retry")
    send(id,execute)
  }
  fun acknowledgement(id:String,ok:Boolean,active:PatientContext?,observationId:String?,reason:String?):Boolean {
    if(id!=pending)return false
    val a=pendingAction;pending=null;pendingAction=null;deadline=0
    if(!ok){if(a?.type=="patient.confirm")clearContext();show("Not saved / sent",reason?:"Ward rejected the action");return true}
    when(a?.type){
      "patient.confirm"->{if(active==null || selected?.id!=active.patientId){invalidate("Patient confirmation unavailable");return true};context=active;show("Patient confirmed","${selected!!.name} · ${active.patientId}\nSay capture reading",true)}
      "observation.save"->{if(observationId==null){invalidate("Save acknowledgement missing record ID; check saved readings");return true};savedId=observationId;show("Saved · synthetic record",candidate?.let{summary(it)}?:"Saved",true,"Say share reading with Ben, capture reading, or done")}
      "note.save"->{noteSaved=true;noteDraftId=null;show("Note saved · synthetic record","${selected?.name} · ${context?.patientId}\n${a.fields["text"]}",false,"Author-reported · event time is only the dictated text")}
      "observation.share"->show("Request sent to ${people.find{it.id==a.to}?.name?:a.to}","Saved reading attached · awaiting nurse response")
    };return true
  }
  private fun show(title:String,body:String,listening:Boolean=false,hint:String="Wake to continue") {card=Card("ready",title,body,hint,listening);deadline=if(listening)clock()+12000 else 0}
  fun tick(){if(deadline==0L||clock()<deadline)return
    val wasQr=qrScanId!=null;qrScanId=null;val capturing=ticket!=null;val uncertain=pending!=null;ticket=null;draft=null;pending=null;pendingAction=null;gesture.disarm();visible=false;deadline=0
    if(savedId!=null && !uncertain){show("Saved · synthetic record",candidate?.let{summary(it)}?:"Saved",false,"Wake to share, capture, or finish");return}
    if(uncertain){clearContext();show("Outcome unknown","Check saved records / requests. Nothing automatically retries.")}
    else {noteText=null;noteDraftId=null;noteSaved=false;if(savedId==null)candidate=null;show(if(capturing)"No reliable reading" else "Review closed",if(capturing)"$lastCaptureIssue · camera off · say Hey Lumi, then retry" else "Nothing submitted · wake to continue")}
  }
}
