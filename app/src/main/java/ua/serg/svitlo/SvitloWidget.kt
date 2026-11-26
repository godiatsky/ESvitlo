package ua.serg.svitlo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class SvitloWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context)
        Refresh.schedule(context)
        Refresh.now(context)
    }

    companion object {
        private class Slot(val start: Int, val end: Int, val type: String)
        private class Day(val date: String, val status: String, val slots: List<Slot>)

        private fun parse(o: JSONObject?): Day? {
            if (o == null) return null
            val a = o.optJSONArray("slots")
            val slots = (0 until (a?.length() ?: 0)).map {
                val s = a!!.getJSONObject(it)
                Slot(s.getInt("start"), s.getInt("end"), s.getString("type"))
            }
            return Day(o.optString("date").take(10), o.optString("status"), slots)
        }

        private fun time(m: Int) = "%02d:%02d".format(m / 60, m % 60)

        private fun describe(d: Day?): String {
            if (d == null) return "немає даних"
            if (d.status == "WaitingForSchedule") return "графік очікується"
            if (d.status != "ScheduleApplies") return "увага: ${d.status}"
            val off = d.slots.filter { it.type == "Definite" }
            if (off.isEmpty()) return "відключень не заплановано"
            return "без світла " + off.joinToString { "${time(it.start)}–${time(it.end)}" }
        }

        private fun bar(d: Day?, now: Int): Bitmap {
            val bmp = Bitmap.createBitmap(720, 60, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint()
            val known = d?.status == "ScheduleApplies" && d.slots.isNotEmpty()
            p.color = Color.GRAY
            c.drawRect(0f, 0f, 720f, 40f, p)
            if (known) for (s in d!!.slots) {
                p.color = when (s.type) {
                    "NotPlanned" -> Color.rgb(67, 160, 71)
                    "Definite" -> Color.rgb(229, 57, 53)
                    else -> Color.GRAY
                }
                c.drawRect(s.start / 2f, 0f, s.end / 2f, 40f, p)
            }
            p.color = Color.WHITE
            c.drawRect(now / 2f - 2, 0f, now / 2f + 2, 40f, p)
            p.textSize = 18f
            p.textAlign = Paint.Align.CENTER
            for (h in 3..21 step 3) c.drawText("$h", h * 30f, 58f, p)
            return bmp
        }

        fun render(context: Context) {
            val prefs = context.getSharedPreferences("svitlo", Context.MODE_PRIVATE)
            val group = prefs.getString("group", "1.1")!!
            val data = try { JSONObject(prefs.getString("json", "{}")!!).optJSONObject(group) } catch (e: Exception) { null }

            val zone = TimeZone.getTimeZone("Europe/Kiev")
            val cal = Calendar.getInstance(zone)
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
            val todayDate = fmt.format(cal.time)
            val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val tomorrowDate = fmt.format((cal.clone() as Calendar).apply { add(Calendar.DATE, 1) }.time)

            // Дні обираються за датою, щоб старий кеш не показувався як сьогоднішній
            val days = listOfNotNull(parse(data?.optJSONObject("today")), parse(data?.optJSONObject("tomorrow")))
            val today = days.firstOrNull { it.date == todayDate }
            val tomorrow = days.firstOrNull { it.date == tomorrowDate }

            val loaded = prefs.getLong("loaded", 0)
            val stamp = if (loaded == 0L) "—" else
                SimpleDateFormat("dd.MM HH:mm", Locale.US).apply { timeZone = zone }.format(Date(loaded))
            val warn = if (prefs.getBoolean("error", false)) " ⚠" else ""

            val v = RemoteViews(context.packageName, R.layout.widget)
            v.setTextViewText(R.id.title, "Черга $group · $stamp$warn")
            v.setImageViewBitmap(R.id.today, bar(today, now))
            v.setTextViewText(R.id.todayText, "Сьогодні: ${describe(today)}")
            v.setTextViewText(R.id.tomorrowText, "Завтра: ${describe(tomorrow)}")
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.root, open)
            AppWidgetManager.getInstance(context)
                .updateAppWidget(ComponentName(context, SvitloWidget::class.java), v)
        }
    }
}
