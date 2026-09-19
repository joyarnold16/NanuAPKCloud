package com.example.llama

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sin

class NanuVoiceWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    enum class Mode { IDLE, LISTENING, THINKING, SPEAKING }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = resources.displayMetrics.density * 3f
    }
    private var phase = 0f
    private var rms = 0.12f
    private var mode = Mode.IDLE
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    fun setMode(value: Mode) {
        if (mode == value) return
        mode = value
        if (isAttachedToWindow && ValueAnimator.areAnimatorsEnabled() && value != Mode.IDLE) animator.start() else animator.cancel()
        invalidate()
    }

    fun updateRms(db: Float) {
        val target = ((db + 2f) / 14f).coerceIn(0.08f, 1f)
        rms = rms * 0.68f + target * 0.32f
        if (mode == Mode.LISTENING) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val accent = ContextCompat.getColor(context, when (mode) {
            Mode.IDLE -> R.color.nanu_muted
            Mode.LISTENING -> R.color.nanu_success
            Mode.THINKING -> R.color.nanu_code
            Mode.SPEAKING -> R.color.nanu_accent
        })
        paint.color = accent
        val bars = 22
        val gap = width / (bars + 1f)
        val baseline = height / 2f
        val maxHeight = height * 0.72f
        repeat(bars) { index ->
            val oscillation = abs(sin((phase * Math.PI * 2.0) + index * 0.52)).toFloat()
            val modeLevel = when (mode) {
                Mode.IDLE -> 0.10f + index % 3 * 0.02f
                Mode.LISTENING -> 0.16f + rms * (0.38f + oscillation * 0.32f)
                Mode.THINKING -> 0.18f + oscillation * 0.45f
                Mode.SPEAKING -> 0.22f + abs(sin((phase * Math.PI * 4.0) + index * 0.7)).toFloat() * 0.58f
            }
            val barHeight = (maxHeight * modeLevel).coerceAtLeast(paint.strokeWidth)
            val x = gap * (index + 1)
            canvas.drawLine(x, baseline - barHeight / 2f, x, baseline + barHeight / 2f, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ValueAnimator.areAnimatorsEnabled() && mode != Mode.IDLE) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
