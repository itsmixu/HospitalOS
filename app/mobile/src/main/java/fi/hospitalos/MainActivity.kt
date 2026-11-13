package fi.hospitalos

import android.Manifest
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.TextStyle
import okhttp3.*
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
 private lateinit var status:TextView
 private lateinit var preview:TextView
 private var session:DeviceSession?=null
 private var display:Display?=null
 private val jobs=mutableListOf<Job>()
 private var socket:WebSocket?=null
 private val client=OkHttpClient()
 private var latest="Connect to the simulated ward"
 private var ready=false
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ granted -> if(granted) initialize() else status.text="Bluetooth permission denied" }
 private fun initialize(){if(ready)return;Wearables.initialize(this);ready=true;status.text="Ready to register with Meta AI"}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
 val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,28,28,28)}
 setContentView(ScrollView(this).apply{addView(layout)})
 fun button(label:String,action:()->Unit){layout.addView(Button(this).apply{text=label;setOnClickListener{action()}})}
 status=TextView(this).apply{text="Synthetic development mode · no audio capture"};layout.addView(status)
 val url=EditText(this).apply{hint="Server WebSocket URL";setText("ws://127.0.0.1:8787")};layout.addView(url)
 val token=EditText(this).apply{hint="Demo staff token";inputType=129};layout.addView(token)
 preview=TextView(this);layout.addView(preview)
 button("Connect to ward") {socket?.close(1000,"Reconnect");socket=client.newWebSocket(Request.Builder().url(url.text.toString()).build(),object:WebSocketListener(){
 override fun onOpen(ws:WebSocket,response:Response){ws.send(JSONObject().put("type","auth").put("token",token.text.toString()).toString())}
 override fun onMessage(ws:WebSocket,text:String){runOnUiThread{try{val msg=JSONObject(text);if(msg.optString("type")=="snapshot"){val data=msg.getJSONObject("payload");latest="Tasks: "+data.getJSONArray("tasks").length()+" · Reviewed notes: "+data.getJSONArray("notes").length();preview.text=data.toString(2);status.text="Connected to synthetic ward";render()}else if(msg.optString("type")=="error")status.text=msg.optString("message")}catch(e:Exception){status.text="Invalid server message"}}}
 override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){runOnUiThread{status.text="Disconnected: "+t.message;latest="Offline — no actions replayed";render()}}
 override fun onClosed(ws:WebSocket,code:Int,reason:String){runOnUiThread{status.text="Disconnected";latest="Offline";render()}}
 })}
 button("Request Ben for Demo Patient A") {send("help.request",JSONObject().put("patientId","p1").put("to","ben").put("summary","Assistance requested"))}
 button("Enable Bluetooth / initialise SDK") {permission.launch(Manifest.permission.BLUETOOTH_CONNECT)}
 button("Register with Meta AI") {if(ready)Wearables.startRegistration(this)else status.text="Initialise SDK first"}
 button("Start Display") {startDisplay()}
 button("Stop Display") {stopDisplay()}
 }
 private fun send(type:String,payload:JSONObject){val ok=socket?.send(JSONObject().put("id",UUID.randomUUID().toString()).put("type",type).put("payload",payload).toString())?:false;if(!ok)status.text="Not sent — connect first"}
 private fun render(){val d=display?:return;lifecycleScope.launch{d.sendContent{flexBox(gap=12,padding=24){text("HospitalOS · Demo",style=TextStyle.HEADING);text(latest)}}.onFailure{error,_->status.text=error.description}}}
 private fun startDisplay(){if(!ready||Wearables.registrationState.value!=RegistrationState.REGISTERED){status.text="Register with Meta AI first";return};stopDisplay()
 Wearables.createSession(AutoDeviceSelector(filter={it.isDisplayCapable()})).onSuccess{s->session=s
 jobs+=lifecycleScope.launch{s.errors.collect{status.text=it.description}}
 jobs+=lifecycleScope.launch{s.state.collect{state->status.text="Display session: $state";if(state==DeviceSessionState.STARTED&&display==null){s.addDisplay().onSuccess{d->display=d;jobs+=lifecycleScope.launch{d.state.collect{if(it==DisplayState.STARTED)render()}}}.onFailure{error,_->status.text=error.description}};if(state==DeviceSessionState.STOPPED)display=null}}
 s.start()}.onFailure{error,_->status.text=error.description}
 }
 private fun stopDisplay(){jobs.forEach{it.cancel()};jobs.clear();session?.stop();session=null;display=null}
 override fun onDestroy(){socket?.close(1000,"Closing");stopDisplay();client.dispatcher.executorService.shutdown();super.onDestroy()}
}
