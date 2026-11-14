package fi.hospitalos.handsfree

import fi.hospitalos.handsfree.capture.*
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.types.DisplayState
import com.meta.wearable.dat.display.views.*
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockGlasses
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.JSONObject
import java.util.UUID
import com.meta.wearable.dat.motion.addMotion
import com.meta.wearable.dat.motion.types.*
import kotlin.math.abs


/** Debug-only input bridge; controller is the same pure local engine intended for physical adapters. */
class HandsFreePreviewActivity : ComponentActivity() {
  private val kit by lazy { MockDeviceKit.getInstance(applicationContext) }
  private var motionSamples=0L
  private var motionLastAt=0L
  private var motionSource="none"
  private var motionState="not attached"
  private var motionReportAt=0L
  private var motionRaw="No sample"
  private var motionAttached=false
  private var motionStatus by mutableStateOf("Head motion connecting…")
  private var mock: MockGlasses? = null
  private var session: DeviceSession? = null
  private var display: Display? = null
  private var socket: WebSocket? = null
  private val http = OkHttpClient()
  private lateinit var engine: HandsFreeEngine
  private var shown by mutableStateOf(Card("starting","Starting simulation","Synthetic ward only","No microphone access"))
  private var displayReady by mutableStateOf(false)
  private var error by mutableStateOf("")
  private val lock = Mutex()
  private var lastCard: Card? = null
  private var nextRenderAt=0L
  private var stopped = false
  private var authReady = false
  private var foreground = true
  private var simulatedLoss = false
  private var computerVoice = false
  private var glassesVoice by mutableStateOf(false)
  private var voiceStatus by mutableStateOf("Microphone off")
  private var speechDiagnostic by mutableStateOf("")
  private var listeningDisplayRefreshAt=0L
  private var speechInput: DatSpeechInput? = null
  private var renderedCard: Card? = null
  private var reportedSpeechListening=false
  private var listeningWindow:SpeechWindow?=null
  private var recoveryHealthy = true
  private var eligible = false
  private var reportedReady:Boolean?=null
  private var gestureJob: Job? = null
  private var cameraJob: Job? = null
  private var cameraId: String? = null
  private var cameraSeed=42
  private var cameraScenario="clear"
  private val physical by lazy { intent.getBooleanExtra("physical",false) }
  private val datCamera by lazy { intent.getBooleanExtra("datCamera",false) }
  private val actor by lazy { intent.getStringExtra("actor") ?: "aino" }
  private val delivered = mutableSetOf<String>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    engine = HandsFreeEngine(actor, SystemClock::elapsedRealtime) { id,a ->
      val m=JSONObject(a.fields).put("id",id).put("type",a.type)
      if(a.to.isNotEmpty()) m.put("to",a.to)
      if(a.room.isNotEmpty()) m.put("room",a.room)
      if(a.text.isNotEmpty()) m.put("text",a.text)
      if(a.taskId.isNotEmpty()) m.put("taskId",a.taskId).put("revision",a.revision)
      if(!persistPending()){error="Local recovery state unavailable; restart after restoring storage";recoveryHealthy=false;syncReadiness()}
      else if(socket?.send(m.toString()) != true) {authReady=false;syncReadiness()}
    }
    engine.quietMode=physical
    getSharedPreferences("handsfree-recovery",MODE_PRIVATE).getString("pending",null)?.let{engine.restoreUnresolvedCommand(it)}
    try {
      Wearables.initialize(applicationContext)
      if(physical) lifecycleScope.launch {
        val found=withTimeoutOrNull(30_000) {
          while(isActive) {
            if(Wearables.registrationState.value==com.meta.wearable.dat.core.types.RegistrationState.REGISTERED) {
              val id=Wearables.devices.value.firstOrNull { id ->
                val d=Wearables.devicesMetadata[id]?.value
                d!=null && d.isDisplayCapable() && d.linkState==com.meta.wearable.dat.core.types.LinkState.CONNECTED
              }
              if(id!=null)return@withTimeoutOrNull id
            }
            delay(200)
          }
          null
        }
        if(found!=null)beginDisplaySession(SpecificDeviceSelector(found))
        else {
          val devices=Wearables.devices.value
          val links=devices.map{Wearables.devicesMetadata[it]?.value}.map{d->if(d==null)"metadata pending" else "display=${d.isDisplayCapable()}, link=${d.linkState}"}
          error="Display discovery: registration=${Wearables.registrationState.value}; devices=${devices.size}; ${links.joinToString()}"
          android.util.Log.i("LumiDiscovery",error)
        }
      }
      else {
        kit.enable()
        kit.pairGlasses(GlassesModel.META_RAYBAN_DISPLAY).onSuccess { g ->
          mock=g;g.powerOn();g.don();beginDisplaySession(SpecificDeviceSelector(g.deviceIdentifier))
          kit.startTestServer(9001).onFailure{e,_->error=e.toString()}
        }.onFailure{e,_->error=e.toString()}
      }
    } catch(e:Exception) { error=e.message?:"Preview failed" }
    engine.bedside.cameraSource=if(physical || datCamera) "dat-camera" else "injected-camera"
    speechInput=DatSpeechInput(lifecycleScope,
      window={SpeechWindow(engine.card,renderedCard==engine.card,eligible)},
      result={route,text->when(route){SpeechRoute.WAKE->engine.wake();SpeechRoute.COMMAND->engine.transcript(text);SpeechRoute.DRAFT->engine.proposeTranscript(text);else->Unit};publish()},
      status={active,note->
        if(!active && glassesVoice && engine.card.microphone)engine.transcript("cancel")
        glassesVoice=active
        if(note.isNotEmpty())voiceStatus=if(physical)note else note.replace("Glasses voice", "Simulated SDK voice").replace("Glasses microphone", "Simulated SDK microphone")
        lastCard=null;publish()
      },
      listening={listeningWindow=it;lastCard=null;publish()},
      diagnostic={speechDiagnostic=it})
    connect()
    lifecycleScope.launch { while(isActive) { delay(100); engine.tick(); if(physical)engine.offerIncoming(); publish(); if(physical && SystemClock.elapsedRealtime()-motionReportAt>1000){motionReportAt=SystemClock.elapsedRealtime();report(engine.card,renderedCard==engine.card)} } }
    setContent {
      MaterialTheme(colorScheme=darkColorScheme()) {
        Column(Modifier.fillMaxSize().background(Color(0xFF10191B)).padding(20.dp)) {
          Text("HospitalOS · Hands-free",style=MaterialTheme.typography.titleLarge)
          Text("${if(physical) "Real glasses display" else "Official DAT display simulation"} · ${actor.replaceFirstChar { it.uppercase() }} · ${if(physical) "real glasses camera" else if(datCamera) "DAT mock camera" else "injected camera frames"}",style=MaterialTheme.typography.bodySmall)
          Spacer(Modifier.height(16.dp))
          val d=mock?.services?.display
          if(displayReady && d!=null) AndroidView(factory={d.createPreviewView(it)},modifier=Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black))
          else if(displayReady) {Text(shown.title,style=MaterialTheme.typography.titleLarge);Text(shown.body);Text(shown.hint)}
          else Text("Connecting display…")
          if(error.isNotEmpty()) Text(error,color=MaterialTheme.colorScheme.error)
          Spacer(Modifier.height(16.dp))
          Text(motionStatus)
          Text(voiceStatus)
          if(speechDiagnostic.isNotBlank())Text(speechDiagnostic,style=MaterialTheme.typography.bodySmall)
          Text("Listening stays on until stopped, the app leaves the foreground, or the glasses disconnect.",style=MaterialTheme.typography.bodySmall)
          Button(enabled=displayReady && !computerVoice && !glassesVoice,onClick={startGlassesVoice()}){Text("Enable glasses voice")}
          Button(enabled=glassesVoice,onClick={stopGlassesVoice()}){Text("Stop glasses microphone")}
          Text("Keep this screen open and USB connected. Say Hey Lumi, pause for Listening, then speak. Review before confirming. Computer voice and glasses voice are separate inputs. Tilt right to accept; tilt left to go back.",style=MaterialTheme.typography.bodySmall)
        }
      }
    }
  }
  private fun beginDisplaySession(selector:DeviceSelector){
        Wearables.createSession(selector).onSuccess { s ->
          session=s
          lifecycleScope.launch {s.errors.collect {error="Glasses session error: ${it.description}"}}
          lifecycleScope.launch {
            s.state.collect { state ->
              when(state) {
                DeviceSessionState.STARTED -> if(display==null) {
                  if(physical)startHeadMotion(s)
                  s.addDisplay().onSuccess { d ->
                    display=d
                    lifecycleScope.launch { d.state.collect { ds ->
                      displayReady=ds==DisplayState.STARTED
                      syncReadiness();if(displayReady)lastCard=null;publish()
                    } }
                  }.onFailure { e,_ -> error=e.description }
                }
                DeviceSessionState.PAUSED, DeviceSessionState.STOPPED -> { displayReady=false;syncReadiness();publish() }
                else -> Unit
              }
            }
          }
          s.start()
        }.onFailure { e,_ -> error=e.description }
  }
  private fun startHeadMotion(s:DeviceSession){
    if(motionAttached)return
    s.addMotion(MotionConfiguration(samplingRate=MotionSamplingRate.HZ_30)).onSuccess { motion ->
      motionAttached=true
      lifecycleScope.launch {motion.state.collect{motionState=it.toString()}}
      lifecycleScope.launch {motion.errors.collect { e->if(e!=null)motionStatus="Head motion error: ${e.description}"}}
      lifecycleScope.launch {
        val tilt=PhysicalTiltDetector()
        motion.samples.collect { sample ->
          motionSamples++;motionLastAt=SystemClock.elapsedRealtime();motionSource=sample.source.toString()
          val c=engine.card
          val now=SystemClock.elapsedRealtime()
          val g=sample.gyroscope
          motionRaw="a=${sample.accelerometer} g=$g t=${sample.timestampNs}"
          if(eligible && renderedCard==c && c.phase !in setOf("quiet","offline","saving","sending","reconciling") && sample.source==MotionSource.GLASSES && g!=null){
            val gravity=sample.accelerometer
            val speed=kotlin.math.sqrt((g.x*g.x+g.y*g.y+g.z*g.z).toDouble())
            if(gravity==null)tilt.reset() else {
              val action=tilt.sample("${c.phase}:${c.promptId}:${c.title}",sample.timestampNs,gravity.x.toDouble(),gravity.y.toDouble(),gravity.z.toDouble(),speed)
              motionStatus="Tilt ${tilt.degrees.toInt()}° · ${tilt.status}"
              if(action!=null){if(action==TiltAction.BACK)engine.dismissScreen() else engine.physicalAccept(c.promptId);motionStatus=if(action==TiltAction.BACK)"Left tilt · back" else "Right tilt · ${engine.card.title}";publish();return@collect}
            }
          }else tilt.reset()

        }
      }
      motion.start()
    }.onFailure{e,_->motionStatus="Head motion unavailable: ${e.description}"}
  }
  private fun connect() {
    if(stopped) return
    authReady=false;reportedReady=null;syncReadiness()
    socket=http.newWebSocket(okhttp3.Request.Builder().url("ws://127.0.0.1:${intent.getIntExtra("wardPort",8790)}").build(),object:WebSocketListener(){
      override fun onOpen(ws:WebSocket,response:Response) { ws.send(JSONObject().put("type","auth").put("token","$actor-demo").put("role","headset").put("ready",false).toString()) }
      override fun onMessage(ws:WebSocket,text:String) { lifecycleScope.launch { if(ws===socket && !stopped) receive(JSONObject(text)) } }
      override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) { lost(ws) }
      override fun onClosing(ws:WebSocket,code:Int,reason:String) {ws.close(code,reason)}
      override fun onClosed(ws:WebSocket,code:Int,reason:String) { lost(ws) }
      private fun lost(ws:WebSocket) { lifecycleScope.launch { if(ws!==socket || stopped)return@launch; authReady=false;reportedReady=null;syncReadiness();publish();delay(2000);if(ws===socket)connect() } }
    })
  }
  private fun receive(m:JSONObject) {
    when(m.getString("type")) {
      "snapshot" -> {
        if(!authReady) {authReady=true;syncReadiness()}
        val people=m.getJSONArray("staff");val requests=m.getJSONArray("tasks")
        val staff=(0 until people.length()).map { people.getJSONObject(it).let { Colleague(it.getString("id"),it.getString("name"),it.getBoolean("reachable"),it.optString("status","available")) } }
        val tasks=(0 until requests.length()).map { requests.getJSONObject(it).let { Request(it.getString("id"),it.getString("from"),it.getString("to"),it.getString("room"),it.getString("text"),it.getString("status"),it.getInt("revision"),it.optInt("required",0),it.optJSONArray("acceptedBy")?.let{a->(0 until a.length()).map{a.getString(it)}}?:emptyList()) } }
        val messages=m.optJSONArray("messages")
        val mail=if(messages==null)emptyList()else (0 until messages.length()).map{messages.getJSONObject(it).let{WardMessage(it.getString("id"),it.getString("from"),it.getString("to"),it.getString("text"),it.has("readAt") && !it.isNull("readAt"))}}
        engine.snapshot(staff,tasks,mail)
        val patients=m.optJSONArray("patients")
        val assigned=if(patients==null)emptyList()else (0 until patients.length()).map{patients.getJSONObject(it).let{Patient(it.getString("id"),it.getString("name"),it.getString("encounterId"),it.getString("room"))}}
        val obs=m.optJSONArray("observations")
        val saved=if(obs==null)emptyList()else (0 until obs.length()).map{obs.getJSONObject(it).let{o->val v=o.getJSONObject("values");SavedReading(o.getString("id"),o.getString("patientId"),Reading(v.getInt("systolic"),v.getInt("diastolic"),v.getInt("pulse")),o.getString("capturedAt"),o.getJSONArray("corrected").length()>0)}}
        val notes=m.optJSONArray("notes")
        val savedNotes=if(notes==null)emptyList()else (0 until notes.length()).map{notes.getJSONObject(it).let{SavedNote(it.getString("id"),it.getString("patientId"),it.getString("text"),it.getString("savedAt"))}}
        engine.bedside.snapshot(assigned,staff,context(m.optJSONObject("context")),saved,savedNotes)
        if(eligible) for(t in tasks) if(t.to==actor && t.status=="sent" && delivered.add(t.id)) socket?.send(JSONObject().put("id",UUID.randomUUID().toString()).put("type","request.delivered").put("taskId",t.id).put("revision",t.revision).toString())
      }
      "command.status" -> {val ack=m.optJSONObject("ack");engine.reconciled(m.getString("commandId"),if(m.optBoolean("found"))ack?.optBoolean("ok") else null,ack?.optString("taskId")?.takeIf{it.isNotEmpty()},ack?.optString("reason")?.takeIf{it.isNotEmpty()})}
      "sim.query" -> { lastCard=null }
      "ack" -> engine.acknowledgement(m.getString("id"),m.getBoolean("ok"),m.optString("taskId").takeIf {it.isNotEmpty()},m.optString("reason").takeIf {it.isNotEmpty()},context(m.optJSONObject("context")),m.optString("observationId").takeIf{it.isNotEmpty()})
      "sim.input" -> {
        val input=m.getJSONObject("input")
        when(input.getString("kind")) {
          "camera" -> {cameraSeed=input.getInt("seed");cameraScenario=input.getString("scenario")}
          "voice-session" -> {if(input.getBoolean("active"))stopGlassesVoice();computerVoice=input.getBoolean("active");voiceStatus=if(computerVoice)"Computer microphone on" else "Microphone off";lastCard=null}
          "sdk-voice" -> if(!physical){if(input.getBoolean("active"))startGlassesVoice()else stopGlassesVoice()}
          "sdk-transcript" -> if(!physical)mock?.services?.speech?.simulateTranscription(input.getString("text"),input.optBoolean("final",true),0.95f)
          "wake" -> if(eligible && !glassesVoice)engine.wake()
          "transcript" -> if(eligible && !glassesVoice)engine.transcript(input.getString("text"),input.optBoolean("final",true))
          "gesture" -> if(eligible)simulateGesture(input.getString("value"))
          "device-lost" -> {simulatedLoss=true;syncReadiness()}
          "device-ready" -> {simulatedLoss=false;syncReadiness()}
        }
      }
    }
    publish()
  }
  private fun simulateGesture(kind:String) {
    gestureJob?.cancel()
    val prompt=engine.card.promptId
    val nod=listOf(0.0,0.0,0.0,0.0,0.0,8.0,16.0,8.0,0.0,8.0,16.0,8.0,0.0)
    val shake=listOf(0.0,0.0,0.0,0.0,0.0,8.0,16.0,8.0,0.0,-8.0,-16.0,-8.0,0.0)
    val trace=if(kind=="shake")shake else if(kind=="single-nod")nod.take(9) else nod
    gestureJob=lifecycleScope.launch {
      for(v in trace) {
        engine.motion(HeadSample(prompt,SystemClock.elapsedRealtime(),if(kind=="shake")0.0 else v,if(kind=="shake"||kind=="mixed")v else 0.0,"simulation",kind=="walking")); publish(); delay(100)
      }
    }
  }
  private fun publish() {
    if(cameraId!=null && (engine.bedside.ticket?.id?:engine.bedside.qrScanId)!=cameraId){cameraJob?.cancel();cameraJob=null;cameraId=null}
    persistPending()
    shown=engine.card
    val card=shown
    if(physical && card.phase in setOf("listening","dictating-message","dictating-note") && glassesVoice && SystemClock.elapsedRealtime()>=listeningDisplayRefreshAt){
      listeningDisplayRefreshAt=SystemClock.elapsedRealtime()+5000
      lastCard=null
    }
    if(card==lastCard){if(reportedSpeechListening!=(mock?.services?.speech?.isListening ?: false))report(card,renderedCard==card);return}
    lastCard=card
    report(card)
    if(SystemClock.elapsedRealtime()<nextRenderAt){lastCard=null;return}
    lifecycleScope.launch { lock.withLock {
      if(card!=engine.card || !displayReady)return@withLock
      if(card.phase=="quiet"){
        display?.clearDisplay()?.onSuccess {if(card==engine.card){renderedCard=card;report(card,true)}}?.onFailure{e,_->error=e.description;renderedCard=null;lastCard=null;nextRenderAt=SystemClock.elapsedRealtime()+1000}
        return@withLock
      }
      display?.sendContent {
        flexBox(direction=Direction.COLUMN,alignment=Alignment.END,crossAlignment=Alignment.STRETCH,gap=10,padding=18) {
          flexBox(direction=Direction.COLUMN,gap=7,padding=16,background=FlexBoxBackground.CARD) {
            text(if(glassesVoice) {if(listeningWindow?.card!=card) "Preparing microphone…" else if(!physical) "SDK speech · simulated" else if(card.microphone) "Glasses mic · listening" else "Glasses mic · say Hey Lumi"} else if(computerVoice) {if(card.microphone) "Computer mic · listening" else "Computer mic · wake phrase only"} else if(card.microphone) "Simulated input window" else "Microphone off",style=TextStyle.BODY,color=TextColor.SECONDARY)
            text(card.title,style=TextStyle.HEADING)
            card.body.split("\n").forEach{line->text(line,style=TextStyle.BODY)}
            text(if(physical)engine.tiltHint.ifEmpty{card.hint}else card.hint,style=TextStyle.BODY,color=TextColor.SECONDARY)
          }
        }
      }?.onSuccess { if(card==engine.card)renderedCard=card
        if(card.phase=="review" || card.phase=="camera" || card.phase=="camera-qr" || card.phase=="message-notice")engine.reviewDisplayed(card.promptId)
        if(card==engine.card){report(card,true);if(card.phase=="camera")startCamera();if(card.phase=="camera-qr")startQrCamera()} }?.onFailure { e,_ -> error=e.description;renderedCard=null;lastCard=null;nextRenderAt=SystemClock.elapsedRealtime()+1000 }
    } }
  }
  private fun persistPending():Boolean{
    val prefs=getSharedPreferences("handsfree-recovery",MODE_PRIVATE);val id=engine.unresolvedCommandId
    return if(prefs.getString("pending",null)!=id)prefs.edit().apply{if(id==null)remove("pending")else putString("pending",id)}.commit() else true
  }
  private fun report(card:Card,rendered:Boolean=false){
    reportedSpeechListening=mock?.services?.speech?.isListening ?: false
    socket?.send(JSONObject().put("type","sim.state").put("connectionError",error).put("motion",JSONObject().put("raw",motionRaw).put("samples",motionSamples).put("ageMs",if(motionLastAt==0L)-1 else SystemClock.elapsedRealtime()-motionLastAt).put("source",motionSource).put("state",motionState).put("status",motionStatus).put("eligible",eligible).put("rendered",renderedCard==card)).put("glassesVoice",glassesVoice).put("sdkReady",listeningWindow==SpeechWindow(engine.card,renderedCard==engine.card,eligible)).put("sdkListening",mock?.services?.speech?.isListening ?: false).put("renderedPromptId",if(rendered)card.promptId else 0).put("card",JSONObject().put("phase",card.phase).put("title",card.title).put("body",card.body).put("hint",if(physical)engine.tiltHint.ifEmpty{card.hint}else card.hint).put("microphone",card.microphone).put("promptId",card.promptId)).toString())
  }
  private fun context(o:JSONObject?):PatientContext?=o?.let{PatientContext(it.getString("patientId"),it.getString("encounterId"),it.getInt("contextRevision"))}
  private fun startQrCamera(){
    val id=engine.bedside.qrScanId?:return
    if(cameraId==id||!engine.bedside.qrReady(id))return
    cameraId=id
    cameraJob=lifecycleScope.launch {
      try{
        val source=if(physical)DatCameraSource(session?:throw CameraFailure("Glasses session unavailable"))else object:CameraSource{
          override suspend fun acquire(consume:suspend(PixelFrame)->Boolean){consume(PatientQrReader.synthetic())}
        }
        withTimeout(5000){source.acquire{frame->
          val code=try{withContext(Dispatchers.Default){PatientQrReader.read(frame)}}finally{frame.close()}
          if(code!=null)engine.bedside.qrResult(id,code)
          val done=engine.bedside.qrScanId!=id;publish();done
        }}
      }catch(e:TimeoutCancellationException){engine.bedside.qrError(id,"No readable code");publish()}
      catch(e:CameraFailure){engine.bedside.qrError(id,e.userReason);publish()}
      catch(e:CancellationException){throw e}
      catch(e:Exception){engine.bedside.qrError(id,"QR processing failed");publish()}
    }
  }
  private fun startCamera(){
    val ticket=engine.bedside.ticket?:return
    if(cameraId==ticket.id || !engine.bedside.cameraReady(ticket.id))return
    cameraId=ticket.id
    cameraJob=lifecycleScope.launch {
      val pipeline=CapturePipeline()
      val temporary=mutableListOf<File>()
      try {
        val source:CameraSource=if(physical) DatCameraSource(session?:throw CameraFailure("Glasses session unavailable")) else if(datCamera){
          // Only generated debug fixtures are materialized for the SDK's URI-based mock inputs.
          val photo=File(cacheDir,"injected-monitor.png");val video=File(cacheDir,"injected-feed.mp4");temporary.addAll(listOf(photo,video))
          val pixels=SyntheticCameraSource(cameraSeed,cameraScenario).render()
          try {val rgb=IntArray(pixels.gray.size){val v=pixels.gray[it];(255 shl 24) or (v shl 16) or (v shl 8) or v};val bitmap=Bitmap.createBitmap(rgb,pixels.width,pixels.height,Bitmap.Config.ARGB_8888)
            try {photo.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle();rgb.fill(0)}
          }finally{pixels.close()}
          assets.open("monitor-feed.mp4").use{input->video.outputStream().use{input.copyTo(it)}}
          mock!!.services.camera.setCapturedImage(Uri.fromFile(photo));mock!!.services.camera.setCameraFeed(Uri.fromFile(video))
          DatCameraSource(session!!)
        }else SyntheticCameraSource(cameraSeed,cameraScenario)
        withTimeout(5000){source.acquire { frame ->
        val result=try{withContext(Dispatchers.Default){pipeline.consume(frame,SystemClock.elapsedRealtime())}}finally{frame.close()}
        if(result!=null)engine.bedside.captureResult(ticket.id,result)
        val done=engine.bedside.ticket?.id!=ticket.id
        publish();done
      }}} catch(e:TimeoutCancellationException){engine.bedside.cameraError(ticket.id,"No stable complete reading");publish()}
      catch(e:CameraFailure){engine.bedside.cameraError(ticket.id,e.userReason);publish()}
      catch(e:CancellationException){throw e}
      catch(e:Exception){engine.bedside.cameraError(ticket.id,"Camera processing failed");publish()}
      finally{pipeline.reset();temporary.forEach{it.delete()}}
    }
  }
  private fun syncReadiness(){
    if(!::engine.isInitialized)return
    val ready=authReady && displayReady && foreground && recoveryHealthy && !simulatedLoss && !stopped
    if(eligible!=ready){
      eligible=ready
      // Invalidate the controller before sensor-stop callbacks can publish another card.
      engine.connection(ready)
      if(!ready){renderedCard=null;speechInput?.stop();gestureJob?.cancel();cameraJob?.cancel();delivered.clear()}
    }
    if(authReady && reportedReady!=ready){reportedReady=ready;socket?.send(JSONObject().put("type","device.state").put("ready",ready).toString())}
  }
  private fun startGlassesVoice(){
    if(!eligible || computerVoice){voiceStatus="Connect the display and stop computer voice first";return}
    session?.let{speechInput?.start(it)}
  }
  private fun stopGlassesVoice(){speechInput?.stop();if(::engine.isInitialized && engine.card.microphone)engine.transcript("cancel");lastCard=null;publish()}
  override fun onResume(){super.onResume();foreground=true;if(::engine.isInitialized){syncReadiness();lastCard=null;publish()}}
  override fun onStop(){super.onStop();foreground=false;if(::engine.isInitialized){syncReadiness();publish()}}
  override fun onDestroy() {
    stopped=true;speechInput?.stop(); cameraJob?.cancel();gestureJob?.cancel();engine.deviceLost();socket?.close(1000,"Preview closed");session?.stop();mock?.doff();mock?.powerOff();if(!physical)runCatching{kit.stopTestServer();kit.disable()};http.dispatcher.executorService.shutdown();super.onDestroy()
  }
}
