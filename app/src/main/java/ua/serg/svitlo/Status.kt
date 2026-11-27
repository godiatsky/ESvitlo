package ua.serg.svitlo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Постійне тихе сповіщення зі станом локацій, видиме на екрані блокування. */
object Status {
    private const val CHANNEL = "status"
    private const val ID = 7

    fun enabled(c: Context) = c.getSharedPreferences("svitlo", Context.MODE_PRIVATE).getBoolean("status", true)

    fun update(c: Context) {
        val manager = c.getSystemService(NotificationManager::class.java)
        val places = Places.all(c)
        if (!enabled(c) || places.isEmpty()) {
            manager.cancel(ID)
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Стан світла", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        val states = places.map { Schedule.load(c, it) }
        val lines = places.zip(states).map { (p, st) -> "${p.name}: ${st.headline.replaceFirstChar { ch -> ch.lowercase() }}" }
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        // Розгорнутий вигляд: ті самі рядки зі шкалами, що й у віджеті
        val rows = RemoteViews(c.packageName, R.layout.notif_rows)
        places.zip(states).forEach { (p, st) -> rows.addView(R.id.rows, AllWidget.compact(c, p, st, 320, false)) }
        val builder = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.bolt)
            .setContentTitle(lines.first())
            .setContentText(if (lines.size > 1) lines.drop(1).joinToString(" · ") else states.first().detail)
            .setSubText(states.first().footer)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomBigContentView(rows)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        manager.notify(ID, builder.build())
    }
}
