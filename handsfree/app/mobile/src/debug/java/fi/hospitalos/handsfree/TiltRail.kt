package fi.hospitalos.handsfree

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Small code-drawn DAT image. Constant dimensions at every fill level; no animation clock. */
object TiltRail {
 const val GREEN=0xFF9CE6B5.toInt()
 const val CORAL=0xFFFFA58C.toInt()
 const val NEUTRAL=0xFFB8C2C8.toInt()
 private val cache=mutableMapOf<Pair<Int,Int>,Bitmap>()
 fun bitmap(progress:Double,color:Int):Bitmap {
  val step=(progress.coerceIn(0.0,1.0)*20).toInt()
  return cache.getOrPut(step to color){
   val bitmap=Bitmap.createBitmap(6,220,Bitmap.Config.ARGB_8888)
   val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
   paint.color=0x446F777D;canvas.drawRoundRect(1f,0f,5f,220f,2f,2f,paint)
   if(step>0){paint.color=color;canvas.drawRoundRect(1f,220f*(1-step/20f),5f,220f,2f,2f,paint)}
   bitmap
  }
 }
}
