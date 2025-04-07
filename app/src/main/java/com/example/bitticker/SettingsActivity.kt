package com.example.bitticker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val refreshInput = findViewById<EditText>(R.id.refresh_interval)
        val alphaInput = findViewById<EditText>(R.id.alpha_input)
        val fontSizeInput = findViewById<EditText>(R.id.font_size)
        val fontColorInput = findViewById<EditText>(R.id.font_color)
        val bgColorInput = findViewById<EditText>(R.id.bg_color)
        val saveButton = findViewById<Button>(R.id.save_button)

        val prefs = getSharedPreferences("BitTickerPrefs", MODE_PRIVATE)
        refreshInput.setText((prefs.getLong("refresh_interval", 60_000L) / 1000).toString())
        alphaInput.setText(prefs.getFloat("alpha", 0.7f).toString())
        fontSizeInput.setText(prefs.getFloat("font_size", 16f).toString())
        fontColorInput.setText(prefs.getString("font_color", "#FFFFFF") ?: "#FFFFFF")
        bgColorInput.setText(prefs.getString("bg_color", "#80000000") ?: "#80000000")

        saveButton.setOnClickListener {
            val editor = prefs.edit()
            try {
                val refreshSeconds = refreshInput.text.toString().toLongOrNull() ?: 60L
                val alpha = alphaInput.text.toString().toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.7f
                val fontSize = fontSizeInput.text.toString().toFloatOrNull()?.coerceIn(8f, 50f) ?: 16f
                val fontColor = fontColorInput.text.toString().ifEmpty { "#FFFFFF" }
                val bgColor = bgColorInput.text.toString().ifEmpty { "#80000000" }
                Color.parseColor(fontColor) // 验证颜色
                Color.parseColor(bgColor)
                editor.putLong("refresh_interval", refreshSeconds * 1000)
                editor.putFloat("alpha", alpha)
                editor.putFloat("font_size", fontSize)
                editor.putString("font_color", fontColor)
                editor.putString("bg_color", bgColor)
                editor.apply()
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()

                stopService(Intent(this, PriceService::class.java))
                startForegroundService(Intent(this, PriceService::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "输入无效，请检查", Toast.LENGTH_SHORT).show()
            }
        }
    }
}