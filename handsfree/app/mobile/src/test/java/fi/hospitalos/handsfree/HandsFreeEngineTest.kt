package fi.hospitalos.handsfree
import org.junit.Assert.*
import org.junit.Test

class HandsFreeEngineTest {
  private var now=1000L
  private val sent=mutableListOf<Pair<String,Action>>()
  private fun engine()=HandsFreeEngine("aino",{now}){id,a->sent.add(id to a)}.also {it.connection(true);it.snapshot(listOf(Colleague("aino","Aino",true),Colleague("ben","Ben",true)),emptyList())}
  private fun draft(e:HandsFreeEngine){e.wake();e.transcript("Ask Ben to bring the hoist to room 12");e.reviewDisplayed(e.card.promptId)}
  private fun nod(e:HandsFreeEngine, yaw:Boolean=false,moving:Boolean=false,prompt:Int=e.card.promptId){
    e.reviewDisplayed(e.card.promptId)
    val values=if(yaw)listOf(0,0,0,0,0,8,16,8,0,-8,-16,-8,0)else listOf(0,0,0,0,0,8,16,8,0,8,16,8,0)
    for(v in values){now+=100;e.motion(HeadSample(prompt,now,if(yaw)0.0 else v.toDouble(),if(yaw)v.toDouble()else 0.0,"simulation",moving))}
  }
  @Test fun delayedGestureStillWorks(){val e=engine();draft(e);now+=2000;nod(e);assertEquals(1,sent.size)}
  @Test fun failedDisplayNeverArms(){val e=engine();e.wake();e.transcript("Ask Ben to bring the hoist to room 12");e.transcript("send request");assertTrue(sent.isEmpty())}
  @Test fun draftDoesNotSend(){val e=engine();draft(e);assertEquals("review",e.card.phase);assertTrue(sent.isEmpty());assertTrue(e.microphoneActive)}
  @Test fun finalOnly(){val e=engine();e.wake();e.transcript("Ask Ben to bring the hoist to room 12",false);assertEquals("listening",e.card.phase);assertTrue(sent.isEmpty())}
  @Test fun noWakeNoAction(){val e=engine();e.transcript("Ask Ben to bring the hoist to room 12");nod(e);assertTrue(sent.isEmpty())}
  @Test fun doubleNodCommitsOnce(){val e=engine();draft(e);nod(e);nod(e);assertEquals(1,sent.size);assertEquals("sending",e.card.phase);assertFalse(e.microphoneActive)}
  @Test fun ackMustMatch(){val e=engine();draft(e);e.transcript("send request");e.acknowledgement("wrong",true,null,null);assertEquals("sending",e.card.phase);e.acknowledgement(sent.single().first,true,null,null);assertEquals("status",e.card.phase)}
  @Test fun shakeCancelsDraft(){val e=engine();draft(e);nod(e,yaw=true);assertEquals("Cancelled",e.card.title);assertTrue(sent.isEmpty())}
  @Test fun walkingRejects(){val e=engine();draft(e);nod(e,moving=true);assertTrue(sent.isEmpty());e.transcript("send request");assertEquals(1,sent.size)}
  @Test fun wrongPromptRejects(){val e=engine();draft(e);nod(e,prompt=999);assertTrue(sent.isEmpty())}
  @Test fun timeoutClosesListening(){val e=engine();draft(e);now+=8001;e.tick();e.transcript("send request");nod(e);assertTrue(sent.isEmpty());assertFalse(e.microphoneActive)}
  @Test fun offlineDiscardsAndNeverReplays(){val e=engine();draft(e);e.connection(false);e.connection(true);e.transcript("send request");nod(e);assertTrue(sent.isEmpty())}
  @Test fun disconnectedRecipientInvalidates(){val e=engine();draft(e);e.snapshot(listOf(Colleague("ben","Ben",false)),emptyList());e.transcript("send request");assertTrue(sent.isEmpty())}
  @Test fun negationDoesNotSend(){for(text in listOf("Don't send help","Don't set me busy","Don't ask Ben to bring the hoist to room 12","Ask Ben to not bring the hoist to room 12","Send it","Accept the request")){val e=engine();e.wake();e.transcript(text);assertFalse(e.card.phase=="review");assertTrue(sent.isEmpty())}}
  @Test fun messageIsContentNotCommand(){val e=engine();e.wake();e.transcript("Tell Ben I'm busy");e.reviewDisplayed(e.card.promptId);e.transcript("send message");assertEquals(Action("message.send",to="ben",text="I'm busy"),sent.single().second)}
  private fun incoming(status:String="delivered",revision:Int=2)=Request("t1","ben","aino","7","bring the transfer belt",status,revision)
  @Test fun incomingDoesNotArmOrInterrupt(){val e=engine();draft(e);val before=e.card;e.snapshot(listOf(Colleague("ben","Ben",true)),listOf(incoming()));assertEquals(before,e.card)}
  @Test fun acceptAndComplete(){val e=engine();e.snapshot(listOf(Colleague("ben","Ben",true)),listOf(incoming()));nod(e);assertTrue(sent.isEmpty());e.wake();e.transcript("review request");nod(e);assertEquals("request.accept",sent.single().second.type);e.acknowledgement(sent.last().first,true,"t1",null);e.snapshot(listOf(Colleague("ben","Ben",true)),listOf(incoming("accepted",3)));e.wake();e.transcript("complete request");e.reviewDisplayed(e.card.promptId);e.transcript("confirm completion");assertEquals("request.complete",sent.last().second.type)}
  @Test fun staleTaskCannotBeAccepted(){val e=engine();e.snapshot(emptyList(),listOf(incoming()));e.wake();e.transcript("review request");e.snapshot(emptyList(),listOf(incoming("cancelled",3)));nod(e);assertTrue(sent.isEmpty())}
  @Test fun ambiguousRequestNotChosen(){val e=engine();e.snapshot(emptyList(),listOf(incoming(),incoming().copy(id="t2")));e.wake();e.transcript("review request");assertNotEquals("review",e.card.phase);assertTrue(sent.isEmpty())}
  @Test fun shakeDeclinesIncoming(){val e=engine();e.snapshot(emptyList(),listOf(incoming()));e.wake();e.transcript("review request");nod(e,yaw=true);assertEquals("request.decline",sent.single().second.type)}
  @Test fun noAckMeansUnknown(){val e=engine();draft(e);e.transcript("send request");now+=5001;e.tick();assertEquals("Outcome unknown",e.card.title);assertEquals(1,sent.size)}
  @Test fun singleNodDoesNotConfirm(){val e=engine();draft(e);val p=e.card.promptId;for(v in listOf(0,0,0,0,0,8,16,8,0)){now+=100;e.motion(HeadSample(p,now,v.toDouble(),0.0))};assertTrue(sent.isEmpty())}
  @Test fun spokenRoomNumber(){val e=engine();e.wake();e.transcript("Ask Ben to bring the hoist to room twelve");e.reviewDisplayed(e.card.promptId);e.transcript("send request");assertEquals("12",sent.single().second.room)}
  @Test fun sourceAndMotionRejection(){
    for(source in listOf("phone","wrist")){val e=engine();draft(e);val p=e.card.promptId;for(v in listOf(0,0,0,0,0,8,16,8,0,8,16,8,0)){now+=100;e.motion(HeadSample(p,now,v.toDouble(),0.0,source))};assertTrue(sent.isEmpty())}
  }
  @Test fun mixedAxisGapAndOldTimestampsReject(){
    for(mode in listOf("mixed","gap","old")){val e=engine();draft(e);val p=e.card.promptId;for((i,v) in listOf(0,0,0,0,0,8,16,8,0,8,16,8,0).withIndex()){now+=if(mode=="gap" && i==5)500 else 100;e.motion(HeadSample(p,if(mode=="old" && i==5)now-200 else now,v.toDouble(),if(mode=="mixed")v.toDouble() else 0.0))};assertTrue(sent.isEmpty())}
  }
  @Test fun requestStatusCanBeCheckedWithoutMutation(){val e=engine();e.snapshot(emptyList(),listOf(incoming("accepted",3)));e.wake();e.transcript("check requests");assertTrue(e.card.body.contains("accepted"));assertFalse(e.microphoneActive);assertTrue(sent.isEmpty())}
 @Test fun physicalOfferRequiresVisibleExactReview(){val e=engine();e.snapshot(emptyList(),listOf(incoming()));e.offerIncoming();assertEquals("ben needs help",e.card.title);val p=e.card.promptId;e.physicalNod(p);assertTrue(sent.isEmpty());e.reviewDisplayed(p);e.physicalNod(p+1);assertTrue(sent.isEmpty());e.physicalNod(p);e.physicalNod(p);assertEquals(1,sent.size)}
 @Test fun physicalOfferDoesNotInterruptOrRepeat(){val e=engine();draft(e);val c=e.card;e.snapshot(listOf(Colleague("ben","Ben",true)),listOf(incoming()));e.offerIncoming();assertEquals(c,e.card);e.transcript("cancel");e.offerIncoming();assertTrue(e.nodAcceptAvailable);now+=8001;e.tick();e.offerIncoming();assertFalse(e.nodAcceptAvailable)}
 @Test fun physicalNodCannotSendOutgoingRequest(){val e=engine();draft(e);e.physicalNod(e.card.promptId);assertTrue(sent.isEmpty())}
 @Test fun quietExpiryClearsWithoutSending(){val e=engine();e.quietMode=true;e.wake();now+=20001;e.tick();assertEquals("quiet",e.card.phase);assertTrue(sent.isEmpty());e.wake();assertEquals("listening",e.card.phase)}
 @Test fun dismissalDoesNotDeclineOrReopenRequest(){val e=engine();e.quietMode=true;e.snapshot(emptyList(),listOf(incoming()));e.offerIncoming();e.reviewDisplayed(e.card.promptId);e.dismissScreen();assertEquals("quiet",e.card.phase);assertTrue(sent.isEmpty());e.offerIncoming();assertEquals("quiet",e.card.phase);e.wake();e.transcript("review request");assertTrue(e.nodAcceptAvailable)}
 @Test fun dismissalPreservesPendingOutcome(){val e=engine();e.quietMode=true;draft(e);e.transcript("send request");e.dismissScreen();assertEquals("sending",e.card.phase);assertEquals(1,sent.size)}
 @Test fun messageNoticeDoesNotReadOrRepeat(){val e=engine();e.quietMode=true;e.dismissScreen();e.snapshot(emptyList(),emptyList(),listOf(WardMessage("m1","ben","aino","hello")));e.offerIncoming();assertEquals("message-notice",e.card.phase);e.dismissScreen();e.offerIncoming();assertEquals("quiet",e.card.phase);assertTrue(sent.isEmpty())}
 @Test fun nodOpensOnlyDisplayedMessageAndDismissKeepsUnread(){val e=engine();e.quietMode=true;e.dismissScreen();e.snapshot(emptyList(),emptyList(),listOf(WardMessage("m1","ben","aino","Bring belt")));e.offerIncoming();val p=e.card.promptId;e.physicalNod(p);assertEquals("message-notice",e.card.phase);e.reviewDisplayed(p);e.physicalNod(p+1);assertEquals("message-notice",e.card.phase);e.physicalNod(p);assertEquals("Bring belt",e.card.body);assertTrue(sent.isEmpty());e.dismissScreen();e.wake();e.transcript("read message");assertEquals("Bring belt",e.card.body)}
 @Test fun rightTiltConfirmsOnlyExactRenderedReviewOnce(){val e=engine();e.wake();e.transcript("Ask Ben to bring the hoist to room 12");val p=e.card.promptId;e.physicalAccept(p);assertTrue(sent.isEmpty());e.reviewDisplayed(p);e.physicalAccept(p+1);assertTrue(sent.isEmpty());assertEquals("send request",e.tiltActionLabel);e.physicalAccept(p);e.physicalAccept(p);assertEquals(1,sent.size);assertEquals("request.create",sent.single().second.type)}
 @Test fun broadcastNeedsReviewAndDoesNotGuessRoom(){val e=engine();e.wake();e.transcript("Request two nurses to room twelve");assertEquals("Request 2 nurses?",e.card.title);assertTrue(sent.isEmpty());e.reviewDisplayed(e.card.promptId);e.physicalAccept(e.card.promptId);assertEquals("request.broadcast",sent.single().second.type);assertEquals(2,sent.single().second.fields["required"]);assertEquals("12",sent.single().second.room)}
}
