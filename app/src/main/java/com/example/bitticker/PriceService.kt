package com.example.bitticker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build

class PriceService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatView: View
    private var lastPrice: Double? = null
    private var currentCurrency = "usd"
    private var refreshInterval = 60_000L // 默认60秒
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupForegroundNotification() // 恢复调用
        setupFloatingWindow()
        startPriceUpdates()
    }

    private fun setupForegroundNotification() {
        val channelId = "bitticker_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "BitTicker Service", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_bitcoin)
            .setContentTitle("BitTicker") // 简化为应用名
            .build()

        startForeground(1, notification)
    }

    private fun setupFloatingWindow() {
        floatView = LayoutInflater.from(this).inflate(R.layout.float_window, null)
        val displayMetrics = resources.displayMetrics
        val statusBarHeight = getStatusBarHeight()
        val prefs = getSharedPreferences("BitTickerPrefs", MODE_PRIVATE)

        params = WindowManager.LayoutParams(
            (displayMetrics.widthPixels / 3), // 宽度为屏幕1/3
            statusBarHeight, // 高度与状态栏相同
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 默认位置调整
            x = (displayMetrics.widthPixels / 4) - (width / 2) // 左侧一半的中心
            y = 0 // 顶部对齐屏幕顶端，覆盖状态栏
            alpha = prefs.getFloat("alpha", 0.7f)
        }

        windowManager.addView(floatView, params)
        floatView.findViewById<TextView>(R.id.price_text).apply {
            setTextSize(prefs.getFloat("font_size", 16f))
            setTextColor(Color.parseColor(prefs.getString("font_color", "#FFFFFF")))
            setBackgroundResource(R.drawable.rounded_background)
            background.setTint(Color.parseColor(prefs.getString("bg_color", "#80000000")))
        }

        // 拖动实现
        floatView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatView, params)
                        return true
                    }
                }
                return false
            }
        })

        // 点击切换货币
        floatView.setOnClickListener {
            currentCurrency = if (currentCurrency == "usd") "cny" else "usd"
            updatePrice()
        }

        // 长按关闭服务
        floatView.setOnLongClickListener {
            stopSelf()
            true
        }
    }

    private fun startPriceUpdates() {
        val prefs = getSharedPreferences("BitTickerPrefs", MODE_PRIVATE)
        refreshInterval = prefs.getLong("refresh_interval", 60_000L)
        handler.post(object : Runnable {
            override fun run() {
                updatePrice()
                handler.postDelayed(this, refreshInterval)
            }
        })
    }

    private fun updatePrice() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.priceApi.getBitcoinPrice(vsCurrencies = currentCurrency)
                val price = response.bitcoin[currentCurrency] ?: return@launch
                withContext(Dispatchers.Main) {
                    val textView = floatView.findViewById<TextView>(R.id.price_text)
                    //textView.text = price.toLong().toString() //String.format("%.0f", price)
                    textView.text = if (currentCurrency == "usd") "$${price.toLong()}" else "¥${price.toLong()}"
                    textView.setTextColor(when {
                        lastPrice == null -> Color.WHITE
                        price > lastPrice!! -> Color.GREEN
                        price < lastPrice!! -> Color.RED
                        else -> Color.WHITE
                    })
                    lastPrice = price
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    floatView.findViewById<TextView>(R.id.price_text).text = "Error"
                }
            }
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 24
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        windowManager.removeView(floatView)
    }
}