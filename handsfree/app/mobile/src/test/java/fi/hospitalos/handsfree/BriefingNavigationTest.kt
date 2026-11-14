package fi.hospitalos.handsfree

import fi.hospitalos.handsfree.capture.Reading
import org.junit.Assert.*
import org.junit.Test

class BriefingNavigationTest {
  private var now = 1000L
  private val sent = mutableListOf<Pair<String, Action>>()
  private val staff = listOf(Colleague("aino", "Aino", true), Colleague("ben", "Ben", true), Colleague("sara", "Sara", false))
  private val patient = Patient("demo-a", "Demo Patient A", "enc-a", "12", "Assistance required for transfers", "Discomfort when standing")
  private val context = PatientContext("demo-a", "enc-a", 1)
  private fun task(id: String = "t1", status: String = "delivered") = Request(id, "ben", "aino", "12", "Review bedside reading", status, 2, attachment = attachment())
  private fun attachment() = RequestAttachment("ob-1", "demo-a", "Demo Patient A", "enc-a", Reading(137, 82, 71), "mmHg", "bpm", "2026-11-14T10:00:00Z", listOf("pulse"), "dat-camera")
  private fun engine(tasks: List<Request> = emptyList()) = HandsFreeEngine("aino", { now }) { id, a -> sent.add(id to a) }.also {
    it.connection(true)
    it.bedside.snapshot(listOf(patient), staff, null)
    it.snapshot(staff, tasks)
  }
  private fun speak(e: HandsFreeEngine, text: String) { e.wake(); e.transcript(text) }
  private fun confirm(e: HandsFreeEngine, text: String) { e.reviewDisplayed(e.card.promptId); e.transcript(text) }
  private fun identify(e: HandsFreeEngine) {
    speak(e, "select patient demo-a"); confirm(e, "confirm patient")
    e.acknowledgement(sent.last().first, true, null, null, context)
    sent.clear()
  }

  @Test fun receivingGlassesShowExactReadingAndProvenanceWithoutAccepting() {
    val e = engine(listOf(task()))
    speak(e, "review request"); val old = e.card.promptId; e.reviewDisplayed(old)
    e.transcript("show details")
    assertEquals("details", e.card.phase)
    e.physicalAccept(old); e.transcript("accept request")
    assertTrue(sent.isEmpty())
    speak(e, "show requests"); e.transcript("open"); e.transcript("next")
    assertTrue(e.card.body.contains("Demo Patient A · demo-a"))
    assertTrue(e.card.body.contains("SYS 137 / DIA 82 mmHg"))
    assertTrue(e.card.body.contains("Pulse 71 bpm"))
    e.transcript("next")
    assertTrue(e.card.body.contains("2026-11-14T10:00:00Z"))
    assertTrue(e.card.body.contains("freshness unknown"))
    assertTrue(e.card.body.contains("Manual correction: pulse"))
    e.transcript("next")
    assertTrue(e.card.body.contains(patient.warning!!))
    assertTrue(sent.isEmpty())
  }

  @Test fun backFromDetailsCreatesFreshReviewAndRejectsOldConfirmation() {
    val e = engine(listOf(task()))
    speak(e, "review request"); val old = e.card.promptId; e.reviewDisplayed(old)
    e.transcript("show details"); e.transcript("back")
    assertEquals("review", e.card.phase); assertNotEquals(old, e.card.promptId)
    e.physicalAccept(old); e.transcript("accept request"); assertTrue(sent.isEmpty())
    confirm(e, "accept request"); assertEquals("request.accept", sent.single().second.type)
  }

  @Test fun cancelledRequestCannotBeRevivedByReturningFromDetails() {
    val e = engine(listOf(task()))
    speak(e, "review request"); e.transcript("show details")
    e.snapshot(staff, listOf(task().copy(status = "cancelled", revision = 3)))
    e.transcript("back"); e.physicalAccept(e.card.promptId)
    assertNotEquals("review", e.card.phase); assertTrue(sent.isEmpty())
  }

  @Test fun attachmentRevocationRemovesVisibleValuesAndInvalidatesReviewEvenAtSameRevision() {
    val e = engine(listOf(task()))
    speak(e, "review request"); val old = e.card.promptId; e.reviewDisplayed(old)
    val revoked = task().copy(attachment = null, attachmentUnavailable = true)
    e.snapshot(staff, listOf(revoked)); e.physicalAccept(old); assertTrue(sent.isEmpty())
    speak(e, "show requests"); e.transcript("open"); e.transcript("next")
    assertTrue(e.card.body.contains("attachment unavailable")); assertFalse(e.card.body.contains("137"))
  }

