package kr.skku.drivedna

import android.content.res.Configuration
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
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

/**
 * 이번 운전 DNA. 다시 보기면 [top]에 "←  운전 기록" 칩.
 * 3차: 맨 위 그래프 카드, [reveal](공개를 시작한 uptime)이 있으면 타일 둘레 링, 막 시작했으면 네 글자가 차례로 떠오른다(research R16).
 * [caption]은 들은 음성 자막(제안 카드 아래)
 */
fun Ui.result(m: Metrics, axes: List<Axis>, diff: String?, title: String, top: View? = null, reveal: Long? = null, caption: View? = null): View {
    val code = Types.code(axes.map { it.value })
    val letters = reveal != null && SystemClock.uptimeMillis() - reveal < 150 // 공개 중에 다시 그리면 글자는 그대로 둔다
    val name = if (!letters) text(code, 48f, R.color.text1, 700) else row(2, *code.mapIndexed { i, ch ->
        text(ch.toString(), 48f, R.color.text1, 700).apply { // 0.25초 동안 위로 12dp, 알파 0 → 1, 지연 i × 150ms
            alpha = 0f; translationY = px(12).toFloat()
            animate().alpha(1f).translationY(0f).setStartDelay(i * 150L).setDuration(250).start()
        }
    }.toTypedArray()).apply { contentDescription = code }
    val type = card(row(28, ringed(tile(R.drawable.ic_dna, Tile.BLUE, 120), 120, reveal), column(6,
        name,
        text(Types.alias(code), 28f, R.color.text1, 600),
        text(axes.joinToString(" · ") { it.name }, 22f, R.color.text3),
    ), grow = 1).apply { clipChildren = false }).apply { clipChildren = false }
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
        graphCard(m),
        type,
        card(*rows.toTypedArray(), gap = 32),
        card(text("한 가지 제안", 26f, R.color.text1, 600), body(Coach.line(m), 24f)),
        caption,
        diff?.let { row(14, dot(R.color.blue, 12), text(it, 24f, R.color.text2), grow = 1) },
        text("잠정 기준 · 속도는 주행거리로 추정했어요", 20f, R.color.text4),
    ).toTypedArray())
}

/** 타일 크기 틀 + 1.6배 링(사방 음수 여백). 카드 높이는 그대로이고 링은 틀 밖까지 그린다(위임 결정 D14) */
private fun Ui.ringed(tile: View, size: Int, reveal: Long?): View = FrameLayout(ctx).apply {
    clipChildren = false
    if (reveal != null) addView(PulseRing(ctx, px(size).toFloat(), reveal), FrameLayout.LayoutParams(px(size * 1.6f), px(size * 1.6f)).apply {
        val m = -px(size * 0.3f); setMargins(m, m, m, m)
    })
    addView(tile, FrameLayout.LayoutParams(px(size), px(size)))
    layoutParams = LinearLayout.LayoutParams(px(size), px(size))
}

