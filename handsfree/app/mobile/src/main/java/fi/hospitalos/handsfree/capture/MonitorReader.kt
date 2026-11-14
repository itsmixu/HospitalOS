package fi.hospitalos.handsfree.capture

/** Owned grayscale buffer. No filename, expected tuple, EXIF or fixture metadata enters extraction. */
class PixelFrame(val width:Int,val height:Int,val gray:IntArray) : AutoCloseable {
  init { require(width>0 && height>0 && gray.size==width*height) }
  override fun close() { gray.fill(0) }
}
data class Reading(val systolic:Int,val diastolic:Int,val pulse:Int) {
  fun corrected(field:String,n:Int)=when(field){"systolic"->copy(systolic=n);"diastolic"->copy(diastolic=n);else->copy(pulse=n)}
  fun changes(original:Reading)=buildList {if(systolic!=original.systolic)add("systolic");if(diastolic!=original.diastolic)add("diastolic");if(pulse!=original.pulse)add("pulse")}
  fun display()="SYS $systolic mmHg · DIA $diastolic mmHg\nPulse $pulse bpm"
}
sealed class Extraction { data class Accepted(val reading:Reading):Extraction();data class Rejected(val reason:String):Extraction() }

/** HOS-BP-1/v1 only: rectified complete panel. Sample stroke interiors, never guess from ranges. */
class MonitorReader {
  companion object {
    const val PROFILE="HOS-BP-1/v1"
    // Glyphs are the declared optical alphabet, not expected observation values.
    val glyphs=mapOf(
      'H' to "101101111101101", 'O' to "111101101101111", 'S' to "111100111001111",
      'B' to "110101110101110", 'P' to "110101110100100", 'Y' to "101101010010010",
      'D' to "110101101101110", 'I' to "111010010010111", 'A' to "010101111101101",
      'U' to "101101101101111", 'L' to "100100100100111", 'E' to "111100110100111",
      'm' to "000111111101101", 'g' to "000111101001111", 'b' to "100100110101110",
      'p' to "000110101110100", '-' to "000000111000000", '1' to "010110010010111"
    )
    val segments=listOf(setOf(0,1,2,3,4,5),setOf(1,2),setOf(0,1,6,4,3),setOf(0,1,6,2,3),setOf(5,6,1,2),setOf(0,5,6,2,3),setOf(0,5,6,4,2,3),setOf(0,1,2),setOf(0,1,2,3,4,5,6),setOf(0,1,2,3,5,6))
    // x,y,width,height relative to 34 x 64 digit.
    val rects=listOf(intArrayOf(7,2,20,5),intArrayOf(27,8,5,21),intArrayOf(27,35,5,21),intArrayOf(7,57,20,5),intArrayOf(2,35,5,21),intArrayOf(2,8,5,21),intArrayOf(7,30,20,5))
  }
  fun read(frame:PixelFrame):Extraction {
    if(frame.width<480 || frame.height<360 || kotlin.math.abs(frame.width.toDouble()/frame.height-4.0/3)>0.015)return Extraction.Rejected("Unsupported framing; align the whole HOS-BP-1 panel")
    fun pixel(x:Int,y:Int):Int = frame.gray[((y+.5)*frame.height/360).toInt().coerceAtMost(frame.height-1)*frame.width+((x+.5)*frame.width/480).toInt().coerceAtMost(frame.width-1)]
    fun match(x:Int,y:Int,w:Int,h:Int,dark:(Int,Int)->Boolean):Boolean {
      var wrong=0;var uncertain=0;var sampled=0
      // Exclude one pixel at printed edges: antialiasing there is not missing ink.
      // Every remaining light/dark interior must still match; no nearest-digit repair.
      for(j in 1 until h-1)for(i in 1 until w-1){
        val expected=dark(i,j)
        if((-1..1).any{dy->(-1..1).any{dx->dark(i+dx,j+dy)!=expected}})continue
        sampled++;val v=pixel(x+i,y+j)
        if(v in 81..169)uncertain++
        if((v<125)!=expected)wrong++
      }
      return sampled>0 && wrong.toDouble()/sampled<0.012 && uncertain.toDouble()/sampled<0.02
    }
    fun border(x:Int,y:Int,w:Int,h:Int)=match(x,y,w,h){_,_->true}
    fun margin(x:Int,y:Int,w:Int,h:Int)=match(x,y,w,h){_,_->false}
    if(!border(12,12,456,4)||!border(12,344,456,4)||!border(12,12,4,336)||!border(464,12,4,336)||!margin(0,0,480,8)||!margin(0,352,480,8))return Extraction.Rejected("Cropped or unsupported panel")
    fun label(text:String,x:Int,y:Int):Boolean {
      for((index,c) in text.withIndex()){
        val glyph=glyphs[c]?:return false
        if(!match(x+index*16,y,16,20){i,j->i<12 && glyph[(j/4)*3+i/4]=='1'})return false
      };return true
    }
    if(!label("HOS-BP-1",32,30))return Extraction.Rejected("Unsupported monitor")
    val values=mutableListOf<Int>()
    for((row,label) in listOf("SYS","DIA","PULSE").withIndex()) {
      val y=72+row*88
      if(!label(label,32,y+20)||!label(if(row==2)"bpm" else "mmHg",350,y+20))return Extraction.Rejected("Missing or unclear labels / units")
      var digits=""
      for(slot in 0..2){
        val x=190+44*slot
        val blank=match(x,y,34,64){_,_->false}
        if(blank)return Extraction.Rejected("Missing digit; all three slots must be visible")
        // Each stroke has its own certainty gate so a small ambiguous stroke cannot
        // disappear inside the much larger blank area of the digit bounding box.
        val present=mutableSetOf<Int>()
        for((index,r) in rects.withIndex()){
          var darkCount=0;var lightCount=0;var count=0
          for(j in r[1]+1 until r[1]+r[3]-1)for(i in r[0]+1 until r[0]+r[2]-1){
            val v=pixel(x+i,y+j);count++;if(v<=80)darkCount++;if(v>=170)lightCount++
          }
          if(darkCount.toDouble()/count>=.92)present.add(index)
          else if(lightCount.toDouble()/count<.92)return Extraction.Rejected("Unclear segment; reduce glare or hold closer")
        }
        val candidates=(0..9).filter { n->segments[n]==present && match(x,y,34,64){i,j->segments[n].any { r->val b=rects[r];i in b[0] until b[0]+b[2] && j in b[1] until b[1]+b[3] }} }
        if(candidates.size!=1)return Extraction.Rejected("Blur, glare or ambiguous digit")
        digits+=candidates.single()
      }
      values+=digits.toInt()
    }
    return Extraction.Accepted(Reading(values[0],values[1],values[2]))
  }
}

/** No retained image; consecutive agreement only across distinct increasing frame times. */
class CapturePipeline(private val reader:MonitorReader=MonitorReader()) {
  private var last:Reading?=null;private var count=0;private var first=0L;private var lastTime=-1L
  fun reset(){last=null;count=0;first=0;lastTime=-1}
  fun consume(frame:PixelFrame,at:Long):Extraction? {
    try {
      if(at<=lastTime)return null
      lastTime=at
      val normalized=PanelProcessor().normalize(frame) ?: run{last=null;count=0;return Extraction.Rejected("Align one complete supported panel")}
      val result=try{reader.read(normalized)}finally{normalized.close()}
      return when(result) {
        is Extraction.Rejected->{last=null;count=0;result}
        is Extraction.Accepted->{if(last!=result.reading){last=result.reading;count=1;first=at}else count++;if(count>=3 && at-first>=400)result else null}
      }
    } finally {frame.close()}
  }
}