  @Test fun attachmentWithoutPatientAssignmentNeverAddsWarnings() {
    val e = engine(listOf(task())); e.bedside.snapshot(emptyList(), staff, null)
    speak(e, "show requests"); e.transcript("open"); repeat(8) { e.transcript("next") }
    assertFalse(e.card.body.contains(patient.warning!!))
  }

  @Test fun listTraversesMoreThanThreeRequestsAndOpensTheDisplayedId() {
    val e = engine((1..6).map { task("t$it").copy(room = "$it") })
    speak(e, "show requests"); repeat(4) { e.transcript("next") }
    assertTrue(e.card.body.contains("5/6")); e.transcript("open")
    assertTrue(e.card.body.contains("Room 5")); e.transcript("review request")
    confirm(e, "accept request"); assertEquals("t5", sent.single().second.taskId)
  }

  @Test fun listSelectionSurvivesReorderingButDoesNotSilentlyChooseReplacement() {
    val t1 = task(); val t2 = task("t2").copy(room = "14")
    val e = engine(listOf(t1, t2)); speak(e, "show requests"); e.transcript("next")
    e.snapshot(staff, listOf(t2, t1)); assertTrue(e.card.body.contains("Room 14"))
    e.snapshot(staff, listOf(t1)); e.transcript("open")
    assertEquals("browse", e.card.phase); assertTrue(e.card.body.contains("List changed"))
    e.transcript("next"); e.transcript("open"); assertTrue(e.card.body.contains("Room 12"))
    assertTrue(sent.isEmpty())
  }

  @Test fun listsSeparatePendingAcceptedAndHistory() {
    val e = engine(listOf(task(), task("t2", "accepted"), task("t3", "completed")))
    speak(e, "show requests"); assertTrue(e.card.body.contains("1/1")); assertTrue(e.card.body.contains("delivered"))
    e.transcript("show accepted requests"); assertTrue(e.card.body.contains("accepted"))
    e.transcript("show request history"); assertTrue(e.card.body.contains("completed"))
    e.transcript("open"); e.transcript("complete request"); assertTrue(sent.isEmpty()); assertNotEquals("review", e.card.phase)
  }

  @Test fun backFromOpenedRequestRestoresTheListSelection() {
    val e = engine(listOf(task(), task("t2")))
    speak(e, "show requests"); e.transcript("next"); val listing = e.card.body
    e.transcript("open"); e.transcript("next"); e.transcript("back")
    assertEquals(listing, e.card.body)
  }

  @Test fun availableColleaguesExcludeSelfBusyAndOffline() {
    val e = engine(); e.snapshot(staff + Colleague("busy", "Busy", true, "busy"), emptyList())
    speak(e, "who's available?")
    assertTrue(e.card.body.contains("Ben")); assertTrue(e.card.body.contains("1/1"))
    e.transcript("open"); assertEquals("dictating-message", e.card.phase); assertTrue(sent.isEmpty())
  }

  @Test fun rightTiltOpensOnlyExactRenderedListItem() {
    val e = engine(); speak(e, "show patients"); val old = e.card.promptId
    e.physicalAccept(old); assertEquals("browse", e.card.phase)
    e.reviewDisplayed(old); e.physicalAccept(old)
    assertEquals("Confirm patient", e.card.title); assertTrue(sent.none { it.second.type == "patient.confirm" })
    e.physicalAccept(old); assertTrue(sent.none { it.second.type == "patient.confirm" })
    confirm(e, "confirm patient"); assertEquals("patient.confirm", sent.last().second.type)
  }

  @Test fun patientOverviewRequiresConfirmedIdentityAndShowsReportedLabels() {
    val e = engine(); speak(e, "show patient details"); assertEquals("Confirm patient first", e.card.title)
    identify(e); e.transcript("show patient details")
    assertTrue(e.card.body.contains("demo-a")); e.transcript("next"); assertTrue(e.card.body.contains(patient.warning!!))
    e.transcript("next"); assertTrue(e.card.body.contains("Patient reported")); assertTrue(e.card.body.contains(patient.reported!!))
    assertTrue(sent.isEmpty())
  }

  @Test fun latestNoteAndReadingStayBoundToSelectedPatient() {
    val e = engine(); identify(e)
    e.bedside.snapshot(listOf(patient), staff, context,
      listOf(SavedReading("a", "demo-a", Reading(120, 80, 70), "2026-11-14T10:00:00Z", false), SavedReading("b", "demo-b", Reading(999, 999, 999), "2026-11-14T11:00:00Z", true)),
      listOf(SavedNote("n1", "demo-a", "Requested water.", "2026-11-14T10:00:00Z"), SavedNote("n2", "demo-b", "Other patient's note", "2026-11-14T11:00:00Z")))
    e.transcript("show latest note"); assertTrue(e.card.body.contains("Requested water.")); assertFalse(e.card.body.contains("Other patient's"))
    e.transcript("show latest reading"); assertTrue(e.card.body.contains("120")); assertFalse(e.card.body.contains("999"))
  }

