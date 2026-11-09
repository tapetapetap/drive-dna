package kr.skku.drivedna

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout

// 상황 화면(contracts §6)과 나의 DNA 칸(§7)

private val AXIS_ICONS = mapOf(AxisId.DYN to R.drawable.ic_speed, AxisId.RES to R.drawable.ic_signal,
    AxisId.RHY to R.drawable.ic_rhythm, AxisId.PUR to R.drawable.ic_energy)

/** 첫 실행 */
fun Ui.first(): View {
    fun step(n: Int, color: Int, title: String, sub: String) = row(24,
        text("$n", 24f, R.color.on_accent, 700).apply {
            gravity = Gravity.CENTER
            background = round(c(color), 100f)
            layoutParams = LinearLayout.LayoutParams(px(52), px(52))
        },
        column(4, text(title, 28f, R.color.text1, 600), text(sub, 22f, R.color.text3)), grow = 1,
    ).apply {
        background = round(c(R.color.card), 22f, c(R.color.line))
        setPadding(px(28), px(24), px(28), px(24))
    }
    return column(28,
        small("DRIVE DNA"),
        hero(tile(R.drawable.ic_dna, Tile.VIOLET), "운전이 끝나면\n이번 운전의 성향을\n보여 드려요", null),
        step(1, R.color.blue, "평소처럼 운전하세요", "브레이크·핸들·방향지시등·속도·전력 신호를 지켜봐요"),
        step(2, R.color.blue, "P에 놓으면 이번 운전 DNA", "네 가지 축과 근거, 코칭 한 줄"),
        step(3, R.color.green, "쌓일수록 나의 DNA", "기록은 이 차 안에만 남아요"),
    )
}

/** 주행 중: 고정 문구만(원칙 I). 운전 도중에 켜져 세지 않는 운전이면 다른 문구 */
fun Ui.driving(recording: Boolean): View = column(32,
    row(0, tile(R.drawable.ic_wheel, Tile.SOFT, 150)),
    headline("운전 중이에요", 60f),
    body(if (recording) "운전이 끝나면 이번 운전 DNA를 보여 드릴게요." else "이번 운전은 기록하지 않아요. 다음 운전부터 지켜볼게요.", 30f),
    row(0, row(14, dot(R.color.green, 14), text("주행 중에는 말하지도, 화면을 바꾸지도 않아요", 22f, R.color.text3)).apply {
        background = round(c(R.color.card), 18f, c(R.color.line))
        setPadding(px(24), px(18), px(28), px(18))
    }),
).apply { setPadding(0, px(120), 0, 0) }

/** 이번 운전 DNA. 다시 보기면 [top]에 "←  운전 기록" 칩 */
fun Ui.result(m: Metrics, axes: List<Axis>, diff: String?, title: String, top: View? = null): View {
    val code = Types.code(axes.map { it.value })
    val type = card(row(28, tile(R.drawable.ic_dna, Tile.BLUE, 120), column(6,
        text(code, 48f, R.color.text1, 700),
        text(Types.alias(code), 28f, R.color.text1, 600),
        text(axes.joinToString(" · ") { it.name }, 22f, R.color.text3),
    ), grow = 1))
    // 축 줄: 관측 사실(없으면 보류 이유) → 판정 글자, 그 아래 막대
    val rows = axes.map { a ->
        val lines = listOfNotNull(a.fact?.let { body(it, 24f) }, a.withheld?.let { text(it, 22f, R.color.text3) })
        column(10,
            row(12, icon(AXIS_ICONS.getValue(a.id), c(R.color.text3), 26), text(a.id.title, 22f, R.color.text3, 600)),
            row(20, column(6, *lines.toTypedArray()), text(a.letter.toString(), 40f, if (a.value == null) R.color.text4 else R.color.blue, 700), grow = 0),
            AxisBar(ctx, a.id, a.value, R.color.blue),
        )
    }
    return column(28, *listOfNotNull(
        top,
        small(title),
        type,
        card(*rows.toTypedArray(), gap = 32),
        card(text("한 가지 제안", 26f, R.color.text1, 600), body(Coach.line(m), 24f)),
        diff?.let { row(14, dot(R.color.blue, 12), text(it, 24f, R.color.text2), grow = 1) },
        text("잠정 기준 · 속도는 주행거리로 추정했어요", 20f, R.color.text4),
    ).toTypedArray())
}

/** 짧은 운전. 기준 숫자는 [l]에서(시연용 빌드는 30초·0.2km) */
fun Ui.short(m: Metrics, l: Limits): View = column(28,
    small("방금 끝난 운전 · ${elapsed(m.durationMs)}"),
    hero(tile(R.drawable.ic_clock, Tile.GREY), "짧은 운전은\n분석하지 않아요",
        "${elapsed(l.minDriveMs)}, ${l.minDriveKm.toString().removeSuffix(".0")}km 넘게 달리면 이번 운전 DNA를 보여 드려요."),
)

