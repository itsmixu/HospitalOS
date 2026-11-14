package fi.hospitalos.handsfree
import fi.hospitalos.handsfree.capture.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class MonitorReaderTest {
  private val root=File("../../demo-data/monitor")
  // Android's compile bootclasspath omits java.desktop; host JVM provides its PNG decoder.
  private fun load(file:String):PixelFrame {
    val image=Class.forName("javax.imageio.ImageIO").getMethod("read",File::class.java).invoke(null,File(root,file))
    val cls=Class.forName("java.awt.image.BufferedImage")
    val width=cls.getMethod("getWidth").invoke(image) as Int;val height=cls.getMethod("getHeight").invoke(image) as Int
    val raster=cls.getMethod("getRaster").invoke(image)
    val get=Class.forName("java.awt.image.Raster").getMethod("getSample",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
    val gray=IntArray(width*height){i->get.invoke(raster,i%width,i/width,0) as Int}
    return PixelFrame(width,height,gray)
  }
  @Test fun frozenRandomizedAndHeldoutEvaluation(){
    val stats=linkedMapOf<String,IntArray>();var wrong=0
    for(line in File(root,"cases.tsv").readLines()){
      val v=line.split('\t');val key="${v[0]}/${v[2]}";val counts=stats.getOrPut(key){IntArray(4)};counts[0]++
      val frame=load(v[1]);val pipeline=CapturePipeline();var result:Extraction?=null;for(at in listOf(0L,240L,480L)){result=pipeline.consume(PixelFrame(frame.width,frame.height,frame.gray.copyOf()),at)};frame.close()
      if(result is Extraction.Accepted){val expected=Reading(v[3].toInt(),v[4].toInt(),v[5].toInt());if(result.reading==expected)counts[1]++ else {counts[2]++;wrong++}}
      else counts[3]++
    }
    val report="split/scenario,total,exact,wrong,rejected\n"+stats.entries.joinToString("\n"){(k,v)->"$k,${v.joinToString(",")}"}+"\n"
    File(root,"evaluation.csv").writeText(report);println(report)
    assertEquals("Wrong proposals",0,wrong)
    for((k,v) in stats){if(k.endsWith("/clear"))assertEquals(k,v[0],v[1])else assertEquals(k,v[0],v[3])}
  }
  @Test fun temporalConsensusAndBufferDisposal(){
    val path=File(root,"cases.tsv").readLines().first().split('\t')[1];val p=CapturePipeline()
    val first=load(path);assertNull(p.consume(first,100));assertTrue(first.gray.all{it==0})
    assertNull(p.consume(load(path),100));assertNull(p.consume(load(path),300));assertTrue(p.consume(load(path),500) is Extraction.Accepted)
    p.reset();assertNull(p.consume(load(path),600))
  }
  @Test fun badFrameBreaksAgreement(){val rows=File(root,"cases.tsv").readLines().map{it.split('\t')};val good=rows.first()[1];val bad=rows.first{it[2]=="blur"}[1];val p=CapturePipeline();p.consume(load(good),0);p.consume(load(good),240);assertTrue(p.consume(load(bad),480) is Extraction.Rejected);assertNull(p.consume(load(good),720))}

  private fun optical(split:String){
    val stats=linkedMapOf<String,IntArray>();val reasons=linkedMapOf<String,Int>();var wrong=0
    for(v in File(root,"optical-cases.tsv").readLines().map{it.split('\t')}.filter{it[0]==split}){
      val counts=stats.getOrPut(v[2]){IntArray(4)};counts[0]++
      val frame=load(v[1]);val p=CapturePipeline();var result:Extraction?=null
      for(at in listOf(0L,240L,480L))result=p.consume(PixelFrame(frame.width,frame.height,frame.gray.copyOf()),at)
      frame.close()
      if(result is Extraction.Accepted){if(result.reading==Reading(v[3].toInt(),v[4].toInt(),v[5].toInt()))counts[1]++else{counts[2]++;wrong++}}
      else {counts[3]++;val reason=(result as? Extraction.Rejected)?.reason?:"No consensus";reasons[reason]=(reasons[reason]?:0)+1}
    }
    val report="scenario,total,exact,wrong,rejected\n"+stats.entries.joinToString("\n"){(k,v)->"$k,${v.joinToString(",")}"}+"\n"
    File(root,"optical-$split-evaluation.csv").writeText(report);println(report);println(reasons)
    assertEquals("Wrong optical proposals",0,wrong)
    for((k,v) in stats)if(k!="clear")assertEquals(k,v[0],v[3])
    // Rejection is an allowed result; never relax certainty to force every clear render through.
    // Development target is >=90% useful reads; held-out acceptance is reported, not tuned.
    if(split=="development")assertTrue("Development useful-read coverage",stats["clear"]!![1]>=stats["clear"]!![0]*.9)
  }
  @Test fun opticalDevelopment(){optical("development")}
  @Test fun opticalHeldout(){optical("heldout")}

  @Test fun multiplePanelsAndLowContrastRejectWithoutRetainingPixels(){
    val path=File(root,"cases.tsv").readLines().first().split('\t')[1];val source=load(path)
    val two=PixelFrame(1000,400,IntArray(1000*400){235})
    for(y in 0 until 360)for(x in 0 until 480){two.gray[(y+20)*1000+x+10]=source.gray[y*480+x];two.gray[(y+20)*1000+x+510]=source.gray[y*480+x]}
    assertTrue(CapturePipeline().consume(two,0) is Extraction.Rejected);assertTrue(two.gray.all{it==0})
    val dim=PixelFrame(480,360,IntArray(480*360){if(source.gray[it]<125)75 else 150})
    assertTrue(CapturePipeline().consume(dim,0) is Extraction.Rejected);assertTrue(dim.gray.all{it==0});source.close()
  }
}
