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
        if (intent.action == TICK) render(context)
    }

    companion object {
        private const val TICK = "ua.serg.svitlo.TICK"

        fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SvitloWidget::class.java))
            if (ids.isEmpty()) return
            val s = Schedule.load(context)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE)
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            for (id in ids) {
                val o = manager.getAppWidgetOptions(id)
                val width = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                    else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
                val height = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                    else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 130)
                val v = RemoteViews(context.packageName, R.layout.widget)
                v.setTextColor(R.id.dot, Timeline.color(context, s.power))
                v.setTextViewText(R.id.status, s.headline)
                v.setTextViewText(R.id.group, if (s.group.isEmpty()) "" else "Черга ${s.group}")
                v.setTextViewText(R.id.until, s.detail)
                v.setImageViewBitmap(R.id.bar, Timeline.draw(context, s.today, s.now, width - 32, (height - 96).coerceIn(40, 110)))
                val tomorrow = if (s.tomorrow?.known == true) "Завтра без світла: " +
                    Schedule.summary(s.tomorrow) else null
                v.setTextViewText(R.id.footer, if (s.stale || tomorrow == null || height >= 150)
                    listOfNotNull(tomorrow, s.footer).joinToString("\n") else "$tomorrow · ${s.footer}")
                v.setViewVisibility(R.id.until, if (height >= 150 || s.power == Power.UNKNOWN) View.VISIBLE else View.GONE)
                v.setOnClickPendingIntent(R.id.root, open)
                manager.updateAppWidget(id, v)
            }
            s.nextChange?.let {
                val tick = PendingIntent.getBroadcast(context, 1,
                    Intent(context, SvitloWidget::class.java).setAction(TICK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                context.getSystemService(AlarmManager::class.java)
                    .setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + it * 60_000L, tick)
            }
        }
    }
}
