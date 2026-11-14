package fi.hospitalos.handsfree

/** Pure local control. Only final transcripts are accepted; no language model executes actions. */
data class Colleague(val id: String, val name: String, val reachable: Boolean, val status: String = "available")
data class Request(val id: String, val from: String, val to: String, val room: String, val text: String, val status: String, val revision: Int, val required:Int=0, val acceptedBy:List<String> = emptyList(), val attachment:RequestAttachment? = null, val attachmentUnavailable:Boolean = false)
data class WardMessage(val id:String,val from:String,val to:String,val text:String,val read:Boolean=false)
data class Action(val type: String, val to: String = "", val room: String = "", val text: String = "", val taskId: String = "", val revision: Int = 0, val fields: Map<String,Any> = emptyMap())
data class Card(val phase: String, val title: String, val body: String, val hint: String, val microphone: Boolean = false, val promptId: Int = 0)

class HandsFreeEngine(val me: String, private val clock: () -> Long, private val send: (String, Action) -> Unit) {
  var quietMode=false
  private var visibleSince=0L
  private var previousVisible:Card?=null
  private val notifiedMessages=mutableSetOf<String>()
  private var messageBaselineLoaded=false
  val bedside = BedsideController(clock, System::currentTimeMillis) { id, action ->
    // A note save uses the same opaque-ID recovery contract as communication.
    if(action.type in setOf("note.save","observation.save","observation.share")){pending=id;pendingKind=action.type;deadline=clock()+5000}
    send(id,action)
  }
  private val navigator = WardNavigator(me, bedside)
  private var draftContext: PatientContext? = null
  val emergency = EmergencyController(me,clock,{pending!=null || uncertainId!=null || bedside.hasPending},{
    bedside.interruptForEmergency();navigator.clear();draftContext=null;draft=null;composeTo=null;openedMessage=null;noticeMessage=null;trackedTask=null;reviewVisible=false;gesture.disarm()
    if(pending==null && uncertainId==null)deadline=0
    communicationCard=Card("quiet","Display hidden","Say Hey Lumi","")
  },{idle()},{submit(it)})
  val card get() = emergency.card ?: bedside.card ?: communicationCard
  var communicationCard = Card("offline", "Not connected", "Nothing will be sent", "Reconnect to the local ward")
    private set
  private var online = false
  private var staff = emptyList<Colleague>()
  private var tasks = emptyList<Request>()
  private var messages = emptyList<WardMessage>()
  private var openedMessage:WardMessage?=null
  private var composeTo:Colleague?=null
  private var pendingKind:String?=null
  private var noticeMessage:WardMessage?=null
  private var uncertainId:String?=null
  val unresolvedCommandId get()=pending ?: uncertainId
  private var draft: Action? = null
  private var pending: String? = null
  private var reviewVisible = false
  private var deadline = 0L
  private var sequence = 0
  private val session = java.util.UUID.randomUUID().toString()
  private var trackedTask: String? = null
  private val gesture = HeadGestureDetector()
  val microphoneActive get() = card.microphone

  fun connection(connected: Boolean) {
    emergency.connection(connected)
    val hadBedside=bedside.card!=null || bedside.context!=null
    bedside.connection(connected)
    if(!connected && (!hadBedside || pending!=null || uncertainId!=null))bedside.dismiss()
    online = connected
    if (!connected) {
      if(pending!=null)uncertainId=pending
      val uncertain = uncertainId != null
      navigator.clear(); draftContext=null
      draft = null; pending = null; openedMessage=null;composeTo=null; gesture.disarm(); deadline = 0
      communicationCard = Card("offline", "Connection lost", if (uncertain) "Outcome unknown. Check requests after reconnecting." else "Unsent draft discarded", "No automatic resend")
    } else if (uncertainId!=null) {
      bedside.dismiss()
      deadline=clock()+5000
      communicationCard=Card("reconciling","Checking previous action","Reading its stored outcome; nothing is resent","Microphone off")
      send(java.util.UUID.randomUUID().toString(),Action("command.status",fields=mapOf("commandId" to uncertainId!!)))
    } else if (communicationCard.phase == "offline") idle()
  }

