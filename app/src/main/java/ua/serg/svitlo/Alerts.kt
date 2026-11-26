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

    val MODES = arrayOf("Звук", "Особлива вібрація", "Звук і вібрація")

    // Пари «пауза, вібрація» в мс. Відключення: імпульси згасають. Увімкнення: наростають.
    private val OFF_PATTERN = longArrayOf(0, 700, 150, 400, 150, 200, 150, 80)
    private val ON_PATTERN = longArrayOf(0, 80, 150, 200, 150, 400, 150, 700)

    fun mode(c: Context) = c.getSharedPreferences("svitlo", Context.MODE_PRIVATE).getInt("signal", 0)

    /** Android фіксує звук і вібрацію за каналом, тому кожен варіант має власний канал. */
    fun channel(c: Context, off: Boolean): String {
        val mode = mode(c)
        val id = if (mode == 0) "alerts" else "alerts_${mode}_${if (off) "off" else "on"}"
        val name = if (mode == 0) "Сигнали про світло" else
            "${MODES[mode]}: ${if (off) "відключення" else "увімкнення"}"
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
        if (mode != 0) {
            channel.enableVibration(true)
            channel.vibrationPattern = if (off) OFF_PATTERN else ON_PATTERN
            if (mode == 1) channel.setSound(null, null)
        }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return id
    }

    fun notify(c: Context, id: Int, title: String, body: String, off: Boolean = id != 2) {
        val manager = c.getSystemService(NotificationManager::class.java)
        val channel = channel(c, off)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, Notification.Builder(c, channel)
            .setSmallIcon(R.drawable.bolt).setContentTitle(title).setContentText(body)
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
