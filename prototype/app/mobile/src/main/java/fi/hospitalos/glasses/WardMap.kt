package fi.hospitalos.glasses

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Draws the ward floor plan as a bitmap for the glasses display.
 *
 * The display's view components can't draw shapes or colors, so the map is rendered on the phone
 * and sent as an image. Black pixels are see-through on the glasses, so the background stays black.
 * Same layout as mockups/glasses-scenes.html (map space 260 x 150).
 */
object WardMap {
  private const val MAP_W = 260f
  private const val MAP_H = 150f
  private const val SCALE = 2f // 520 x 300 px bitmap

  private val GREEN = 0xFF5BE38F.toInt()
  private val AMBER = 0xFFFFCC4D.toInt()
  private val BLUE = 0xFF64D2FF.toInt()
  private val RED = 0xFFFF5F57.toInt()

  private val topRooms = listOf("7", "8", "9", "10", "11")
  private val bottomRooms = listOf("12", "13", "14", "Meds", "Desk")

  /** Map with every staff member as a colored dot (green free, amber busy, blue you). */
  fun staffMap(staff: List<Staff>): Bitmap = draw { canvas ->
    staff.forEach { s ->
      val color =
          when {
            s.isMe -> BLUE
            s.status == StaffStatus.BUSY -> AMBER
            else -> GREEN
          }
      dot(canvas, s.position, color)
      label(canvas, s.name, s.position, color)
    }
  }

  /** Map with a dashed route from [from] to the alert room and a red target. */
  fun alertMap(from: MapPoint, alertRoom: Int): Bitmap = draw { canvas ->
    val target = roomCenter(alertRoom)
    val path =
        Path().apply {
          moveTo(from.x * SCALE, from.y * SCALE)
          lineTo(target.x * SCALE, from.y * SCALE)
          lineTo(target.x * SCALE, (target.y + 12f) * SCALE)
        }
    canvas.drawPath(
        path,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 3.5f * SCALE
          strokeCap = Paint.Cap.ROUND
          color = 0xFFFFFFFF.toInt()
          pathEffect = DashPathEffect(floatArrayOf(7f * SCALE, 7f * SCALE), 0f)
        },
    )
    // target ring + dot
    canvas.drawCircle(
        target.x * SCALE,
        target.y * SCALE,
        14f * SCALE,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 2.5f * SCALE
          color = RED
          alpha = 140
        },
    )
    dot(canvas, target, RED, radius = 9f)
    dot(canvas, from, BLUE)
    label(canvas, "You", from, BLUE)
  }

  private fun roomCenter(room: Int): MapPoint {
    val top = topRooms.indexOf(room.toString())
    if (top >= 0) return MapPoint(28f + top * 52f, 34f)
    val bottom = bottomRooms.indexOf(room.toString())
    if (bottom >= 0) return MapPoint(28f + bottom * 52f, 116f)
    return MapPoint(MAP_W / 2, MAP_H / 2)
  }

  private fun draw(block: (Canvas) -> Unit): Bitmap {
    val bmp =
        Bitmap.createBitmap(
            (MAP_W * SCALE).toInt(),
            (MAP_H * SCALE).toInt(),
            Bitmap.Config.ARGB_8888,
        )
    val canvas = Canvas(bmp)
    canvas.drawColor(0xFF000000.toInt())
    drawPlan(canvas)
    block(canvas)
    return bmp
  }

  private fun drawPlan(canvas: Canvas) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18FFFFFF }
    val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = 1.2f * SCALE
          color = 0x73FFFFFF
        }
    val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = 0xB3FFFFFF.toInt()
          textSize = 12f * SCALE
          typeface = Typeface.DEFAULT_BOLD
        }
    for (i in 0 until 5) {
      val x = 4f + i * 52f
      val w = if (i == 4) 44f else 48f
      listOf(4f to topRooms[i], 94f to bottomRooms[i]).forEach { (y, name) ->
        val r = RectF(x * SCALE, y * SCALE, (x + w) * SCALE, (y + 52f) * SCALE)
        canvas.drawRoundRect(r, 6f * SCALE, 6f * SCALE, fill)
        canvas.drawRoundRect(r, 6f * SCALE, 6f * SCALE, stroke)
        val ty = if (y < 50f) y + 15f else y + 46f
        canvas.drawText(name, (x + 5f) * SCALE, ty * SCALE, text)
      }
    }
  }

  private fun dot(canvas: Canvas, p: MapPoint, color: Int, radius: Float = 8f) {
    canvas.drawCircle(
        p.x * SCALE,
        p.y * SCALE,
        radius * SCALE,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
    )
  }

  private fun label(canvas: Canvas, text: String, p: MapPoint, color: Int) {
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          this.color = color
          textSize = 13f * SCALE
          typeface = Typeface.DEFAULT_BOLD
        }
    canvas.drawText(text, (p.x + 13f) * SCALE, (p.y + 5f) * SCALE, paint)
  }
}
