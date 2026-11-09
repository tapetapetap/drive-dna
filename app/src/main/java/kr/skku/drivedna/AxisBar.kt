package kr.skku.drivedna

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/** 축 막대: 양 끝 글자와 −1~+1 위치의 점. 판정된 쪽 글자가 진하다. 보류면 점선에 가운데 "·" */
class AxisBar(context: Context, private val axis: AxisId, private val value: Double?, private val dotColor: Int) : View(context) {
    private val d = resources.displayMetrics.density
    private val letters = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics)
        typeface = Typeface.create(Typeface.SANS_SERIF, 700, false)
        textAlign = Paint.Align.CENTER
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 6 * d; strokeCap = Paint.Cap.ROUND; color = context.getColor(R.color.line) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(dotColor) }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) =
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthSpec), (40 * d).toInt())

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val baseline = cy - (letters.descent() + letters.ascent()) / 2
        val v = value
        fun letter(ch: Char, x: Float, on: Boolean) {
            letters.color = context.getColor(if (on) R.color.text1 else R.color.text4)
            canvas.drawText(ch.toString(), x, baseline, letters)
        }
        letter(axis.left, 12 * d, v != null && v < 0)
        letter(axis.right, width - 12 * d, v != null && v >= 0)
        val start = 36 * d
        val end = width - 36 * d
        track.pathEffect = if (v == null) DashPathEffect(floatArrayOf(10 * d, 10 * d), 0f) else null
        canvas.drawLine(start, cy, end, cy, track)
        if (v == null) letter('·', (start + end) / 2, false)
        else canvas.drawCircle(start + (v.toFloat() + 1) / 2 * (end - start), cy, 10 * d, dot)
    }
}
