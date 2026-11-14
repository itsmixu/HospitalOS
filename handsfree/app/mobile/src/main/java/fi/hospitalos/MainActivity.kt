package fi.hospitalos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import fi.hospitalos.ui.HospitalApp

class MainActivity : ComponentActivity() {
  private val vm: AppViewModel by viewModels()

  private val bluetooth =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.bluetoothGranted()
      }

  /** Phone microphone: only for the speech fallback. */
  private val phoneMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

  /** Glasses microphone for on-device speech; the Meta AI app asks the wearer. */
  private val glassesMic =
      registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        result.onFailure { error, _ -> vm.notice("Microphone: ${error.description}") }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
      vm.bluetoothGranted()
    }
    setContent {
      HospitalApp(
          vm = vm,
          onAllowBluetooth = { bluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) },
          onRegister = { vm.register(this) },
          onUnregister = { vm.unregister(this) },
          onAllowGlassesMic = { glassesMic.launch(Permission.MICROPHONE) },
          onAllowPhoneMic = { phoneMic.launch(Manifest.permission.RECORD_AUDIO) },
      )
    }
  }
}
