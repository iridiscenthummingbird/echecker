package ua.echecker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import ua.echecker.domain.Interval

/**
 * Glance has no Canvas, so the widget's 24h timeline (bar + hour labels) is drawn into a bitmap.
 * Mirrors the app's hero timeline: translucent "on" track, solid outage segments, a "now" pin.
 */
object TimelineBitmap {
    data class Colors(val track: Int, val off: Int, val marker: Int, val label: Int)

    fun render(
        intervals: List<Interval>,
        nowMinute: Int,
        widthPx: Int,
        density: Float,
        colors: Colors,
    ): Bitmap {
        val barTop = 4 * density
        val barH = 9 * density
        val labelSize = 10 * density
        val height = (barTop + barH + 4 * density + labelSize + 2 * density).toInt()
        val w = widthPx.coerceAtLeast(1).toFloat()
        val bmp = Bitmap.createBitmap(w.toInt(), height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = colors.track
        c.drawRoundRect(RectF(0f, barTop, w, barTop + barH), barH / 2, barH / 2, p)

        p.color = colors.off
        intervals.forEach { i ->
            val x0 = w * i.startMinute / 1440f
            val x1 = maxOf(w * i.endMinute / 1440f, x0 + 2f)
            c.drawRoundRect(RectF(x0, barTop, x1, barTop + barH), barH / 3, barH / 3, p)
        }

        val x = (w * nowMinute / 1440f).coerceIn(2 * density, w - 2 * density)
        p.color = colors.marker
        p.strokeWidth = 2.5f * density
        c.drawLine(x, 2 * density, x, barTop + barH + density, p)
        c.drawCircle(x, 3 * density, 3.5f * density, p)

        p.color = colors.label
        p.textSize = labelSize
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val baseline = barTop + barH + 4 * density + labelSize
        listOf("00", "06", "12", "18", "24").forEachIndexed { idx, label ->
            val lx = w * idx / 4f
            p.textAlign = when (idx) {
                0 -> Paint.Align.LEFT
                4 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            c.drawText(label, lx, baseline, p)
        }
        return bmp
    }
}