/** ▲ 급가속 n · ▼ 급감속 n · ◆ 빠른 조향 n(0이거나 신호 없음은 뺌, data-model §6 D6). 기호는 그래프 색 */
private fun Ui.events(m: Metrics): CharSequence {
    val parts = listOfNotNull(
        m.hardAccel?.takeIf { it > 0 }?.let { "▲ 급가속 $it" to R.color.mark_accel },
        m.hardDecel?.takeIf { it > 0 }?.let { "▼ 급감속 $it" to R.color.mark_decel },
        m.quickSteers?.takeIf { it > 0 }?.let { "◆ 빠른 조향 $it" to R.color.mark_steer },
    )
    if (parts.isEmpty()) return "급한 조작 표시 없음"
    val s = SpannableString(parts.joinToString(" · ") { it.first })
    var at = 0
    for ((t, col) in parts) {
        s.setSpan(ForegroundColorSpan(c(col)), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        at += t.length + 3
    }
    return s
}

/** 결과 맨 위 그래프 카드(contracts §1). 그래프가 없으면 까닭 한 줄 */
private fun Ui.graphCard(m: Metrics): View {
    val g = m.graph ?: return small(if (m.noSpeed) "이 운전은 속도를 추정하지 못해 그래프가 없어요" else "이 기록은 그래프를 남기기 전 운전이에요")
    return card(
        text("이번 운전 그래프", 26f, R.color.text1, 600),
        GraphView(ctx, GraphView.Mode.RESULT, g),
        text("처음 ${timeOfDay(m.endedAt - m.durationMs)} · 끝 ${timeOfDay(m.endedAt)}", 22f, R.color.text3),
        text(events(m), 22f, R.color.text2),
        text("속도는 주행거리로 추정했어요", 20f, R.color.text4),
    )
}

/** 들은 음성 자막(data-model §5): 스피커, 소리 막대(until 전까지 움직임), 문장. 회색 카드 */
fun Ui.caption(spoken: String, until: Long): View = row(14,
    icon(R.drawable.ic_speaker, c(R.color.blue), 26), SoundBars(ctx, until), text(spoken, 22f, R.color.text2), grow = 2).apply {
    background = round(c(R.color.chip), 18f)
    setPadding(px(22), px(16), px(22), px(16))
}

/** 짧은 운전. 기준 숫자는 [l]에서(시연용 빌드는 30초·0.2km) */
fun Ui.short(m: Metrics, l: Limits): View = column(28,
    small("${driveTitle(m.endedAt)} · ${elapsed(m.durationMs)}"),
    hero(tile(R.drawable.ic_clock, Tile.GREY), "짧은 운전은\n분석하지 않아요",
        "${elapsed(l.minDriveMs)}, ${l.minDriveKm.toString().removeSuffix(".0")}km 넘게 달리면 이번 운전 DNA를 보여 드려요."),
)

/** 운전 기록: 분석된 운전, 최신이 위. 날짜가 바뀌면 머리. 3차: 줄 왼쪽 썸네일, "비교" 고르기(contracts §2) */
fun Ui.history(list: List<Metrics>, l: Limits, comparing: Boolean, picked: List<Long>, onOpen: (Metrics) -> Unit, onClose: () -> Unit,
               onCompare: () -> Unit, onGo: () -> Unit): View {
    val close = chip("✕  닫기", R.color.text2, R.color.chip).apply { setOnClickListener { onClose() } }
    val can = list.size >= 2
    val toggle = chip("비교", if (comparing) R.color.on_accent else R.color.blue, if (comparing) R.color.blue else R.color.blue_soft).apply {
        isEnabled = can; alpha = if (can) 1f else 0.4f
        if (can) setOnClickListener { onCompare() }
    }
    val head = row(20, column(6, small("지난 운전"), headline("운전 기록", 44f)), row(12, toggle, close), grow = 0)
    if (list.isEmpty()) return column(28, head,
        hero(tile(R.drawable.ic_list, Tile.GREY), "아직 분석된 운전이 없어요", "운전이 끝나 P에 놓으면 여기에 남아요."))
    val parts = mutableListOf<View>(head)
    if (comparing) parts += body("비교할 운전 두 개를 고르세요", 24f)
    list.forEachIndexed { i, m ->
        val label = dayLabel(m.endedAt)
        if (i == 0 || label != dayLabel(list[i - 1].endedAt)) parts += small(label)
        val code = Judge.code(m, l)
        val order = picked.indexOf(m.endedAt)
        val mark = if (!comparing) null else View(ctx).apply { // 고른 차례대로 파랑, 초록
            background = if (order < 0) round(c(R.color.card), 100f, c(R.color.line), 2f)
            else round(c(if (order == 0) R.color.graph_line else R.color.graph_line2), 100f)
            layoutParams = LinearLayout.LayoutParams(px(32), px(32))
            contentDescription = if (order < 0) "고르지 않음" else "${order + 1}번째로 고름"
        }
        val thumb = m.graph?.let { GraphView(ctx, GraphView.Mode.THUMB, it) } ?: tile(R.drawable.ic_dna, Tile.BLUE, 88)
        parts += card(row(24, thumb, column(6,
            text("$code · ${Types.alias(code)}", 28f, R.color.text1, 700),
            text("${timeOfDay(m.endedAt)} · ${m.km.km1()}km · ${elapsed(m.durationMs)}", 22f, R.color.text3),
        ), *listOfNotNull(mark).toTypedArray(), grow = 1)).apply { setOnClickListener { onOpen(m) } }
    }
    if (comparing) parts += text("두 운전 비교", 24f, R.color.on_accent, 700).apply {
        val ok = picked.size == 2
        gravity = Gravity.CENTER
        background = round(c(R.color.blue), 18f)
        setPadding(px(28), px(18), px(28), px(18))
        isEnabled = ok; alpha = if (ok) 1f else 0.4f
        if (ok) setOnClickListener { onGo() }
    }
    return column(20, *parts.toTypedArray())
}

/** 두 운전 비교(contracts §3). 먼저 고른 [a]는 파랑, [b]는 초록. 우열 표현은 쓰지 않는다(헌법 II) */
fun Ui.compare(a: Metrics, b: Metrics, l: Limits, onClose: () -> Unit): View {
    val close = chip("✕  닫기", R.color.text2, R.color.chip).apply { setOnClickListener { onClose() } }
    val axesA = Judge.axes(a, l)
    val axesB = Judge.axes(b, l)
    fun typeCard(m: Metrics, axes: List<Axis>, line: Int): View {
        val code = Types.code(axes.map { it.value })
        return column(6,
            text(code, 40f, R.color.text1, 700),
            text(Types.alias(code), 24f, R.color.text1, 600),
            text("${dayLabel(m.endedAt)} ${timeOfDay(m.endedAt)} · ${m.km.km1()}km · ${elapsed(m.durationMs)}", 20f, R.color.text3),
        ).apply {
            background = round(c(R.color.card), 22f, c(line), 3f)
            setPadding(px(24), px(20), px(24), px(20))
        }
    }
    val cards = LinearLayout(ctx).apply {
        listOf(typeCard(a, axesA, R.color.graph_line), typeCard(b, axesB, R.color.graph_line2)).forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = px(16) })
        }
    }
    fun legend(col: Int, m: Metrics) = row(8, dot(col, 14), text("${timeOfDay(m.endedAt)} 운전", 20f, R.color.text3),
        *listOfNotNull(if (m.graph == null) text("이 운전은 그래프가 없어요", 20f, R.color.text4) else null).toTypedArray())
    val graphs = listOfNotNull(a.graph, b.graph)
    val graph = card(*listOfNotNull(
        text("속도 흐름(처음~끝을 같은 폭으로)", 26f, R.color.text1, 600),
        legend(R.color.graph_line, a), legend(R.color.graph_line2, b),
        // 그래프가 하나뿐이면 있는 쪽 색으로 그린다
        when {
            graphs.size == 2 -> GraphView(ctx, GraphView.Mode.COMPARE, a.graph!!, b.graph)
            a.graph != null -> GraphView(ctx, GraphView.Mode.COMPARE, a.graph)
            b.graph != null -> GraphView(ctx, GraphView.Mode.COMPARE, b.graph, firstColor = R.color.graph_line2)
            else -> null
        },
    ).toTypedArray())
    val rows = AxisId.entries.mapIndexed { i, id ->
        fun says(ax: Axis, col: Int) = row(10, dot(col, 12), text(ax.fact ?: ax.withheld.orEmpty(), 22f, R.color.text2), grow = 1)
        column(8, text(id.title, 22f, R.color.text3, 600),
            AxisBar(ctx, id, axesA[i].value, R.color.blue, axesB[i].value, compare = true),
            says(axesA[i], R.color.graph_line), says(axesB[i], R.color.graph_line2))
    }
    val (n, m, k) = Compare.counts(a, b, l)
    return column(24,
        row(20, column(6, small("두 운전"), headline("운전 비교", 44f)), close, grow = 0),
        cards, graph,
        card(*rows.toTypedArray(), gap = 28),
        body("다른 축 ${n}개 · 같은 축 ${m}개" + if (k > 0) " · 보류 ${k}개" else "", 24f),
    )
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

