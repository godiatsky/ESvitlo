package ua.serg.svitlo

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Основний віджет: обрана локація велика, інші тонкими рядками під нею. */
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
        if (intent.action == SHOW) {
            Places.setForWidget(context, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0),
                intent.getLongExtra("place", 0))
            render(context)
        }
        if (intent.action == TICK) render(context)
    }

    companion object {
        private const val TICK = "ua.serg.svitlo.TICK"
        private const val SHOW = "ua.serg.svitlo.SHOW"

        fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SvitloWidget::class.java))
            val allIds = manager.getAppWidgetIds(ComponentName(context, AllWidget::class.java))
            Status.update(context)
            val places = Places.all(context)
            for (id in allIds) AllWidget.draw(context, manager, id)
            for (id in ids) {
                val primary = Places.forWidget(context, id)
                val ordered = listOfNotNull(primary) + places.filter { it.id != primary?.id }
                AllWidget.draw(context, manager, id, ordered) { place ->
                    PendingIntent.getBroadcast(context, 0,
                        Intent(context, SvitloWidget::class.java).setAction(SHOW)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("place", place)
                            .setData(Uri.parse("svitlo://widget/$id/$place")),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                }
            }
            // Наступне перемальовування: найближча зміна стану серед усіх локацій
            val next = places.mapNotNull { Schedule.load(context, it).nextChange }.minOrNull()
                ?: Schedule.load(context, null).nextChange
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
