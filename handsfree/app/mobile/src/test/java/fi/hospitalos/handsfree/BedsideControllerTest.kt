package fi.hospitalos.handsfree
import fi.hospitalos.handsfree.capture.*
import org.junit.Test
import org.junit.Assert.*

class BedsideControllerTest {
  private var now=1000L;private val sent=mutableListOf<Pair<String,Action>>()
  private val patients=listOf(Patient("demo-a","Demo Patient A","enc-a","12"),Patient("demo-b","Demo Patient B","enc-b","14"))
  private val people=listOf(Colleague("aino","Aino",true),Colleague("ben","Ben",true))
  private fun controller()=BedsideController({now},{1800000000000+now}){id,a->sent.add(id to a)}.also{it.connection(true);it.snapshot(patients,people,null)}
  private fun identify(c:BedsideController){c.wake();c.transcript("select patient demo-a");c.displayAccepted(c.card!!.promptId);c.transcript("confirm patient");val id=sent.last().first;c.acknowledgement(id,true,PatientContext("demo-a","enc-a",1),null,null)}
  private fun acquire(c:BedsideController):String {c.transcript("capture reading");c.displayAccepted(c.card!!.promptId);return c.ticket!!.id}
  private fun read(c:BedsideController){val id=acquire(c);c.captureResult(id,Extraction.Accepted(Reading(139,81,73)));c.displayAccepted(c.card!!.promptId)}
  private fun saves()=sent.count{it.second.type=="observation.save"}
  @Test fun fullSaveThenSeparateShare(){val c=controller();identify(c);read(c);assertEquals(0,saves());c.transcript("save reading");assertEquals(1,saves());c.acknowledgement(sent.last().first,true,null,"ob-1",null);assertEquals("ob-1",c.savedId);assertTrue(sent.none{it.second.type=="observation.share"});c.transcript("share reading with Ben");assertTrue(c.card!!.body.contains("139 mmHg"));assertTrue(c.card!!.title.contains("Ben"));c.displayAccepted(c.card!!.promptId);c.transcript("send attachment");assertEquals("observation.share",sent.last().second.type)}
  @Test fun roomAloneNeverIdentifies(){val c=controller();c.wake();c.transcript("select patient 12");assertNull(c.context);assertTrue(sent.none{it.second.type=="patient.confirm"})}
  @Test fun identityMustBeRenderedAndConfirmed(){val c=controller();c.wake();c.transcript("select patient Demo Patient A");c.transcript("confirm patient");assertNull(c.context);assertTrue(sent.none{it.second.type=="patient.confirm"})}
  @Test fun captureRequiresPatient(){val c=controller();c.wake();c.transcript("capture reading");assertNull(c.ticket)}
  @Test fun cameraWaitsForVisibleIndicator(){val c=controller();identify(c);c.transcript("capture reading");assertFalse(c.cameraReady(c.ticket!!.id));c.captureResult(c.ticket!!.id,Extraction.Accepted(Reading(1,2,3)));assertNull(c.candidate)}
  @Test fun saveRequiresRenderedExactReview(){val c=controller();identify(c);val id=acquire(c);c.captureResult(id,Extraction.Accepted(Reading(1,2,3)));c.transcript("save reading");assertEquals(0,saves())}
  @Test fun correctionsInvalidateOldPromptAndKeepProvenance(){val c=controller();identify(c);read(c);val old=c.card!!.promptId;c.transcript("correct systolic 141");assertNotEquals(old,c.card!!.promptId);c.displayAccepted(old);c.transcript("save reading");assertEquals(0,saves());c.displayAccepted(c.card!!.promptId);c.transcript("save reading");val a=sent.last().second;assertEquals(listOf("systolic"),a.fields["corrected"]);assertEquals(139,(a.fields["original"] as Map<*,*>)["systolic"]);assertEquals(141,(a.fields["values"] as Map<*,*>)["systolic"])}
  @Test fun duplicateConfirmationOnlySendsOnce(){val c=controller();identify(c);read(c);c.transcript("save reading");c.transcript("save reading");assertEquals(1,saves())}
  @Test fun switchingPatientRejectsOldFrame(){val c=controller();identify(c);val id=acquire(c);c.transcript("select patient demo-b");c.captureResult(id,Extraction.Accepted(Reading(1,2,3)));assertNull(c.candidate);assertNull(c.context);assertTrue(c.card!!.body.contains("demo-b"))}
  @Test fun staleResultAfterRetryIgnored(){val c=controller();identify(c);read(c);val old=c.candidate!!.ticket.id;c.transcript("retry");c.displayAccepted(c.card!!.promptId);assertNotEquals(old,c.ticket!!.id);c.captureResult(old,Extraction.Accepted(Reading(9,9,9)));assertNull(c.candidate)}
  @Test fun cancelDiscardsFramesAndDraft(){val c=controller();identify(c);val id=acquire(c);c.transcript("cancel");c.captureResult(id,Extraction.Accepted(Reading(1,2,3)));c.transcript("save reading");assertNull(c.candidate);assertEquals(0,saves())}
  @Test fun suspensionAndDisconnectRejectLateResults(){for(mode in listOf("suspend","disconnect")){val c=controller();identify(c);val id=acquire(c);if(mode=="suspend")c.suspend()else c.connection(false);c.captureResult(id,Extraction.Accepted(Reading(1,2,3)));assertNull(c.candidate);assertNull(c.context)}}
  @Test fun offlineSaveNeverQueues(){val c=controller();identify(c);read(c);c.connection(false);c.transcript("save reading");c.connection(true);assertEquals(0,saves());assertNull(c.candidate)}
  @Test fun timeoutStopsCameraAndInvalidatesReview(){val c=controller();identify(c);val id=acquire(c);now+=5001;c.tick();assertNull(c.ticket);c.captureResult(id,Extraction.Accepted(Reading(1,2,3)));assertNull(c.candidate)}
  @Test fun deniedAndUnavailableCamerasCannotSave(){for(reason in listOf("Permission denied","Camera unavailable")){val c=controller();identify(c);val id=acquire(c);c.cameraError(id,reason);c.transcript("save reading");assertNull(c.candidate);assertEquals(0,saves())}}
  @Test fun changedEncounterRevisionInvalidatesReview(){val c=controller();identify(c);read(c);c.snapshot(patients,people,PatientContext("demo-a","enc-a",2));c.transcript("save reading");assertEquals(0,saves());assertNull(c.candidate)}
  @Test fun unrelatedRequestCannotStealReview(){val e=HandsFreeEngine("aino",{now}){id,a->sent.add(id to a)};e.connection(true);e.snapshot(people,emptyList());e.bedside.snapshot(patients,people,null);e.wake();e.transcript("select patient demo-a");e.reviewDisplayed(e.card.promptId);e.transcript("confirm patient");e.acknowledgement(sent.last().first,true,null,null,PatientContext("demo-a","enc-a",1));e.transcript("capture reading");e.reviewDisplayed(e.card.promptId);e.bedside.captureResult(e.bedside.ticket!!.id,Extraction.Accepted(Reading(1,2,3)));val prompt=e.card;e.snapshot(people,listOf(Request("incoming","ben","aino","7","help","sent",1)));assertEquals(prompt,e.card);e.reviewDisplayed(e.card.promptId);e.transcript("accept request");assertTrue(sent.none{it.second.type=="request.accept"});e.transcript("save reading");assertEquals("observation.save",sent.last().second.type)}
  @Test fun repeatedSaveAfterAckPreservesSavedCard(){val c=controller();identify(c);read(c);c.transcript("save reading");c.acknowledgement(sent.last().first,true,null,"o",null);val card=c.card;c.transcript("save reading");assertEquals(card,c.card);assertEquals(1,saves())}
  @Test fun wrongAckDoesNotSave(){val c=controller();identify(c);read(c);c.transcript("save reading");assertFalse(c.acknowledgement("late",true,null,"o",null));assertNull(c.savedId)}
  @Test fun displayWaitDoesNotConsumeAcquisitionBudget(){val c=controller();identify(c);c.transcript("capture reading");val id=c.ticket!!.id;now+=4000;c.displayAccepted(c.card!!.promptId);now+=4500;c.captureResult(id,Extraction.Accepted(Reading(123,81,70)));assertNotNull(c.candidate)}
  @Test fun repeatedDisplayAcceptanceCannotExtendCameraLifetime(){val c=controller();identify(c);acquire(c);now+=4000;c.displayAccepted(c.card!!.promptId);now+=1001;c.tick();assertNull(c.ticket);assertNull(c.candidate)}
  @Test fun absentDisplayTimesOutWithoutStartingCamera(){val c=controller();identify(c);c.transcript("capture reading");val id=c.ticket!!.id;now+=8001;c.displayAccepted(c.card!!.promptId);assertFalse(c.cameraReady(id));c.tick();assertNull(c.ticket)}
 @Test fun rightTiltPatientSaveShareAreSeparateExactReviews(){
 val c=controller();c.wake();c.transcript("select patient demo-a");val p=c.card!!.promptId
 c.physicalAccept(p);assertTrue(sent.none{it.second.type=="patient.confirm"});c.displayAccepted(p);c.physicalAccept(p+1);assertTrue(sent.none{it.second.type=="patient.confirm"});c.physicalAccept(p)
 assertEquals("patient.confirm",sent.last().second.type);c.acknowledgement(sent.last().first,true,PatientContext("demo-a","enc-a",1),null,null)
 read(c);assertEquals("save reading",c.tiltActionLabel);c.physicalAccept(c.card!!.promptId);assertEquals(1,saves());c.acknowledgement(sent.last().first,true,null,"ob-tilt",null)
 c.transcript("share reading with Ben");val share=c.card!!.promptId;c.physicalAccept(share);assertTrue(sent.none{it.second.type=="observation.share"});c.displayAccepted(share);c.physicalAccept(share);assertEquals("observation.share",sent.last().second.type)
 }
}
