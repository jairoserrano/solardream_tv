package com.jairo.solardream.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Dibuja Paneles → Casa → Red con flechas animadas. Toda la lógica está en [FlowState]. */
class FlowView(context: Context) : View(context) {

    private var state: FlowState = FlowState.CONNECTING
    private var dashPhase = 0f
    private var shiftIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density

    private val dashOn = 24f * density
    private val dashOff = 18f * density

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val animator = ValueAnimator.ofFloat(0f, dashOn + dashOff).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            dashPhase = it.animatedValue as Float
            invalidate()
        }
    }

    private val shifter = object : Runnable {
        override fun run() {
            shiftIndex = (shiftIndex + 1) % SHIFTS.size
            invalidate()
            handler.postDelayed(this, 60_000L)
        }
    }

    fun render(newState: FlowState) {
        state = newState
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
        handler.postDelayed(shifter, 60_000L)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        handler.removeCallbacks(shifter)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BACKGROUND)
        val (dx, dy) = SHIFTS[shiftIndex]
        canvas.save()
        canvas.translate(dx * density, dy * density)

        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w, h) * 0.12f
        val cy = h * 0.44f
        val xs = floatArrayOf(w * 0.2f, w * 0.5f, w * 0.8f)
        val alpha = if (state.dimmed) 90 else 255
        val gap = r * 0.25f

        val gridColor = when (state.gridMode) {
            GridMode.EXPORT -> GREEN
            GridMode.IMPORT -> ORANGE
            else -> MUTED
        }

        drawArrow(canvas, xs[0] + r + gap, xs[1] - r - gap, cy, state.panelsToHouse, YELLOW, alpha)
        drawArrow(canvas, xs[1] + r + gap, xs[2] - r - gap, cy, state.houseGrid, gridColor, alpha)

        drawNode(canvas, xs[0], cy, r, YELLOW, alpha, state.panelsText, "Paneles", this::drawSun)
        drawNode(canvas, xs[1], cy, r, BLUE, alpha, state.houseText, "Casa", this::drawHouse)
        drawNode(canvas, xs[2], cy, r, gridColor, alpha, state.gridText, state.gridLabel, this::drawBolt)

        text.color = MUTED
        text.alpha = 255
        text.textSize = r * 0.30f
        text.isFakeBoldText = false
        canvas.drawText(state.footer, w / 2f, h * 0.86f, text)

        text.textAlign = Paint.Align.RIGHT
        text.textSize = r * 0.26f
        canvas.drawText(state.corner, w - 24f * density, 36f * density, text)
        text.textAlign = Paint.Align.CENTER

        canvas.restore()
    }

    private fun drawNode(
        canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int,
        value: String, label: String,
        icon: (Canvas, Float, Float, Float) -> Unit,
    ) {
        stroke.color = color
        stroke.alpha = alpha
        stroke.pathEffect = null
        canvas.drawCircle(cx, cy, r, stroke)
        icon(canvas, cx, cy, r)

        text.color = TEXT
        text.alpha = alpha
        text.textSize = r * 0.5f
        text.isFakeBoldText = true
        canvas.drawText(value, cx, cy + r + r * 0.6f, text)

        text.color = MUTED
        text.alpha = alpha
        text.textSize = r * 0.26f
        text.isFakeBoldText = false
        canvas.drawText(label, cx, cy + r + r * 1.0f, text)
    }

    private fun drawArrow(
        canvas: Canvas, x1: Float, x2: Float, y: Float, dir: Arrow, color: Int, alpha: Int,
    ) {
        if (dir == Arrow.OFF) {
            stroke.color = MUTED
            stroke.alpha = if (alpha < 255) 30 else 60
            stroke.pathEffect = null
            canvas.drawLine(x1, y, x2, y, stroke)
            return
        }
        val forward = dir == Arrow.FORWARD
        val start = if (forward) x1 else x2
        val end = if (forward) x2 else x1
        stroke.color = color
        stroke.alpha = alpha
        stroke.pathEffect = DashPathEffect(floatArrayOf(dashOn, dashOff), -dashPhase)
        canvas.drawLine(start, y, end, y, stroke)
        stroke.pathEffect = null

        val head = 14f * density
        val sign = if (forward) 1f else -1f
        val path = Path().apply {
            moveTo(end, y)
            lineTo(end - sign * head, y - head * 0.6f)
            lineTo(end - sign * head, y + head * 0.6f)
            close()
        }
        fill.color = color
        fill.alpha = alpha
        canvas.drawPath(path, fill)
    }

    private fun drawSun(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r * 0.28f, stroke)
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4f
            canvas.drawLine(
                cx + cos(a) * r * 0.42f, cy + sin(a) * r * 0.42f,
                cx + cos(a) * r * 0.62f, cy + sin(a) * r * 0.62f, stroke,
            )
        }
    }

    private fun drawHouse(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val roof = Path().apply {
            moveTo(cx - r * 0.45f, cy)
            lineTo(cx, cy - r * 0.45f)
            lineTo(cx + r * 0.45f, cy)
        }
        canvas.drawPath(roof, stroke)
        canvas.drawRect(cx - r * 0.32f, cy, cx + r * 0.32f, cy + r * 0.4f, stroke)
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val bolt = Path().apply {
            moveTo(cx + r * 0.10f, cy - r * 0.50f)
            lineTo(cx - r * 0.25f, cy + r * 0.05f)
            lineTo(cx, cy + r * 0.05f)
            lineTo(cx - r * 0.10f, cy + r * 0.50f)
            lineTo(cx + r * 0.25f, cy - r * 0.05f)
            lineTo(cx, cy - r * 0.05f)
            close()
        }
        canvas.drawPath(bolt, stroke)
    }

    private companion object {
        const val BACKGROUND = 0xFF0B0D10.toInt()
        const val TEXT = 0xFFE8EAED.toInt()
        const val MUTED = 0xFF9AA0A6.toInt()
        const val YELLOW = 0xFFFDD663.toInt()
        const val BLUE = 0xFF8AB4F8.toInt()
        const val GREEN = 0xFF81C995.toInt()
        const val ORANGE = 0xFFFFA726.toInt()
        val SHIFTS = listOf(0 to 0, 12 to 8, -10 to 14, 14 to -10, -12 to -8)
    }
}