/** 보조 태그 화면(specs/002-tags contracts/ui.md): 태그마다 카드 한 장. 위치는 비율만 */
fun Ui.tags(list: List<TagView>, onClose: () -> Unit): View {
    val close = chip("✕  닫기", R.color.text2, R.color.chip).apply { setOnClickListener { onClose() } }
    val cards = list.map { v ->
        val t = v.now
        val badge = t.name?.let { chip(it, R.color.green, R.color.green_soft) } ?: chip("보류", R.color.text3, R.color.chip)
        // 3차 바뀜(contracts §4): 제목 줄 칩과 근거 위 "지난: …"
        val change = when (v.change) {
            Change.NEW -> chip("새로 정해짐", R.color.on_accent, R.color.green)
            Change.CHANGED -> chip("방금 운전으로 바뀜", R.color.on_accent, R.color.blue)
            Change.NONE -> null
        }
        card(row(16, text(t.id.title, 28f, R.color.text1, 700), badge, *listOfNotNull(change).toTypedArray(), grow = 0),
            *listOfNotNull(
                v.before?.let { text("지난: $it", 20f, R.color.text4) },
                t.fact?.let { body(it) },
                t.withheld?.let { why ->
                    text(why, 22f, R.color.text3).apply {
                        background = round(c(R.color.card), 18f, c(R.color.line), 2f, dash = 6f, gap = 4f)
                        setPadding(px(20), px(14), px(20), px(14))
                    }
                },
            ).toTypedArray(), gap = 12)
    }
    return column(24,
        row(20, column(6, small("쌓인 운전으로 정해요 · 최근 100번"), headline("보조 태그", 44f)), close, grow = 0),
        *cards.toTypedArray(),
        small("태그는 쌓인 운전으로 정하는 잠정 판정이에요. 성향에 좋고 나쁨은 없어요."),
    )
}

