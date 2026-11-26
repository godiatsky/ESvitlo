package ua.serg.svitlo

import android.content.Context
import android.graphics.*

object Timeline {
    fun color(c: Context, p: Power) = c.getColor(when (p) {
        Power.ON -> R.color.on
        Power.OFF -> R.color.off
        Power.UNKNOWN -> R.color.unknown
    })

    /** Малює шкалу доби в точному розмірі, щоб цифри не розтягувались. */
    fun draw(c: Context, day: Day?, now: Int?, widthDp: Int): Bitmap {
        val d = c.resources.displayMetrics.density.coerceAtMost(3f)
        val w = widthDp.coerceIn(180, 480) * d
        val bmp = Bitmap.createBitmap(w.toInt(), (36 * d).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = 4 * d
        val bottom = 18 * d
        val clip = Path().apply { addRoundRect(0f, top, w, bottom, 7 * d, 7 * d, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        p.color = color(c, Power.UNKNOWN)
        canvas.drawRect(0f, top, w, bottom, p)
        if (day != null && day.known) for (s in day.slots) {
            p.color = color(c, s.power)
            canvas.drawRect(w * s.start / 1440, top, w * s.end / 1440, bottom, p)
        }
        canvas.restore()
        if (now != null) {
            val x = (w * now / 1440).coerceIn(3 * d, w - 3 * d)
            p.color = c.getColor(R.color.card)
            canvas.drawRoundRect(x - 3 * d, 0f, x + 3 * d, bottom + 4 * d, 3 * d, 3 * d, p)
            p.color = c.getColor(R.color.text)
            canvas.drawRoundRect(x - 1.5f * d, 1.5f * d, x + 1.5f * d, bottom + 2.5f * d, 1.5f * d, 1.5f * d, p)
        }
        p.color = c.getColor(R.color.sub)
        p.textSize = 10 * d
        for (h in 0..24 step 6) {
            p.textAlign = if (h == 0) Paint.Align.LEFT else if (h == 24) Paint.Align.RIGHT else Paint.Align.CENTER
            canvas.drawText("%02d".format(h), w * h / 24, 33 * d, p)
        }
        return bmp
    }
}