  fun snapshot(people: List<Colleague>, requests: List<Request>, incomingMessages: List<WardMessage> = messages, emergencies:List<Emergency>?=null) {
    val priorTasks = tasks
    // The first snapshot is saved inbox history, not newly arriving notifications.
    if(!messageBaselineLoaded){
      notifiedMessages.addAll(incomingMessages.map { it.id })
      messageBaselineLoaded=true
    }
    staff = people; tasks = requests; messages=incomingMessages
    navigator.snapshot(staff, tasks, messages)
    if(emergencies!=null)emergency.snapshot(emergencies) else emergency.refresh()
    val a = draft
    if (a != null && ((a.taskId.isNotEmpty() && tasks.none { it.id == a.taskId && it.revision == a.revision && it == priorTasks.find { old -> old.id == a.taskId } }) ||
          (a.to.isNotEmpty() && staff.none { it.id == a.to && it.reachable }))) {
      if(a.taskId.isNotEmpty())offeredIncoming.remove(a.taskId)
      reset("Review expired", "Request changed or colleague disconnected. Start again.")
    }
    if (draftContext != null && draftContext != bedside.context) reset("Review expired", "Patient context changed. Start again.")
    if (communicationCard.phase in setOf("browse", "details")) renderNavigation()
    if (communicationCard.phase == "idle") idle()
    if(communicationCard.phase=="status" && trackedTask==null && notifications().isNotEmpty())communicationCard=communicationCard.copy(hint=notifications()+" · wake to review")
    if (communicationCard.phase == "status" && trackedTask != null) {
      tasks.find { it.id == trackedTask }?.let { t ->
        if(t.required>0){communicationCard=Card("status",if(t.to==me&&t.status=="accepted")"Accepted · ${name(t.from)} notified" else if(t.to==me&&t.status=="declined")"Declined · ${name(t.from)} notified" else "${t.acceptedBy.size}/${t.required} nurses confirmed", "Room ${t.room} · ${t.status}\n"+t.acceptedBy.joinToString { name(it) },"Say check requests for updates");return@let}
        communicationCard = Card("status", when(t.status) { "accepted" -> if(t.to == me) "Accepted · ${name(t.from)} notified" else "${name(t.to)} is coming"; "completed" -> "Marked complete"; "delivered" -> "Delivered to ${name(t.to)}"; "declined" -> if(t.to==me) "Declined · ${name(t.from)} notified" else "${name(t.to)} declined"; "expired" -> "Request expired"; "cancelled" -> "Request cancelled"; else -> "Request ${t.status}" },
          "Room ${t.room} · ${t.text}", if(tasks.any { it.to == me && it.status in setOf("sent", "delivered") }) "Request waiting · wake and say review request" else if(t.status == "delivered") "Delivery does not mean acceptance" else "Wake to give another command")
      }
    }
  }

  private val offeredIncoming = mutableSetOf<String>()
  fun dismissScreen() {
    if(emergency.active){emergency.command("back");return}
    if(pending!=null || card.phase in setOf("saving","sending","reconciling"))return
    if(navigator.active && communicationCard.phase in setOf("browse", "details", "message")) { navigationResult(if(card.phase=="message") WardNavigator.Result.Changed else navigator.command("back")); return }
    navigator.clear();draftContext=null
    bedside.dismiss();draft=null;openedMessage=null;noticeMessage=null;gesture.disarm();deadline=0;trackedTask=null
    composeTo=null
    communicationCard=Card("quiet","Display hidden","Say Hey Lumi with glasses voice enabled","",false)
  }
  fun offerIncoming() {
    emergency.refresh();if(emergency.active)return
    if(!online || draft!=null || pending!=null || bedside.card!=null || communicationCard.phase !in setOf("idle","quiet","status","notice"))return
    val next=tasks.firstOrNull{it.to==me && it.status=="delivered" && it.id !in offeredIncoming}
    if(next==null){
      if(quietMode && card.phase=="quiet")messages.firstOrNull{it.to==me && !it.read && it.id !in notifiedMessages}?.let {
        notifiedMessages.add(it.id);noticeMessage=it;reviewVisible=false;sequence++;communicationCard=Card("message-notice","Message from ${name(it.from)}","Tilt right to open · tilt left to read later","Saved until you open it",false,sequence)
      }
      return
    }
    offeredIncoming.add(next.id)
    reviewIncoming(next)
  }
  val nodAcceptAvailable get()=card.phase=="review" && draft?.type=="request.accept"
  val nodAvailable get()=nodAcceptAvailable || (card.phase=="message-notice" && noticeMessage!=null)
  fun physicalNod(promptId:Int) {
    if(online && reviewVisible && card.phase=="message-notice" && card.promptId==promptId){
      val m=noticeMessage ?: return
      noticeMessage=null;openedMessage=m;deadline=clock()+20000;gesture.disarm()
      communicationCard=Card("message","Message from ${name(m.from)}",m.text,"Say reply followed by your message, or close message · tilt left to close",true,++sequence)
      return
    }
    if(card.phase=="review" && card.promptId==promptId && draft?.type=="request.accept")confirm(true)
  }
  val tiltActionLabel:String? get()=if(emergency.active)when(card.phase){"emergency-alert"->"respond";"emergency-briefing"->"next";else->null}else if(bedside.card!=null)bedside.tiltActionLabel else when {
    card.phase=="browse" -> if(navigator.canOpen) "open" else null
    card.phase=="message-notice" && noticeMessage!=null -> "open"
    card.phase=="review" -> when(draft?.type){
      "request.accept"->"accept";"request.create"->"send request";"request.broadcast"->"request nurses";"message.send"->"send message"
      "request.cancel"->"cancel request";"presence.set"->"confirm status";"request.complete"->"complete";else->if(draft!=null)"confirm" else null
    }
    else->null
  }
  fun physicalAccept(promptId:Int){
    tick();if(!online || card.promptId!=promptId)return
    if(emergency.active){emergency.right(promptId);return}
    if(bedside.card!=null){bedside.physicalAccept(promptId);return}
    if(card.phase=="browse" && reviewVisible){navigationResult(navigator.command("open"));return}
    if(card.phase=="message-notice"){physicalNod(promptId);return}
    if(card.phase=="review")confirm(true)
  }
  /** Incoming request review: decline. All other cards: back/cancel/later. */
  fun physicalBack(promptId:Int){
    tick();if(!online || card.promptId!=promptId)return
    if(emergency.active){emergency.left(promptId);return}
    if(bedside.card==null && card.phase=="review" && draft?.type=="request.accept"){
      confirm(false);return
    }
    dismissScreen()
  }
  val tiltBackLabel:String get()=if(card.phase=="emergency-alert")"decline" else if(bedside.card==null && card.phase=="review" && draft?.type=="request.accept")"decline" else "back / later"
  val tiltHint:String get()=if(emergency.active)card.hint else listOfNotNull(tiltActionLabel?.let{"Tilt right to $it"},
    if(card.phase !in setOf("saving","sending","reconciling","offline","quiet"))"Tilt left to $tiltBackLabel" else null).joinToString(" · ")
  fun notifications():String {
    val requests=tasks.count{it.to==me && it.status in setOf("sent","delivered")}
    val unread=messages.count{it.to==me && !it.read}
    return listOfNotNull(if(requests>0)"$requests request(s) waiting" else null,if(unread>0)"$unread unread message(s)" else null).joinToString(" · ")
  }

