package fi.hospitalos.ui

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fi.hospitalos.AppViewModel
import fi.hospitalos.glasses.GlassesStep
import fi.hospitalos.ward.Link

/** Setup in the order you actually do it: server, Bluetooth, Meta AI, display. */
@Composable
fun ConnectScreen(
    vm: AppViewModel,
    modifier: Modifier,
    onAllowBluetooth: () -> Unit,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
) {
  val link by vm.ward.link.collectAsStateWithLifecycle()
  val snapshot by vm.ward.snapshot.collectAsStateWithLifecycle()
  val step by vm.glasses.step.collectAsStateWithLifecycle()
  var url by remember { mutableStateOf(vm.serverUrl) }
  var token by remember { mutableStateOf(vm.token) }
  val context = LocalContext.current

  Column(
      modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Spacer(Modifier.height(12.dp))
    Text("Connect", color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Four steps. Each turns green when it's done.", color = C.Muted, fontSize = 15.sp)

    // 1 — ward server
    val serverDone = link == Link.ONLINE
    StepCard(1, "Ward server", done = serverDone, active = !serverDone) {
      when (link) {
        Link.ONLINE -> StatusPill("Connected as ${snapshot?.me?.name ?: "staff"}", C.Green)
        Link.CONNECTING -> StatusPill("Connecting…", C.Amber)
        Link.OFFLINE -> StatusPill("Not connected", C.Muted)
      }
      Field("Server address", url) { url = it }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("Demo token (e.g. aino-demo)", token, modifier = Modifier.weight(1f)) { token = it }
        SecondaryButton("Paste", onClick = {
          val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
          clip?.getItemAt(0)?.text?.toString()?.let { token = it.trim() }
        })
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(if (serverDone) "Reconnect" else "Connect", onClick = { vm.connect(url, token) }, enabled = token.isNotBlank())
        if (serverDone) SecondaryButton("Disconnect", onClick = { vm.ward.disconnect() })
      }
      Text("Over USB, run `adb reverse tcp:8787 tcp:8787` on the laptop and keep ws://127.0.0.1:8787.", color = C.Muted, fontSize = 13.sp)
    }

    // 2 — Bluetooth
    val btDone = step != GlassesStep.NEEDS_PERMISSION
    StepCard(2, "Bluetooth", done = btDone, active = serverDone && !btDone) {
      if (btDone) StatusPill("Allowed", C.Green) else PrimaryButton("Allow Bluetooth", onAllowBluetooth)
    }

    // 3 — Meta AI registration
    val regDone = step in setOf(GlassesStep.REGISTERED, GlassesStep.STARTING, GlassesStep.ON, GlassesStep.PAUSED)
    StepCard(3, "Meta AI", done = regDone, active = btDone && !regDone) {
      Text("Meta AI app: glasses paired, Developer Mode on (tap the app version 5 times).", color = C.Muted, fontSize = 13.sp)
      if (regDone) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          StatusPill("Connected", C.Green)
          SecondaryButton("Disconnect app", onUnregister)
        }
      } else {
        PrimaryButton(if (step == GlassesStep.REGISTERING) "Waiting for Meta AI…" else "Connect to Meta AI", onRegister, enabled = btDone && step != GlassesStep.REGISTERING)
      }
    }

    // 4 — Display
    val displayOn = step == GlassesStep.ON
    StepCard(4, "Glasses display", done = displayOn, active = regDone && !displayOn) {
      StatusPill(step.label, when (step) {
        GlassesStep.ON -> C.Green
        GlassesStep.STARTING, GlassesStep.PAUSED -> C.Amber
        else -> C.Muted
      })
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Start display", { vm.glasses.startDisplay() }, enabled = regDone && !displayOn && step != GlassesStep.STARTING)
        if (displayOn || step == GlassesStep.PAUSED) SecondaryButton("Stop", { vm.glasses.stopDisplay() })
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun StepCard(n: Int, title: String, done: Boolean, active: Boolean, content: @Composable () -> Unit) {
  Panel(accent = if (done) C.Green else if (active) C.Blue else null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(if (done) "✓" else "$n", color = if (done) C.Green else if (active) C.Blue else C.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
      Text(title, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
    content()
  }
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
  OutlinedTextField(
      value = value,
      onValueChange = onChange,
      label = { Text(label) },
      singleLine = true,
      modifier = modifier,
      shape = RoundedCornerShape(14.dp),
      visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
      keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
      colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line, focusedTextColor = C.Text, unfocusedTextColor = C.Text, focusedLabelColor = C.Blue, unfocusedLabelColor = C.Muted, cursorColor = C.Blue, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
  )
}
