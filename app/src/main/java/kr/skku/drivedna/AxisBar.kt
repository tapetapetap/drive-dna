package kr.skku.drivedna

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * 축 막대: 양 끝 글자와 −1~+1 위치의 점. 판정된 쪽 글자가 진하다. 보류면 점선에 가운데 "·".
 * 3차 compare(data-model §6 D4·D13): 두 운전의 점(앞 graph_line, 뒤 graph_line2). 보류인 쪽은 가운데 테두리 원과 "보류", 끝 글자는 흐리게
 */
class AxisBar(context: Context, private val axis: AxisId, private val value: Double?, private val dotColor: Int,
              private val second: Double? = null, private val compare: Boolean = false) : View(context) {
    private val d = resources.displayMetrics.density
    private val letters = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics)
        typeface = Typeface.create(Typeface.SANS_SERIF, 700, false)
        textAlign = Paint.Align.CENTER
    }
    private val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
        color = context.getColor(R.color.text3)
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 6 * d; strokeCap = Paint.Cap.ROUND; color = context.getColor(R.color.line) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(dotColor) }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) =
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthSpec), ((if (compare) 64 else 40) * d).toInt())

    override fun onDraw(canvas: Canvas) {
        val cy = if (compare) 22 * d else height / 2f
        val baseline = cy - (letters.descent() + letters.ascent()) / 2
        val v = value
        fun letter(ch: Char, x: Float, on: Boolean) {
            letters.color = context.getColor(if (on && !compare) R.color.text1 else R.color.text4)
            canvas.drawText(ch.toString(), x, baseline, letters)
        }
        letter(axis.left, 12 * d, v != null && v < 0)
        letter(axis.right, width - 12 * d, v != null && v >= 0)
        val start = 36 * d
        val end = width - 36 * d
        fun at(x: Double) = start + (x.toFloat() + 1) / 2 * (end - start)
        if (!compare) {
            track.pathEffect = if (v == null) DashPathEffect(floatArrayOf(10 * d, 10 * d), 0f) else null
            canvas.drawLine(start, cy, end, cy, track)
            if (v == null) letter('·', (start + end) / 2, false)
            else canvas.drawCircle(at(v), cy, 10 * d, dot)
            return
        }
        canvas.drawLine(start, cy, end, cy, track)
        val both = value == null && second == null
        val close = value != null && second != null && kotlin.math.abs(at(value) - at(second)) < 20 * d
        listOf(value to R.color.graph_line, second to R.color.graph_line2).forEachIndexed { i, (x, col) ->
            dot.color = context.getColor(col)
            if (x != null) {
                dot.style = Paint.Style.FILL
                canvas.drawCircle(at(x), cy, if (close) (if (i == 0) 12 else 6) * d else 10 * d, dot)
            } else { // 보류: 가운데 테두리 원. 둘 다 보류면 왼쪽·오른쪽으로 14dp씩
                dot.style = Paint.Style.STROKE; dot.strokeWidth = 2.5f * d
                canvas.drawCircle((start + end) / 2 + if (both) (if (i == 0) -14 else 14) * d else 0f, cy, 10 * d, dot)
                dot.style = Paint.Style.FILL
            }
        }
        if (value == null || second == null) canvas.drawText("보류", (start + end) / 2, cy + 30 * d, note)
    }
}
