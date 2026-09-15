package com.example.llama

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min

/** A small, dependency-free Nanu Visual orb. It pauses when the view is off-screen. */
class NanuPulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
    }
    private var phase = 0f
    private var gradient: RadialGradient? = null
    private val pulse = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2_800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            postInvalidateOnAnimation()
        }
    }

    init {
        contentDescription = "Nanu AI ready"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val radius = min(width, height) * 0.5f
        gradient = RadialGradient(
            width * 0.42f,
            height * 0.38f,
            radius,
            intArrayOf(Color.WHITE, Color.rgb(73, 223, 247), Color.rgb(93, 70, 224), Color.TRANSPARENT),
            floatArrayOf(0f, 0.18f, 0.63f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) * 0.22f
        fill.shader = gradient
        canvas.drawCircle(cx, cy, base * (1f + phase * 0.08f), fill)
        fill.shader = null

        repeat(2) { index ->
            val local = (phase + index * 0.5f) % 1f
            ring.color = Color.argb(((1f - local) * 115).toInt(), 73, 223, 247)
            canvas.drawCircle(cx, cy, base * (1.2f + local * 1.35f), ring)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ValueAnimator.areAnimatorsEnabled() && !pulse.isStarted) pulse.start() else invalidate()
    }

    override fun onDetachedFromWindow() {
        pulse.cancel()
        super.onDetachedFromWindow()
    }
}
