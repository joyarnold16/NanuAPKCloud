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

class NanuThinkingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedFraction; invalidate() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = ContextCompat.getColor(context, R.color.nanu_accent)
        val radius = 4f * resources.displayMetrics.density
        val centerY = height / 2f
        repeat(3) { index ->
            val pulse = 0.48f + abs(sin((phase * Math.PI * 2) + index * 1.25)).toFloat() * 0.52f
            paint.alpha = (255 * pulse).toInt()
            canvas.drawCircle(radius * (1.2f + index * 2.8f), centerY, radius * (0.72f + pulse * 0.28f), paint)
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && ValueAnimator.areAnimatorsEnabled()) animator.start() else animator.cancel()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