  @Test fun revokingPatientContextClearsAnOpenOverview() {
    val e = engine(); identify(e); e.transcript("show patient details"); e.transcript("next")
    e.bedside.snapshot(emptyList(), staff, null); e.snapshot(staff, emptyList())
    assertFalse(e.card.body.contains(patient.warning!!))
    speak(e, "show patient details"); assertEquals("Confirm patient first", e.card.title)
  }

  @Test fun openingAndBackingOutOfMessageDoesNotMarkItRead() {
    val e = engine(); e.snapshot(staff, emptyList(), listOf(WardMessage("m1", "ben", "aino", "Bring water")))
    speak(e, "show messages"); e.transcript("open"); assertEquals("Bring water", e.card.body)
    e.transcript("back"); assertEquals("browse", e.card.phase); assertTrue(sent.isEmpty())
    e.transcript("open"); e.transcript("close message"); assertEquals("message.read", sent.single().second.type)
  }

  @Test fun sentMessageHistoryCannotBecomeReplyToSelfOrIncomingRead() {
    val e = engine(); e.snapshot(staff, emptyList(), listOf(WardMessage("m1", "aino", "ben", "Bring water")))
    speak(e, "show messages"); e.transcript("open"); e.transcript("reply yes")
    assertTrue(sent.isEmpty()); assertNotEquals("review", e.card.phase)
  }

  @Test fun helpOnReviewRequiresRenderingAgainAndDoesNotExtendDeadline() {
    val e = engine(listOf(task())); speak(e, "review request"); val old = e.card.promptId; e.reviewDisplayed(old)
    now += 7000; e.transcript("what can I say?"); assertTrue(e.card.hint.contains("show details"))
    e.physicalAccept(old); assertTrue(sent.isEmpty())
    now += 1001; e.tick(); e.reviewDisplayed(e.card.promptId); e.physicalAccept(e.card.promptId); assertTrue(sent.isEmpty())
  }

  @Test fun navigationTimeoutAndReconnectDoNotRestoreOldViewsOrActions() {
    val e = engine(listOf(task())); e.quietMode = true
    speak(e, "show requests"); e.transcript("open"); now += 20001; e.tick()
    assertEquals("quiet", e.card.phase); e.physicalAccept(e.card.promptId); assertTrue(sent.isEmpty())
    speak(e, "show requests"); e.connection(false); e.connection(true)
    assertNotEquals("browse", e.card.phase); assertNotEquals("details", e.card.phase)
  }

  @Test fun messageAliasPreservesFreeDictationAndDoesNotInterpretNavigationWords() {
    val e = engine(); speak(e, "Send Ben a message"); assertEquals("dictating-message", e.card.phase)
    e.transcript("show patients"); assertEquals("show patients", e.card.body)
    confirm(e, "send message"); assertEquals("show patients", sent.single().second.text)
  }

  @Test fun noteDictationTreatsPatientSelectionWordsAsContent() {
    val e = engine(); identify(e); e.transcript("add patient note"); e.transcript("select patient demo-b")
    assertEquals("Review patient note", e.card.title); assertTrue(e.card.body.contains("select patient demo-b"))
    assertEquals(context, e.bedside.context); assertTrue(sent.isEmpty())
  }

  @Test fun helpAliasRequiresLocationAndExplicitConfirmation() {
    val e = engine(); speak(e, "Ask Ben for help"); assertEquals("Which room?", e.card.title); assertTrue(sent.isEmpty())
    speak(e, "Ask Ben for help in room twelve"); assertEquals("review", e.card.phase)
    e.transcript("send request"); assertTrue(sent.isEmpty())
    confirm(e, "send request"); assertEquals("ben", sent.single().second.to); assertEquals("12", sent.single().second.room)
  }

  @Test fun contextualHelpUsesConfirmedRoomAndInvalidatesOnContextChange() {
    val e = engine(); speak(e, "get help here"); assertEquals("Which room?", e.card.title)
    identify(e); e.transcript("get help here"); assertTrue(e.card.body.contains("Room 12")); val old = e.card.promptId; e.reviewDisplayed(old)
    e.bedside.snapshot(listOf(patient), staff, null); e.snapshot(staff, emptyList()); e.physicalAccept(old)
    assertTrue(sent.none { it.second.type.startsWith("request.") })
  }

  @Test fun confirmedContextualHelpBroadcastContainsNoPatientInformation() {
    val e = engine(); identify(e); e.transcript("get help here"); confirm(e, "send request")
    val action = sent.single().second
    assertEquals("request.broadcast", action.type); assertEquals("12", action.room)
    assertEquals(mapOf("required" to 1), action.fields)
  }

