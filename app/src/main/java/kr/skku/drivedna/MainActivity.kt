package kr.skku.drivedna

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** 상황 화면(오른쪽, 세로는 위)과 나의 DNA 칸(왼쪽, 세로는 아래). 상태 우선순위는 data-model §8 */
class MainActivity : Activity() {

    private enum class State { DRIVING, TYPES, TAGS, COMPARE, RECORD, HISTORY, FIRST, SHORT, RESULT }

    companion object {
        /** 3차: 공개 효과를 낸 결과의 endedAt과 시작 uptime. 프로세스 범위라 회전·다시 열기에도 한 결과에 한 번이다(위임 결정 D1·D15) */
        private var animatedAt: Long? = null
        private var revealAt = 0L
    }

    private lateinit var records: Records
    private lateinit var ui: Ui
    private lateinit var limits: Limits
    private var page: State? = null // 열린 페이지(TYPES, HISTORY). 한 번에 하나
    private var openRecord: Long? = null // 기록에서 다시 보는 운전의 endedAt
    private var shownCode: String? = null // 지금 보이는 운전 결과의 유형
    private var typesFor: String? = null // 16유형을 열 때 보던 운전의 유형(파랑)
    private var listY = 0 // 다시 보기로 가기 전 기록 목록의 스크롤 위치
    private var shown: State? = null
    private var seenAt: Long? = null // 마지막 기록의 endedAt. 바뀌면 새 기록
    // 3차 비교(contracts §2·§3): 켜짐과 고른 운전의 endedAt(고른 차례)
    private var comparing = false
    private val picked = mutableListOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        records = Records(this)
        ui = Ui(this)
        limits = limits()
        findViewById<View>(R.id.dnaPane).background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(getColor(R.color.pane_top), getColor(R.color.pane_bottom)),
        )
        // 위치(끝난 곳 한 점, 이동 범위 태그)는 런타임 권한이다. 거부돼도 앱은 돌고 위치 없는 운전으로 남는다
        val need = listOfNotNull(
            Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= 33 },
            "pleos.car.permission.FUSED_LOCATION",
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 0)
        WatchService.start(this)
    }

    override fun onResume() {
        super.onResume()
        WatchService.onUpdate = ::render
        shown = null
        render()
    }

    override fun onPause() {
        WatchService.onUpdate = null
        page = null; openRecord = null // 가려지는 동안 운전이 있었을 수 있다. 돌아오면 상황 화면부터
        comparing = false; picked.clear()
        super.onPause()
    }

    /** 뒤로 가기: 다시 보기 → 기록 목록 → 페이지 닫기 → 앱 나가기 */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            openRecord != null -> { openRecord = null; render() }
            page == State.COMPARE -> open(State.HISTORY) // 비교 모드와 고른 둘은 남는다(위임 결정 D2)
            page != null -> open(null)
            else -> super.onBackPressed()
        }
    }

    /** 페이지 하나를 열고 나머지는 닫는다. 주행 중에는 열지 않는다 */
    private fun open(p: State?) {
        if (WatchService.driving) return
        if (p == State.TYPES && page != State.TYPES) typesFor = shownCode // 16유형에서 다시 눌러도 파랑을 지키게
        if (p == null || p == State.TYPES || p == State.TAGS) { comparing = false; picked.clear() } // 기록 페이지를 떠나면 비교도 끝
        page = p; openRecord = null
        render()
    }

    private fun render() {
        val all = records.all()
        val lastAt = all.lastOrNull()?.endedAt
        // 운전이 시작됐거나 새 기록이 생기면 열린 페이지를 닫는다(결과가 가려지지 않게)
        if (WatchService.driving || lastAt != seenAt) { page = null; openRecord = null; comparing = false; picked.clear() }
        seenAt = lastAt
        val analyzed = all.filter { it.analyzed }.asReversed() // 최신이 앞
        val last = all.lastOrNull()
        val recordIndex = openRecord?.let { at -> analyzed.indexOfFirst { it.endedAt == at } }?.takeIf { it >= 0 }
        val state = when {
            WatchService.driving -> State.DRIVING
            page == State.TYPES -> State.TYPES
            page == State.TAGS -> State.TAGS
            page == State.COMPARE && picked.size == 2 && picked.all { at -> analyzed.any { it.endedAt == at } } -> State.COMPARE
            page == State.COMPARE -> State.HISTORY // 고른 운전이 기록에서 사라졌으면 목록으로(코드 리뷰 반영)
            recordIndex != null -> State.RECORD
            page == State.HISTORY -> State.HISTORY
            last == null -> State.FIRST
            !last.analyzed -> State.SHORT
            else -> State.RESULT
        }
        if (state == State.DRIVING && shown == State.DRIVING) return // 주행 중에는 화면을 바꾸지 않는다(FR-014)
        val prev = shown
        shown = state

        val scroll = findViewById<ScrollView>(R.id.scroll)
        val screen = findViewById<LinearLayout>(R.id.screen)
        screen.removeAllViews()
        if (WatchService.noSignal) screen.addView(ui.banner(NO_SIGNAL), lp(bottom = 28))
        shownCode = null
        val profile = Profile.of(analyzed, limits)
        val changes = Tags.changes(all, limits) // 3차: 최근 분석 운전으로 바뀐 태그(data-model §4)
        val tags = changes.map { it.now }
        screen.addView(when (state) {
            State.DRIVING -> ui.driving(WatchService.recording)
            State.TYPES -> ui.types(profile.code.takeIf { profile.drives > 0 }, typesFor) { open(null) }
            State.TAGS -> ui.tags(changes) { open(null) }
            State.COMPARE -> ui.compare(analyzed.first { it.endedAt == picked[0] }, analyzed.first { it.endedAt == picked[1] }, limits) {
                open(State.HISTORY)
            }
            State.RECORD -> resultScreen(analyzed, recordIndex!!)
            State.HISTORY -> ui.history(analyzed, limits, comparing, picked,
                onOpen = {
                    if (!comparing) { listY = scroll.scrollY; openRecord = it.endedAt }
                    else if (!picked.remove(it.endedAt)) { picked += it.endedAt; if (picked.size > 2) picked.removeAt(0) } // 세 번째면 첫 것이 풀린다
                    render()
                },
                onClose = { open(null) },
                onCompare = { comparing = !comparing; picked.clear(); render() },
                onGo = { listY = scroll.scrollY; open(State.COMPARE) })
            State.FIRST -> ui.first()
            State.SHORT -> ui.short(last!!, limits)
            State.RESULT -> resultScreen(analyzed, 0)
        }, lp())
        // 다른 화면으로 바뀔 때만 맨 위로. 다시 보기에서 목록으로 돌아오면 보던 자리로
        if (state == State.HISTORY && (prev == State.RECORD || prev == State.COMPARE)) scroll.post { scroll.scrollTo(0, listY) }
        else if (state != prev) scroll.scrollTo(0, 0)

        findViewById<LinearLayout>(R.id.dnaPane).apply {
            removeAllViews()
            addView(ui.profile(profile, tags, changes.count { it.change != Change.NONE }, WatchService.driving,
                onHistory = { open(State.HISTORY) }, onTypes = { open(State.TYPES) }, onTags = { open(State.TAGS) }), lp())
        }
    }

    /** 분석된 운전 [i]번째(0 = 방금 끝난 운전)의 DNA. 차이 줄은 그보다 앞선 기록의 나의 DNA와 비교한다(data-model §5) */
    private fun resultScreen(analyzed: List<Metrics>, i: Int): View {
        val m = analyzed[i]
        val axes = Judge.axes(m, limits)
        shownCode = Types.code(axes.map { it.value })
        val diff = Profile.difference(axes, Profile.of(analyzed.drop(i + 1), limits))
        val length = "${elapsed(m.durationMs)} · ${m.km.km1()}km"
        if (openRecord == null) {
            // 3차: 끝 시각 10초 안에 처음 그릴 때만 공개 효과, 같은 운전에 말한 문장이 있으면 자막(data-model §5)
            val now = SystemClock.uptimeMillis()
            if (System.currentTimeMillis() - m.endedAt < 10_000 && m.endedAt != animatedAt) { animatedAt = m.endedAt; revealAt = now }
            // 공개 중(1.8초)에 다시 그려도 링은 이어지고 글자는 다시 떠오르지 않는다(코드 리뷰 반영)
            val reveal = revealAt.takeIf { m.endedAt == animatedAt && now - it < 1_800 }
            val caption = WatchService.lastSpoken?.takeIf { it.endedAt == m.endedAt }?.let { ui.caption(it.text, it.at + 6_000) }
            return ui.result(m, axes, diff, "${driveTitle(m.endedAt)} · $length", reveal = reveal, caption = caption)
        }
        val back = ui.chip("←  운전 기록", R.color.text2, R.color.chip).apply { setOnClickListener { openRecord = null; render() } }
        return ui.result(m, axes, diff, "${dayLabel(m.endedAt)} ${timeOfDay(m.endedAt)} 운전 · $length", ui.row(0, back))
    }

    private fun lp(bottom: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { bottomMargin = ui.px(bottom) }
}
