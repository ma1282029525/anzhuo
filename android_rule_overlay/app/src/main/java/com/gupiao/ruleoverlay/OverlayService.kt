package com.gupiao.ruleoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository

    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var bodyContainer: FrameLayout? = null
    private var rulesTextView: TextView? = null
    private var toggleButton: ImageButton? = null
    private var isCollapsed = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepository = SettingsRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (overlayView == null) {
            showOverlay()
        } else {
            refreshOverlay()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        val config = settingsRepository.load()
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_widget, null)

        val headerBar = view.findViewById<LinearLayout>(R.id.headerBar)
        bodyContainer = view.findViewById(R.id.bodyContainer)
        rulesTextView = view.findViewById(R.id.textRules)
        toggleButton = view.findViewById(R.id.buttonToggle)
        val editButton = view.findViewById<ImageButton>(R.id.buttonEdit)
        val closeButton = view.findViewById<ImageButton>(R.id.buttonClose)
        val resizeHandle = view.findViewById<View>(R.id.resizeHandle)

        val params = WindowManager.LayoutParams(
            config.width,
            config.height,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.x
            y = config.y
        }

        headerBar.setOnTouchListener(createDragTouchListener())
        resizeHandle.setOnTouchListener(createResizeTouchListener())

        toggleButton?.setOnClickListener {
            setCollapsed(!isCollapsed, persist = true)
        }

        editButton.setOnClickListener {
            val editIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(editIntent)
        }

        closeButton.setOnClickListener {
            stopSelf()
        }

        overlayView = view
        layoutParams = params
        windowManager.addView(view, params)
        applyConfig(config)
    }

    private fun refreshOverlay() {
        val config = settingsRepository.load()
        applyConfig(config)
    }

    private fun applyConfig(config: OverlayConfig) {
        rulesTextView?.text = config.content.ifBlank { getString(R.string.empty_rules_hint) }
        rulesTextView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSizeSp)

        if (overlayView != null) {
            val params = layoutParams ?: return
            params.width = max(minWidthPx(), config.width)
            params.x = config.x
            params.y = config.y
            updateOverlayLayout()
        }

        setCollapsed(config.collapsed, persist = false)
    }

    private fun setCollapsed(collapsed: Boolean, persist: Boolean) {
        isCollapsed = collapsed
        bodyContainer?.visibility = if (collapsed) View.GONE else View.VISIBLE
        toggleButton?.setImageResource(
            if (collapsed) android.R.drawable.arrow_down_float
            else android.R.drawable.arrow_up_float
        )

        val params = layoutParams ?: return
        params.height = if (collapsed) {
            WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            max(minHeightPx(), settingsRepository.load().height)
        }
        updateOverlayLayout()

        if (persist) {
            settingsRepository.saveCollapsed(collapsed)
            persistBounds()
        }
    }

    private fun createDragTouchListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchStartX = 0f
            private var touchStartY = 0f
            private var moved = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val params = layoutParams ?: return false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        moved = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchStartX).roundToInt()
                        val dy = (event.rawY - touchStartY).roundToInt()
                        moved = moved || abs(dx) > 2 || abs(dy) > 2
                        params.x = startX + dx
                        params.y = max(0, startY + dy)
                        updateOverlayLayout()
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (moved) {
                            persistBounds()
                        }
                        return true
                    }
                }

                return false
            }
        }
    }

    private fun createResizeTouchListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startWidth = 0
            private var startHeight = 0
            private var startRawX = 0f
            private var startRawY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                if (isCollapsed) {
                    return true
                }

                val params = layoutParams ?: return false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startWidth = params.width
                        startHeight = if (params.height > 0) params.height else settingsRepository.load().height
                        startRawX = event.rawX
                        startRawY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val width = startWidth + (event.rawX - startRawX).roundToInt()
                        val height = startHeight + (event.rawY - startRawY).roundToInt()
                        params.width = max(minWidthPx(), width)
                        params.height = max(minHeightPx(), height)
                        updateOverlayLayout()
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        persistBounds()
                        return true
                    }
                }

                return false
            }
        }
    }

    private fun persistBounds() {
        val params = layoutParams ?: return
        val storedHeight = if (isCollapsed) settingsRepository.load().height else params.height
        settingsRepository.saveBounds(
            x = params.x,
            y = params.y,
            width = params.width,
            height = storedHeight
        )
    }

    private fun updateOverlayLayout() {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        windowManager.updateViewLayout(view, params)
    }

    private fun stopOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            }
        }
        overlayView = null
        layoutParams = null
        bodyContainer = null
        rulesTextView = null
        toggleButton = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun minWidthPx(): Int = dpToPx(220)

    private fun minHeightPx(): Int = dpToPx(140)

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_SHOW = "com.gupiao.ruleoverlay.action.SHOW"
        const val ACTION_REFRESH = "com.gupiao.ruleoverlay.action.REFRESH"
        const val ACTION_STOP = "com.gupiao.ruleoverlay.action.STOP"

        private const val CHANNEL_ID = "rule_overlay_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
