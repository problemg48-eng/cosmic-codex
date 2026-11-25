package ai.cosmic.data.dol

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import ai.cosmic.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lifecycle-managed floating DOL overlay service.
 * Supports dragging, repositioning, hiding/minimizing, and closing.
 */
class DolOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var statusTextView: TextView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    companion object {
        private const val TAG = "DolOverlayService"
        private const val ACTION_CLOSE = "ai.cosmic.dol.CLOSE_OVERLAY"

        fun start(context: Context) {
            if (!DolOverlayManager.canDrawOverlays(context)) return
            runCatching {
                context.startService(Intent(context, DolOverlayService::class.java))
            }.onFailure {
                Log.w(TAG, "Failed starting DolOverlayService: ${it.message}")
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, DolOverlayService::class.java))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!DolOverlayManager.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        attachOverlay()
        observeDolState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            DolOverlayManager.setFloatingEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!DolOverlayManager.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        removeOverlay()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOverlay() {
        if (overlayView != null) return
        val wm = windowManager ?: return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 240
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 16, 20, 16)
            background = GradientDrawable().apply {
                cornerRadius = 48f
                setColor(Color.parseColor("#EE111217"))
                setStroke(2, Color.parseColor("#FF7C3AED"))
            }
        }

        val badge = TextView(this).apply {
            text = "DOL"
            textSize = 12f
            setTextColor(Color.parseColor("#FF38BDF8"))
            setPadding(0, 0, 14, 0)
        }

        val label = TextView(this).apply {
            text = DolCoordinator.uiState.value.displayMessage.take(36)
            textSize = 12f
            setTextColor(Color.WHITE)
            maxLines = 1
        }
        statusTextView = label

        val closeBtn = TextView(this).apply {
            text = "  ✕"
            textSize = 13f
            setTextColor(Color.parseColor("#FF9CA3AF"))
            setPadding(12, 0, 4, 0)
            setOnClickListener {
                DolOverlayManager.setFloatingEnabled(this@DolOverlayService, false)
                stopSelf()
            }
        }

        container.addView(badge)
        container.addView(label)
        container.addView(closeBtn)

        // Dragging + tap to open app
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    runCatching { wm.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val launch = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(launch)
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            wm.addView(container, params)
            overlayView = container
        }.onFailure {
            Log.w(TAG, "Failed adding overlay view: ${it.message}")
            stopSelf()
        }
    }

    private fun observeDolState() {
        serviceScope.launch {
            DolCoordinator.uiState.collect { st ->
                statusTextView?.text = when (st.expression) {
                    DolExpression.HAPPY, DolExpression.CELEBRATING -> "✓ ${st.displayMessage.take(32)}"
                    DolExpression.SAD -> "✕ ${st.displayMessage.take(32)}"
                    DolExpression.ATTENTIVE -> "! Approval needed"
                    DolExpression.FOCUSED, DolExpression.THINKING -> "Working…"
                    else -> "Ready, boss"
                }
            }
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayView = null
        statusTextView = null
    }
}
