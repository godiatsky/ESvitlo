package ua.serg.svitlo

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class Power { ON, OFF, UNKNOWN }

class Slot(val start: Int, val end: Int, val power: Power)
class Day(val date: String, val status: String, val slots: List<Slot>) {
    val known get() = status == "ScheduleApplies" && slots.isNotEmpty()
    fun at(minute: Int) = if (!known) Power.UNKNOWN else
        slots.firstOrNull { minute >= it.start && minute < it.end }?.power ?: Power.UNKNOWN
}

/** Стан черги на поточний момент, готовий для показу. */
class State(
    val group: String, val now: Int, val today: Day?, val tomorrow: Day?,
    val headline: String, val detail: String, val power: Power,
    val footer: String, val nextChange: Int?, val stale: Boolean
)

object Schedule {
    private val zone: TimeZone = TimeZone.getTimeZone("Europe/Kiev")

    fun time(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    fun duration(m: Int) = when {
        m < 60 -> "$m хв"
        m % 60 == 0 -> "${m / 60} год"
        else -> "${m / 60} год ${m % 60} хв"
    }

    fun groups(c: Context): List<String> {
        val json = c.getSharedPreferences("svitlo", Context.MODE_PRIVATE).getString("json", "{}")!!
        return try {
            JSONObject(json).keys().asSequence().toList().sortedWith(compareBy(
                { it.substringBefore('.').toIntOrNull() ?: 0 }, { it.substringAfter('.').toIntOrNull() ?: 0 }))
        } catch (e: Exception) { emptyList() }
    }

    private fun parse(o: JSONObject?): Day? {
        if (o == null) return null
        val a = o.optJSONArray("slots")
        val slots = (0 until (a?.length() ?: 0)).map {
            val s = a!!.getJSONObject(it)
            val power = when (s.getString("type")) {
                "NotPlanned" -> Power.ON
                "Definite" -> Power.OFF
                else -> Power.UNKNOWN
            }
            Slot(s.getInt("start"), s.getInt("end"), power)
        }
        return Day(o.optString("date").take(10), o.optString("status"), slots)
    }

    fun summary(d: Day?): String {
        if (d == null || d.status == "WaitingForSchedule") return "графік ще не опубліковано"
        if (d.status == "EmergencyShutdowns") return "екстрені відключення"
        if (!d.known) return "немає точного графіка"
        val off = d.slots.filter { it.power == Power.OFF }
        if (off.isEmpty()) return "без відключень"
        return off.joinToString { "${time(it.start)}–${time(it.end)}" }
    }

    fun load(c: Context): State {
        val prefs = c.getSharedPreferences("svitlo", Context.MODE_PRIVATE)
        val group = prefs.getString("group", "")!!
        val data = try { JSONObject(prefs.getString("json", "{}")!!).optJSONObject(group) } catch (e: Exception) { null }

        val cal = Calendar.getInstance(zone)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
        val todayDate = fmt.format(cal.time)
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val tomorrowDate = fmt.format((cal.clone() as Calendar).apply { add(Calendar.DATE, 1) }.time)

        // Дні обираються за датою: старий кеш не показується як сьогоднішній
        val days = listOfNotNull(parse(data?.optJSONObject("today")), parse(data?.optJSONObject("tomorrow")))
        val today = days.firstOrNull { it.date == todayDate }
        val tomorrow = days.firstOrNull { it.date == tomorrowDate }

        val power = today?.at(now) ?: Power.UNKNOWN
        fun at(m: Int) = if (m < 1440) today?.at(m) ?: Power.UNKNOWN else tomorrow?.at(m - 1440) ?: Power.UNKNOWN
        var end = now
        if (power != Power.UNKNOWN) while (end < 2880 && at(end) == power) end++
        val changes = power != Power.UNKNOWN && end < 2880 && at(end) != Power.UNKNOWN

        val clock = if (end >= 1440 && end % 1440 != 0) "завтра " + time(end % 1440)
            else if (end > 1440) "кінця завтра" else if (end == 1440) "24:00" else time(end)
        val headline = when {
            group.isEmpty() -> "Оберіть адресу"
            today?.status == "EmergencyShutdowns" -> "Екстрені відключення"
            power == Power.ON -> "Світло є до $clock"
            power == Power.OFF -> "Без світла до $clock"
            else -> "Немає даних"
        }
        val detail = when {
            group.isEmpty() -> "Торкніться, щоб визначити чергу"
            today?.status == "EmergencyShutdowns" -> "Графік тимчасово не діє"
            power == Power.UNKNOWN -> "Оновіть, коли буде інтернет"
            else -> "За графіком Yasno, не фактичний стан"
        }

        val loaded = prefs.getLong("loaded", 0)
        val old = System.currentTimeMillis() - loaded > 3 * 3600_000L
        val stamp = if (loaded == 0L) "ще не оновлено" else "оновлено " +
            SimpleDateFormat("dd.MM HH:mm", Locale.US).apply { timeZone = zone }.format(Date(loaded))
        val stale = old || prefs.getBoolean("error", false)
        val footer = if (stale) "⚠ Дані можуть бути застарілими · $stamp" else stamp.replaceFirstChar { it.uppercase() }

        // Наступне перемальовування: зміна стану, застарівання даних або північ
        var next = (if (changes && end < 1440) end else 1440) - now
        if (!old) next = minOf(next, ((loaded + 3 * 3600_000L - System.currentTimeMillis()) / 60_000L).toInt() + 1)
        return State(group, now, today, tomorrow, headline, detail, power, footer, next, stale)
    }
}
