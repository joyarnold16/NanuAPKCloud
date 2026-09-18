package com.example.llama

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs

data class NanuInsight(val label: String, val displayValue: String, val fraction: Float, val signed: Boolean)

object NanuInsightParser {
    private val humidity = Regex("(?im)^Humidity:\\s*([-+]?[0-9]+(?:\\.[0-9]+)?)%")
    private val change = Regex("(?im)^24-hour change:\\s*([-+]?[0-9]+(?:\\.[0-9]+)?)%")

    fun parse(text: String): List<NanuInsight> = buildList {
        humidity.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { value ->
            add(NanuInsight("Humidity", "${value.format1()}%", (value / 100f).coerceIn(0f, 1f), false))
        }
        change.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { value ->
            add(NanuInsight("24-hour change", "${if (value >= 0) "+" else ""}${value.format2()}%", (abs(value) / 10f).coerceIn(0f, 1f), true))
        }
    }

    private fun Float.format1(): String = if (this % 1f == 0f) toInt().toString() else String.format(java.util.Locale.US, "%.1f", this)
    private fun Float.format2(): String = String.format(java.util.Locale.US, "%.2f", this)
}

class NanuInsightView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var insights: List<NanuInsight> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun bind(text: String): Boolean {
        insights = NanuInsightParser.parse(text)
        visibility = if (insights.isEmpty()) GONE else VISIBLE
        requestLayout()
        invalidate()
        return insights.isNotEmpty()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = ((34 + insights.size * 48) * resources.displayMetrics.density).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (insights.isEmpty()) return
        val density = resources.displayMetrics.density
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = 12f * resources.configuration.fontScale * density
        paint.color = ContextCompat.getColor(context, R.color.nanu_muted)
        canvas.drawText("LIVE SNAPSHOT", 0f, 18f * density, paint)
        insights.forEachIndexed { index, insight ->
            val top = (34 + index * 48) * density
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.color = ContextCompat.getColor(context, R.color.nanu_text)
            canvas.drawText(insight.label, 0f, top, paint)
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            val valueWidth = paint.measureText(insight.displayValue)
            canvas.drawText(insight.displayValue, width - valueWidth, top, paint)
            val barTop = top + 10f * density
            val barHeight = 8f * density
            paint.color = ContextCompat.getColor(context, R.color.nanu_border)
            canvas.drawRoundRect(0f, barTop, width.toFloat(), barTop + barHeight, barHeight / 2, barHeight / 2, paint)
            paint.color = ContextCompat.getColor(context, if (insight.signed && insight.displayValue.startsWith("-")) R.color.nanu_danger else R.color.nanu_success)
            canvas.drawRoundRect(0f, barTop, width * insight.fraction, barTop + barHeight, barHeight / 2, barHeight / 2, paint)
        }
    }
}