/** 운전 기록: 분석된 운전, 최신이 위. 날짜가 바뀌면 머리 */
fun Ui.history(list: List<Metrics>, l: Limits, onOpen: (Metrics) -> Unit, onClose: () -> Unit): View {
    val close = chip("✕  닫기", R.color.text2, R.color.chip).apply { setOnClickListener { onClose() } }
    val head = row(20, column(6, small("지난 운전"), headline("운전 기록", 44f)), close, grow = 0)
    if (list.isEmpty()) return column(28, head,
        hero(tile(R.drawable.ic_list, Tile.GREY), "아직 분석된 운전이 없어요", "운전이 끝나 P에 놓으면 여기에 남아요."))
    val parts = mutableListOf<View>(head)
    list.forEachIndexed { i, m ->
        val label = dayLabel(m.endedAt)
        if (i == 0 || label != dayLabel(list[i - 1].endedAt)) parts += small(label)
        val code = Judge.code(m, l)
        parts += card(row(24, tile(R.drawable.ic_dna, Tile.BLUE, 88), column(6,
            text("$code · ${Types.alias(code)}", 28f, R.color.text1, 700),
            text("${timeOfDay(m.endedAt)} · ${m.km.km1()}km · ${elapsed(m.durationMs)}", 22f, R.color.text3),
        ), grow = 1)).apply { setOnClickListener { onOpen(m) } }
    }
    return column(20, *parts.toTypedArray())
}

/** 16유형 표. [mine]은 나의 DNA(초록 테두리), [current]는 열기 전에 보던 운전(파랑). 보류 축은 맞는 유형 여럿. 같은 칸이면 초록 */
fun Ui.types(mine: String?, current: String?, onClose: () -> Unit): View {
    val close = chip("✕  닫기", R.color.text2, R.color.chip).apply { setOnClickListener { onClose() } }
    val legend = card(*AxisId.entries.map { a ->
        row(16, text(a.title, 22f, R.color.text3, 600).apply { layoutParams = LinearLayout.LayoutParams(px(150), WRAP_CONTENT) },
            column(2, text("${a.left} ${a.leftName} · ${a.leftText}", 22f), text("${a.right} ${a.rightName} · ${a.rightText}", 22f)), grow = 1)
    }.toTypedArray(), gap = 14)
    val cells = Types.ALIASES.map { (code, alias) ->
        val line = when {
            mine != null && Types.matches(mine, code) -> R.color.green
            current != null && Types.matches(current, code) -> R.color.blue
            else -> 0
        }
        column(4, text(code, 26f, R.color.text1, 700), text(alias, 20f, R.color.text3)).apply {
            background = if (line == 0) round(c(R.color.card), 18f, c(R.color.line)) else round(c(R.color.card), 18f, c(line), 3f)
            setPadding(px(18), px(16), px(18), px(16))
        }
    }
    fun key(color: Int, label: String) = row(10, dot(color, 14), text(label, 20f, R.color.text3))
    return column(24,
        row(20, column(6, small("유형 표"), headline("16가지 DRIVE DNA", 44f)), close, grow = 0),
        legend,
        grid(4, cells),
        row(28, key(R.color.green, "나의 DNA"), key(R.color.blue, "이번 운전")),
    )
}

/** 나의 DNA 칸. 주행 중에는 버튼이 흐리고 눌리지 않는다 */
fun Ui.profile(p: Profile, driving: Boolean, onHistory: () -> Unit, onTypes: () -> Unit): View {
    val head = column(4, small("나의 DRIVE DNA"),
        text(if (p.drives == 0) "운전이 끝나면 여기에 쌓여요" else "운전 ${p.drives}번 기준", 22f, R.color.text3))
    val type = if (p.drives == 0) null else column(2, text(p.code, 56f, R.color.text1, 700), text(Types.alias(p.code), 26f, R.color.text1, 600))
    val bars = AxisId.entries.mapIndexed { i, a -> column(2, text(a.title, 20f, R.color.text3), AxisBar(ctx, a, p.values[i], R.color.green)) }
    val chips = listOf(
        chip("운전 기록  ›", R.color.blue, R.color.blue_soft, R.drawable.ic_list).apply { setOnClickListener { onHistory() } },
        chip("16유형  ›", R.color.blue, R.color.blue_soft, R.drawable.ic_grid).apply { setOnClickListener { onTypes() } },
    ).onEach { it.isEnabled = !driving; it.alpha = if (driving) 0.4f else 1f } // 비활성 뷰는 덤프에 enabled="false"
    return column(24, *listOfNotNull(head, type).toTypedArray(), *bars.toTypedArray(), row(12, *chips.toTypedArray()))
}
