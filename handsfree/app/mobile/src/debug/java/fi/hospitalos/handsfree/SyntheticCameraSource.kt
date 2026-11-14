package fi.hospitalos.handsfree

import fi.hospitalos.handsfree.capture.*
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Debug-only generated sensor substitute. Values are drawn as pixels, never returned as OCR output. */
class SyntheticCameraSource(private val seed:Int,private val scenario:String):CameraSource {
  override suspend fun acquire(consume:suspend (PixelFrame)->Boolean) {
    if(scenario=="denied")throw CameraFailure("Simulated camera permission denied")
    if(scenario=="unavailable")throw CameraFailure("Simulated camera unavailable")
    while(true){if(consume(render()))return;delay(240)}
  }
  fun render():PixelFrame {
    val random=Random(seed);val gray=IntArray(480*360){235}
    fun rect(x:Int,y:Int,w:Int,h:Int,v:Int=25){for(j in y until y+h)for(i in x until x+w)if(i in 0..479 && j in 0..359)gray[j*480+i]=v}
    fun label(text:String,x:Int,y:Int){for((k,c) in text.withIndex())for((i,b) in MonitorReader.glyphs.getValue(c).withIndex())if(b=='1')rect(x+k*16+(i%3)*4,y+i/3*4,4,4)}
    rect(12,12,456,4);rect(12,344,456,4);rect(12,12,4,336);rect(464,12,4,336);label("HOS-BP-1",32,30)
    for((row,name) in listOf("SYS","DIA","PULSE").withIndex()){
      val y=72+row*88;label(name,32,y+20);label(if(row==2)"bpm" else "mmHg",350,y+20)
      val value=random.nextInt(1,300).toString().padStart(3,'0')
      for((slot,c) in value.withIndex())if(c!=' ')for(segment in MonitorReader.segments[c.digitToInt()]){val r=MonitorReader.rects[segment];rect(190+44*slot+r[0],y+r[1],r[2],r[3])}
    }
    when(scenario){
      "missing-label"->rect(32,92,80,20,235)
      "glare"->rect(230,85,40,45,255)
      "crop"->rect(0,0,22,360,235)
      "ambiguous"->rect(278+7,72+30,20,5,130)
      "blur"->{val copy=gray.copyOf();for(y in 4..355)for(x in 4..475){var sum=0;for(j in -4..4)for(i in -4..4)sum+=copy[(y+j)*480+x+i];gray[y*480+x]=sum/81};copy.fill(0)}
    }
    return PixelFrame(480,360,gray)
  }
}
