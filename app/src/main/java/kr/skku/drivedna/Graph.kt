package kr.skku.drivedna

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/* 3차 그리기 View(specs/003-drive-graph research R13·R15·R16, data-model §6). 움직이는 것은 PulseRing·SoundBars뿐이다 */

private fun View.dp(v: Float) = v * resources.displayMetrics.density

/**
 * 운전 그래프. 결과(180dp), 썸네일(120×56dp), 겹침(200dp, 두 운전) 세 모드다.
 * 구간 i의 x는 가운데 (i + 0.5) / n, 세로는 0 ~ 최고 × 1.15. 최고 시속 숫자는 그리지 않는다(헌법 I)
 */
class GraphView(context: Context, private val mode: Mode, private val a: Graph, private val b: Graph? = null,
                private val firstColor: Int = R.color.graph_line) : View(context) {
    enum class Mode(val heightDp: Float, val topDp: Float, val lineDp: Float) { RESULT(180f, 30f, 3f), THUMB(56f, 4f, 2f), COMPARE(200f, 24f, 3f) }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    init { contentDescription = "속도 그래프" }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) = setMeasuredDimension(
        if (mode == Mode.THUMB) dp(120f).toInt() else getDefaultSize(suggestedMinimumWidth, widthSpec), dp(mode.heightDp).toInt())

    private fun color(id: Int) = context.getColor(id)

    override fun onDraw(c: Canvas) {
        val top = dp(mode.topDp)
        val bottom = height - dp(4f)
        val max = (listOfNotNull(a, b).flatMap { it.speeds }.maxOrNull() ?: 0).coerceAtLeast(1) * 1.15f
        fun x(i: Int, n: Int) = (i + 0.5f) / n * width
        fun y(v: Number) = bottom - (bottom - top) * v.toFloat() / max
        val series = listOfNotNull(a to firstColor, b?.let { it to R.color.graph_line2 })
        for ((g, col) in series) {
            val n = g.speeds.size
            val path = Path().apply { g.speeds.forEachIndexed { i, v -> if (i == 0) moveTo(x(i, n), y(v)) else lineTo(x(i, n), y(v)) } }
            val area = Path(path).apply { lineTo(x(n - 1, n), bottom); lineTo(x(0, n), bottom); close() }
            fill.color = color(if (mode == Mode.COMPARE) col else R.color.graph_fill)
            if (mode == Mode.COMPARE) fill.alpha = 40
            c.drawPath(area, fill)
            line.color = color(col); line.strokeWidth = dp(mode.lineDp); line.pathEffect = null
            c.drawPath(path, line)
        }
        if (mode == Mode.RESULT) { // 평균 점선
            line.color = color(R.color.text3); line.alpha = 160; line.strokeWidth = dp(1.5f)
            line.pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(6f)), 0f)
            c.drawLine(0f, y(a.avg), width.toFloat(), y(a.avg), line)
            line.alpha = 255; line.pathEffect = null
        }
        for ((g, col) in series) {
            val n = g.speeds.size
            val stacked = HashMap<Int, Int>()
            for ((kind, bin) in g.marks.sortedBy { Graph.KINDS.indexOf(it.first) }) {
                val k = stacked.merge(bin, 1, Int::plus)!! - 1 // 같은 칸에서 몇 번째(종류 차례 a, d, s)
                val markColor = color(when (kind) { 'a' -> R.color.mark_accel; 'd' -> R.color.mark_decel; else -> R.color.mark_steer })
                val cx = x(bin, n)
                when (mode) {
                    Mode.THUMB -> { fill.color = markColor; fill.alpha = 255; c.drawCircle(cx, y(g.speeds[bin]), dp(3f), fill) }
                    Mode.RESULT -> symbol(c, kind, cx, (y(g.speeds[bin]) - dp(10f) - dp(16f) * k).coerceAtLeast(dp(9f)), dp(18f), markColor)
                    Mode.COMPARE -> symbol(c, kind, cx, (y(g.speeds[bin]) - dp(8f) - dp(12f) * k).coerceAtLeast(dp(7f)), dp(14f), color(col))
                }
            }
        }
    }

    /** ▲ 급가속, ▼ 급감속, ◆ 빠른 조향. (cx, cy)가 가운데, size는 한 변 */
    private fun symbol(c: Canvas, kind: Char, cx: Float, cy: Float, size: Float, col: Int) {
        val h = size / 2
        val p = Path().apply {
            when (kind) {
                'a' -> { moveTo(cx, cy - h); lineTo(cx + h, cy + h); lineTo(cx - h, cy + h) }
                'd' -> { moveTo(cx - h, cy - h); lineTo(cx + h, cy - h); lineTo(cx, cy + h) }
                else -> { moveTo(cx, cy - h); lineTo(cx + h, cy); lineTo(cx, cy + h); lineTo(cx - h, cy) }
            }
            close()
        }
        fill.color = col; fill.alpha = 255
        c.drawPath(p, fill)
    }
}

/**
 * 새 결과의 타일 둘레 링(위임 결정 D9·D14): 타일 반에서 1.6배 반지름까지 0.9초, 두 번 이어서 그리고 1.8초에 멈춘다.
 * start는 공개를 시작한 uptime이라, 그사이 화면을 다시 그려도 링이 처음부터 다시 돌지 않고 이어진다
 */
class PulseRing(context: Context, private val tilePx: Float, private val start: Long) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = context.getColor(R.color.blue) }

    override fun onDraw(c: Canvas) {
        val t = SystemClock.uptimeMillis() - start
        if (t >= 1800 || !ValueAnimator.areAnimatorsEnabled()) return
        val k = (t % 900) / 900f
        val r0 = tilePx / 2
        ring.strokeWidth = dp(2f)
        ring.alpha = ((1 - k) * 255).toInt()
        c.drawCircle(width / 2f, height / 2f, r0 + (1.6f * r0 - r0) * k, ring)
        postInvalidateOnAnimation()
    }
}

/** 소리 막대 세 개(data-model §6 D9): 너비 4dp·간격 3dp·높이 6~20dp, 0.6초 사인, 막대마다 1/3 위상. until(uptime) 뒤나 움직임 줄이기면 멈춘 모습 */
class SoundBars(context: Context, private val until: Long) : View(context) {
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.blue) }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) = setMeasuredDimension(dp(3 * 4f + 2 * 3f).toInt(), dp(20f).toInt())

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val moving = now < until && ValueAnimator.areAnimatorsEnabled()
        val w = dp(4f)
        for (i in 0..2) {
            val h = if (moving) dp(6f) + dp(14f) * (0.5f + 0.5f * sin(2 * PI * (now / 600.0 + i / 3.0)).toFloat()) else dp(STILL[i])
            val x = i * (w + dp(3f))
            c.drawRoundRect(x, (height - h) / 2, x + w, (height + h) / 2, w / 2, w / 2, bar)
        }
        if (moving) postInvalidateOnAnimation()
    }

    private companion object { val STILL = floatArrayOf(6f, 14f, 10f) }
}
