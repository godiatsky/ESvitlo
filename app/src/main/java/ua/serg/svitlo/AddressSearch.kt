package ua.serg.svitlo

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Визначення черги: вулиця → будинок → черга. Підказки з’являються під час введення. */
class AddressSearch(private val a: Activity, private val done: () -> Unit) {
    private class Item(val id: Long, val name: String) { override fun toString() = name }

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(4)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun start() = picker("Ваша вулиця", "Мінімум 3 літери", 3, { q ->
        variants(q).map { "streets" to mapOf("query" to it) }
    }) { street -> house(street) }

    private fun house(street: Item) = picker("Будинок · ${street.name}", "Номер будинку", 1, { q ->
        listOf("houses" to mapOf("streetId" to "${street.id}", "query" to q))
    }) { house -> group(street, house) }

    private fun group(street: Item, house: Item) {
        toast("Визначаємо чергу…")
        pool.execute {
            val result = runCatching {
                val o = JSONObject(get("group", mapOf("streetId" to "${street.id}", "houseId" to "${house.id}")))
                "${o.getInt("group")}.${o.getInt("subgroup")}"
            }
            main.post {
                if (a.isFinishing || a.isDestroyed) return@post
                result.onSuccess {
                    a.getSharedPreferences("svitlo", Context.MODE_PRIVATE).edit()
                        .putString("group", it).putString("address", "${street.name}, ${house.name}").apply()
                    toast("Ваша черга: $it")
                    done()
                }.onFailure { toast("Не вдалося визначити чергу: ${it.message}") }
            }
        }
    }

    /** Російське написання перетворюється на ймовірні українські варіанти. */
    private fun variants(q: String): List<String> {
        val base = q.lowercase().replace('ы', 'и').replace('э', 'е').replace('ё', 'е').replace("ъ", "’")
        return listOf(q, base, base.replace('и', 'і'), base.replace('е', 'є')).distinctBy { it.lowercase() }
    }

    private fun picker(
        title: String, hint: String, min: Int,
        requests: (String) -> List<Pair<String, Map<String, String>>>, chosen: (Item) -> Unit
    ) {
        val d = a.resources.displayMetrics.density
        val input = EditText(a).apply { this.hint = hint; isSingleLine = true }
        val status = TextView(a).apply { setPadding((4 * d).toInt(), 0, 0, 0) }
        val adapter = ArrayAdapter<Item>(a, android.R.layout.simple_list_item_1, ArrayList())
        val list = ListView(a).apply { this.adapter = adapter }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0)
            addView(input)
            addView(status)
            addView(list, LinearLayout.LayoutParams(-1, (260 * d).toInt()))
        }
        val dialog = AlertDialog.Builder(a).setTitle(title).setView(box)
            .setNegativeButton("Скасувати", null).create()
        list.setOnItemClickListener { _, _, i, _ ->
            val item = adapter.getItem(i) ?: return@setOnItemClickListener
            dialog.dismiss()
            chosen(item)
        }

        var version = 0
        var pending: Runnable? = null
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim()
                val mine = ++version
                pending?.let { main.removeCallbacks(it) }
                adapter.clear()
                if (q.length < min) { status.text = ""; return }
                status.text = "Шукаємо…"
                val search = Runnable {
                    val calls = requests(q)
                    val found = LinkedHashMap<Long, Item>()
                    var left = calls.size
                    var error: String? = null
                    calls.forEach { (path, params) ->
                        pool.execute {
                            val result = runCatching {
                                val array = JSONArray(get(path, params))
                                (0 until array.length()).map {
                                    val o = array.getJSONObject(it)
                                    Item(o.getLong("id"), o.getString("value"))
                                }
                            }
                            main.post {
                                if (mine != version) return@post
                                left--
                                result.onSuccess { items ->
                                    items.filter { it.id !in found }.forEach { found[it.id] = it; adapter.add(it) }
                                }.onFailure { error = it.message }
                                status.text = when {
                                    found.isNotEmpty() -> ""
                                    left > 0 -> "Шукаємо…"
                                    error != null -> "Помилка: $error"
                                    else -> "Нічого не знайдено. Спробуйте назву українською"
                                }
                            }
                        }
                    }
                }
                pending = search
                main.postDelayed(search, 250)
            }
        })
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        input.requestFocus()
    }

    private fun toast(message: String) = Toast.makeText(a, message, Toast.LENGTH_LONG).show()

    /** Викликати лише у фоновому потоці. */
    private fun get(path: String, params: Map<String, String>): String {
        val b = Uri.parse("https://app.yasno.ua/api/blackout-service/public/shutdowns/addresses/v2/$path")
            .buildUpon().appendQueryParameter("regionId", "25").appendQueryParameter("dsoId", "902")
        params.forEach { b.appendQueryParameter(it.key, it.value) }
        val url = b.build().toString()
        cache[url]?.let { return it }
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) SvitloWidget")
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Referer", "https://static.yasno.ua/")
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            cache[url] = body
            return body
        } finally { c.disconnect() }
    }
}
