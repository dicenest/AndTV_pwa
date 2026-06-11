package io.autodarts.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * FrameLayout that draws a virtual mouse cursor on top of its children
 * (the WebView) and translates D-pad input into pointer movement and clicks.
 *
 *  - D-pad up/down/left/right: move cursor (accelerates while held)
 *  - D-pad center / Enter:     click at cursor position
 *  - Cursor pushed against top/bottom edge: page scrolls
 *  - Cursor fades out after a few seconds of inactivity
 */
class CursorLayout(context: Context) : FrameLayout(context) {

    /** When false, all key events pass through to the child (WebView) untouched. */
    var cursorEnabled = false

    private val cursor = PointF(200f, 200f)
    private val pressed = HashSet<Int>()
    private var speed = BASE_SPEED
    private var lastTick = 0L
    private var visibleUntil = 0L
    private var clickDown = false

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 30, 30, 30)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val handler2 = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val dt = if (lastTick == 0L) 16 else (now - lastTick)
            lastTick = now

            if (pressed.isNotEmpty()) {
                speed = min(speed + ACCEL * dt, MAX_SPEED)
                val d = speed * dt
                if (KeyEvent.KEYCODE_DPAD_LEFT in pressed) cursor.x -= d
                if (KeyEvent.KEYCODE_DPAD_RIGHT in pressed) cursor.x += d
                if (KeyEvent.KEYCODE_DPAD_UP in pressed) cursor.y -= d
                if (KeyEvent.KEYCODE_DPAD_DOWN in pressed) cursor.y += d

                // Scroll the page when pushing against top/bottom edge
                val child = getChildAt(0)
                if (child != null) {
                    if (cursor.y <= EDGE && KeyEvent.KEYCODE_DPAD_UP in pressed) {
                        child.scrollBy(0, (-d).toInt())
                    }
                    if (cursor.y >= height - EDGE && KeyEvent.KEYCODE_DPAD_DOWN in pressed) {
                        child.scrollBy(0, d.toInt())
                    }
                }

                cursor.x = max(0f, min(cursor.x, width.toFloat()))
                cursor.y = max(0f, min(cursor.y, height.toFloat()))
                visibleUntil = now + HIDE_AFTER_MS
                invalidate()
                handler2.postDelayed(this, 16)
            } else {
                speed = BASE_SPEED
                lastTick = 0
                invalidate()
            }
        }
    }

    init {
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!cursorEnabled) return super.dispatchKeyEvent(event)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (pressed.add(event.keyCode) && pressed.size == 1) {
                        lastTick = 0
                        handler2.post(tick)
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    pressed.remove(event.keyCode)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN && !clickDown) {
                    clickDown = true
                    injectTouch(MotionEvent.ACTION_DOWN)
                } else if (event.action == KeyEvent.ACTION_UP && clickDown) {
                    clickDown = false
                    injectTouch(MotionEvent.ACTION_UP)
                }
                visibleUntil = SystemClock.uptimeMillis() + HIDE_AFTER_MS
                invalidate()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun injectTouch(action: Int) {
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(now, now, action, cursor.x, cursor.y, 0)
        getChildAt(0)?.dispatchTouchEvent(ev)
        ev.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (SystemClock.uptimeMillis() > visibleUntil && pressed.isEmpty()) return
        val r = if (clickDown) RADIUS * 0.8f else RADIUS
        canvas.drawCircle(cursor.x, cursor.y, r, paintFill)
        canvas.drawCircle(cursor.x, cursor.y, r, paintStroke)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cursor.set(w / 2f, h / 2f)
        visibleUntil = SystemClock.uptimeMillis() + HIDE_AFTER_MS
    }

    companion object {
        private const val BASE_SPEED = 0.45f   // px per ms
        private const val MAX_SPEED = 1.6f
        private const val ACCEL = 0.0025f
        private const val RADIUS = 18f
        private const val EDGE = 60f
        private const val HIDE_AFTER_MS = 4000L
    }
}
