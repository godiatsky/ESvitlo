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

    companion object {
        private fun open(c: Context, place: Long) = PendingIntent.getActivity(c, 0,
            Intent(c, MainActivity::class.java).putExtra("place", place)
                .setData(android.net.Uri.parse("svitlo://place/$place")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun draw(c: Context, manager: AppWidgetManager, id: Int) {
            val o = manager.getAppWidgetOptions(id)
            val landscape = c.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val width = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
            val height = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 220)
            val places = Places.all(c)
            val fit = ((height - 16) / 58).coerceAtLeast(1)
            val shown = if (places.size <= fit) places.size else ((height - 36) / 58).coerceAtLeast(1)

            val v = RemoteViews(c.packageName, R.layout.widget_all)
            v.removeAllViews(R.id.rows)
            var stale = false
            for (place in places.take(shown)) {
                val s = Schedule.load(c, place)
                stale = stale || s.stale
                val row = RemoteViews(c.packageName, R.layout.widget_row)
                row.setTextViewText(R.id.icon, place.icon)
                row.setTextViewText(R.id.name, place.name)
                row.setTextViewText(R.id.status, s.headline)
                row.setTextColor(R.id.status, c.getColor(when (s.power) {
                    Power.ON -> R.color.on
                    Power.OFF -> R.color.off
                    Power.UNKNOWN -> R.color.sub
                }))
                row.setImageViewBitmap(R.id.bar, Timeline.draw(c, s.today, s.now, width - 72, 34))
                row.setOnClickPendingIntent(R.id.row, open(c, place.id))
                v.addView(R.id.rows, row)
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
