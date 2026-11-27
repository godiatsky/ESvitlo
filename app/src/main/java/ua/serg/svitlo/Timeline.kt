package ua.serg.svitlo

import android.content.Context
import android.graphics.*

object Timeline {
    fun color(c: Context, p: Power) = c.getColor(when (p) {
        Power.ON -> R.color.on
        Power.OFF -> R.color.off
        Power.UNKNOWN -> R.color.unknown
    })

    /** Шкала доби: штрихування = відключення, минуле приглушене, підписані моменти перемикань. */
    fun draw(c: Context, day: Day?, now: Int?, widthDp: Int, heightDp: Int = 44, labels: Boolean = true): Bitmap {
        val d = c.resources.displayMetrics.density.coerceAtMost(3f)
        val w = widthDp.coerceIn(180, 480) * d
        val h = heightDp * d
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = if (labels) 3 * d else 0f
        val bottom = if (labels) h - 19 * d else h
        val radius = minOf(8 * d, (bottom - top) / 2)
        fun x(m: Int) = w * m / 1440
        val clip = Path().apply { addRoundRect(0f, top, w, bottom, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        p.color = color(c, Power.UNKNOWN)
        canvas.drawRect(0f, top, w, bottom, p)
        if (day != null && day.known) for (s in day.slots) {
            p.color = color(c, s.power)
            canvas.drawRect(x(s.start), top, x(s.end), bottom, p)
            if (s.power == Power.OFF) {
                canvas.save()
                canvas.clipRect(x(s.start), top, x(s.end), bottom)
                p.color = 0x55000000
                p.strokeWidth = 2 * d
                var l = x(s.start) - (bottom - top)
                while (l < x(s.end)) {
                    canvas.drawLine(l, bottom, l + bottom - top, top, p)
                    l += 7 * d
                }
                canvas.restore()
            }
        }
        if (now != null) {
            p.color = (c.getColor(R.color.card) and 0x00FFFFFF) or (0x99 shl 24)
            canvas.drawRect(0f, top, x(now), bottom, p)
        }
        canvas.restore()
        if (now != null) {
            val m = x(now).coerceIn(2 * d, w - 2 * d)
            p.color = c.getColor(R.color.text)
            canvas.drawRoundRect(m - 1.5f * d, 0f, m + 1.5f * d, minOf(h, bottom + 3 * d), 1.5f * d, 1.5f * d, p)
        }
        if (!labels) return bmp
        p.color = c.getColor(R.color.text)
        p.textSize = 11 * d
        val changes = if (day != null && day.known)
            day.slots.zipWithNext().filter { it.first.power != it.second.power }.map { it.second.start }
        else emptyList()
        val labels = if (changes.isEmpty()) listOf(0, 360, 720, 1080, 1440) else changes
        var used = -100f
        for (m in labels) {
            val t = Schedule.time(m)
            val tw = p.measureText(t)
            val left = (x(m) - tw / 2).coerceIn(0f, w - tw)
            if (left < used + 4 * d) continue
            canvas.drawText(t, left, h - 4 * d, p)
            used = left + tw
        }
        return bmp
    }
}
