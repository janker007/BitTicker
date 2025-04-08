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
import android.os.Build
import android.os.IBinder
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
import org.json.JSONArray // 添加缺失的导入

class PriceService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatView: View
    private var lastPrice: Double? = null
    private var currentCurrency = "usd"
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var screenReceiver: BroadcastReceiver
    private lateinit var webSocket: WebSocket
    private var isWebSocketConnected = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupForegroundNotification()
        setupFloatingWindow()
        setupWebSocket()
        registerScreenReceiver()
    }

    private fun setupForegroundNotification() {
        val channelId = "bitticker_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "BitTicker Service", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_bitcoin)
            .setContentTitle("BitTicker")
            .build()

        startForeground(1, notification)
    }

    private fun setupFloatingWindow() {
        floatView = LayoutInflater.from(this).inflate(R.layout.float_window, null)
        val displayMetrics = resources.displayMetrics
        val statusBarHeight = getStatusBarHeight()
        val prefs = getSharedPreferences("BitTickerPrefs", MODE_PRIVATE)

        val textView = LayoutInflater.from(this).inflate(R.layout.float_window, null) as TextView
        textView.textSize = prefs.getFloat("font_size", 16f)
        val paint = textView.paint
        val sampleText = "12345678"
        val textWidth = paint.measureText(sampleText).toInt()
        val padding = 16
        val windowWidth = textWidth + padding * 2

        params = WindowManager.LayoutParams(
            windowWidth,
            statusBarHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (displayMetrics.widthPixels / 2) - windowWidth
            y = -statusBarHeight
            alpha = prefs.getFloat("alpha", 0.7f)
        }

        windowManager.addView(floatView, params)
        floatView.findViewById<TextView>(R.id.price_text).apply {
            setTextSize(prefs.getFloat("font_size", 16f))
            setTextColor(Color.parseColor(prefs.getString("font_color", "#FFFFFF")))
            setBackgroundResource(R.drawable.rounded_background)
            background.setTint(Color.parseColor(prefs.getString("bg_color", "#80000000")))
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
    }

    private fun setupWebSocket() {
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
                    updatePriceUI(price)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isWebSocketConnected = false
                sendErrorMessage("连接关闭：$reason (code: $code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                isWebSocketConnected = false
                sendErrorMessage("连接失败：${t.message}")
            }
        })
    }

    private fun updatePriceUI(price: Double) {
        val textView = floatView.findViewById<TextView>(R.id.price_text)
        textView.text = price.toString()
        textView.setTextColor(when {
            lastPrice == null -> Color.WHITE
            price > lastPrice!! -> Color.GREEN
            price < lastPrice!! -> Color.RED
            else -> Color.WHITE
        })
        lastPrice = price
    }

    private fun sendErrorMessage(message: String) {
        updatePriceUIWithError()
        val intent = Intent("com.example.bitticker.WEBSOCKET_ERROR").apply {
            putExtra("error_message", message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun updatePriceUIWithError() {
        floatView.findViewById<TextView>(R.id.price_text).text = "Error"
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 24
    }

    private fun registerScreenReceiver() {
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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        webSocket.close(1000, "Service stopped")
        windowManager.removeView(floatView)
        unregisterReceiver(screenReceiver)
    }
}