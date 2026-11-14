package fi.hospitalos.handsfree
import org.junit.Assert.*
import org.junit.Test

class SpeechGateTest {
  private val idle=SpeechWindow(Card("idle","Ready","","",false),true,true)
  private val review=SpeechWindow(Card("review","Ask Ben?","Room 12","Send request",true,7),true,true)
  @Test fun wakeMustBeStandalone(){assertEquals(SpeechRoute.WAKE,SpeechGate.route("Hospital!",idle,idle));for(text in listOf("we work in a hospital","hospital send request","send request"))assertEquals(SpeechRoute.IGNORE,SpeechGate.route(text,idle,idle))}
  @Test fun finalCanConfirmOnlyItsExactDisplayedReview(){assertEquals(SpeechRoute.COMMAND,SpeechGate.route("send request",review,review));assertEquals(SpeechRoute.STALE,SpeechGate.route("send request",review,review.copy(card=review.card.copy(promptId=8))))}
  @Test fun changedValuesInvalidateEvenSamePromptNumber(){assertEquals(SpeechRoute.STALE,SpeechGate.route("send request",review,review.copy(card=review.card.copy(body="Room 13"))))}
  @Test fun hiddenOrDisconnectedCardsRejectSpeech(){for(state in listOf(review.copy(rendered=false),review.copy(ready=false)))assertEquals(SpeechRoute.STALE,SpeechGate.route("send request",state,state))}
  @Test fun lowOrMalformedConfidenceDoesNotAct(){for(score in listOf(0.1f,Float.NaN,Float.POSITIVE_INFINITY,-0.5f,1.5f))assertEquals(SpeechRoute.IGNORE,SpeechGate.route("send request",review,review,score));assertEquals(SpeechRoute.COMMAND,SpeechGate.route("send request",review,review,-1f))}
  @Test fun stopIsAlwaysAvailableAndNeverMutation(){assertEquals(SpeechRoute.STOP,SpeechGate.route("Stop listening.",null,idle.copy(ready=false)))}
  @Test fun normalizerDoesNotGuessRecipientsOrRewriteMessages(){assertEquals("select patient demo-a",SpeechGate.normalize("Select patient demo A."));for(text in listOf("ask them to come","tell Ben patient demo a is ready","do not send request"))assertEquals(text,SpeechGate.normalize(text))}
  @Test fun staleWakeAndDuplicateWakeDoNotChangeReview(){assertEquals(SpeechRoute.STALE,SpeechGate.route("hospital",idle,review));assertEquals(SpeechRoute.IGNORE,SpeechGate.route("hospital",review,review))}
 @Test fun practicalActivationPhrase(){for(t in listOf("Hey Lumi","Hey, Lumi!","HEY LUMI"))assertEquals(SpeechRoute.WAKE,SpeechGate.route(t,idle,idle));for(t in listOf("nurse","tell the nurse","hey lumi send request"))assertEquals(SpeechRoute.IGNORE,SpeechGate.route(t,idle,idle));assertEquals(SpeechRoute.IGNORE,SpeechGate.route("Hey Lumi",review,review))}
}
