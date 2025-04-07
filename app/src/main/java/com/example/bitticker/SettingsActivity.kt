package com.example.bitticker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {
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
        refreshInput.setText((prefs.getLong("refresh_interval", 60_000L) / 1000).toString()) // 转为秒
        alphaInput.setText(prefs.getFloat("alpha", 0.7f).toString())
        fontSizeInput.setText(prefs.getFloat("font_size", 16f).toString())
        fontColorInput.setText(prefs.getString("font_color", "#FFFFFF") ?: "#FFFFFF")
        bgColorInput.setText(prefs.getString("bg_color", "#80000000") ?: "#80000000")

        saveButton.setOnClickListener {
            val editor = prefs.edit()
            try {
                val refreshSeconds = refreshInput.text.toString().toLongOrNull() ?: 60L
                editor.putLong("refresh_interval", refreshSeconds * 1000)
                editor.putFloat("alpha", alphaInput.text.toString().toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.7f)
                editor.putFloat("font_size", fontSizeInput.text.toString().toFloatOrNull()?.coerceIn(8f, 50f) ?: 16f)
                editor.putString("font_color", fontColorInput.text.toString().ifEmpty { "#FFFFFF" })
                editor.putString("bg_color", bgColorInput.text.toString().ifEmpty { "#80000000" })
                editor.apply()
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()

                // 重启服务
                stopService(Intent(this, PriceService::class.java))
                startForegroundService(Intent(this, PriceService::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "输入无效，请检查", Toast.LENGTH_SHORT).show()
            }
        }
    }
}