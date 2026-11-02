package com.plszynon.applock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** Siatka 3x3 do rysowania wzoru. Wynik: indeksy punktów oddzielone "-". */
class PatternView(c: Context) : View(c) {
    var onComplete: ((String) -> Unit)? = null
    private val sel = ArrayList<Int>()
    private var fx = 0f
    private var fy = 0f
    private var down = false
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF94A3B8.toInt() }
    private val act = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF818CF8.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6366F1.toInt()
        strokeWidth = c.dp(6).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    private fun cx(i: Int) = (i % 3 + 0.5f) * width / 3f
    private fun cy(i: Int) = (i / 3 + 0.5f) * height / 3f

    fun reset() { sel.clear(); down = false; invalidate() }

    override fun onDraw(canvas: Canvas) {
        for (k in 1 until sel.size)
            canvas.drawLine(cx(sel[k - 1]), cy(sel[k - 1]), cx(sel[k]), cy(sel[k]), line)
        if (down && sel.isNotEmpty())
            canvas.drawLine(cx(sel.last()), cy(sel.last()), fx, fy, line)
        for (i in 0 until 9) {
            val on = sel.contains(i)
            canvas.drawCircle(cx(i), cy(i), if (on) width / 16f else width / 28f, if (on) act else dot)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                sel.clear(); down = true; hit(e)
            }
            MotionEvent.ACTION_MOVE -> hit(e)
            MotionEvent.ACTION_UP -> {
                down = false
                if (sel.isNotEmpty()) {
                    onComplete?.invoke(sel.joinToString("-"))
                    postDelayed({ if (!down) reset() }, 500)
                }
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        invalidate()
        return true
    }

    private fun hit(e: MotionEvent) {
        fx = e.x; fy = e.y
        for (i in 0 until 9) {
            if (!sel.contains(i) && hypot(e.x - cx(i), e.y - cy(i)) < width / 8f) { sel.add(i); break }
        }
    }
}
