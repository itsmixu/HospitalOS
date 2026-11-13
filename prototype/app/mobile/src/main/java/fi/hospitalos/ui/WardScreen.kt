package fi.hospitalos.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.key
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fi.hospitalos.AppViewModel
import fi.hospitalos.glasses.GlassesStep
import fi.hospitalos.glasses.Screen
import fi.hospitalos.ward.Draft
import fi.hospitalos.ward.Link
import fi.hospitalos.ward.Patient
import fi.hospitalos.ward.Snapshot
import fi.hospitalos.ward.Task
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WardScreen(vm: AppViewModel, modifier: Modifier, openConnect: () -> Unit) {
  val link by vm.ward.link.collectAsStateWithLifecycle()
  val s by vm.ward.snapshot.collectAsStateWithLifecycle()
  val step by vm.glasses.step.collectAsStateWithLifecycle()
  val screen by vm.flow.screen.collectAsStateWithLifecycle()
  val snap = s

  Column(
      modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
  ) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("Ward 4B", color = C.Muted, fontSize = 14.sp)
        Text(snap?.me?.name ?: "Not connected", color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
      }
      Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(if (link == Link.ONLINE) "Server" else "Server offline", if (link == Link.ONLINE) C.Green else C.Red)
        StatusPill(if (step == GlassesStep.ON) "Glasses" else "Glasses off", if (step == GlassesStep.ON) C.Green else C.Muted)
      }
    }

    if (snap == null) {
      Spacer(Modifier.height(24.dp))
      Panel(accent = C.Blue) {
        Title("Connect to get started")
        Body("Connect to the ward server and your glasses first.", C.Muted)
        PrimaryButton("Open Connect", openConnect)
      }
      return@Column
    }

    // What the wearer sees right now.
    SectionTitle("On your glasses")
    val stepped by vm.flow.steppedReveal.collectAsStateWithLifecycle()
    Panel(accent = C.Blue) {
      AnimatedContent(
          describe(screen, snap),
          transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith fadeOut(tween(120))
          },
          label = "lens",
      ) { text -> Body(text) }
      when (step) {
        GlassesStep.ON -> Unit
        GlassesStep.REGISTERED -> PrimaryButton("Start glasses display", { vm.glasses.startDisplay() })
        GlassesStep.STARTING -> Body("Starting the display…", C.Muted)
        GlassesStep.PAUSED -> Body("Paused: put the glasses on and unfold them.", C.Amber)
        else -> Body("Glasses not set up yet. Finish the steps under Connect.", C.Muted)
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Home", { vm.flow.home() })
        SecondaryButton("Clear", { vm.flow.clear() })
        SecondaryButton("Re-send", { vm.flow.render() })
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Body("Stepped reveal")
          Body("Key screens arrive in parts on the lens", C.Muted)
        }
        Switch(
            checked = stepped,
            onCheckedChange = { vm.flow.setSteppedReveal(it) },
            colors = SwitchDefaults.colors(checkedTrackColor = C.Blue, checkedThumbColor = Color.Black),
        )
      }
    }

    // Sections slide in when something arrives and out when it's handled. The last value is kept
    // during the exit animation so the card doesn't blank out while it collapses.
    val incoming = snap.incoming
    var lastIncoming by remember { mutableStateOf(incoming) }
    if (incoming.isNotEmpty()) lastIncoming = incoming
    AnimatedVisibility(incoming.isNotEmpty(), enter = SectionEnter, exit = SectionExit) {
      Column(Modifier.animateContentSize()) {
        SectionTitle("Requests for you")
        lastIncoming.forEach { t -> key(t.id) { IncomingCard(vm, snap, snap.tasks.firstOrNull { it.id == t.id } ?: t) } }
      }
    }

    val draft = snap.drafts.firstOrNull()
    var lastDraft by remember { mutableStateOf(draft) }
    if (draft != null) lastDraft = draft
    AnimatedVisibility(draft != null, enter = SectionEnter, exit = SectionExit) {
      lastDraft?.let { d ->
        Column {
          SectionTitle(if (d.status == "open") "Recording" else "Draft note to review")
          DraftCard(vm, snap, d)
        }
      }
    }

    SectionTitle("My patients")
    snap.patients.forEach { p -> key(p.id) { PatientCard(vm, snap, p) } }
    if (snap.patients.isEmpty()) Body("No patients assigned to you.", C.Muted)

    AnimatedVisibility(snap.outgoing.isNotEmpty(), enter = SectionEnter, exit = SectionExit) {
      Column(Modifier.animateContentSize()) {
        SectionTitle("Requests you sent")
        snap.outgoing.take(5).forEach { t -> key(t.id) { OutgoingCard(vm, snap, t) } }
      }
    }

    SectionTitle("Demo extras (simulated)")
    Panel {
      Body("Not part of the core workflow. Positions and the fall are simulated; the turning is real.", C.Muted)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Staff map", { vm.flow.staffMap() })
        SecondaryButton("Fall alert", { vm.flow.fallAlert() })
        SecondaryButton("Reset direction", { vm.flow.resetDirection() })
      }
      val source by vm.glasses.head.source.collectAsStateWithLifecycle()
      val turn by vm.glasses.head.turnDegrees.collectAsStateWithLifecycle()
      StatusPill(
          "${source.label}: ${"%.0f".format(kotlin.math.abs(turn))}° ${if (turn >= 0) "left" else "right"}",
          if (source == fi.hospitalos.glasses.HeadingSource.GLASSES) C.Green else if (source == fi.hospitalos.glasses.HeadingSource.PHONE) C.Amber else C.Muted,
      )
    }
    Spacer(Modifier.height(24.dp))
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatientCard(vm: AppViewModel, s: Snapshot, p: Patient) {
  var open by rememberSaveable(p.id) { mutableStateOf(false) }
  val busy = s.drafts.isNotEmpty()
  Panel(Modifier.padding(bottom = 10.dp).clickable { open = !open }) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Title(p.title)
        Body("Room ${p.room}${p.summary?.let { " · $it" } ?: ""}", C.Muted)
      }
      val turn by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "chevron")
      Text("▼", color = C.Muted, modifier = Modifier.rotate(turn))
    }
    p.warning?.let { StatusPill("⚠ $it", C.Red) }
    AnimatedVisibility(open, enter = SectionEnter, exit = SectionExit) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Body("Patient reported: ${p.reported}")
      s.notesFor(p.id).take(3).forEach { n ->
        Body("${n.time} · ${s.name(n.author)}: ${listOf(n.reported, n.planned).filter { it.isNotBlank() }.joinToString(" / ")}", C.Muted)
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Show on glasses", { vm.flow.openPatient(p.id) })
        SecondaryButton("Record (consent)", { vm.flow.startConsent(p.id) }, enabled = !busy)
        s.colleagues.forEach { c -> SecondaryButton("Ask ${c.name}", { vm.flow.requestHelp(p.id, c.id) }) }
      }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftCard(vm: AppViewModel, s: Snapshot, d: Draft) {
  val p = s.patient(d.patientId)
  if (d.status == "open") {
    Panel(accent = C.Red) {
      Title(p?.title ?: "Patient")
      Body("Consent given. Capture is on (demo: no audio is recorded yet).", C.Muted)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Stop & draft", { vm.flow.stopRecording(d.patientId) }, color = C.Red)
        SecondaryButton("Discard", { vm.send("log.discard", JSONObject().put("encounterId", d.id)) })
      }
    }
    return
  }
  // Review: the nurse can correct before saving (SPEC F3 step 5).
  var reported by remember(d.id) { mutableStateOf(d.reported) }
  var planned by remember(d.id) { mutableStateOf(d.planned) }
  Panel(accent = C.Amber) {
    Title(p?.title ?: "Patient")
    Body("Check and correct, then save. Only saved notes enter the record.", C.Muted)
    NoteField("Patient reported", reported) { reported = it }
    NoteField("Planned", planned) { planned = it }
    s.tasks.filter { it.patientId == d.patientId && it.from == s.me.id && it.status == "completed" }.forEach {
      StatusPill("Done by ${s.name(it.to)}: ${it.summary}", C.Green)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      PrimaryButton("Save note", {
        vm.send("log.confirm", JSONObject().put("encounterId", d.id).put("reported", reported).put("planned", planned))
        vm.flow.home()
      }, color = C.Green)
      SecondaryButton("Discard", { vm.send("log.discard", JSONObject().put("encounterId", d.id)) })
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomingCard(vm: AppViewModel, s: Snapshot, t: Task) {
  val p = s.patient(t.patientId)
  Panel(accent = C.Blue, modifier = Modifier.padding(bottom = 10.dp)) {
    Body("${s.name(t.from)} needs help", C.Muted)
    Title("Room ${p?.room ?: t.room ?: "?"}${p?.let { " · ${it.name}" } ?: ""}")
    t.summary?.let { Body("\"$it\"") }
    TaskStatus(t.status)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      when (t.status) {
        "sent", "delivered" -> {
          PrimaryButton("On my way", { vm.flow.acceptRequest(t.id) }, color = C.Green)
          SecondaryButton("Can't", { vm.flow.declineRequest(t.id) })
        }
        "accepted" -> {
          PrimaryButton("Mark done", { vm.flow.completeRequest(t.id) }, color = C.Green)
          SecondaryButton("Briefing on glasses", { vm.flow.show(Screen.Briefing(t.id)) })
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutgoingCard(vm: AppViewModel, s: Snapshot, t: Task) {
  Panel(Modifier.padding(bottom = 10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Body("To ${s.name(t.to)}", C.Muted)
        Body(t.summary ?: "Assistance")
      }
      TaskStatus(t.status)
    }
    if (t.status in setOf("sent", "delivered")) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Show on glasses", { vm.flow.show(Screen.Outgoing(t.id)) })
        SecondaryButton("Cancel", { vm.flow.cancelRequest(t.id) })
      }
    }
  }
}

/** Sent, delivered, accepted and completed are different things (SPEC F1). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskStatus(status: String) {
  val (label, color) =
      when (status) {
        "sent" -> "Sent, not seen" to C.Amber
        "delivered" -> "Seen" to C.Blue
        "accepted" -> "Accepted" to C.Green
        "completed" -> "Done" to C.Green
        "cancelled" -> "Declined / cancelled" to C.Muted
        else -> status to C.Muted
      }
  StatusPill(label, color)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteField(label: String, value: String, onChange: (String) -> Unit) {
  OutlinedTextField(
      value = value,
      onValueChange = onChange,
      label = { Text(label) },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line, focusedTextColor = C.Text, unfocusedTextColor = C.Text, focusedLabelColor = C.Blue, unfocusedLabelColor = C.Muted, cursorColor = C.Blue, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
  )
}

/** Plain-language mirror of the glasses screen, so the presenter knows what the wearer sees. */
private fun describe(screen: Screen, s: Snapshot): String =
    when (screen) {
      Screen.Clear -> "Nothing (see-through)"
      Screen.Home -> "Ward overview and your patients"
      is Screen.PatientCard -> "Patient card: ${s.patient(screen.patientId)?.title}"
      is Screen.Consent -> "Consent question for ${s.patient(screen.patientId)?.name}"
      is Screen.Recording -> "Small recording pill in the corner"
      is Screen.RecordingMenu -> "Recording menu: stop, ask for help, keep going"
      is Screen.Drafting -> "Preparing the draft note"
      is Screen.NoteReview -> "Draft note: save or discard"
      is Screen.AskListening -> "Listening for a question"
      is Screen.AskAnswer -> "Answer: ${screen.answer}"
      is Screen.Outgoing -> "Your help request and its status"
      is Screen.Incoming -> "Incoming help request"
      is Screen.Briefing -> "Patient briefing for the accepted request"
      Screen.StaffMap -> "Staff map (simulated)"
      Screen.FallAlert -> "Fall alert with route (simulated)"
      Screen.FallBriefing -> "Walking briefing for Room 7 (simulated)"
      Screen.Offline -> "Offline notice"
      is Screen.Toast -> "Notice: ${screen.text}"
    }
