package com.example.bitticker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.json.JSONArray

class PriceService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatView: View
    private var lastPrice: Double? = null
    private var referencePrice: Double? = null
    private var lastUpdateTime: Long = 0
    private var priceComparisonInterval: Long = 3
    private var maxPrice: Float = 0f
    private var minPrice: Float = 0f
    private var enablePriceAlert: Boolean = false
    private var enableVibrationAlert: Boolean = true
    private var enableSoundAlert: Boolean = true
    private var isPriceAlertActive: Boolean = false
    private var lastAlertTime: Long = 0
    private var currentCurrency = "usd"
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var screenReceiver: BroadcastReceiver
    private lateinit var webSocket: WebSocket
    private var isWebSocketConnected = false
    private var isServiceStopping = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val alertHandler = Handler(Looper.getMainLooper())
    private val alertRunnable = object : Runnable {
        override fun run() {
            if (isPriceAlertActive) {
                triggerAlert()
                alertHandler.postDelayed(this, 1000) // 每隔1秒触发一次
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            setupForegroundNotification()
            setupFloatingWindow()
            setupWebSocket()
            registerScreenReceiver()

            val prefs = getSharedPreferences("BitTickerPrefs", MODE_PRIVATE)
            priceComparisonInterval = prefs.getLong("price_comparison_interval", 3L)
            maxPrice = prefs.getFloat("max_price", 0f)
            minPrice = prefs.getFloat("min_price", 0f)
            enablePriceAlert = prefs.getBoolean("price_alert_enabled", false)
            enableVibrationAlert = prefs.getBoolean("vibration_alert", true)
            enableSoundAlert = prefs.getBoolean("sound_alert", true)
        } catch (e: Exception) {
            e.printStackTrace()
            sendErrorMessage("PriceService onCreate 失败：${e.message}")
        }
    }

    private fun setupForegroundNotification() {
        val channelId = "bitticker_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26+
            val channel = NotificationChannel(
                channelId,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_bitcoin)
                .setContentTitle(getString(R.string.app_name))
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_bitcoin)
                .setContentTitle(getString(R.string.app_name))
                .build()
        }

        startForeground(1, notification)
    }

    private fun setupFloatingWindow() = try {
        floatView = LayoutInflater.from(this).inflate(R.layout.float_window, null)
        val displayMetrics = resources.displayMetrics
        val statusBarHeight = this.getStatusBarHeight()
        val prefs = getSharedPreferences("BitTickerPrefs", MODE_PRIVATE)

        val textView = floatView.findViewById<TextView>(R.id.price_text)
        textView.textSize = prefs.getFloat("font_size", 16f)
        val paint = textView.paint
        val sampleText = "12345678.0" // 10 个字符的样本文本
        val textWidth = paint.measureText(sampleText).toInt()
        val textHeight = (paint.descent() - paint.ascent()).toInt() // 计算文本高度
        val padding = 8 // 减少 padding，确保文本居中
        val windowWidth = textWidth + padding * 2 // 宽度能够显示 10 个字符
        val windowHeight = statusBarHeight // 高度与状态栏一致

        params = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 居中于屏幕左半边
            val leftHalfWidth = displayMetrics.widthPixels / 2
            x = (leftHalfWidth - windowWidth) / 2 // 左半边居中
            y = 0 // 顶部与状态栏对齐
        }

        windowManager.addView(floatView, params)
        floatView.findViewById<TextView>(R.id.price_text).apply {
            setTextSize(prefs.getFloat("font_size", 16f))
            val fontColor = Color.parseColor(prefs.getString("font_color", "#FF9800"))
            setTextColor(fontColor) // 确保文字颜色完全不透明
            setBackgroundResource(R.drawable.rounded_background)
            background.setTint(Color.parseColor(prefs.getString("bg_color", "#000000")))
            background.alpha = (prefs.getFloat("alpha", 0.7f) * 255).toInt() // 仅将透明度应用到背景
        }

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

        floatView.setOnClickListener {
            currentCurrency = if (currentCurrency == "usd") "cny" else "usd"
        }

        floatView.setOnLongClickListener {
            stopSelf()
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        sendErrorMessage("设置悬浮窗失败：${e.message}")
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    private fun setupWebSocket() {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("wss://ws.okx.com:8443/ws/v5/public")
                .build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    isWebSocketConnected = true
                    val subMsg = JSONObject().apply {
                        put("op", "subscribe")
                        put("args", JSONArray().apply {
                            put(JSONObject().apply {
                                put("channel", "tickers")
                                put("instId", if (currentCurrency == "usd") "BTC-USDT" else "BTC-USDT")
                            })
                        })
                    }
                    webSocket.send(subMsg.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val data = JSONObject(text)
                    if (data.has("data")) {
                        val ticker = data.getJSONArray("data").getJSONObject(0)
                        val price = ticker.getString("last").toDoubleOrNull() ?: return
                        uiHandler.post { updatePriceUI(price) }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isWebSocketConnected = false
                    if (!isServiceStopping) {
                        uiHandler.post { sendErrorMessage("连接关闭：$reason (code: $code)") }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    isWebSocketConnected = false
                    if (!isServiceStopping) {
                        uiHandler.post { sendErrorMessage("连接失败：${t.message}") }
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            sendErrorMessage("WebSocket 初始化失败：${e.message}")
        }
    }

    private fun updatePriceUI(price: Double) {
        try {
            val textView = floatView.findViewById<TextView>(R.id.price_text)
            textView.text = price.toString()

            val prefs = getSharedPreferences("BitTickerPrefs", MODE_PRIVATE)
            val upColor = Color.parseColor(prefs.getString("up_color", "#00FF00"))
            val downColor = Color.parseColor(prefs.getString("down_color", "#FF0000"))

            val currentTime = System.currentTimeMillis() / 1000
            if (referencePrice == null || (currentTime - lastUpdateTime) >= priceComparisonInterval) {
                referencePrice = price
                lastUpdateTime = currentTime
            }

            textView.setTextColor(when {
                referencePrice == null -> Color.WHITE
                price > referencePrice!! -> upColor
                price < referencePrice!! -> downColor
                else -> Color.WHITE
            })
            lastPrice = price

            checkPriceAlert(price)
        } catch (e: Exception) {
            e.printStackTrace()
            sendErrorMessage("更新价格 UI 失败：${e.message}")
        }
    }

    private fun checkPriceAlert(price: Double) {
        try {
            if (!enablePriceAlert || (maxPrice == 0f && minPrice == 0f)) {
                isPriceAlertActive = false
                alertHandler.removeCallbacks(alertRunnable)
                return
            }

            if ((maxPrice > 0 && price >= maxPrice) || (minPrice > 0 && price <= minPrice)) {
                if (!isPriceAlertActive) {
                    isPriceAlertActive = true
                    alertHandler.post(alertRunnable)
                }
            } else {
                isPriceAlertActive = false
                alertHandler.removeCallbacks(alertRunnable)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sendErrorMessage("价格预警检查失败：${e.message}")
        }
    }

    private fun triggerAlert() {
        try {
            if (enableVibrationAlert) {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }

            if (enableSoundAlert) {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
                ringtone.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sendErrorMessage("触发告警失败：${e.message}")
        }
    }

    private fun sendErrorMessage(message: String) {
        try {
            updatePriceUIWithError()
            val intent = Intent("com.example.bitticker.WEBSOCKET_ERROR").apply {
                putExtra("error_message", message)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePriceUIWithError() {
        try {
            floatView.findViewById<TextView>(R.id.price_text).text = getString(R.string.error_text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerScreenReceiver() {
        try {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_ON && isWebSocketConnected) {
                        setupWebSocket()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
            sendErrorMessage("注册屏幕接收器失败：${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            isServiceStopping = true
            alertHandler.removeCallbacks(alertRunnable)
            webSocket.close(1000, getString(R.string.service_stopped))
            windowManager.removeView(floatView)
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}