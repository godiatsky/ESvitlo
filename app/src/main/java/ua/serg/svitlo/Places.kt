package ua.serg.svitlo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Place(
    val id: Long, val name: String, val icon: String,
    val group: String, val address: String, val alerts: Boolean
)

/** Збережені локації користувача. */
object Places {
    val ICONS = listOf(
        "🏠" to "Дім", "💼" to "Робота", "💅" to "Манікюр", "🛒" to "Магазин", "🔧" to "Майстерня",
        "🏋️" to "Спортзал", "🏫" to "Школа", "☕" to "Кав’ярня", "❤️" to "Рідні", "🏡" to "Дача"
    )

    private fun prefs(c: Context) = c.getSharedPreferences("svitlo", Context.MODE_PRIVATE)

    @Synchronized
    fun all(c: Context): List<Place> {
        val p = prefs(c)
        val json = p.getString("places", null)
        if (json == null) {
            // Перенесення єдиної адреси з попередньої версії
            val group = p.getString("group", "")!!
            if (group.isEmpty()) return emptyList()
            val home = Place(1, "Дім", "🏠", group, p.getString("address", "")!!, true)
            write(c, listOf(home))
            return listOf(home)
        }
        val a = JSONArray(json)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Place(o.getLong("id"), o.getString("name"), o.getString("icon"),
                o.getString("group"), o.optString("address"), o.optBoolean("alerts", true))
        }
    }

    private fun write(c: Context, list: List<Place>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon)
                .put("group", it.group).put("address", it.address).put("alerts", it.alerts))
        }
        prefs(c).edit().putString("places", a.toString()).apply()
    }

    fun newId(c: Context): Long {
        val id = maxOf(prefs(c).getLong("lastId", 1), all(c).maxOfOrNull { it.id } ?: 0) + 1
        prefs(c).edit().putLong("lastId", id).apply()
        return id
    }

    fun save(c: Context, place: Place) {
        val list = all(c)
        write(c, if (list.any { it.id == place.id }) list.map { if (it.id == place.id) place else it }
            else list + place)
    }

    fun delete(c: Context, id: Long) = write(c, all(c).filter { it.id != id })

    /** Робить локацію основною: переносить її на перше місце. */
    fun promote(c: Context, id: Long) {
        val list = all(c)
        val place = list.firstOrNull { it.id == id } ?: return
        write(c, listOf(place) + list.filter { it.id != id })
    }

    fun forWidget(c: Context, widget: Int): Place? {
        val list = all(c)
        val id = prefs(c).getLong("widget_$widget", 0)
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    fun next(c: Context, widget: Int) {
        val list = all(c)
        if (list.isEmpty()) return
        val current = list.indexOf(forWidget(c, widget))
        prefs(c).edit().putLong("widget_$widget", list[(current + 1) % list.size].id).apply()
    }
}