/** 나의 DNA 칸. 주행 중에는 버튼이 흐리고 눌리지 않는다 */
fun Ui.profile(p: Profile, tags: List<Tag>, changed: Int, driving: Boolean, onHistory: () -> Unit, onTypes: () -> Unit, onTags: () -> Unit): View {
    val head = column(4, small("나의 DRIVE DNA"),
        text(if (p.drives == 0) "운전이 끝나면 여기에 쌓여요" else "운전 ${p.drives}번 기준", 22f, R.color.text3))
    val type = if (p.drives == 0) null else column(2, text(p.code, 56f, R.color.text1, 700), text(Types.alias(p.code), 26f, R.color.text1, 600))
    val judged = tags.mapNotNull { it.name }
    val tagLine = when {
        judged.isNotEmpty() -> grid(2, judged.map { chip(it, R.color.green, R.color.green_soft).apply { gravity = Gravity.CENTER } })
        p.drives > 0 -> text("보조 태그는 운전이 더 쌓이면 보여요", 20f, R.color.text3)
        else -> null
    }
    // 3차 FR-041: 바뀐 태그 근거 수. 누르면 태그 화면(P에서만, 주행 중에는 흐리게)
    val news = if (changed == 0) null else text("방금 운전으로 태그 근거 ${changed}개가 바뀌었어요  ›", 22f, R.color.blue, 600).apply {
        isEnabled = !driving; alpha = if (driving) 0.4f else 1f
        if (!driving) setOnClickListener { onTags() }
    }
    val bars = AxisId.entries.mapIndexed { i, a -> column(2, text(a.title, 20f, R.color.text3), AxisBar(ctx, a, p.values[i], R.color.green)) }
    val chips = listOf(
        chip("운전 기록  ›", R.color.blue, R.color.blue_soft, R.drawable.ic_list).apply { setOnClickListener { onHistory() } },
        chip("16유형  ›", R.color.blue, R.color.blue_soft, R.drawable.ic_grid).apply { setOnClickListener { onTypes() } },
        chip("태그  ›", R.color.blue, R.color.blue_soft, R.drawable.ic_dna).apply { setOnClickListener { onTags() } },
    ).onEach { it.isEnabled = !driving; it.alpha = if (driving) 0.4f else 1f } // 비활성 뷰는 덤프에 enabled="false"
    val buttons = row(12, *chips.toTypedArray())
    // 세로 화면의 아래 칸은 낮아서, 버튼을 맨 아래에 두면 스크롤해야 보인다. 그때는 머리 바로 밑에 둔다
    if (ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
        return column(24, head, buttons, *listOfNotNull(type, tagLine, news).toTypedArray(), *bars.toTypedArray())
    return column(24, *listOfNotNull(head, type, tagLine, news).toTypedArray(), *bars.toTypedArray(), buttons)
}
