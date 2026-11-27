package ua.serg.svitlo

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

class SvitloWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context)
        Refresh.schedule(context)
        Refresh.now(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        render(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == NEXT) {
            Places.next(context, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
            render(context)
        }
        if (intent.action == TICK) render(context)
    }

    companion object {
        private const val TICK = "ua.serg.svitlo.TICK"
        private const val NEXT = "ua.serg.svitlo.NEXT"

        fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SvitloWidget::class.java))
            val allIds = manager.getAppWidgetIds(ComponentName(context, AllWidget::class.java))
            if (ids.isEmpty() && allIds.isEmpty()) return
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            var next: Int? = if (allIds.isEmpty()) null else
                Places.all(context).mapNotNull { Schedule.load(context, it).nextChange }.minOrNull()
            for (id in allIds) AllWidget.draw(context, manager, id)
            for (id in ids) {
                val place = Places.forWidget(context, id)
                val s = Schedule.load(context, place)
                next = listOfNotNull(next, s.nextChange).minOrNull()
                val open = PendingIntent.getActivity(context, id,
                    Intent(context, MainActivity::class.java).putExtra("place", place?.id ?: 0L),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val switch = PendingIntent.getBroadcast(context, id,
                    Intent(context, SvitloWidget::class.java).setAction(NEXT)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val o = manager.getAppWidgetOptions(id)
                val width = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                    else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
                val height = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                    else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 130)
                val v = RemoteViews(context.packageName, R.layout.widget)
                v.setTextColor(R.id.dot, Timeline.color(context, s.power))
                v.setTextViewText(R.id.status, s.headline)
                v.setTextViewText(R.id.icon, place?.icon ?: "➕")
                v.setTextViewText(R.id.group, if (place == null) "" else
                    "${place.name} · черга ${place.group}" + if (Places.all(context).size > 1) "  ⇄" else "")
                v.setOnClickPendingIntent(R.id.icon, if (Places.all(context).size > 1) switch else open)
                v.setTextViewText(R.id.until, s.detail)
                v.setImageViewBitmap(R.id.bar, Timeline.draw(context, s.today, s.now, width - 32, (height - 104).coerceIn(40, 110)))
                val tomorrow = if (s.tomorrow?.known == true) "Завтра без світла: " +
                    Schedule.summary(s.tomorrow) else null
                v.setTextViewText(R.id.footer, if (s.stale || tomorrow == null || height >= 150)
                    listOfNotNull(tomorrow, s.footer).joinToString("\n") else "$tomorrow · ${s.footer}")
                v.setViewVisibility(R.id.until, if (height >= 170 || s.power == Power.UNKNOWN) View.VISIBLE else View.GONE)
                v.setOnClickPendingIntent(R.id.root, open)
                manager.updateAppWidget(id, v)
            }
            next?.let {
                val tick = PendingIntent.getBroadcast(context, 1,
                    Intent(context, SvitloWidget::class.java).setAction(TICK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                context.getSystemService(AlarmManager::class.java)
                    .setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + it * 60_000L, tick)
            }
        }
    }
}
