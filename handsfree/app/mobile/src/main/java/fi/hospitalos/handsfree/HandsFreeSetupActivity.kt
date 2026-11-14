package fi.hospitalos.handsfree

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.launch

class HandsFreeSetupActivity : ComponentActivity() {
  private var initialized by mutableStateOf(false)
  private var message by mutableStateOf("Pair the Display glasses in Meta AI first.")
  private var registered by mutableStateOf(false)
  private var cameraGranted by mutableStateOf(false)
  private var microphoneGranted by mutableStateOf(false)
  private val bluetooth=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
    if(granted)initialize() else message="Bluetooth permission is needed to connect the glasses"
  }
  private val permission=registerForActivityResult(Wearables.RequestPermissionContract()){ result->
    result.onFailure{_,_->message="Permission was not granted. You can retry from setup."}
    refreshPermissions()
  }
  private fun refreshPermissions(){if(!initialized)return;lifecycleScope.launch {
    cameraGranted=Wearables.checkPermissionStatus(Permission.CAMERA).fold({it==PermissionStatus.Granted},{_,_->false})
    microphoneGranted=Wearables.checkPermissionStatus(Permission.MICROPHONE).fold({it==PermissionStatus.Granted},{_,_->false})
  }}
  private fun initialize(){
    if(initialized)return
    Wearables.initialize(applicationContext).onSuccess {
      initialized=true
      lifecycleScope.launch{Wearables.registrationState.collect{state->
        registered=state==RegistrationState.REGISTERED
        message=when(state){
          RegistrationState.REGISTERED->"Meta AI connected. Ready to start the display."
          RegistrationState.REGISTERING->"Complete registration in Meta AI, then return here."
          else->"Bluetooth ready. Connect to Meta AI next."
        }
        if(registered)refreshPermissions()
      }}
    }.onFailure{_,_->message="Could not initialize glasses. Check Bluetooth and Meta AI, then retry."}
  }
  override fun onResume(){super.onResume();refreshPermissions()}
  override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
    if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)initialize()
    setContent{MaterialTheme{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      Text("HospitalOS Hands-free",style=MaterialTheme.typography.headlineSmall)
      Text("Real Display test · synthetic ward only")
      Text("Pair the glasses and enable Developer Mode in Meta AI. Android USB debugging is separate.")
      Text(message)
      Button(onClick={bluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)}){Text("1. Allow Bluetooth")}
      Button(enabled=initialized,onClick={Wearables.startRegistration(this@HandsFreeSetupActivity)}){Text("2. Connect to Meta AI")}
      Text("Optional capabilities",style=MaterialTheme.typography.titleMedium)
      Button(enabled=registered,onClick={permission.launch(Permission.CAMERA)}){Text(if(cameraGranted)"Camera allowed · manage" else "Allow glasses camera")}
      Button(enabled=registered,onClick={permission.launch(Permission.MICROPHONE)}){Text(if(microphoneGranted)"Microphone allowed · manage" else "Allow glasses microphone")}
      Text("Permission does not start recording. Camera opens only for Capture reading. Glasses voice needs explicit opt-in and stays on until stopped or disconnected.")
      Button(enabled=registered,onClick={try{
        startActivity(Intent().setClassName(packageName,"fi.hospitalos.handsfree.HandsFreePreviewActivity").putExtra("physical",true))
      }catch(e:Exception){message="Install the debug hardware-test build first"}}){Text("3. Start glasses display")}
      Text("Keep USB connected and the test screen open. Tilt left for back, right for the displayed action. Camera and speech accuracy still need physical testing. No real patient data.")
    }}}
  }
}
