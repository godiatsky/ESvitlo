package ua.serg.svitlo

import android.app.Activity
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("svitlo", MODE_PRIVATE)
        val input = EditText(this).apply {
            hint = "Черга, напр. 1.1"
            setText(prefs.getString("group", "1.1"))
        }
        val save = Button(this).apply { text = "Зберегти та оновити" }
        save.setOnClickListener {
            prefs.edit().putString("group", input.text.toString().trim()).apply()
            Refresh.now(this)
            Toast.makeText(this, "Збережено", Toast.LENGTH_SHORT).show()
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
            addView(input)
            addView(save)
        })
        Refresh.schedule(this)
    }
}
