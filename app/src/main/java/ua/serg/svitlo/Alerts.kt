package ua.serg.svitlo

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.*

/** Сигнали за N хвилин до відключення та увімкнення. */
object Alerts {
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Refresh.schedule(context)
            check(context)
            SvitloWidget.render(context)
        }
    }

    @Synchronized
    fun check(c: Context) {
        val prefs = c.getSharedPreferences("svitlo", Context.MODE_PRIVATE)
        val alarms = c.getSystemService(AlarmManager::class.java)
        val s = Schedule.load(c)
        fun at(m: Int) = if (m < 1440) s.today?.at(m) ?: Power.UNKNOWN
            else s.tomorrow?.at(m - 1440) ?: Power.UNKNOWN

        for ((id, target, key) in listOf(Triple(1, Power.OFF, "leadOff"), Triple(2, Power.ON, "leadOn"))) {
            val pending = PendingIntent.getBroadcast(c, id, Intent(c, Receiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            alarms.cancel(pending)
            val lead = prefs.getInt(key, 0)
            if (lead == 0 || s.group.isEmpty()) continue
            for (m in s.now + 1 until 2880) {
                val before = at(m - 1)
                if (at(m) != target || before == target || before == Power.UNKNOWN) continue
                val event = Calendar.getInstance(TimeZone.getTimeZone("Europe/Kiev")).apply {
                    add(Calendar.DATE, m / 1440)
                    set(Calendar.HOUR_OF_DAY, m % 1440 / 60)
                    set(Calendar.MINUTE, m % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val trigger = event - lead * 60_000L
                if (trigger > System.currentTimeMillis()) {
                    if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())
                        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                    else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                    break
                }
                // Час сигналу вже настав: показуємо один раз і шукаємо наступну подію
                val mark = "${s.group}:$event"
                if (prefs.getString("sent$id", "") != mark) {
                    prefs.edit().putString("sent$id", mark).apply()
                    val action = if (target == Power.OFF) "вимкнуть" else "увімкнуть"
                    notify(c, id, "О ${Schedule.time(m % 1440)} $action світло",
                        "Залишилось ${Schedule.duration(maxOf(1, m - s.now))} · черга ${s.group}")
                }
            }
        }
    }

    const val CHANNEL = "alerts"

    fun channel(c: Context) = c.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL, "Сигнали про світло", NotificationManager.IMPORTANCE_HIGH))

    fun notify(c: Context, id: Int, title: String, body: String) {
        val manager = c.getSystemService(NotificationManager::class.java)
        channel(c)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.bolt).setContentTitle(title).setContentText(body)
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
