package fi.hospitalos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import fi.hospitalos.ui.HospitalApp

class MainActivity : ComponentActivity() {
  private val vm: AppViewModel by viewModels()

  private val bluetooth =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.bluetoothGranted()
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
      )
    }
  }
}
