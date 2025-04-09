package com.example.bitticker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

class MainActivity : AppCompatActivity() {
    private lateinit var floatingWindowSwitch: SwitchMaterial
    private lateinit var alphaSlider: Slider
    private lateinit var fontSizeSpinner: Spinner
    private lateinit var priceComparisonIntervalSpinner: Spinner
    private lateinit var fontColorButton: Button
    private lateinit var bgColorButton: Button
    private lateinit var upColorButton: Button
    private lateinit var downColorButton: Button
    private lateinit var priceAlertSwitch: SwitchMaterial
    private lateinit var maxPriceInput: EditText
    private lateinit var minPriceInput: EditText
    private lateinit var vibrationAlertCheckbox: CheckBox
    private lateinit var soundAlertCheckbox: CheckBox
    private lateinit var errorText: TextView
    private var selectedFontColor: String = "#FF9800"
    private var selectedBgColor: String = "#000000"
    private var selectedUpColor: String = "#388E3C"
    private var selectedDownColor: String = "#D32F2F"

    private val errorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val errorMessage = intent?.getStringExtra("error_message") ?: getString(R.string.unknown_error)
            errorText.text = getString(R.string.websocket_error_prefix) + errorMessage
            Toast.makeText(context, getString(R.string.websocket_failed_prefix) + errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            floatingWindowSwitch = findViewById(R.id.floating_window_switch)
            alphaSlider = findViewById(R.id.alpha_slider)
            fontSizeSpinner = findViewById(R.id.font_size_spinner)
            priceComparisonIntervalSpinner = findViewById(R.id.price_comparison_interval_spinner)
            fontColorButton = findViewById(R.id.font_color_button)
            bgColorButton = findViewById(R.id.bg_color_button)
            upColorButton = findViewById(R.id.up_color_button)
            downColorButton = findViewById(R.id.down_color_button)
            priceAlertSwitch = findViewById(R.id.price_alert_switch)
            maxPriceInput = findViewById(R.id.max_price_input)
            minPriceInput = findViewById(R.id.min_price_input)
            vibrationAlertCheckbox = findViewById(R.id.vibration_alert_checkbox)
            soundAlertCheckbox = findViewById(R.id.sound_alert_checkbox)
            errorText = findViewById(R.id.error_text)

            val prefs = getSharedPreferences("BitTickerPrefs", MODE_PRIVATE)
            val editor = prefs.edit()

            // 设置默认值（仅在首次启动时）
            if (!prefs.contains("alpha")) {
                editor.putFloat("alpha", 0.7f) // 透明度 70%
                editor.putFloat("font_size", 16f) // 字体大小 16
                editor.putLong("price_comparison_interval", 5L) // 涨跌间隔 5
                editor.putString("font_color", "#FF9800") // 字体颜色 橙色
                editor.putString("bg_color", "#000000") // 背景颜色 黑色
                editor.putString("up_color", "#388E3C") // 上涨颜色 深绿色
                editor.putString("down_color", "#D32F2F") // 下跌颜色 深红色
                editor.putBoolean("floating_window_enabled", true) // 悬浮窗开关 开启
                editor.putBoolean("vibration_alert", true) // 震动警示 默认开启
                editor.putBoolean("sound_alert", true) // 声音警示 默认开启
                editor.putBoolean("price_alert_enabled", false) // 价格预警 默认关闭
                editor.putFloat("max_price", 0f) // 价格上限 默认 0
                editor.putFloat("min_price", 0f) // 价格下限 默认 0
                editor.apply()
            }

            // 加载设置
            alphaSlider.value = (prefs.getFloat("alpha", 0.7f) * 100)
            selectedFontColor = prefs.getString("font_color", "#FF9800") ?: "#FF9800"
            selectedBgColor = prefs.getString("bg_color", "#000000") ?: "#000000"
            selectedUpColor = prefs.getString("up_color", "#388E3C") ?: "#388E3C"
            selectedDownColor = prefs.getString("down_color", "#D32F2F") ?: "#D32F2F"
            maxPriceInput.setText(prefs.getFloat("max_price", 0f).toString())
            minPriceInput.setText(prefs.getFloat("min_price", 0f).toString())
            vibrationAlertCheckbox.isChecked = prefs.getBoolean("vibration_alert", true)
            soundAlertCheckbox.isChecked = prefs.getBoolean("sound_alert", true)
            priceAlertSwitch.isChecked = prefs.getBoolean("price_alert_enabled", false)
            floatingWindowSwitch.isChecked = prefs.getBoolean("floating_window_enabled", true)

            // 字体大小下拉框
            val fontSizes = (12..30).toList().map { it.toString() }
            val fontSizeAdapter = ArrayAdapter(this, R.layout.spinner_item, fontSizes)
            fontSizeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            fontSizeSpinner.adapter = fontSizeAdapter
            val defaultFontSize = prefs.getFloat("font_size", 16f).toInt()
            val fontSizeIndex = fontSizes.indexOf(defaultFontSize.toString())
            fontSizeSpinner.setSelection(if (fontSizeIndex != -1) fontSizeIndex else fontSizes.indexOf("16"))

            // 涨跌判断间隔下拉框
            val intervals = (1..60).toList().map { it.toString() }
            val intervalAdapter = ArrayAdapter(this, R.layout.spinner_item, intervals)
            intervalAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            priceComparisonIntervalSpinner.adapter = intervalAdapter
            val defaultInterval = prefs.getLong("price_comparison_interval", 5L).toInt()
            val intervalIndex = intervals.indexOf(defaultInterval.toString())
            priceComparisonIntervalSpinner.setSelection(if (intervalIndex != -1) intervalIndex else intervals.indexOf("5"))

            // 设置圆形颜色按钮
            setColorButton(fontColorButton, selectedFontColor)
            setColorButton(bgColorButton, selectedBgColor)
            setColorButton(upColorButton, selectedUpColor)
            setColorButton(downColorButton, selectedDownColor)

            // 透明度调整（即时生效）
            alphaSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    val alpha = value / 100f
                    prefs.edit().putFloat("alpha", alpha).apply()
                    updateService()
                }
            }

            // 字体大小调整（即时生效）
            fontSizeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                    val fontSize = fontSizes[position].toFloat()
                    prefs.edit().putFloat("font_size", fontSize).apply()
                    updateService()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }

            // 涨跌判断间隔调整（即时生效）
            priceComparisonIntervalSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                    val interval = intervals[position].toLong()
                    prefs.edit().putLong("price_comparison_interval", interval).apply()
                    updateService()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }

            // 字体颜色选择（即时生效）
            fontColorButton.setOnClickListener {
                ColorPickerDialog.Builder(this)
                    .setTitle(getString(R.string.font_color_label))
                    .setPreferenceName("FontColorPicker")
                    .setPositiveButton(getString(R.string.apply), ColorEnvelopeListener { envelope, _ ->
                        selectedFontColor = "#${envelope.hexCode.substring(2)}"
                        setColorButton(fontColorButton, selectedFontColor)
                        prefs.edit().putString("font_color", selectedFontColor).apply()
                        updateService()
                    })
                    .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
                    .attachAlphaSlideBar(false)
                    .attachBrightnessSlideBar(true)
                    .setBottomSpace(12)
                    .show()
            }

            // 背景颜色选择（即时生效）
            bgColorButton.setOnClickListener {
                ColorPickerDialog.Builder(this)
                    .setTitle(getString(R.string.bg_color_label))
                    .setPreferenceName("BgColorPicker")
                    .setPositiveButton(getString(R.string.apply), ColorEnvelopeListener { envelope, _ ->
                        selectedBgColor = "#${envelope.hexCode.substring(2)}"
                        setColorButton(bgColorButton, selectedBgColor)
                        prefs.edit().putString("bg_color", selectedBgColor).apply()
                        updateService()
                    })
                    .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
                    .attachAlphaSlideBar(false)
                    .attachBrightnessSlideBar(true)
                    .setBottomSpace(12)
                    .show()
            }

            // 上涨颜色选择（即时生效）
            upColorButton.setOnClickListener {
                ColorPickerDialog.Builder(this)
                    .setTitle(getString(R.string.up_color_label))
                    .setPreferenceName("UpColorPicker")
                    .setPositiveButton(getString(R.string.apply), ColorEnvelopeListener { envelope, _ ->
                        selectedUpColor = "#${envelope.hexCode.substring(2)}"
                        setColorButton(upColorButton, selectedUpColor)
                        prefs.edit().putString("up_color", selectedUpColor).apply()
                        updateService()
                    })
                    .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
                    .attachAlphaSlideBar(false)
                    .attachBrightnessSlideBar(true)
                    .setBottomSpace(12)
                    .show()
            }

            // 下跌颜色选择（即时生效）
            downColorButton.setOnClickListener {
                ColorPickerDialog.Builder(this)
                    .setTitle(getString(R.string.down_color_label))
                    .setPreferenceName("DownColorPicker")
                    .setPositiveButton(getString(R.string.apply), ColorEnvelopeListener { envelope, _ ->
                        selectedDownColor = "#${envelope.hexCode.substring(2)}"
                        setColorButton(downColorButton, selectedDownColor)
                        prefs.edit().putString("down_color", selectedDownColor).apply()
                        updateService()
                    })
                    .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
                    .attachAlphaSlideBar(false)
                    .attachBrightnessSlideBar(true)
                    .setBottomSpace(12)
                    .show()
            }

            // 价格预警开关
            priceAlertSwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("price_alert_enabled", isChecked).apply()
                updateService()
                if (isChecked) {
                    Toast.makeText(this, "价格预警已启用", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "价格预警已关闭", Toast.LENGTH_SHORT).show()
                }
            }

            // 价格上限调整（即时生效）
            maxPriceInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val maxPrice = s.toString().toFloatOrNull() ?: 0f
                    prefs.edit().putFloat("max_price", maxPrice).apply()
                    updateService()
                }
            })

            // 价格下限调整（即时生效）
            minPriceInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val minPrice = s.toString().toFloatOrNull() ?: 0f
                    prefs.edit().putFloat("min_price", minPrice).apply()
                    updateService()
                }
            })

            // 震动警示（即时生效）
            vibrationAlertCheckbox.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("vibration_alert", isChecked).apply()
                updateService()
            }

            // 声音警示（即时生效）
            soundAlertCheckbox.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("sound_alert", isChecked).apply()
                updateService()
            }

            // 悬浮窗开关逻辑
            floatingWindowSwitch.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("floating_window_enabled", isChecked).apply()
                if (isChecked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                    } else {
                        startService(Intent(this, PriceService::class.java))
                        errorText.text = getString(R.string.websocket_status_normal)
                    }
                } else {
                    stopService(Intent(this, PriceService::class.java))
                    errorText.text = getString(R.string.websocket_status_closed)
                }
            }

            // 初始状态
            if (floatingWindowSwitch.isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                } else {
                    startService(Intent(this, PriceService::class.java))
                }
            }

            // 注册错误信息接收器
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(errorReceiver, IntentFilter("com.example.bitticker.WEBSOCKET_ERROR"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "MainActivity 启动失败：${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setColorButton(button: Button, color: String) {
        try {
            // 确保颜色值是有效的十六进制格式
            val parsedColor = if (color.startsWith("#")) {
                Color.parseColor(color)
            } else {
                Color.parseColor("#$color")
            }
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(parsedColor)
                setStroke(2, Color.WHITE)
            }
            button.background = drawable
            button.invalidate() // 强制刷新按钮
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "设置颜色按钮失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateService() {
        try {
            if (floatingWindowSwitch.isChecked) {
                stopService(Intent(this, PriceService::class.java))
                startService(Intent(this, PriceService::class.java))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "更新服务失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(errorReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}