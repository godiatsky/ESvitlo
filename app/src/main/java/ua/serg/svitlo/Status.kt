package ua.serg.svitlo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

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
        val lines = places.map { "${it.name}: ${Schedule.load(c, it).headline.replaceFirstChar { ch -> ch.lowercase() }}" }
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val style = Notification.InboxStyle()
        lines.drop(1).forEach { style.addLine(it) }
        val builder = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.bolt)
            .setContentTitle(lines.first())
            .setContentText(lines.drop(1).joinToString(" · "))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (lines.size > 1) builder.setStyle(style)
        manager.notify(ID, builder.build())
    }
}