  @Test fun negatedAndIncidentalAliasesNeverBecomeRequests() {
    for (words in listOf("Don't get help here", "Do not ask Ben for help", "She said send Ben a message", "Ask Ben for help in room banana")) {
      val e = engine(); speak(e, words); assertNotEquals("review", e.card.phase); assertTrue(sent.isEmpty())
    }
  }

  @Test fun browsingCannotBypassUnresolvedActionGuard() {
    val e = engine(listOf(task())); e.restoreUnresolvedCommand("unknown")
    speak(e, "show requests"); e.transcript("open"); e.transcript("review request")
    assertNotEquals("review", e.card.phase); assertTrue(sent.isEmpty()); assertEquals("unknown", e.unresolvedCommandId)
  }

  private fun wireAttachment() = mapOf<String, Any?>(
    "id" to "ob-1", "patientId" to "demo-a", "patientName" to "Demo Patient A", "encounterId" to "enc-a",
    "values" to mapOf("systolic" to 137, "diastolic" to 82, "pulse" to 71),
    "units" to mapOf("pressure" to "mmHg", "pulse" to "bpm"), "capturedAt" to "2026-11-14T10:00:00Z",
    "corrected" to listOf("pulse"), "source" to "dat-camera", "measurementTime" to null,
  )
  private fun wireTask(a: Any?) = mapOf("id" to "t1", "from" to "ben", "to" to "aino", "room" to "12", "text" to "Review bedside reading", "status" to "delivered", "revision" to 2, "attachment" to a)

  @Test fun productionMapperRetainsAttachmentAndPatientFields() {
    val t = WardSnapshot.request(wireTask(wireAttachment()))!!
    assertEquals(attachment(), t.attachment); assertFalse(t.attachmentUnavailable)
    val p = WardSnapshot.patient(mapOf("id" to "demo-a", "name" to "Demo Patient A", "encounterId" to "enc-a", "room" to "12", "warning" to "Transfer assistance", "reported" to "Discomfort"))!!
    assertEquals("Transfer assistance", p.warning); assertEquals("Discomfort", p.reported)
  }

  @Test fun malformedAttachmentIsMarkedUnavailableNeverFilledWithDefaults() {
    val patches = listOf(
      mapOf("values" to mapOf("systolic" to 137, "pulse" to 71)),
      mapOf("values" to mapOf("systolic" to 137.5, "diastolic" to 82, "pulse" to 71)),
      mapOf("units" to mapOf("pressure" to "kPa", "pulse" to "bpm")),
      mapOf("capturedAt" to "yesterday"), mapOf("corrected" to listOf("unknown")), mapOf("source" to "guess"),
    )
    for (patch in patches) {
      val t = WardSnapshot.request(wireTask(wireAttachment() + patch))!!
      assertNull(t.attachment); assertTrue(t.attachmentUnavailable)
    }
    assertFalse(WardSnapshot.request(wireTask(null))!!.attachmentUnavailable)
  }

  @Test fun receivingWorkflowUsesMappedPayloadThroughReviewAcceptanceAndExplicitCompletion() {
    val received = WardSnapshot.request(wireTask(wireAttachment()))!!
    val e = engine(listOf(received))
    e.offerIncoming(); assertTrue(e.card.body.contains("Reading attached"))
    e.transcript("show details"); e.transcript("next"); assertTrue(e.card.body.contains("Pulse 71 bpm"))
    e.transcript("back"); confirm(e, "accept request")
    assertEquals("request.accept", sent.last().second.type)
    e.acknowledgement(sent.last().first, true, received.id, null)
    e.snapshot(staff, listOf(received.copy(status = "accepted", revision = 3)))
    speak(e, "show details"); e.transcript("next"); assertTrue(e.card.body.contains("Pulse 71 bpm"))
    e.transcript("complete request"); assertEquals("Mark complete?", e.card.title)
    assertEquals(1, sent.size); confirm(e, "confirm completion")
    assertEquals("request.complete", sent.last().second.type); assertEquals(3, sent.last().second.revision)
  }

  @Test fun interruptedDraftAndCaptureCannotBeReplacedByLists() {
    val e = engine(); speak(e, "message Ben"); e.transcript("Please come.")
    val message = e.card; e.transcript("show patients"); assertEquals(message, e.card)
    e.transcript("cancel"); identify(e); e.transcript("capture reading")
    val capture = e.card; e.transcript("show requests"); assertEquals(capture, e.card)
    assertTrue(sent.isEmpty())
  }

  @Test fun helpAliasWithPunctuationPreservesDictatedBodyPunctuation() {
    val e = engine(); speak(e, "Send Ben a message."); assertEquals("dictating-message", e.card.phase)
    e.transcript("cancel"); speak(e, "Send Ben a message saying Please bring water!")
    assertEquals("Please bring water!", e.card.body)
  }
}
