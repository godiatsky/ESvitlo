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
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("svitlo", MODE_PRIVATE) }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { show() } }

    private var selected = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Refresh.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        selected = savedInstanceState?.getLong("place") ?: intent.getLongExtra("place", 0L)
        if (Places.all(this).isEmpty()) add()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("place", selected)
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
        val places = Places.all(this)
        val all = selected == -1L && places.size > 1
        val place = if (all) null else places.firstOrNull { it.id == selected } ?: places.firstOrNull()
        selected = if (all) -1L else place?.id ?: 0L
        val s = Schedule.load(this, place ?: places.firstOrNull())
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
        column.addView(chips(places))
        if (all) {
            for (p in places) {
                val state = Schedule.load(this, p)
                val status = text(state.headline, 15f, R.color.sub, true)
                if (state.power != Power.UNKNOWN) status.setTextColor(Timeline.color(this, state.power))
                val item = card(text("${p.icon}  ${p.name}", 17f, bold = true), status, bar(state.today, state.now))
                item.setOnClickListener { selected = p.id; show() }
                column.addView(item)
            }
            column.addView(text(s.footer, 12f, R.color.sub).apply { setPadding(dp(4), dp(12), 0, 0) })
            display(column)
            return
        }

        val headline = text(s.headline, 28f, bold = true).apply {
            setTextColor(if (s.power == Power.UNKNOWN) getColor(R.color.text) else Timeline.color(context, s.power))
        }
        column.addView(card(headline, text(s.detail, 15f, R.color.sub)))
        column.addView(card(text("Сьогодні", 17f, bold = true), bar(s.today, s.now),
            text(Schedule.summary(s.today), 15f, R.color.sub)))
        column.addView(card(text("Завтра", 17f, bold = true), bar(s.tomorrow, null),
            text(Schedule.summary(s.tomorrow), 15f, R.color.sub)))

        if (place != null) {
            val edit = card(text("${place.icon}  ${place.name}", 17f, bold = true),
                text("${place.address.ifEmpty { "Адресу не вказано" }}\nЧерга ${place.group}  ·  змінити ›", 14f, R.color.sub))
            edit.setOnClickListener { menu(place) }
            column.addView(edit)
            val signals = Switch(this).apply {
                text = "Сигнали для цієї локації"
                textSize = 16f
                setTextColor(getColor(R.color.text))
                isChecked = place.alerts
                setOnCheckedChangeListener { _, on ->
                    if (!on) Alerts.cancel(this@MainActivity, place.id)
                    Places.save(this@MainActivity, place.copy(alerts = on))
                    changed()
                }
            }
            column.addView(card(signals))
        }

        column.addView(alertRow("Сигнал перед відключенням", "leadOff"))
        column.addView(alertRow("Сигнал перед увімкненням", "leadOn"))
        val mode = Alerts.mode(this)
        val kind = card(text("Тип сигналу", 17f, bold = true), text("${Alerts.MODES[mode]}  ›", 14f, R.color.sub))
        kind.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Тип сигналу").setSingleChoiceItems(Alerts.MODES, mode) { dialog, i ->
                prefs.edit().putInt("signal", i).apply()
                dialog.dismiss()
            }.show()
        }
        column.addView(kind)
        for ((title, off) in listOf("Перевірити: відключення" to true, "Перевірити: увімкнення" to false)) {
            val test = card(text(title, 15f, R.color.accent))
            test.setOnClickListener { testSignal(off) }
            column.addView(test)
        }

        val refresh = card(text("Оновити", 17f, R.color.accent, true))
        refresh.setOnClickListener {
            Refresh.now(this)
            Toast.makeText(this, "Оновлюємо…", Toast.LENGTH_SHORT).show()
        }
        column.addView(refresh)
        column.addView(text(s.footer, 12f, R.color.sub).apply { setPadding(dp(4), dp(12), 0, 0) })

        display(column)
    }

    private fun display(column: View) {
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
    private fun testSignal(off: Boolean) {
        val id = Alerts.channel(this, off)
        val mode = Alerts.mode(this)
        val manager = getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(id)
        val audio = getSystemService(AudioManager::class.java)
        val app = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        val sound = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, id)
        when {
            !manager.areNotificationsEnabled() ->
                problem("Сповіщення для «Світло» вимкнені. Увімкніть їх.", app)
            channel.importance < NotificationManager.IMPORTANCE_DEFAULT ||
                (mode != 1 && channel.sound == null) || (mode != 0 && !channel.shouldVibrate()) ->
                problem("У каналі сигналів вимкнено звук або вібрацію.", sound)
            mode == 0 && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ->
                problem("Телефон у беззвучному режимі. Увімкніть звук або оберіть вібрацію.", Intent(Settings.ACTION_SOUND_SETTINGS))
            audio.ringerMode == AudioManager.RINGER_MODE_SILENT ->
                problem("Телефон у режимі «Без звуку»: увімкніть звук або вібрацію.", Intent(Settings.ACTION_SOUND_SETTINGS))
            mode == 0 && audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0 ->
                problem("Гучність сповіщень на нулі.", Intent(Settings.ACTION_SOUND_SETTINGS))
            manager.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL ->
                problem("Увімкнено режим «Не турбувати».", Intent(Settings.ACTION_SOUND_SETTINGS))
            else -> {
                manager.cancel(99)
                Alerts.notify(this, 99, "Перевірка сигналу", Alerts.MODES[mode], off)
                problem("Тестовий сигнал надіслано. Якщо його не було, перевірте налаштування.", sound)
            }
        }
    }

    private fun problem(message: String, settings: Intent) {
        AlertDialog.Builder(this).setMessage(message)
            .setPositiveButton("Налаштування") { _, _ -> startActivity(settings) }
            .setNegativeButton("Закрити", null).show()
    }

    private fun chips(places: List<Place>): View {
        val row = LinearLayout(this)
        fun chip(icon: String, label: String, active: Boolean, click: () -> Unit) {
            val view = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setOnClickListener { click() }
            }
            view.addView(text(icon, 28f).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(getColor(R.color.card))
                    if (active) setStroke(dp(3), getColor(R.color.accent))
                }
            }, LinearLayout.LayoutParams(dp(64), dp(64)))
            view.addView(text(label, 12f, if (active) R.color.text else R.color.sub, active).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(dp(76), -2))
            row.addView(view)
        }
        if (places.size > 1) chip("🗂️", "Всі", selected == -1L) { selected = -1L; show() }
        places.forEach { p -> chip(p.icon, p.name, p.id == selected) { selected = p.id; show() } }
        chip("➕", "Додати", false) { add() }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(12), 0, 0)
            addView(row)
        }
    }

    /** Нова локація: іконка та назва → адреса або черга. */
    private fun add() = look(null) { icon, name ->
        source { group, address ->
            val place = Place(Places.newId(this), name, icon, group, address, true)
            selected = place.id
            Places.save(this, place)
            changed()
        }
    }

    private fun menu(place: Place) {
        val items = arrayOf("Іконка та назва", "Адреса або черга", "Видалити локацію")
        AlertDialog.Builder(this).setTitle("${place.icon} ${place.name}").setItems(items) { _, i ->
            when (i) {
                0 -> look(place) { icon, name -> Places.save(this, place.copy(icon = icon, name = name)); changed() }
                1 -> source { group, address ->
                    Alerts.cancel(this, place.id)
                    Places.save(this, place.copy(group = group, address = address))
                    changed()
                }
                else -> AlertDialog.Builder(this).setMessage("Видалити «${place.name}»?")
                    .setPositiveButton("Видалити") { _, _ ->
                        Alerts.cancel(this, place.id)
                        Places.delete(this, place.id)
                        changed()
                    }.setNegativeButton("Скасувати", null).show()
            }
        }.show()
    }

    private fun look(place: Place?, done: (icon: String, name: String) -> Unit) {
        var icon = place?.icon ?: Places.ICONS[0].first
        val name = EditText(this).apply {
            hint = "Назва"
            isSingleLine = true
            setText(place?.name ?: Places.ICONS[0].second)
        }
        val grid = GridLayout(this).apply { columnCount = 5 }
        val cells = ArrayList<TextView>()
        fun mark() = cells.forEach {
            it.background = if (it.text.toString() != icon) null else GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(3), getColor(R.color.accent))
            }
        }
        for ((emoji, label) in Places.ICONS) {
            val cell = text(emoji, 28f).apply {
                gravity = Gravity.CENTER
                contentDescription = label
                layoutParams = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply { width = 0; height = dp(56) }
            }
            cell.setOnClickListener {
                // Назву, яку користувач ввів сам, не перезаписуємо
                val typed = name.text.toString()
                if (typed.isBlank() || Places.ICONS.any { it.second == typed }) name.setText(label)
                icon = emoji
                mark()
            }
            cells.add(cell)
            grid.addView(cell)
        }
        mark()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(grid)
            addView(name)
        }
        AlertDialog.Builder(this).setTitle(if (place == null) "Нова локація" else "Іконка та назва")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Далі") { _, _ ->
                val typed = name.text.toString().trim()
                done(icon, typed.ifEmpty { Places.ICONS.first { it.first == icon }.second })
            }.setNegativeButton("Скасувати", null).show()
    }

    private fun source(done: (group: String, address: String) -> Unit) {
        AlertDialog.Builder(this).setTitle("Як визначити чергу?")
            .setItems(arrayOf("Пошук за адресою", "Обрати чергу вручну")) { _, i ->
                if (i == 0) AddressSearch(this, done).start() else pick { done(it, "") }
            }.show()
    }

    private fun pick(done: (String) -> Unit) {
        val list = Schedule.groups(this)
        if (list.isEmpty()) {
            Refresh.now(this)
            Toast.makeText(this, "Графік ще завантажується, спробуйте за мить", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Ваша черга")
            .setItems(list.toTypedArray()) { _, i -> done(list[i]) }.show()
    }
}
