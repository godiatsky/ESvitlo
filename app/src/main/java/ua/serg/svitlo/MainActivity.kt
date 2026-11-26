package ua.serg.svitlo

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("svitlo", MODE_PRIVATE) }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { show() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Refresh.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        show()
        SvitloWidget.render(this)
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
        column.addView(card(headline, text(s.detail, 16f, R.color.sub)))
        column.addView(card(text("Сьогодні", 17f, bold = true), bar(s.today, s.now),
            text(Schedule.summary(s.today), 15f, R.color.sub)))
        column.addView(card(text("Завтра", 17f, bold = true), bar(s.tomorrow, null),
            text(Schedule.summary(s.tomorrow), 15f, R.color.sub)))

        val queue = card(text("Черга ${s.group}   ›", 17f, bold = true),
            text("Перевірте номер своєї черги на сайті Yasno", 13f, R.color.sub))
        queue.setOnClickListener { pick(s.group) }
        column.addView(queue)

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

    private fun pick(current: String) {
        val list = Schedule.groups(this)
        if (list.isEmpty()) {
            Toast.makeText(this, "Спочатку потрібно завантажити графік", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Ваша черга")
            .setSingleChoiceItems(list.toTypedArray(), list.indexOf(current)) { dialog, i ->
                prefs.edit().putString("group", list[i]).apply()
                SvitloWidget.render(this)
                dialog.dismiss()
            }.show()
    }
}
