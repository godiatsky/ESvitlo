package ua.serg.svitlo

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Визначення черги: вулиця → будинок → черга. */
class AddressSearch(private val a: Activity, private val done: () -> Unit) {
    private class Item(val id: Long, val name: String)

    fun start() = ask("Ваша вулиця", "Напр. Закревського") { query ->
        load("streets", mapOf("query" to query)) { body ->
            choose("Оберіть вулицю", items(body)) { street -> house(street) }
        }
    }

    private fun house(street: Item) = ask("Номер будинку", "Напр. 23а") { query ->
        load("houses", mapOf("streetId" to "${street.id}", "query" to query)) { body ->
            choose("Оберіть будинок", items(body)) { house ->
                load("group", mapOf("streetId" to "${street.id}", "houseId" to "${house.id}")) { result ->
                    val o = JSONObject(result)
                    val group = "${o.getInt("group")}.${o.getInt("subgroup")}"
                    a.getSharedPreferences("svitlo", Context.MODE_PRIVATE).edit()
                        .putString("group", group).putString("address", "${street.name}, ${house.name}").apply()
                    done()
                }
            }
        }
    }

    private fun items(body: String): List<Item> {
        val array = JSONArray(body)
        return (0 until array.length()).map {
            val o = array.getJSONObject(it)
            Item(o.getLong("id"), o.optString("value", o.optString("name")))
        }
    }

    private fun ask(title: String, hint: String, next: (String) -> Unit) {
        val input = EditText(a).apply { this.hint = hint; isSingleLine = true }
        AlertDialog.Builder(a).setTitle(title).setView(input)
            .setPositiveButton("Знайти") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) next(q)
            }.setNegativeButton("Скасувати", null).show()
    }

    private fun choose(title: String, list: List<Item>, next: (Item) -> Unit) {
        if (list.isEmpty()) return fail("Нічого не знайдено. Спробуйте коротший запит")
        AlertDialog.Builder(a).setTitle(title)
            .setItems(list.map { it.name }.toTypedArray()) { _, i -> next(list[i]) }.show()
    }

    private fun fail(message: String) = Toast.makeText(a, message, Toast.LENGTH_LONG).show()

    private fun load(path: String, params: Map<String, String>, ok: (String) -> Unit) {
        val url = Uri.parse("https://app.yasno.ua/api/blackout-service/public/shutdowns/addresses/v2/$path")
            .buildUpon().appendQueryParameter("regionId", "25").appendQueryParameter("dsoId", "902")
        params.forEach { url.appendQueryParameter(it.key, it.value) }
        thread {
            val result = runCatching {
                val c = URL(url.build().toString()).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = 15000
                    c.readTimeout = 15000
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) SvitloWidget")
                    check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
                    c.inputStream.bufferedReader().use { it.readText() }
                } finally { c.disconnect() }
            }
            a.runOnUiThread {
                if (!a.isFinishing && !a.isDestroyed) result.mapCatching(ok)
                    .onFailure { fail("Помилка пошуку: ${it.message}. Чергу можна обрати вручну") }
            }
        }
    }
}
