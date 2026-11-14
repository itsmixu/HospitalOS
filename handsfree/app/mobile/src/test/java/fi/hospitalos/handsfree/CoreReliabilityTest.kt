package fi.hospitalos.handsfree
import org.junit.Test
import org.junit.Assert.*
class CoreReliabilityTest {
 private var now=1000L
 private val sends=mutableListOf<Pair<String,Action>>()
 private val staff=listOf(Colleague("aino","Aino",true),Colleague("ben","Ben",true),Colleague("sara","Sara",true))
 private fun engine()=HandsFreeEngine("aino",{now}){id,a->sends.add(id to a)}.also{it.connection(true);it.snapshot(staff,emptyList())}
 private fun speak(e:HandsFreeEngine,text:String){e.wake();e.transcript(text)}
 private fun confirm(e:HandsFreeEngine,words:String){e.reviewDisplayed(e.card.promptId);e.transcript(words)}
 private fun task(id:String="t1",from:String="ben",room:String="12",status:String="delivered",revision:Int=2)=Request(id,from,"aino",room,"bring hoist",status,revision)
 @Test fun wakeDoesNotOpenBedside(){val e=engine();e.wake();assertNull(e.bedside.card);assertEquals("Listening",e.card.title)}
 @Test fun multipleRequestsResolveByRoomAndSender(){val e=engine();e.snapshot(staff,listOf(task(),task("t2","sara")));speak(e,"review request");assertNotEquals("review",e.card.phase);speak(e,"review request in room twelve from Sara");confirm(e,"accept request");assertEquals("t2",sends.single().second.taskId)}
 @Test fun multipleAcceptedTasksCanBeCompletedIndividually(){val e=engine();e.snapshot(staff,listOf(task(status="accepted"),task("t2","ben","7","accepted")));speak(e,"complete request in room seven");confirm(e,"confirm completion");assertEquals("t2",sends.single().second.taskId)}
 @Test fun outgoingCancellationRequiresCurrentReview(){val e=engine();e.snapshot(staff,listOf(task().copy(from="aino",to="ben")));speak(e,"cancel request to Ben");assertTrue(sends.isEmpty());confirm(e,"confirm cancellation");assertEquals("request.cancel",sends.single().second.type)}
 @Test fun availabilityIsReviewed(){val e=engine();speak(e,"set me busy");assertTrue(sends.isEmpty());confirm(e,"confirm status");assertEquals("busy",sends.single().second.fields["status"])}
 @Test fun incomingMessageCanBeReadAndRepliedTo(){val e=engine();e.snapshot(staff,emptyList(),listOf(WardMessage("m","ben","aino","Can you help?")));speak(e,"read message");assertEquals("Can you help?",e.card.body);assertTrue(sends.isEmpty());e.transcript("reply I am on my way");confirm(e,"send message");assertEquals("ben",sends.single().second.to);assertEquals("I am on my way",sends.single().second.text)}
 @Test fun messageClosureIsExplicit(){val e=engine();e.snapshot(staff,emptyList(),listOf(WardMessage("m","ben","aino","Hello")));speak(e,"read message");e.transcript("close message");assertEquals("message.read",sends.single().second.type)}
 @Test fun newAlertDoesNotChangeCurrentRecipientOrRevision(){val e=engine();e.snapshot(staff,listOf(task()));speak(e,"review request");val card=e.card;e.snapshot(staff,listOf(task(),task("t2","sara","7")));assertEquals(card,e.card);confirm(e,"accept request");assertEquals("t1",sends.single().second.taskId)}
 @Test fun heartbeatCannotEraseUnknownOutcome(){val e=engine();speak(e,"ask Ben to bring the hoist to room twelve");confirm(e,"send request");now+=5001;e.tick();val card=e.card;e.snapshot(staff,emptyList());assertEquals(card,e.card);assertEquals("Outcome unknown",card.title)}
 @Test fun reconnectLooksUpButDoesNotResend(){val e=engine();speak(e,"ask Ben to bring the hoist to room twelve");confirm(e,"send request");val original=sends.single();e.connection(false);e.connection(true);assertEquals(2,sends.size);assertEquals("command.status",sends.last().second.type);assertEquals(original.first,sends.last().second.fields["commandId"]);e.reconciled(original.first,true,"t1",null);assertNull(e.unresolvedCommandId)}
 @Test fun wrongReconciliationCannotResolve(){val e=engine();e.restoreUnresolvedCommand("pending");e.connection(true);e.reconciled("wrong",true,null,null);assertEquals("pending",e.unresolvedCommandId)}
 @Test fun restoredOpaqueIdCanBeReconciled(){val e=engine();e.restoreUnresolvedCommand("old-session-1");e.connection(true);assertEquals("command.status",sends.single().second.type);e.reconciled("old-session-1",null,null,null);assertEquals("No recorded outcome",e.card.title);assertEquals(1,sends.size)}
 @Test fun unresolvedSendBlocksNewMutation(){val e=engine();e.restoreUnresolvedCommand("unknown");speak(e,"ask Ben to bring the hoist to room twelve");assertEquals("Previous outcome unresolved",e.card.title);assertTrue(sends.isEmpty())}
 @Test fun cancelledNoticeSurvivesSnapshot(){val e=engine();speak(e,"ask Ben to bring the hoist to room twelve");e.transcript("cancel");e.snapshot(staff,emptyList());assertEquals("Cancelled",e.card.title)}
 @Test fun bedsideReviewNotInterruptedByWakeOrTask(){val e=engine();e.bedside.snapshot(listOf(Patient("demo-a","Demo Patient A","enc-a","12")),staff,null);speak(e,"select patient demo-a");val card=e.card;e.wake();e.snapshot(staff,listOf(task()));assertEquals(card,e.card);assertTrue(sends.none{it.second.type.startsWith("request.")})}
 @Test fun oldReplyContextCannotLeakIntoNewCommand(){val e=engine();e.snapshot(staff,emptyList(),listOf(WardMessage("m","ben","aino","Hello")));speak(e,"read message");now+=12001;e.tick();speak(e,"reply I agree");assertNotEquals("review",e.card.phase);assertTrue(sends.isEmpty())}
 @Test fun disconnectShowsCommunicationFailureNotUnrelatedPatientScreen(){val e=engine();speak(e,"ask Ben to bring the hoist to room twelve");confirm(e,"send request");e.connection(false);assertEquals("Connection lost",e.card.title);assertTrue(e.card.body.contains("unknown"));assertNull(e.bedside.card)}
 @Test fun lateMatchingAcknowledgementResolvesWithoutRetry(){val e=engine();speak(e,"ask Ben to bring the hoist to room twelve");confirm(e,"send request");val id=sends.single().first;now+=5001;e.tick();assertEquals(id,e.unresolvedCommandId);e.acknowledgement(id,true,"t1",null);assertNull(e.unresolvedCommandId);assertEquals(1,sends.size);assertEquals("Previous action confirmed",e.card.title)}
 @Test fun leavingBedsideNeverRevealsUnboundedCommunicationMic(){val e=engine();e.bedside.snapshot(listOf(Patient("demo-a","Demo Patient A","enc-a","12")),staff,null);speak(e,"select patient demo-a");e.transcript("cancel");e.wake();e.transcript("done");assertFalse(e.microphoneActive);assertNull(e.bedside.card);now+=30000;e.tick();assertFalse(e.microphoneActive)}
 @Test fun retainedPatientContextCannotHideUnknownCommunicationOutcome(){val e=engine();e.bedside.snapshot(listOf(Patient("demo-a","Demo Patient A","enc-a","12")),staff,null);speak(e,"select patient demo-a");confirm(e,"confirm patient");e.acknowledgement(sends.last().first,true,null,null,PatientContext("demo-a","enc-a",1),null);e.transcript("done");sends.clear();speak(e,"ask Ben to bring the hoist to room twelve");confirm(e,"send request");e.connection(false);assertNull(e.bedside.context);assertNull(e.bedside.card);assertEquals("Connection lost",e.card.title);assertTrue(e.card.body.contains("unknown"))}
 @Test fun sharedReadingUsesLiveRequestStatusWithoutInterruptingNextReview(){
  val e=engine();e.bedside.snapshot(listOf(Patient("demo-a","Demo Patient A","enc-a","12")),staff,null)
  speak(e,"select patient demo-a");confirm(e,"confirm patient");e.acknowledgement(sends.last().first,true,null,null,PatientContext("demo-a","enc-a",1))
  e.transcript("capture reading");e.reviewDisplayed(e.card.promptId)
  e.bedside.captureResult(e.bedside.ticket!!.id,fi.hospitalos.handsfree.capture.Extraction.Accepted(fi.hospitalos.handsfree.capture.Reading(120,80,70)))
  confirm(e,"save reading");e.acknowledgement(sends.last().first,true,null,null,null,"ob-1")
  e.transcript("share reading with Ben");confirm(e,"send attachment")
  val shared=task("shared","aino",status="sent",revision=1).copy(to="ben")
  e.snapshot(staff,listOf(shared));e.acknowledgement(sends.last().first,true,"shared",null)
  assertNull(e.bedside.card);assertFalse(e.microphoneActive)
  e.snapshot(staff,listOf(shared.copy(status="accepted",revision=2)));assertEquals("Ben is coming",e.card.title)
  speak(e,"ask Ben to bring the hoist to room twelve");val review=e.card
  e.snapshot(staff,listOf(shared.copy(status="completed",revision=3)));assertEquals(review,e.card)
 }

}
