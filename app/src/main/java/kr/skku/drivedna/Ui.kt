package kr.skku.drivedna

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 첫 앱 "안 써본 기능" Screens.kt·Views.kt에서 가져온 화면 도우미(plan "첫 앱에서 가져오는 것")

fun timeOfDay(ms: Long): String = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(ms))

/** 올해면 "M월 d일", 아니면 "yyyy년 M월 d일" */
fun dayLabel(ms: Long): String {
    val year = { t: Long -> Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) }
    return SimpleDateFormat(if (year(ms) == year(System.currentTimeMillis())) "M월 d일" else "yyyy년 M월 d일", Locale.KOREA).format(Date(ms))
}

/** 결과 제목 앞부분: 10분 안에 끝난 운전은 "방금 끝난 운전", 그 밖은 "{날짜} {시각} 운전"(앱을 나중에 다시 열었을 때) */
fun driveTitle(endedAt: Long, now: Long = System.currentTimeMillis()) =
    if (now - endedAt < 10 * 60_000) "방금 끝난 운전" else "${dayLabel(endedAt)} ${timeOfDay(endedAt)} 운전"

/** 1분 미만은 초로: "40초", 그 밖은 "3분"(내림) */
fun elapsed(ms: Long) = if (ms < 60_000) "${ms / 1000}초" else "${ms / 60_000}분"

/** 아이콘 타일: 바탕 그라데이션과 아이콘 색 */
enum class Tile(val from: Int, val to: Int, val fg: Int) {
    BLUE(R.color.blue, R.color.blue_dark, R.color.on_accent),
    VIOLET(R.color.violet, R.color.blue, R.color.on_accent),
    SOFT(R.color.blue_soft, R.color.blue_soft, R.color.blue),
    GREY(R.color.chip, R.color.chip, R.color.text3),
}

/** 화면을 코드로 조립하는 작은 도구들. 글자 크기는 contracts 머리 */
class Ui(val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density
    fun px(v: Number) = (v.toFloat() * density).toInt()
    fun c(id: Int) = ctx.getColor(id)

    fun text(s: CharSequence, sp: Float, color: Int = R.color.text1, weight: Int = 400): TextView = TextView(ctx).apply {
        text = s
        textSize = sp
        setTextColor(c(color))
        typeface = Typeface.create(Typeface.SANS_SERIF, weight, false)
        setLineSpacing(0f, 1.18f)
    }

    fun small(s: String) = text(s, 22f, R.color.text3, 600)
    fun headline(s: String, sp: Float = 48f) = text(s, sp, R.color.text1, 700)
    fun body(s: String, sp: Float = 26f) = text(s, sp, R.color.text2)

    /** [dash]가 0보다 크면 점선 테두리(dp) */
    fun round(fill: Int, radius: Float, line: Int = 0, width: Float = 1.5f, dash: Float = 0f, gap: Float = 0f) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius * density
        if (line != 0) setStroke(px(width), line, dash * density, gap * density)
    }

    fun dot(color: Int, size: Int = 12) = View(ctx).apply {
        background = round(c(color), 100f)
        layoutParams = LinearLayout.LayoutParams(px(size), px(size))
    }

    fun column(gap: Int, vararg views: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        views.forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { if (i > 0) topMargin = px(gap) }) }
    }

    /** 가로 줄. [grow] 번째 칸이 남은 폭을 차지한다(글자가 줄바꿈되게) */
    fun row(gap: Int, vararg views: View, grow: Int = -1) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { i, v ->
            val lp = if (i == grow) LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            else (v.layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            addView(v, lp.apply { if (i > 0) marginStart = px(gap) })
        }
    }

    /** [columns]칸 격자. 칸 폭은 똑같다 */
    fun grid(columns: Int, views: List<View>) = GridLayout(ctx).apply {
        columnCount = columns
        views.forEach {
            addView(it, GridLayout.LayoutParams().apply {
                setMargins(0, 0, px(12), px(12))
                width = 0; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
    }

    fun card(vararg views: View, gap: Int = 16) = column(gap, *views).apply {
        background = round(c(R.color.card), 24f, c(R.color.line))
        setPadding(px(32), px(28), px(32), px(28))
    }

    fun icon(res: Int, color: Int, size: Int) = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(color)
        layoutParams = LinearLayout.LayoutParams(px(size), px(size))
    }

    fun tile(res: Int, tile: Tile, size: Int = 176): View = FrameLayout(ctx).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(tile.from), c(tile.to))).apply {
            cornerRadius = size * 0.24f * density
        }
        if (tile.fg == R.color.on_accent) elevation = px(size / 16).toFloat()
        addView(ImageView(ctx).apply { setImageResource(res); setColorFilter(c(tile.fg)) }, FrameLayout.LayoutParams(px(size * 0.56f), px(size * 0.56f), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(px(size), px(size))
    }

    fun chip(label: String, fg: Int, bg: Int, iconRes: Int = 0) = text(label, 20f, fg, 600).apply {
        background = round(c(bg), 100f)
        setPadding(px(18), px(9), px(20), px(9))
        if (iconRes != 0) {
            val d = ctx.getDrawable(iconRes)!!.mutate()
            d.setTint(c(fg))
            d.setBounds(0, 0, px(22), px(22))
            setCompoundDrawablesRelative(d, null, null, null)
            compoundDrawablePadding = px(8)
        }
    }

    /** 아이콘 타일 + 큰 제목 + 아래 글자 */
    fun hero(tile: View, title: String, sub: String?) =
        row(36, tile, column(14, headline(title), *listOfNotNull(sub?.let { body(it) }).toTypedArray()), grow = 1)

    fun banner(text: String) = row(14, dot(R.color.red), text(text, 22f, R.color.red_text, 600), grow = 1).apply {
        background = round(c(R.color.red_soft), 18f, c(R.color.red))
        setPadding(px(24), px(18), px(24), px(18))
    }
}
