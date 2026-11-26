package ua.serg.svitlo

import android.content.Context
import androidx.work.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class Refresh(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("svitlo", Context.MODE_PRIVATE)
        val ok = try {
            val c = URL(API).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15000
                c.readTimeout = 15000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) SvitloWidget")
                check(c.responseCode == 200)
                val body = c.inputStream.bufferedReader().use { it.readText() }
                org.json.JSONObject(body)
                prefs.edit().putString("json", body).putLong("loaded", System.currentTimeMillis()).apply()
                true
            } finally { c.disconnect() }
        } catch (e: Exception) { false }
        prefs.edit().putBoolean("error", !ok).apply()
        SvitloWidget.render(applicationContext)
        return Result.success()
    }

    companion object {
        const val API = "https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/25/dsos/902/planned-outages"
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(c: Context) {
            val r = PeriodicWorkRequestBuilder<Refresh>(30, TimeUnit.MINUTES).setConstraints(net).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("periodic", ExistingPeriodicWorkPolicy.KEEP, r)
        }

        fun now(c: Context) {
            val r = OneTimeWorkRequestBuilder<Refresh>().setConstraints(net).build()
            WorkManager.getInstance(c).enqueueUniqueWork("now", ExistingWorkPolicy.KEEP, r)
        }
    }
}
