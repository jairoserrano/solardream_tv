package com.jairo.solardream.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A TV-sized energy scene. Measurements and flow directions belong to [FlowState]. */
class FlowView(context: Context) : View(context) {
    private var state = FlowState.CONNECTING
    private var phase = 0f
    private var shiftIndex = 0
    private var attached = false
    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)

    // Cache shaders; avoid bitmaps, blur layers and full-screen allocations while drawing.
    private val background = LinearGradient(
        0f, 0f, 1600f, 900f, intArrayOf(0xFF0A1422.toInt(), 0xFF050B14.toInt()),
        null, Shader.TileMode.CLAMP,
    )
    private val houseHalo = RadialGradient(800f, 414f, 330f,
        intArrayOf(0x252C91C0, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    private val solarHalo = RadialGradient(320f, 420f, 200f,
        intArrayOf(0x16E9B95F, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    private val gridExportHalo = RadialGradient(1280f, 420f, 200f,
        intArrayOf(0x164FE0B1, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    private val gridImportHalo = RadialGradient(1280f, 420f, 200f,
        intArrayOf(0x16F5AA70, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    private val roofGradient = LinearGradient(710f, 355f, 850f, 425f,
        0xFF26485C.toInt(), 0xFF142D40.toInt(), Shader.TileMode.CLAMP)
    private val wallGradient = LinearGradient(740f, 402f, 840f, 510f,
        0xFF203F50.toInt(), 0xFF102333.toInt(), Shader.TileMode.CLAMP)

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
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
        contentDescription = "Paneles: ${state.panelsText}. Casa: ${state.houseText}. " +
            "${state.gridLabel}: ${state.gridText}. ${state.footer}"
        updateAnimation()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        updateAnimation()
        handler.postDelayed(shifter, 60_000L)
    }

    override fun onDetachedFromWindow() {
        attached = false
        animator.cancel()
        handler.removeCallbacks(shifter)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }

    private fun updateAnimation() {
        val flowing = state.panelsToHouse != Arrow.OFF || state.houseGrid != Arrow.OFF
        if (attached && windowVisibility == VISIBLE && !state.dimmed && flowing) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(BACKGROUND)
        val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        if (scale <= 0f) return
        canvas.save()
        canvas.translate((width - DESIGN_WIDTH * scale) / 2f, (height - DESIGN_HEIGHT * scale) / 2f)
        canvas.scale(scale, scale)
        fill(BACKGROUND)
        paint.shader = background
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, paint)
        paint.shader = null
        val (dx, dy) = SHIFTS[shiftIndex]
        canvas.translate(dx, dy)

        val liveAlpha = if (state.dimmed) 95 else 255
        val producing = state.panelsToHouse != Arrow.OFF
        val solarColor = if (producing) SOLAR else MUTED
        val gridColor = when (state.gridMode) {
            GridMode.EXPORT -> MINT
            GridMode.IMPORT -> AMBER
            else -> MUTED
        }

        drawAtmosphere(canvas, producing, liveAlpha)
        drawHeader(canvas)
        drawFlow(canvas, 426f, 644f, state.panelsToHouse, SOLAR)
        drawFlow(canvas, 956f, 1174f, state.houseGrid, gridColor)
        drawSatellite(canvas, 320f, solarColor, liveAlpha, producing)
        drawSolarPanel(canvas, 320f, 420f, solarColor, liveAlpha)
        drawHome(canvas, liveAlpha)
        drawSatellite(canvas, 1280f, gridColor, liveAlpha, state.houseGrid != Arrow.OFF)
        drawPylon(canvas, 1280f, 420f, gridColor, liveAlpha)

        label(canvas, "PRODUCCIÓN SOLAR", 320f, 259f, 16f, MUTED, medium)
        label(canvas, "CONSUMO DEL HOGAR", 800f, 225f, 16f, BLUE, medium)
        label(canvas, "RED ELÉCTRICA", 1280f, 259f, 16f, MUTED, medium)
        drawMetric(canvas, state.panelsText, 320f, 615f, 58f, liveAlpha)
        drawMetric(canvas, state.houseText, 800f, 620f, 76f, liveAlpha)
        drawMetric(canvas, state.gridText, 1280f, 615f, 58f, liveAlpha)

        val solarStatus = when {
            state.dimmed -> "EN ESPERA"
            producing -> "GENERANDO ENERGÍA"
            else -> "EN REPOSO"
        }
        val gridStatus = when {
            state.dimmed -> "EN ESPERA"
            state.gridMode == GridMode.EXPORT -> "EXPORTANDO EXCEDENTE"
            state.gridMode == GridMode.IMPORT -> "IMPORTANDO ENERGÍA"
            state.gridMode == GridMode.IDLE -> "SIN INTERCAMBIO"
            else -> "SIN MEDIDOR"
        }
        label(canvas, solarStatus, 320f, 659f, 14f, solarColor, medium, liveAlpha)
        label(canvas, if (state.dimmed) "ÚLTIMA LECTURA" else "POTENCIA INSTANTÁNEA",
            800f, 667f, 14f, MUTED, medium)
        label(canvas, gridStatus, 1280f, 659f, 14f, gridColor, medium, liveAlpha)
        drawSummary(canvas, liveAlpha)
        canvas.restore()
    }

    private fun drawAtmosphere(canvas: Canvas, producing: Boolean, alpha: Int) {
        fill(BLUE, alpha)
        paint.shader = houseHalo
        canvas.drawCircle(800f, 414f, 330f, paint)
        if (producing) {
            paint.shader = solarHalo
            canvas.drawCircle(320f, 420f, 200f, paint)
        }
        paint.shader = when (state.gridMode) {
            GridMode.EXPORT -> gridExportHalo
            GridMode.IMPORT -> gridImportHalo
            else -> null
        }
        if (paint.shader != null) canvas.drawCircle(1280f, 420f, 200f, paint)
        paint.shader = null
        fill(BLUE, 17)
        for (x in 128..1472 step 56) {
            for (y in 200..720 step 56) canvas.drawCircle(x.toFloat(), y.toFloat(), 1f, paint)
        }
        line(BORDER, 1f, 100)
        canvas.drawLine(96f, 162f, 1504f, 162f, paint)
    }

    private fun drawHeader(canvas: Canvas) {
        line(SOLAR, 2f)
        canvas.drawCircle(109f, 77f, 8f, paint)
        for (i in 0..7) {
            val a = i * PI.toFloat() / 4f
            canvas.drawLine(109f + cos(a) * 13f, 77f + sin(a) * 13f,
                109f + cos(a) * 17f, 77f + sin(a) * 17f, paint)
        }
        label(canvas, "SOLAR DREAM", 143f, 85f, 23f, TEXT, medium, align = Paint.Align.LEFT)
        label(canvas, "La energía de tu hogar", 96f, 129f, 19f, MUTED, regular, align = Paint.Align.LEFT)

        val statusColor = if (!state.dimmed) MINT else AMBER
        fill(statusColor, 14)
        roundRect(canvas, 1110f, 61f, 1324f, 100f, 19f)
        fill(statusColor)
        canvas.drawCircle(1132f, 80f, 3.5f, paint)
        val status = when {
            !state.dimmed -> "EN VIVO · DTU LOCAL"
            state.corner.isEmpty() -> "CONECTANDO AL DTU"
            else -> "SIN CONEXIÓN"
        }
        label(canvas, status, 1147f, 85f, 12f, statusColor, medium, align = Paint.Align.LEFT)
        label(canvas, state.corner.ifEmpty { "— : —" }, 1504f, 94f, 35f, TEXT, light, align = Paint.Align.RIGHT)
        label(canvas, "ÚLTIMA LECTURA", 1504f, 124f, 11f, MUTED, medium, align = Paint.Align.RIGHT)
    }

    private fun drawSatellite(canvas: Canvas, cx: Float, color: Int, alpha: Int, active: Boolean) {
        fill(SURFACE, alpha)
        canvas.drawCircle(cx, 420f, 96f, paint)
        line(color, 1f, alpha * 20 / 100)
        canvas.drawCircle(cx, 420f, 106f, paint)
        line(color, 1.5f, alpha * 48 / 100)
        canvas.drawCircle(cx, 420f, 95f, paint)
        if (active && !state.dimmed) {
            line(color, 2f, 190)
            rect.set(cx - 106f, 314f, cx + 106f, 526f)
            canvas.drawArc(rect, -65f, 70f, false, paint)
            canvas.drawArc(rect, 115f, 70f, false, paint)
        }
    }

    private fun drawHome(canvas: Canvas, alpha: Int) {
        line(BLUE, 1f, alpha * 15 / 100)
        canvas.drawCircle(800f, 420f, 164f, paint)
        fill(SURFACE)
        canvas.drawCircle(800f, 420f, 145f, paint)
        line(BLUE, 1.5f, alpha * 45 / 100)
        canvas.drawCircle(800f, 420f, 145f, paint)
        line(BLUE, 3f, alpha * 80 / 100)
        rect.set(655f, 275f, 945f, 565f)
        canvas.drawArc(rect, 205f, 82f, false, paint)
        canvas.drawArc(rect, 25f, 82f, false, paint)

        // Architectural silhouette with a rooftop solar array.
        polygon(738f, 413f, 800f, 368f, 851f, 413f, 851f, 492f, 738f, 492f)
        fill(TEXT, alpha)
        paint.shader = wallGradient
        canvas.drawPath(path, paint)
        paint.shader = null
        polygon(851f, 413f, 888f, 390f, 888f, 468f, 851f, 492f)
        fill(0xFF0E2130.toInt(), alpha)
        canvas.drawPath(path, paint)
        line(BLUE, 1.4f, alpha * 50 / 100)
        canvas.drawPath(path, paint)
        polygon(726f, 414f, 798f, 355f, 841f, 330f, 901f, 389f, 852f, 420f, 798f, 373f, 741f, 420f)
        fill(BLUE, alpha)
        paint.shader = roofGradient
        canvas.drawPath(path, paint)
        paint.shader = null
        line(BLUE, 1.8f, alpha * 80 / 100)
        canvas.drawPath(path, paint)

        polygon(813f, 361f, 840f, 345f, 875f, 380f, 848f, 397f)
        fill(0xFF123448.toInt(), alpha)
        canvas.drawPath(path, paint)
        line(BLUE, 1f, alpha * 80 / 100)
        canvas.drawPath(path, paint)
        canvas.drawLine(826.5f, 353f, 861.5f, 388.5f, paint)
        canvas.drawLine(825f, 373f, 852f, 357f, paint)
        canvas.drawLine(837f, 385f, 864f, 369f, paint)
        fill(BLUE, alpha * 65 / 100)
        roundRect(canvas, 752f, 432f, 776f, 457f, 3f)
        roundRect(canvas, 813f, 432f, 837f, 457f, 3f)
        fill(0xFF0B1D2B.toInt(), alpha)
        roundRect(canvas, 787f, 454f, 805f, 492f, 3f)
        line(BLUE, 1f, alpha * 40 / 100)
        canvas.drawLine(738f, 493f, 851f, 493f, paint)
        fill(BLUE, alpha * 16 / 100)
        rect.set(727f, 507f, 886f, 521f)
        canvas.drawOval(rect, paint)
    }

    private fun drawSolarPanel(canvas: Canvas, cx: Float, cy: Float, color: Int, alpha: Int) {
        canvas.save()
        canvas.translate(cx, cy)
        fill(color, alpha * 10 / 100)
        polygon(-45f, -20f, 40f, -20f, 57f, 32f, -62f, 32f)
        canvas.drawPath(path, paint)
        line(color, 2f, alpha)
        canvas.drawPath(path, paint)
        for (i in 1..3) {
            val t = i / 4f
            canvas.drawLine(-45f + 85f * t, -20f, -62f + 119f * t, 32f, paint)
        }
        canvas.drawLine(-51f, -3f, 45.5f, -3f, paint)
        canvas.drawLine(-56.5f, 14f, 51f, 14f, paint)
        canvas.drawLine(-14f, 33f, -20f, 51f, paint)
        canvas.drawLine(14f, 33f, 20f, 51f, paint)
        canvas.drawLine(-30f, 51f, 30f, 51f, paint)
        canvas.drawCircle(34f, -48f, 11f, paint)
        for (i in 0..7) {
            val a = i * PI.toFloat() / 4f
            canvas.drawLine(34f + cos(a) * 17f, -48f + sin(a) * 17f,
                34f + cos(a) * 22f, -48f + sin(a) * 22f, paint)
        }
        canvas.restore()
    }

    private fun drawPylon(canvas: Canvas, cx: Float, cy: Float, color: Int, alpha: Int) {
        canvas.save()
        canvas.translate(cx, cy)
        line(color, 2.2f, alpha)
        polygon(-30f, 54f, -10f, -50f, 10f, -50f, 30f, 54f)
        canvas.drawPath(path, paint)
        canvas.drawLine(-35f, -24f, 35f, -24f, paint)
        canvas.drawLine(-45f, -3f, 45f, -3f, paint)
        canvas.drawLine(-17f, -15f, 22f, 14f, paint)
        canvas.drawLine(17f, -15f, -22f, 14f, paint)
        canvas.drawLine(-22f, 14f, 30f, 54f, paint)
        canvas.drawLine(22f, 14f, -30f, 54f, paint)
        canvas.drawLine(-23f, 14f, 23f, 14f, paint)
        canvas.drawLine(-37f, 54f, 37f, 54f, paint)
        for (side in -1..1 step 2) {
            canvas.drawLine(side * 35f, -24f, side * 35f, -12f, paint)
            canvas.drawLine(side * 45f, -3f, side * 45f, 9f, paint)
        }
        canvas.restore()
    }

    private fun drawFlow(canvas: Canvas, x1: Float, x2: Float, direction: Arrow, color: Int) {
        val active = direction != Arrow.OFF && !state.dimmed
        line(if (active) color else BORDER, 2f, if (active) 65 else 100)
        canvas.drawLine(x1, 420f, x2, 420f, paint)
        line(if (active) color else BORDER, 1f, if (active) 20 else 30)
        canvas.drawLine(x1, 430f, x2, 430f, paint)
        canvas.drawLine(x1, 410f, x2, 410f, paint)
        if (!active) return
        val forward = direction == Arrow.FORWARD
        for (i in 0..4) {
            val travel = (phase + i / 5f) % 1f
            val t = if (forward) travel else 1f - travel
            val x = x1 + (x2 - x1) * t
            val a = (sin(travel * PI) * 225).toInt()
            fill(color, a / 12)
            canvas.drawCircle(x, 420f, 12f, paint)
            fill(color, a / 4)
            canvas.drawCircle(x, 420f, 6f, paint)
            fill(color, a)
            canvas.drawCircle(x, 420f, 2.5f, paint)
        }
        val center = (x1 + x2) / 2f
        val sign = if (forward) 1f else -1f
        line(color, 2f, 210)
        path.reset()
        path.moveTo(center - sign * 5f, 412f)
        path.lineTo(center + sign * 4f, 420f)
        path.lineTo(center - sign * 5f, 428f)
        canvas.drawPath(path, paint)
        label(canvas, if (forward) "HACIA ${if (x1 < 500f) "TU HOGAR" else "LA RED"}" else "HACIA TU HOGAR",
            center, 465f, 10f, color, medium, 190)
    }

    private fun drawMetric(canvas: Canvas, value: String, cx: Float, y: Float, size: Float, alpha: Int) {
        val unitStart = value.indexOf(' ')
        val number = if (unitStart > 0) value.substring(0, unitStart) else value
        val unit = if (unitStart > 0) value.substring(unitStart + 1) else ""
        fill(TEXT, alpha)
        paint.typeface = light
        paint.textSize = size
        val numberWidth = paint.measureText(number)
        paint.textSize = 23f
        val unitWidth = if (unit.isNotEmpty()) paint.measureText(unit) + 10f else 0f
        val left = cx - (numberWidth + unitWidth) / 2f
        label(canvas, number, left, y, size, TEXT, light, alpha, Paint.Align.LEFT)
        if (unit.isNotEmpty()) label(canvas, unit, left + numberWidth + 10f, y - 2f,
            23f, MUTED, regular, alpha, Paint.Align.LEFT)
    }

    private fun drawSummary(canvas: Canvas, alpha: Int) {
        fill(SURFACE)
        roundRect(canvas, 96f, 746f, 1504f, 842f, 20f)
        line(BORDER, 1f, 160)
        roundRect(canvas, 96f, 746f, 1504f, 842f, 20f)
        fill(if (state.dimmed) AMBER else SOLAR, 22)
        roundRect(canvas, 118f, 768f, 168f, 820f, 14f)
        line(if (state.dimmed) AMBER else SOLAR, 2f, alpha)
        path.reset()
        path.moveTo(146f, 779f)
        path.lineTo(136f, 795f)
        path.lineTo(146f, 795f)
        path.lineTo(140f, 810f)
        canvas.drawPath(path, paint)
        if (state.dimmed) {
            label(canvas, if (state.corner.isEmpty()) "Buscando la energía de tu hogar" else "Esperando al DTU",
                188f, 787f, 18f, TEXT, medium, align = Paint.Align.LEFT)
            label(canvas, state.footer, 188f, 815f, 16f, MUTED, regular, align = Paint.Align.LEFT)
        } else {
            label(canvas, "ENERGÍA SOLAR · HOY", 188f, 785f, 12f, MUTED, medium, align = Paint.Align.LEFT)
            label(canvas, state.todayText, 188f, 817f, 28f, TEXT, light, align = Paint.Align.LEFT)
            line(BORDER, 1f)
            canvas.drawLine(465f, 772f, 465f, 817f, paint)
            val title = when (state.gridMode) {
                GridMode.EXPORT -> "Tu hogar comparte energía"
                GridMode.IMPORT -> if (state.panelsToHouse == Arrow.OFF) "La red mantiene tu hogar en marcha" else "El sol y la red alimentan tu hogar"
                GridMode.IDLE -> "Tu energía está en equilibrio"
                GridMode.UNKNOWN -> "Tu producción, en tiempo real"
            }
            label(canvas, title, 500f, 789f, 21f, TEXT, regular, align = Paint.Align.LEFT)
            label(canvas, "${state.gridLabel} · ${state.gridText}", 500f, 816f, 15f, MUTED, regular, align = Paint.Align.LEFT)
            label(canvas, "CONEXIÓN LOCAL", 1476f, 789f, 12f, MINT, medium, align = Paint.Align.RIGHT)
            label(canvas, "Sin depender de la nube", 1476f, 815f, 14f, MUTED, regular, align = Paint.Align.RIGHT)
        }
    }

    private fun label(
        canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int,
        font: Typeface = regular, alpha: Int = 255, align: Paint.Align = Paint.Align.CENTER,
    ) {
        fill(color, alpha)
        paint.textSize = size
        paint.typeface = font
        paint.textAlign = align
        canvas.drawText(value, x, y, paint)
    }

    private fun fill(color: Int, alpha: Int = 255) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = alpha
    }

    private fun line(color: Int, width: Float, alpha: Int = 255) {
        fill(color, alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    private fun roundRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun polygon(vararg coordinates: Float) {
        path.reset()
        path.moveTo(coordinates[0], coordinates[1])
        for (i in 2 until coordinates.size step 2) path.lineTo(coordinates[i], coordinates[i + 1])
        path.close()
    }

    private companion object {
        const val DESIGN_WIDTH = 1600f
        const val DESIGN_HEIGHT = 900f
        const val BACKGROUND = 0xFF050B14.toInt()
        const val SURFACE = 0xFF0B1825.toInt()
        const val BORDER = 0xFF294152.toInt()
        const val TEXT = 0xFFE8F0F4.toInt()
        const val MUTED = 0xFF8699AB.toInt()
        const val SOLAR = 0xFFEBC477.toInt()
        const val BLUE = 0xFF83C9E5.toInt()
        const val MINT = 0xFF69DDB5.toInt()
        const val AMBER = 0xFFEBA575.toInt()
        val SHIFTS = listOf(0f to 0f, 8f to 5f, -7f to 9f, 9f to -7f, -8f to -5f)
    }
}
