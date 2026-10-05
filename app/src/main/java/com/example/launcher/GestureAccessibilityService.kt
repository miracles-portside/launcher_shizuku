package com.example.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

class GestureAccessibilityService : AccessibilityService() {

    companion object {
        const val PREFS = "launcher_prefs"
        const val KEY_GESTURES = "gestures_enabled"
        @Volatile var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private val overlays = mutableListOf<View>()
    private val handler = Handler(Looper.getMainLooper())

    private val edgeWidthPx by lazy { (14 * resources.displayMetrics.density).toInt() }
    private val bottomHeightPx by lazy { (16 * resources.displayMetrics.density).toInt() }
    private val swipeThresholdPx by lazy { (40 * resources.displayMetrics.density).toInt() }
    private val holdDelayMs = 180L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        setupOverlays()
    }

    private fun overlayParams(width: Int, height: Int, gravity: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            this.gravity = gravity
            x = 0
            y = 0
        }
    }

    private fun setupOverlays() {
        // Left edge → Back
        val leftView = buildSwipeView(
            direction = SwipeDirection.RIGHT,
            onTrigger = { performGlobalAction(GLOBAL_ACTION_BACK) }
        )
        windowManager.addView(leftView, overlayParams(
            edgeWidthPx, WindowManager.LayoutParams.MATCH_PARENT,
            Gravity.START or Gravity.CENTER_VERTICAL
        ))
        overlays += leftView

        // Right edge → Back
        val rightView = buildSwipeView(
            direction = SwipeDirection.LEFT,
            onTrigger = { performGlobalAction(GLOBAL_ACTION_BACK) }
        )
        windowManager.addView(rightView, overlayParams(
            edgeWidthPx, WindowManager.LayoutParams.MATCH_PARENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ))
        overlays += rightView

        // Bottom → Home (short swipe) / Recents (swipe + hold)
        val bottomView = buildBottomView()
        windowManager.addView(bottomView, overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT, bottomHeightPx,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ))
        overlays += bottomView
    }

    private enum class SwipeDirection { LEFT, RIGHT, UP }

    private fun buildSwipeView(direction: SwipeDirection, onTrigger: () -> Unit): View {
        return View(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0f
                private var startY = 0f
                private var triggered = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX
                            startY = event.rawY
                            triggered = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (triggered) return true
                            val dx = event.rawX - startX
                            val dy = event.rawY - startY
                            val horizontal = abs(dx) > abs(dy)
                            val pass = when (direction) {
                                SwipeDirection.LEFT -> horizontal && dx < -swipeThresholdPx
                                SwipeDirection.RIGHT -> horizontal && dx > swipeThresholdPx
                                SwipeDirection.UP -> !horizontal && dy < -swipeThresholdPx
                            }
                            if (pass) {
                                triggered = true
                                onTrigger()
                            }
                        }
                    }
                    return true
                }
            })
        }
    }

    private fun buildBottomView(): View {
        return View(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0f
                private var startY = 0f
                private var triggered = false
                private var holdRunnable: Runnable? = null

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX
                            startY = event.rawY
                            triggered = false
                            holdRunnable = Runnable {
                                if (!triggered) {
                                    triggered = true
                                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                                }
                            }
                            // 320 ms gives the user time to start moving before we treat it as a hold
                            handler.postDelayed(holdRunnable!!, 320L)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (triggered) return true
                            val dy = event.rawY - startY
                            val dx = event.rawX - startX
                            // Any real movement cancels the hold timer — it's a swipe, not a press
                            if (abs(dy) > swipeThresholdPx / 3 || abs(dx) > swipeThresholdPx / 3) {
                                holdRunnable?.let { handler.removeCallbacks(it) }
                            }
                            // Upward swipe → Home
                            if (dy < -swipeThresholdPx && abs(dy) > abs(dx)) {
                                triggered = true
                                holdRunnable?.let { handler.removeCallbacks(it) }
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            holdRunnable?.let { handler.removeCallbacks(it) }
                            if (!triggered) {
                                val dy = event.rawY - startY
                                val dx = event.rawX - startX
                                // Fast flick detection — some swipes complete between DOWN and UP
                                if (dy < -swipeThresholdPx && abs(dy) > abs(dx)) {
                                    triggered = true
                                    performGlobalAction(GLOBAL_ACTION_HOME)
                                }
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            holdRunnable?.let { handler.removeCallbacks(it) }
                        }
                    }
                    return true
                }
            })
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no-op */ }
    override fun onInterrupt() { /* no-op */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        cleanup()
        isRunning = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        isRunning = false
        super.onDestroy()
    }

    private fun cleanup() {
        overlays.forEach { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlays.clear()
    }
}
