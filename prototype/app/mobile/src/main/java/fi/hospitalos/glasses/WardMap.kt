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

  /** Downscale for images re-sent often (alert banner, turning map). */
  private const val SMALL = 0.7f

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

  /**
   * Red alert banner. The display's text views can't be colored, so the alert is drawn as an
   * image; red light shows clearly on the see-through lens.
   */
  fun alertBanner(text: String): Bitmap {
    // Designed at 520x96, stored at ~half the pixels: less to push over Bluetooth on every update.
    val k = SMALL
    val bmp = Bitmap.createBitmap((520 * k).toInt(), (96 * k).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFF000000.toInt())
    c.scale(k, k)
    val w = 520
    val h = 96
    c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED })
    // Warning triangle, white outline with "!".
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f; strokeJoin = Paint.Join.ROUND }
    val tri = Path().apply { moveTo(56f, 22f); lineTo(84f, 72f); lineTo(28f, 72f); close() }
    c.drawPath(tri, white)
    c.drawLine(56f, 40f, 56f, 56f, white)
    c.drawCircle(56f, 64f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    c.drawText(
        text,
        104f,
        62f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 40f; typeface = Typeface.DEFAULT_BOLD },
    )
    return bmp
  }

  /**
   * Heading-up map: the plan rotates around the wearer so "up" is where they're facing.
   * [turnDeg] is how far they've turned left since the alert opened (they start facing map-up).
   */
  fun headingUpMap(from: MapPoint, alertRoom: Int, turnDeg: Float): Bitmap {
    // Re-sent on every head turn, so keep it small: designed at 520x300, stored at ~half the pixels.
    val k = SMALL
    val bmp = Bitmap.createBitmap((520 * k).toInt(), (300 * k).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFF000000.toInt())
    c.scale(k, k)
    val w = 520
    val h = 300
    val target = roomCenter(alertRoom)
    c.save()
    c.translate(w / 2f, h * 0.68f)
    c.rotate(turnDeg) // turning left swings the world clockwise
    c.translate(-from.x * SCALE, -from.y * SCALE)
    drawPlan(c)
    c.drawPath(routePath(from, target), routePaint())
    c.drawCircle(target.x * SCALE, target.y * SCALE, 14f * SCALE, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * SCALE; color = RED; alpha = 140 })
    dot(c, target, RED, radius = 9f)
    c.restore()
    // The wearer: a blue arrow at the center, always pointing up (= forward).
    val cx = w / 2f
    val cy = h * 0.68f
    val arrow = Path().apply { moveTo(cx, cy - 18f); lineTo(cx + 13f, cy + 14f); lineTo(cx, cy + 6f); lineTo(cx - 13f, cy + 14f); close() }
    c.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE })
    return bmp
  }

  /** First leg of the route as a bearing, degrees clockwise from map-up. */
  fun firstLegBearing(from: MapPoint, alertRoom: Int): Float {
    val target = roomCenter(alertRoom)
    return if (kotlin.math.abs(target.x - from.x) > 1f) (if (target.x < from.x) -90f else 90f) else (if (target.y < from.y) 0f else 180f)
  }

  /** "Turn left" etc. for where to go next, given how far the wearer has turned left. */
  fun direction(from: MapPoint, alertRoom: Int, turnDeg: Float): String {
    var rel = firstLegBearing(from, alertRoom) + turnDeg // relative to where they face; + = right
    while (rel > 180f) rel -= 360f
    while (rel < -180f) rel += 360f
    return when {
      rel in -30f..30f -> "Straight ahead"
      rel in 30f..150f -> "Turn right"
      rel in -150f..-30f -> "Turn left"
      else -> "Turn around"
    }
  }

  private fun routePath(from: MapPoint, target: MapPoint) =
      Path().apply {
        moveTo(from.x * SCALE, from.y * SCALE)
        lineTo(target.x * SCALE, from.y * SCALE)
        lineTo(target.x * SCALE, (target.y + 12f) * SCALE)
      }

  private fun routePaint() =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * SCALE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(7f * SCALE, 7f * SCALE), 0f)
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
