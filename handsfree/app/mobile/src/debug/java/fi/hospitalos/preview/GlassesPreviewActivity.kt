package fi.hospitalos.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import fi.hospitalos.AppViewModel
import fi.hospitalos.glasses.GlassesStep

/** Debug-only official display preview, sharing the production renderer and action callbacks. */
class GlassesPreviewActivity : ComponentActivity() {
  private val vm: AppViewModel by viewModels()
  private val kit by lazy { MockDeviceKit.getInstance(applicationContext) }
  private var mock: MockGlasses? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    var startupError: String? = null
    try {
      vm.bluetoothGranted()
      kit.enable()
      kit.pairGlasses(GlassesModel.META_RAYBAN_DISPLAY)
          .onSuccess { glasses ->
            mock = glasses
            glasses.powerOn()
            glasses.don()
            vm.glasses.startPreview(glasses.deviceIdentifier)
            if (intent.getBooleanExtra("chromePreview", false)) {
              kit.startTestServer(9000).onFailure { error, _ -> startupError = error.toString() }
            }
          }
          .onFailure { error, _ -> startupError = error.toString() }
    } catch (e: Exception) {
      startupError = e.message ?: "Preview could not start"
    }
    setContent {
      MaterialTheme(colorScheme = darkColorScheme()) {
        val step by vm.glasses.step.collectAsStateWithLifecycle()
        val ward by vm.ward.snapshot.collectAsStateWithLifecycle()
        val link by vm.ward.link.collectAsStateWithLifecycle()
        var command by remember { mutableStateOf("") }
        var notice by remember { mutableStateOf(startupError) }
        LaunchedEffect(Unit) { vm.notices.collect { notice = it } }
        Column(Modifier.fillMaxSize().background(Color(0xFF101416)).padding(16.dp)) {
          Text("Glasses preview", style = MaterialTheme.typography.titleLarge)
          Text("Meta display simulator · synthetic ward", style = MaterialTheme.typography.bodySmall)
          Text("${ward?.me?.name ?: "No ward account"} · $link · ${step.label}", style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(12.dp))
          val displayKit = if (step == GlassesStep.ON) mock?.services?.display else null
          if (displayKit != null) {
            key(displayKit) {
              AndroidView(factory = { context -> displayKit.createPreviewView(context) }, modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black))
            }
          } else {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black)) {
              Text(notice ?: "Starting simulated display…", Modifier.padding(20.dp))
            }
          }
          Text("Click the display controls. Black represents the transparent area; optics and brightness need real glasses.", style = MaterialTheme.typography.bodySmall)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.flow.home() }) { Text("Home") }
            TextButton(onClick = { vm.flow.fallAlert() }) { Text("Demo alert") }
            TextButton(onClick = { finish() }) { Text("Close preview") }
          }
          OutlinedTextField(command, { command = it }, label = { Text("Typed voice substitute") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
          TextButton(onClick = { vm.flow.askAssistant(command.trim()); command = "" }, enabled = command.isNotBlank() && ward != null) { Text("Try command") }
          notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
      }
    }
  }

  override fun onDestroy() {
    vm.glasses.stopDisplay()
    mock?.doff()
    mock?.powerOff()
    runCatching { kit.stopTestServer(); kit.disable() }
    super.onDestroy()
  }
}
