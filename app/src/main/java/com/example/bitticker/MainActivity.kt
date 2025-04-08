package com.example.bitticker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

class MainActivity : AppCompatActivity() {
    private lateinit var floatingWindowSwitch: Switch
    private lateinit var alphaSlider: SeekBar
    private lateinit var fontSizeSpinner: Spinner
    private lateinit var fontColorButton: Button
    private lateinit var bgColorButton: Button
    private lateinit var errorText: TextView
    private var selectedFontColor: String = "#FFFFFF"
    private var selectedBgColor: String = "#80000000"

    private val errorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val errorMessage = intent?.getStringExtra("error_message") ?: "未知错误"
            errorText.text = "WebSocket错误：$errorMessage"
            // 使用 context 参数，而不是 this@MainActivity
            Toast.makeText(context, "WebSocket连接失败：$errorMessage", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        floatingWindowSwitch = findViewById(R.id.floating_window_switch)
        alphaSlider = findViewById(R.id.alpha_slider)
        fontSizeSpinner = findViewById(R.id.font_size_spinner)
        fontColorButton = findViewById(R.id.font_color_button)
        bgColorButton = findViewById(R.id.bg_color_button)
        errorText = findViewById(R.id.error_text)
        val applyButton = findViewById<Button>(R.id.apply_button)

        val prefs = getSharedPreferences("BitTickerPrefs", MODE_PRIVATE)
        alphaSlider.progress = (prefs.getFloat("alpha", 0.7f) * 100).toInt()
        selectedFontColor = prefs.getString("font_color", "#FFFFFF") ?: "#FFFFFF"
        selectedBgColor = prefs.getString("bg_color", "#80000000") ?: "#80000000"

        // 字体大小下拉框
        val fontSizes = (12..30).toList().map { it.toString() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fontSizes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fontSizeSpinner.adapter = adapter
        val defaultFontSize = prefs.getFloat("font_size", 16f).toInt()
        fontSizeSpinner.setSelection(fontSizes.indexOf(defaultFontSize.toString()))

        // 字体颜色选择
        fontColorButton.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("选择字体颜色")
                .setPreferenceName("FontColorPicker")
                .setPositiveButton("确定", ColorEnvelopeListener { envelope, _ ->
                    selectedFontColor = "#${envelope.hexCode}"
                    fontColorButton.setBackgroundColor(envelope.color)
                })
                .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
                .attachAlphaSlideBar(true)
                .attachBrightnessSlideBar(true)
                .setBottomSpace(12)
                .show()
        }

        // 背景颜色选择
        bgColorButton.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("选择背景颜色")
                .setPreferenceName("BgColorPicker")
                .setPositiveButton("确定", ColorEnvelopeListener { envelope, _ ->
                    selectedBgColor = "#${envelope.hexCode}"
                    bgColorButton.setBackgroundColor(envelope.color)
                })
                .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
                .attachAlphaSlideBar(true)
                .attachBrightnessSlideBar(true)
                .setBottomSpace(12)
                .show()
        }

        fontColorButton.setBackgroundColor(Color.parseColor(selectedFontColor))
        bgColorButton.setBackgroundColor(Color.parseColor(selectedBgColor))

        // 悬浮窗开关逻辑
        floatingWindowSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    startForegroundService(Intent(this, PriceService::class.java))
                    errorText.text = "WebSocket状态：正常"
                } else {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            } else {
                stopService(Intent(this, PriceService::class.java))
                errorText.text = "WebSocket状态：已关闭"
            }
        }

        // 初始状态
        if (Settings.canDrawOverlays(this) && floatingWindowSwitch.isChecked) {
            startForegroundService(Intent(this, PriceService::class.java))
        }

        // 应用按钮逻辑
        applyButton.setOnClickListener {
            val editor = prefs.edit()
            try {
                val alpha = alphaSlider.progress / 100f
                val fontSize = fontSizeSpinner.selectedItem.toString().toFloat()
                editor.putFloat("alpha", alpha)
                editor.putFloat("font_size", fontSize)
                editor.putString("font_color", selectedFontColor)
                editor.putString("bg_color", selectedBgColor)
                editor.apply()
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()

                if (floatingWindowSwitch.isChecked) {
                    stopService(Intent(this, PriceService::class.java))
                    startForegroundService(Intent(this, PriceService::class.java))
                }
            } catch (e: Exception) {
                Toast.makeText(this, "设置保存失败", Toast.LENGTH_SHORT).show()
            }
        }

        // 注册错误信息接收器
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(errorReceiver, IntentFilter("com.example.bitticker.WEBSOCKET_ERROR"))
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(errorReceiver)
    }
}