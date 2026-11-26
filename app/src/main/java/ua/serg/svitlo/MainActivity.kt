package ua.serg.svitlo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("svitlo", MODE_PRIVATE) }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { show() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Refresh.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        if (prefs.getString("group", "").isNullOrEmpty()) AddressSearch(this) { changed() }.start()
    }

    private fun changed() {
        SvitloWidget.render(this)
        Alerts.check(this)
        show()
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        show()
        SvitloWidget.render(this)
        Alerts.check(this)
        Refresh.now(this)
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onPause()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float, color: Int = R.color.text, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(getColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun card(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.card)
        setPadding(dp(20), dp(18), dp(20), dp(18))
        views.forEach { addView(it) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    private fun show() {
        val s = Schedule.load(this)
        val width = (resources.configuration.screenWidthDp - 80).coerceAtMost(480)
        fun bar(day: Day?, now: Int?) = ImageView(this).apply {
            setImageBitmap(Timeline.draw(this@MainActivity, day, now, width))
            adjustViewBounds = true
            setPadding(0, dp(10), 0, dp(6))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        column.addView(text("Світло", 34f, bold = true))

        val headline = text(s.headline, 28f, bold = true).apply {
            setTextColor(if (s.power == Power.UNKNOWN) getColor(R.color.text) else Timeline.color(context, s.power))
        }
        column.addView(card(headline, text(s.detail, 15f, R.color.sub)))
        column.addView(card(text("Сьогодні", 17f, bold = true), bar(s.today, s.now),
            text(Schedule.summary(s.today), 15f, R.color.sub)))
        column.addView(card(text("Завтра", 17f, bold = true), bar(s.tomorrow, null),
            text(Schedule.summary(s.tomorrow), 15f, R.color.sub)))

        val address = card(text(prefs.getString("address", null) ?: "Вказати адресу", 17f, bold = true),
            text(if (s.group.isEmpty()) "Чергу не обрано" else "Черга ${s.group}  ·  змінити ›", 14f, R.color.sub))
        address.setOnClickListener { AddressSearch(this) { changed() }.start() }
        column.addView(address)
        val manual = card(text("Обрати чергу вручну  ›", 15f, R.color.accent))
        manual.setOnClickListener { pick(s.group) }
        column.addView(manual)

        column.addView(alertRow("Сигнал перед відключенням", "leadOff"))
        column.addView(alertRow("Сигнал перед увімкненням", "leadOn"))
        val test = card(text("Перевірити сигнал", 15f, R.color.accent))
        test.setOnClickListener { testSignal() }
        column.addView(test)

        val refresh = card(text("Оновити", 17f, R.color.accent, true))
        refresh.setOnClickListener {
            Refresh.now(this)
            Toast.makeText(this, "Оновлюємо…", Toast.LENGTH_SHORT).show()
        }
        column.addView(refresh)
        column.addView(text(s.footer, 12f, R.color.sub).apply { setPadding(dp(4), dp(12), 0, 0) })

        val old = (findViewById<View>(android.R.id.content) as? android.view.ViewGroup)?.getChildAt(0)
        val y = old?.scrollY ?: 0
        val scroll = ScrollView(this).apply { fitsSystemWindows = true; addView(column) }
        setContentView(scroll)
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun alertRow(title: String, key: String): View {
        val values = intArrayOf(0, 5, 10, 15, 30, 60)
        val names = values.map { if (it == 0) "Вимкнено" else "За $it хв" }.toTypedArray()
        val current = values.indexOf(prefs.getInt(key, 0)).coerceAtLeast(0)
        val row = card(text(title, 17f, bold = true), text("${names[current]}  ›", 14f, R.color.sub))
        row.setOnClickListener {
            AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(names, current) { dialog, i ->
                prefs.edit().putInt(key, values[i]).apply()
                dialog.dismiss()
                changed()
            }.show()
        }
        return row
    }

    /** Показує тестовий сигнал або пояснює, чому звуку не буде, і відкриває потрібні налаштування. */
    private fun testSignal() {
        Alerts.channel(this)
        val manager = getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(Alerts.CHANNEL)
        val audio = getSystemService(AudioManager::class.java)
        val app = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        val sound = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Alerts.CHANNEL)
        when {
            !manager.areNotificationsEnabled() ->
                problem("Сповіщення для «Світло» вимкнені. Увімкніть їх.", app)
            channel.importance < NotificationManager.IMPORTANCE_DEFAULT || channel.sound == null ->
                problem("Канал сигналів беззвучний. Увімкніть звук.", sound)
            audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ->
                problem("Телефон у беззвучному режимі або на вібрації. Увімкніть звук.", Intent(Settings.ACTION_SOUND_SETTINGS))
            audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0 ->
                problem("Гучність сповіщень на нулі.", Intent(Settings.ACTION_SOUND_SETTINGS))
            manager.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL ->
                problem("Увімкнено режим «Не турбувати».", Intent(Settings.ACTION_SOUND_SETTINGS))
            else -> {
                manager.cancel(99)
                Alerts.notify(this, 99, "Перевірка сигналу", "Так звучатиме попередження")
                problem("Тестовий сигнал надіслано. Якщо звуку не було, перевірте налаштування.", sound)
            }
        }
    }

    private fun problem(message: String, settings: Intent) {
        AlertDialog.Builder(this).setMessage(message)
            .setPositiveButton("Налаштування") { _, _ -> startActivity(settings) }
            .setNegativeButton("Закрити", null).show()
    }

    private fun pick(current: String) {
        val list = Schedule.groups(this)
        if (list.isEmpty()) {
            Toast.makeText(this, "Спочатку потрібно завантажити графік", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Ваша черга")
            .setSingleChoiceItems(list.toTypedArray(), list.indexOf(current)) { dialog, i ->
                prefs.edit().putString("group", list[i]).remove("address").apply()
                changed()
                dialog.dismiss()
            }.show()
    }
}
