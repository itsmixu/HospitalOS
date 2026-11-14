package fi.hospitalos.handsfree.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.removeCamera
import com.meta.wearable.dat.camera.types.*
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Acquisition transfers ownership of each frame; caller must close it. No camera file writes. */
interface CameraSource { suspend fun acquire(consume:suspend (PixelFrame)->Boolean) }
class CameraFailure(val userReason:String):Exception(userReason)
fun Bitmap.ownedPixels():PixelFrame {
  val rgb=IntArray(width*height);getPixels(rgb,0,width,0,0,width,height)
  for(i in rgb.indices){val c=rgb[i];rgb[i]=(((c shr 16) and 255)*299+((c shr 8) and 255)*587+(c and 255)*114)/1000}
  return PixelFrame(width,height,rgb)
}
/** Uses the SAME STARTED session as Display. Hardware path is compiled, not hardware validated. */
class DatCameraSource(private val session:DeviceSession):CameraSource {
  override suspend fun acquire(consume:suspend (PixelFrame)->Boolean) {
    if(session.state.value!=DeviceSessionState.STARTED)throw CameraFailure("Glasses camera unavailable")
    var granted=false
    Wearables.checkPermissionStatus(Permission.CAMERA).onSuccess{granted=it==PermissionStatus.Granted}
    if(!granted)throw CameraFailure("Camera permission denied; grant it during setup")
    var camera:com.meta.wearable.dat.camera.Camera?=null
    session.addCamera(StreamConfiguration(videoQuality=VideoQuality.MEDIUM,frameRate=7)).onSuccess{camera=it}
    val active=camera?:throw CameraFailure("Camera unavailable; display remains connected")
    try {
      coroutineScope {
        val errors=launch {active.stream.errorStream.collect {throw CameraFailure("Camera stream error; retry")}}
        var started=false;active.stream.start().onSuccess{started=true}
        if(!started)throw CameraFailure("Camera could not start")
        withTimeout(3000){active.stream.state.first{it==StreamState.STREAMING}}
        while(currentCoroutineContext().isActive){
          var bitmap:Bitmap?=null
          active.stream.capturePhoto().onSuccess { photo->
            bitmap=when(photo){is PhotoData.Bitmap->photo.bitmap;is PhotoData.HEIC->{val b=photo.data.duplicate();val bytes=ByteArray(b.remaining());b.get(bytes);try{BitmapFactory.decodeByteArray(bytes,0,bytes.size)}finally{bytes.fill(0)}}}
          }
          val image=bitmap?:throw CameraFailure("No camera image; retry")
          val frame=try{withContext(Dispatchers.Default){image.ownedPixels()}}finally{image.recycle()}
          if(consume(frame))break
          delay(220)
        }
        errors.cancel()
      }
    } finally {active.stop();session.removeCamera()}
  }
}