  fun wake() {
    tick()
    if(emergency.active){emergency.refresh();return}
    if (!online || pending != null || draft != null || bedside.blocksCommunication) return
    if(navigator.active && card.phase in setOf("browse","details")){deadline=clock()+20000;renderNavigation();return}
    if(bedside.card!=null){bedside.wake();return}
    openedMessage=null;composeTo=null; gesture.disarm(); deadline = if(quietMode)0 else clock()+12_000
    communicationCard = Card("listening", "Listening", "Ask Ben to bring the hoist to room 12", "Say cancel to stop", true, ++sequence)
  }

  /** Stable SDK partials can compose a draft, never confirm a displayed action. */
  fun proposeTranscript(raw:String) {
    if(card.phase !in setOf("listening","dictating-message","dictating-note") || draft!=null || pending!=null)return
    transcript(raw)
  }

  fun transcript(raw: String, final: Boolean = true) {
    tick()
    if (!final || !card.microphone || !online) return
    val command = raw.trim().trimEnd('.', '!', '?').lowercase()
    if(command=="check outcome" && uncertainId!=null){connection(true);return}
    if(command in setOf("show emergency","show isbar")){if(!emergency.open())reset("No active emergency","No current briefing available");return}
    if(emergency.command(command))return
    if(uncertainId!=null && command !in setOf("check outcome","check requests","show requests","show pending requests","show accepted requests","show request history","show all requests","show messages","next","previous","back","show details","what can i say","open")){
      reset("Previous outcome unresolved","Say check outcome before sending another action");return
    }
    if(composeTo==null && pending==null && !bedside.blocksCommunication && draft==null && navigationCommand(command)) return
    if(composeTo==null && draft==null && pending==null && bedside.handles(raw)) { navigator.clear();deadline=0;gesture.disarm();idle();bedside.transcript(raw,final);return }
    if(bedside.blocksCommunication)return
    bedside.dismiss()
    val text = raw.trim().replace(Regex("[.!?]+$"), "")
    val lower = text.lowercase()
    if (lower == "cancel" || lower == "stop") { reset("Cancelled", "Nothing sent"); return }
    if(composeTo!=null){val who=composeTo!!;composeTo=null;reviewMessage(who,raw.trim());return}
    if (draft != null) {
      if(lower=="show details" && draft!!.taskId.isNotEmpty()) { openRequestDetails(draft!!.taskId, true); return }
      if(lower=="what can i say") {
        val hint=when(draft!!.type){
          "request.accept"->"Say show details, accept request, decline request or cancel"
          "request.complete"->"Say show details, confirm completion or cancel"
          "message.send"->"Say send message, edit message or cancel"
          else->communicationCard.hint
        }
        reviewVisible=false;gesture.disarm();communicationCard=communicationCard.copy(hint="Commands: $hint",promptId=++sequence);return
      }
      if(lower=="back"){reset("Review closed","Nothing sent");return}
      if(draft?.type=="message.send" && lower=="edit message") {staff.find{it.id==draft?.to}?.let{beginMessage(it)};return}
      if(draft?.type=="message.send" && lower.startsWith("replace message ")) {staff.find{it.id==draft?.to}?.let{reviewMessage(it,raw.trim().substring(16).trim())};return}
      when(lower) {
        "send request" -> if(draft?.type in setOf("request.create","request.broadcast")) confirm(true)
        "send message" -> if(draft?.type == "message.send") confirm(true)
        "accept request" -> if(draft?.type == "request.accept") confirm(true)
        "decline request" -> if(draft?.type == "request.accept") confirm(false)
        "confirm status" -> if(draft?.type == "presence.set") confirm(true)
        "confirm cancellation" -> if(draft?.type == "request.cancel") confirm(true)
        "confirm completion" -> if(draft?.type == "request.complete") confirm(true)
        else -> { /* Unrecognised speech never commits or extends the confirmation window. */ }
      }
      return
    }
    if (lower == "check requests") {
      draft=null; gesture.disarm(); deadline=0; trackedTask=null
      communicationCard=Card("status", "Your requests", tasks.takeLast(3).joinToString("\n") { "Room ${it.room} · ${if(it.to==me) "from "+name(it.from) else "to "+name(it.to)} · ${it.status}" }.ifBlank { "No requests in this ward session" }, "Microphone off · wake to continue")
      return
    }
    if(lower=="check outcome") {
      if(uncertainId!=null){connection(true)}else reset("No unresolved action","Say check requests to see task status")
      return
    }
    if(uncertainId!=null){reset("Previous outcome unresolved","Say check outcome before sending another action");return}
    if(lower in setOf("set me busy","set me available")) {
      val status=if(lower.endsWith("busy"))"busy" else "available"
      review(Action("presence.set",fields=mapOf("status" to status)), "Set $status?", "Your declared availability · connectivity is separate", "confirm status");return
    }
    if(lower=="read message" || lower.startsWith("read message from ")) {
      val who=lower.removePrefix("read message from ")
      val found=messages.filter{it.to==me && !it.read && (lower=="read message" || name(it.from).equals(who,true))}
      if(found.isEmpty()){reset("No unread messages","Wake to send a message");return}
      openedMessage=found.first();val m=openedMessage!!;deadline=clock()+12000;gesture.disarm()
      communicationCard=Card("message","Message from ${name(m.from)}",m.text,"Say reply followed by your message, or close message",true);return
    }
    if(lower=="close message" && openedMessage?.to==me) {
      val m=openedMessage!!;openedMessage=null
      submit(Action("message.read",fields=mapOf("messageId" to m.id)));return
    }
    if(lower=="reply" && openedMessage?.to==me){staff.find{it.id==openedMessage?.from}?.let{beginMessage(it)};return}
    if(lower.startsWith("reply ") && openedMessage?.to==me) {
      val m=openedMessage!!;val body=raw.trim().substring(6).trim()
      if(body.length !in 1..160){reset("Message too long","Use up to 160 characters");return}
      if(staff.none{it.id==m.from && it.reachable}){reset("Colleague unavailable","Nothing sent");return}
      review(Action("message.send",to=m.from,text=body),"Reply to ${name(m.from)}?",body,"send message");return
    }
    val selection=Regex("(review|complete|cancel) request(?: (?:in )?room ([A-Za-z -]+?|\\d{1,3}))?(?: (?:from|to) ([A-Za-z]+))?",RegexOption.IGNORE_CASE).matchEntire(text)
    if(selection!=null) {
      val verb=selection.groupValues[1].lowercase();val roomText=selection.groupValues[2];val who=selection.groupValues[3]
      val room=if(roomText.isEmpty())null else roomNumber(roomText)
      if(roomText.isNotEmpty() && room==null){reset("Room not recognised","Say a room number from 1 to 99");return}
      val found=tasks.filter { t ->
        val eligible=when(verb){"complete"->t.to==me && t.status=="accepted";"cancel"->t.from==me && t.status in setOf("sent","delivered");else->t.to==me && t.status in setOf("sent","delivered")}
        eligible && (room==null || t.room==room) && (who.isEmpty() || name(if(verb=="cancel")t.to else t.from).equals(who,true))
      }
      if(found.size!=1){reset(if(found.isEmpty())"No matching request" else "Choose one request",if(found.isEmpty())"Say check requests" else "Say $verb request in room twelve from Ben");return}
      val t=found.single()
      when(verb) {
        "complete"->review(Action("request.complete",taskId=t.id,revision=t.revision),"Mark complete?","Room ${t.room} · ${t.text}","confirm completion")
        "cancel"->review(Action("request.cancel",taskId=t.id,revision=t.revision),"Cancel request to ${name(t.to)}?","Room ${t.room} · ${t.text}","confirm cancellation")
        else->reviewIncoming(t)
      };return
    }
    val naturalMessagePattern=Regex("send ([A-Za-z]+) (?:a )?message(?: saying (.+))?",RegexOption.IGNORE_CASE)
    val naturalMessage=naturalMessagePattern.matchEntire(raw.trim()) ?: naturalMessagePattern.matchEntire(text)
    if(naturalMessage!=null){
      val people=staff.filter{it.id!=me && it.name.equals(naturalMessage.groupValues[1],true)}
      if(people.size!=1){reset("Colleague not identified","Say show available colleagues");return}
      val body=naturalMessage.groupValues[2]
      if(body.isEmpty())beginMessage(people.single()) else reviewMessage(people.single(),body)
      return
    }
    val help=Regex("ask ([A-Za-z]+) for help(?: (?:in|to) room ([A-Za-z -]+|\\d{1,3})| (here))?",RegexOption.IGNORE_CASE).matchEntire(text)
    if(help!=null || lower=="get help here"){
      val p=bedside.currentPatient
      val explicitRoom=help?.groupValues?.get(2).orEmpty()
      val room=if(explicitRoom.isNotEmpty())roomNumber(explicitRoom) else p?.room
      if(room==null){reset("Which room?","Say ask Ben for help in room twelve, or confirm a patient first");return}
      if(help==null){
        val available=staff.count{it.id!=me && it.reachable && it.status=="available"}
        if(available==0){reset("No available nurses","Nothing sent");return}
        review(Action("request.broadcast",room=room,fields=mapOf("required" to 1)),"Request one nurse?","Room $room · from confirmed patient location\nSent to $available available nurses · first acceptance fills request","send request")
      }else{
        val people=staff.filter{it.id!=me && it.name.equals(help.groupValues[1],true) && it.reachable}
        if(people.size!=1){reset("Colleague unavailable","Choose one connected colleague by name");return}
        val who=people.single()
        review(Action("request.create",to=who.id,room=room,text="Help needed"),"Ask ${who.name}${if(who.status=="busy") " (busy)" else ""}?","Room $room · Help needed","send request")
      }
      if(explicitRoom.isEmpty())draftContext=bedside.context
      return
    }
    Regex("(?:message|send (?:a )?message to|tell) ([A-Za-z]+)",RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
      val people=staff.filter{it.name.equals(m.groupValues[1],true)&&it.id!=me}
      if(people.size!=1){reset("Colleague not identified","Say message followed by one colleague's name");return}
      beginMessage(people.single());return
    }
    Regex("(?:request|ask for) (one|two|three|[1-9]|10) nurses? (?:to|in) room ([A-Za-z -]+|\\d{1,3})",RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
      val count=when(m.groupValues[1].lowercase()){ "one"->1;"two"->2;"three"->3;else->m.groupValues[1].toInt() }
      val room=roomNumber(m.groupValues[2]);if(room==null){reset("Room not recognised","Say a room number");return}
      val available=staff.count{it.id!=me&&it.reachable&&it.status=="available"}
      if(available==0){reset("No available nurses","No request sent");return}
      review(Action("request.broadcast",room=room,fields=mapOf("required" to count)),"Request $count nurses?","Room $room · all $available available nurses\n${if(available<count)"Fewer nurses available than requested" else "First $count acceptances fill the request"}","send request");return
    }
    // Anchored grammars prevent incidental words or negations from executing another intent.
    val request = Regex("ask ([A-Za-z]+) to (.{1,120}) (?:to|in) room ([A-Za-z -]+|\\d{1,3})", RegexOption.IGNORE_CASE).matchEntire(text)
    val message = Regex("(?:tell|message|send (?:a )?message to) ([A-Za-z]+) (?:saying )?(.+)", RegexOption.IGNORE_CASE).matchEntire(raw.trim())
    if (request != null || message != null) {
      val m = request ?: message!!
      val people = staff.filter { it.name.equals(m.groupValues[1], true) && it.id != me }
      if (people.size != 1 || !people.single().reachable) { reset("Colleague unavailable", "Choose one connected colleague by name"); return }
      val who = people.single()
      if (request != null) {
        val room=roomNumber(m.groupValues[3])
        if(room == null) { reset("Room not recognised", "Say one room number from 1 to 99"); return }
        val body = m.groupValues[2]
        if (Regex("\\b(don't|do not|never|cancel|not)\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)) { reset("Please rephrase", "Say the positive action you want to request"); return }
        review(Action("request.create", to=who.id, room=room, text=body), "Ask ${who.name}${if(who.status=="busy") " (busy)" else ""}?", "Room $room · $body", "send request")
      } else reviewMessage(who,m.groupValues[2])
      return
    }
    reset("Didn't understand", "Try: ask Ben to bring the hoist to room 12")
  }

  private fun navigationCommand(words: String): Boolean {
    val section=when(words){
      "show patients"->WardNavigator.Section.PATIENTS
      "who's available", "who is available", "show available colleagues"->WardNavigator.Section.STAFF
      "show requests", "show pending requests", "show accepted requests", "show request history", "show all requests"->WardNavigator.Section.REQUESTS
      "show messages"->WardNavigator.Section.MESSAGES
      else->null
    }
    if(section!=null){
      bedside.dismiss();openedMessage=null;trackedTask=null
      val filter=when(words){"show accepted requests"->"accepted";"show request history"->"history";"show all requests"->"all";else->"pending"}
      navigator.list(section,filter);deadline=clock()+20000;renderNavigation();return true
    }
    if(words in setOf("show patient details","show latest note","show latest reading")){
      val p=bedside.currentPatient
      if(p==null){bedside.dismiss();reset("Confirm patient first","Say show patients, then open one");return true}
      bedside.dismiss();openedMessage=null
      navigator.patient(when(words){"show latest note"->"note";"show latest reading"->"reading";else->"patient"},p.id)
      deadline=clock()+20000;renderNavigation();return true
    }
    if(words=="show details"){
      val id=navigator.selectedRequestId ?: trackedTask
      if(id==null){bedside.dismiss();reset("Choose a request","Say show requests, then open one")}else openRequestDetails(id,false)
      return true
    }
    if(words=="what can i say"){
      bedside.dismiss();navigator.help();deadline=clock()+20000;renderNavigation();return true
    }
    if(words in setOf("review request","complete request","cancel request") && navigator.selectedRequestId!=null){
      val t=tasks.find{it.id==navigator.selectedRequestId}
      if(t!=null)reviewTask(t,words) else reset("Request unavailable","Say show requests")
      return true
    }
    if(navigator.active){
      val result=if(words=="back" && card.phase=="message") WardNavigator.Result.Changed else navigator.command(words)
      if(result!=null){navigationResult(result);return true}
    }
    return false
  }
  private fun renderNavigation() {
    val next=navigator.card() ?: return
    if(communicationCard.copy(promptId=0)!=next){reviewVisible=false;gesture.disarm();communicationCard=next.copy(promptId=++sequence)}
  }
  private fun openRequestDetails(id:String,returnReview:Boolean){
    draft=null;draftContext=null;reviewVisible=false;gesture.disarm();bedside.dismiss()
    navigator.request(id,returnReview);deadline=clock()+20000;renderNavigation()
  }
  private fun navigationResult(result:WardNavigator.Result?) {
    deadline=clock()+20000
    when(result){
      WardNavigator.Result.Changed->{openedMessage=null;renderNavigation()}
      WardNavigator.Result.Exit->{navigator.clear();openedMessage=null;deadline=0;idle()}
      is WardNavigator.Result.SelectPatient->{
        if(uncertainId!=null)reset("Previous outcome unresolved","Say check outcome first")
        else {navigator.clear();idle();bedside.transcript("select patient ${result.id}")}
      }
      is WardNavigator.Result.Compose->{staff.find{it.id==result.id}?.let{beginMessage(it)} ?: reset("Colleague unavailable","Say who's available")}
      is WardNavigator.Result.ReadMessage->{
        val m=messages.find{it.id==result.id}
        if(m==null)reset("Message unavailable","Say show messages") else {
          openedMessage=m
          communicationCard=Card("message",if(m.to==me)"Message from ${name(m.from)}" else "Message to ${name(m.to)}",m.text,if(m.to==me)"Say reply, close message or back" else "Say back",true,++sequence)
        }
      }
      is WardNavigator.Result.ReviewRequest->{
        val t=tasks.find{it.id==result.id}
        if(t==null)reset("Request unavailable","Say show requests") else if(t.to==me && t.status in setOf("sent","delivered"))reviewIncoming(t)
        else {navigator.request(t.id);renderNavigation()}
      }
      null->Unit
    }
  }
  private fun reviewTask(t:Request,words:String){
    when {
      words=="review request" && t.to==me && t.status in setOf("sent","delivered")->reviewIncoming(t)
      words=="complete request" && t.to==me && t.status=="accepted"->review(Action("request.complete",taskId=t.id,revision=t.revision),"Mark complete?","Room ${t.room} · ${t.text}","confirm completion")
      words=="cancel request" && t.from==me && t.status in setOf("sent","delivered")->review(Action("request.cancel",taskId=t.id,revision=t.revision),"Cancel request to ${name(t.to)}?","Room ${t.room} · ${t.text}","confirm cancellation")
      else->reset("Action unavailable","Request is ${t.status}; say show requests")
    }
  }

  private fun beginMessage(who:Colleague){
    if(uncertainId!=null){reset("Previous outcome unresolved","Say check outcome first");return}
    navigator.clear()
    if(!who.reachable || who.id==me){reset("Colleague unavailable","Nothing sent");return}
    draft=null;composeTo=who;reviewVisible=false;gesture.disarm();deadline=if(quietMode)0 else clock()+30000
    communicationCard=Card("dictating-message","Message ${who.name}","Say your short message. It will be shown before sending.","Up to 160 characters · say cancel to stop",true,++sequence)
  }
  private fun reviewMessage(who:Colleague,body:String){
    if(body.length !in 1..160 || body.any{it.code<32 || it.code==127}){reset("Message needs rephrasing","Use one short message, up to 160 characters. Nothing sent.");return}
    if(!who.reachable){reset("Colleague unavailable","Nothing sent");return}
    composeTo=null
    review(Action("message.send",to=who.id,text=body),"Message ${who.name}?",body,"send message")
    if(draft!=null)communicationCard=communicationCard.copy(hint="Say send message, edit message, or cancel")
  }
  private fun reviewIncoming(t: Request) {
    review(Action("request.accept", taskId=t.id, revision=t.revision), if(t.required>0)"${t.required} nurses requested" else "${name(t.from)} needs help", "Room ${t.room} · ${t.text}"+(if(t.required>0)"\n${t.acceptedBy.size}/${t.required} confirmed · from ${name(t.from)}" else "")+(if(t.attachmentUnavailable)"\nPatient details unavailable" else if(t.attachment!=null)"\nReading attached" else ""), "accept request")
    if(draft!=null)communicationCard=communicationCard.copy(hint="Tilt right to accept · tilt left to decline · say show details")
  }
  private fun review(action: Action, title: String, body: String, phrase: String) {
    if(uncertainId!=null){reset("Previous outcome unresolved","Say check outcome first");return}
    navigator.clear();draftContext=null
    reviewVisible = false; draft = action; deadline = clock() + (if(quietMode)20_000 else 8_000); sequence++
    communicationCard = Card("review", title, body, "Tilt right or say ‘$phrase’ · tilt left to ${if(action.type == "request.accept") "decline" else "cancel"}", true, sequence)
    gesture.disarm()
  }
  /** Called after DAT accepts this exact review communicationCard; failed rendering never arms confirmation. */
  fun reviewDisplayed(promptId: Int) {
    if(emergency.active){emergency.displayed(promptId);return}
    if(card.phase=="browse" && card.promptId==promptId){reviewVisible=true;return}
    if(card.phase=="message-notice" && card.promptId==promptId){reviewVisible=true;return}
    if(bedside.card!=null){bedside.displayAccepted(promptId);return}
    if(draft != null && communicationCard.promptId == promptId && !reviewVisible && clock() < deadline) { reviewVisible=true; gesture.arm(promptId, clock(), deadline) }
  }
  fun motion(sample: HeadSample) { tick(); if(emergency.active)return; if(bedside.card!=null){bedside.motion(sample);return}; gesture.sample(sample, clock())?.let { confirm(it) } }
  private fun confirm(yes: Boolean) {
    val action = draft ?: return
    if(draftContext!=null && draftContext!=bedside.context){reset("Review expired","Patient context changed. Start again.");return}
    if(!reviewVisible) return
    if (!online || clock() >= deadline) { tick(); return }
    if (!yes && action.type != "request.accept") { reset("Cancelled", "Nothing sent"); return }
    val execute = if(yes) action else action.copy(type="request.decline")
    submit(execute)
  }
  private fun submit(execute:Action) {
    navigator.clear();draftContext=null
    draft = null;composeTo=null;pendingKind=execute.type; gesture.disarm(); deadline = clock() + 5_000
    val id = "$session-${++sequence}"; pending = id
    communicationCard = Card("sending", "Waiting for ward acknowledgement", "No colleague response yet", "Microphone off")
    send(id, execute)
  }
  fun acknowledgement(id: String, ok: Boolean, taskId: String?, reason: String?, context:PatientContext?=null, observationId:String?=null, emergencyId:String?=null) {
    if(bedside.acknowledgement(id,ok,context,observationId,reason)) {
      if(pending==id){pending=null;uncertainId=null;pendingKind=null;deadline=0}
      emergency.refresh()
      if(ok && context!=null && !emergency.active){bedside.dismiss();navigator.patient("patient",context.patientId);deadline=clock()+20000;renderNavigation()}
      // A shared observation is now a request: use the same live status as communication.
      if(ok && taskId!=null){bedside.dismiss();trackedTask=taskId;deadline=0;communicationCard=Card("status","Request sent","Saved reading attached · awaiting nurse response","Microphone off");snapshot(staff,tasks)}
      return
    }
    if(pending==null && id==uncertainId){reconciled(id,ok,taskId,reason,emergencyId);return}
    if(id != pending) return
    pending = null; uncertainId=null; deadline = 0
    trackedTask = if(ok) taskId else null
    val wasEmergency=pendingKind?.startsWith("emergency.")==true
    val wasMessage=pendingKind=="message.send";pendingKind=null
    if(wasEmergency){emergency.acknowledged(ok);return}
    emergency.refresh()
    communicationCard = Card("status", if(ok && wasMessage) "Message saved and sent" else if(ok) "Ward confirmed" else "Not sent", reason ?: if(ok && wasMessage) "Stored in local message history · waiting for recipient to open" else if(ok) "Confirmed by the local ward server" else "Request rejected", "Wake for another command")
    snapshot(staff, tasks)
  }
  /** Restore only an opaque command id, never an unconfirmed draft or patient context. */
  fun restoreUnresolvedCommand(id:String){if(id.length in 1..100)uncertainId=id}
  fun reconciled(commandId:String,ok:Boolean?,taskId:String?,reason:String?,emergencyId:String?=null) {
    if(commandId!=uncertainId)return
    deadline=0
    if(ok==null){communicationCard=Card("notice","No recorded outcome","Check requests before deliberately trying again","Microphone off · no automatic resend");uncertainId=null;return}
    uncertainId=null;pendingKind=null;trackedTask=if(ok)taskId else null
    emergency.acknowledged(ok)
    if(ok && emergencyId!=null)emergency.open(emergencyId)
    communicationCard=Card("status",if(ok)"Previous action confirmed" else "Previous action rejected",reason ?: "Recovered from the local ward record","Wake to continue")
    snapshot(staff,tasks)
  }
  fun tick() {
    bedside.tick()
    emergency.refresh()
    if(emergency.active && pending==null && uncertainId==null)return
    if(quietMode){
      if(card!=previousVisible){previousVisible=card;visibleSince=clock()}
      if(card.phase in setOf("status","notice","message-notice") && clock()-visibleSince>=15000 && pending==null){dismissScreen();return}
    }
    if(deadline == 0L || clock() < deadline) return
    if(pending!=null){uncertainId=pending;emergency.outcomeUnknown()}
    val unknown = uncertainId != null
    pending = null
    if(quietMode && !unknown){navigator.clear();dismissScreen();return}
    reset(if(unknown) "Outcome unknown" else "Timed out", if(unknown) "Check requests. Do not assume delivery; nothing is retried." else "Nothing sent. Wake to try again.")
  }
  fun deviceLost() { connection(false) }
  private fun reset(title: String, body: String) {
    navigator.clear();draftContext=null
    draft = null; openedMessage=null;composeTo=null; gesture.disarm(); deadline=0; trackedTask=null
    communicationCard = Card("notice", title, body, "Microphone off · wake to continue")
  }
  private fun idle() {
    if(quietMode){communicationCard=Card("quiet","Display hidden","Say Hey Lumi with glasses voice enabled","");return}
    val unread=messages.count{it.to==me && !it.read}
    val open = tasks.count { it.to == me && it.status in setOf("sent", "delivered") }
    communicationCard = Card("idle", if(open > 0) "$open request${if(open==1) "" else "s"} waiting" else "Ready", if(open > 0) "Wake, then say ‘review request’" else if(unread>0) "$unread unread message(s) · wake and say read message" else "Hands free · local ward", "Microphone off · head gestures inactive")
  }
  private fun roomNumber(raw: String): String? {
    raw.toIntOrNull()?.let { return if(it in 1..99) it.toString() else null }
    val singles=listOf("zero","one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen")
    val text=raw.lowercase().trim().replace('-', ' ')
    val n=singles.indexOf(text); if(n>0)return n.toString()
    val tens=mapOf("twenty" to 20,"thirty" to 30,"forty" to 40,"fifty" to 50,"sixty" to 60,"seventy" to 70,"eighty" to 80,"ninety" to 90)
    val parts=text.split(' ');val base=tens[parts[0]]?:return null
    if(parts.size==1)return base.toString()
    val unit=singles.indexOf(parts.getOrNull(1));return if(parts.size==2 && unit in 1..9)(base+unit).toString() else null
  }
  private fun name(id: String) = staff.find { it.id == id }?.name ?: id
}
