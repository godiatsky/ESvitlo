package ua.serg.svitlo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

/** Віджет з усіма локаціями одразу. */
class AllWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        SvitloWidget.render(context)
        Refresh.schedule(context)
        Refresh.now(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        SvitloWidget.render(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == PROMOTE) {
            Places.promote(context, intent.getLongExtra("place", 0))
            SvitloWidget.render(context)
        }
    }

    companion object {
        private const val PROMOTE = "ua.serg.svitlo.PROMOTE"

        private fun open(c: Context, place: Long) = PendingIntent.getActivity(c, 0,
            Intent(c, MainActivity::class.java).putExtra("place", place)
                .setData(android.net.Uri.parse("svitlo://place/$place")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun promote(c: Context, place: Long) = PendingIntent.getBroadcast(c, 0,
            Intent(c, AllWidget::class.java).setAction(PROMOTE).putExtra("place", place)
                .setData(android.net.Uri.parse("svitlo://promote/$place")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun color(c: Context, p: Power) = c.getColor(when (p) {
            Power.ON -> R.color.on
            Power.OFF -> R.color.off
            Power.UNKNOWN -> R.color.sub
        })

        fun draw(c: Context, manager: AppWidgetManager, id: Int) =
            draw(c, manager, id, Places.all(c)) { promote(c, it) }

        /** Перша локація показується великою, решта тонкими рядками. */
        fun draw(c: Context, manager: AppWidgetManager, id: Int, places: List<Place>, promote: (Long) -> PendingIntent) {
            val o = manager.getAppWidgetOptions(id)
            val landscape = c.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val width = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
            val height = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
            val states = places.map { Schedule.load(c, it) }
            val stale = states.any { it.stale }
            // Повна картка: шапка 42dp + шкала від 30dp. Тонкий рядок 34dp. Примітка 14dp.
            val head = 42; val minFull = head + 30; val row = 34
            val n = places.size
            var avail = height - 16 - (if (stale) 14 else 0)
            val full: Int
            val shown: Int
            when {
                n == 0 -> { full = 0; shown = 0 }
                n * minFull <= avail -> { full = n; shown = n }          // усі повні, рівномірно
                minFull + (n - 1) * row <= avail -> { full = 1; shown = n } // одна повна, решта тонкі
                n * row <= avail -> { full = 0; shown = n }              // усі тонкі
                else -> { avail -= 14; full = 0; shown = (avail / row).coerceIn(1, n) }
            }
            val barHeight = if (full == 0) 30 else ((avail - (shown - full) * row) / full - head).coerceIn(30, 56)

            val v = RemoteViews(c.packageName, R.layout.widget_all)
            v.removeAllViews(R.id.rows)
            places.take(shown).forEachIndexed { i, place ->
                val s = states[i]
                val big = i < full
                val view = RemoteViews(c.packageName, if (big) R.layout.widget_main else R.layout.widget_row)
                view.setTextViewText(R.id.icon, place.icon)
                view.setTextViewText(R.id.name, place.name)
                view.setTextViewText(R.id.status, s.headline)
                view.setTextColor(R.id.status, color(c, s.power))
                if (big) {
                    view.setImageViewBitmap(R.id.bar, Timeline.draw(c, s.today, s.now, width - 28, barHeight))
                    view.setOnClickPendingIntent(R.id.main, open(c, place.id))
                } else {
                    view.setImageViewBitmap(R.id.bar, Timeline.draw(c, s.today, s.now, width - 60, 6, false))
                    // «⇧» має сенс, лише коли є повна картка, на місце якої можна підняти
                    view.setViewVisibility(R.id.hint, if (full > 0) View.VISIBLE else View.GONE)
                    view.setOnClickPendingIntent(R.id.row, if (full > 0) promote(place.id) else open(c, place.id))
                }
                v.addView(R.id.rows, view)
            }
            val note = listOfNotNull(
                if (places.isEmpty()) "Торкніться, щоб додати локацію" else null,
                if (places.size > shown) "Ще ${places.size - shown}: розтягніть віджет" else null,
                if (stale) "⚠ Дані можуть бути застарілими" else null
            ).joinToString(" · ")
            v.setTextViewText(R.id.note, note)
            v.setViewVisibility(R.id.note, if (note.isEmpty()) View.GONE else View.VISIBLE)
            v.setOnClickPendingIntent(R.id.root, open(c, -1))
            manager.updateAppWidget(id, v)
        }
    }
}
