package fi.hospitalos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fi.hospitalos.AppViewModel
import fi.hospitalos.assistant.ModelState
import fi.hospitalos.glasses.GlassesStep
import fi.hospitalos.glasses.HeadingSource
import fi.hospitalos.glasses.Screen
import fi.hospitalos.glasses.VoiceSource
import fi.hospitalos.ward.Link
import fi.hospitalos.ward.Snapshot

/**
 * The phone on stage: not part of the product, just the presenter's remote. A live mirror of the
 * lens, the assistant (talk or type), jumps to any glasses screen, and demo triggers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageScreen(vm: AppViewModel, modifier: Modifier, openConnect: () -> Unit) {
  val link by vm.ward.link.collectAsStateWithLifecycle()
  val s by vm.ward.snapshot.collectAsStateWithLifecycle()
  val step by vm.glasses.step.collectAsStateWithLifecycle()
  val screen by vm.flow.screen.collectAsStateWithLifecycle()
  val heard by vm.flow.heardSoFar.collectAsStateWithLifecycle()
  val voice by vm.glasses.voice.source.collectAsStateWithLifecycle()
  val head by vm.glasses.head.source.collectAsStateWithLifecycle()
  val turn by vm.glasses.head.turnDegrees.collectAsStateWithLifecycle()
  var lastReply by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) { vm.ward.replies.collect { lastReply = "\"${it.heard}\" → ${it.say}${if (it.usedClaude) "" else "  (offline parser)"}" } }

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("Stage remote", color = C.Muted, fontSize = 14.sp)
        Text(s?.me?.name ?: "Not connected", color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
      }
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(if (link == Link.ONLINE) "Server" else "Server offline", if (link == Link.ONLINE) C.Green else C.Red)
        StatusPill(if (step == GlassesStep.ON) "Glasses" else "Glasses off", if (step == GlassesStep.ON) C.Green else C.Muted)
        s?.me?.status?.let { StatusPill(if (it == "busy") "Busy" else "Free", if (it == "busy") C.Amber else C.Green) }
      }
    }

    if (s == null || step != GlassesStep.ON) {
      Spacer(Modifier.height(16.dp))
      Panel(accent = C.Blue) {
        Title(if (s == null) "Connect the ward server" else "Start the glasses display")
        if (step == GlassesStep.REGISTERED && s != null) PrimaryButton("Start glasses display", { vm.glasses.startDisplay() })
        else PrimaryButton("Open Connect", openConnect)
      }
    }

    // Live mirror of the lens.
    SectionTitle("On the glasses now")
    Panel(accent = C.Blue) {
      AnimatedContent(
          describe(screen, s),
          transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith fadeOut(tween(120)) },
          label = "lens",
      ) { Text(it, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
      if (heard.isNotBlank()) Body("Hearing: \"$heard\"", C.Blue)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Home", { vm.flow.home() })
        SecondaryButton("Patients", { vm.flow.patients() })
        SecondaryButton("Message", { vm.flow.pickColleague() })
        SecondaryButton("Requests", { vm.flow.requests() })
        SecondaryButton("Clear", { vm.flow.clear() })
      }
    }

    // Assistant: speak (glasses/phone mic) or type as a stage fallback.
    SectionTitle("Assistant")
    var typed by remember { mutableStateOf("") }
    Panel {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Talk", { vm.flow.assistant() })
        StatusPill(voice.label, if (voice == VoiceSource.GLASSES) C.Green else if (voice == VoiceSource.PHONE) C.Amber else C.Muted)
      }
      OutlinedTextField(
          value = typed,
          onValueChange = { typed = it },
          label = { Text("Or type: two nurses to my room") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line, focusedTextColor = C.Text, unfocusedTextColor = C.Text, focusedLabelColor = C.Blue, unfocusedLabelColor = C.Muted, cursorColor = C.Blue, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Send", { if (typed.isNotBlank()) vm.flow.askAssistant(typed.trim()).also { typed = "" } }, enabled = typed.isNotBlank())
        if (s?.patients?.isNotEmpty() == true) Body("Context: ${vm.flow.selected.value?.let { id -> s?.patient(id)?.let { "${it.name}, Room ${it.room}" } } ?: "none (open a patient)"}", C.Muted)
      }
      lastReply?.let { Body(it, C.Muted) }
      // On-phone Gemma status; the server assistant answers when it isn't loaded.
      val model by vm.local.state.collectAsStateWithLifecycle()
      val brain by vm.flow.lastBrain.collectAsStateWithLifecycle()
      when (val m = model) {
        is ModelState.Ready -> StatusPill("Gemma on phone: ${m.name}", C.Green)
        ModelState.Loading -> StatusPill("Loading Gemma…", C.Amber)
        is ModelState.Failed -> StatusPill("Gemma failed: ${m.reason}", C.Red)
        ModelState.Missing -> {
          StatusPill("No on-phone model, using the server", C.Muted)
          Body("Push a model: adb push gemma3-1b-it-int4.task ${vm.local.modelFolder().absolutePath}/ then tap Load.", C.Muted)
        }
      }
      if (model !is ModelState.Ready && model !is ModelState.Loading) SecondaryButton("Load model", { vm.local.load() })
      brain?.let { Body("Last answer: $it", C.Muted) }
      // Fixed 10-phrase test: accuracy + speed of the on-phone model.
      var evalOut by remember { mutableStateOf<String?>(null) }
      val scope = rememberCoroutineScope()
      if (model is ModelState.Ready) {
        SecondaryButton(if (evalOut == "Running…") "Testing…" else "Test Gemma", {
          val snap = s ?: return@SecondaryButton
          evalOut = "Running…"
          scope.launch { evalOut = vm.local.runEval(snap) }
        }, enabled = evalOut != "Running…" && s != null)
      }
      evalOut?.let { Body(it, C.Muted) }
    }

    // Demo events.
    SectionTitle("Demo events")
    Panel {
      Body("Colleagues' requests and messages come from the browser at the laptop (log in as ben-demo).", C.Muted)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Fall alert", { vm.flow.fallAlert() })
        SecondaryButton("Reset direction", { vm.flow.resetDirection() })
        SecondaryButton("Staff map", { vm.flow.staffMap() })
      }
      StatusPill(
          "${head.label}: ${"%.0f".format(kotlin.math.abs(turn))}° ${if (turn >= 0) "left" else "right"}",
          if (head == HeadingSource.GLASSES) C.Green else if (head == HeadingSource.PHONE) C.Amber else C.Muted,
      )
    }
    Spacer(Modifier.height(24.dp))
  }
}

/** Plain-language mirror of the glasses screen. */
private fun describe(screen: Screen, s: Snapshot?): String =
    when (screen) {
      Screen.Clear -> "Nothing (see-through)"
      Screen.Home -> "Home: status, Patients, Message, Requests, Ask assistant"
      Screen.Patients -> "Patient list"
      is Screen.PatientCard -> "Patient card: ${s?.patient(screen.patientId)?.title}"
      is Screen.Consent -> "Consent question"
      is Screen.Dictating -> "Listening for a spoken note"
      is Screen.Drafting -> "Preparing the draft note"
      is Screen.NoteReview -> "Draft note: save or discard"
      Screen.PickColleague -> "Pick who to message"
      is Screen.SpeakMessage -> "Listening for a message to ${s?.name(screen.to)}"
      is Screen.ConfirmMessage -> "Confirm message to ${s?.name(screen.to)}"
      Screen.Requests -> "Requests list"
      is Screen.Outgoing -> "Your request and its status"
      is Screen.HelpSent -> "Who's been asked to help, live"
      is Screen.AssistantListening -> "Assistant listening"
      is Screen.AssistantThinking -> "Assistant working on: \"${screen.heard}\""
      is Screen.AssistantAnswer -> "Assistant: ${screen.say}"
      is Screen.Incoming -> "Incoming help request"
      is Screen.Briefing -> "Briefing for the accepted request"
      is Screen.MessageIn -> "Incoming message"
      Screen.FallAlert -> "Fall alert, map turns with the head"
      Screen.FallBriefing -> "Walking briefing for Room 7"
      Screen.StaffMap -> "Staff map (simulated)"
      Screen.Offline -> "Offline notice"
      is Screen.Toast -> "Notice: ${screen.text}"
    }
