package kr.skku.drivedna

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val MINUTE = 60_000L

fun Double.one() = String.format(Locale.US, "%.1f", this)

/** 거리 글자: 소수 1자리 내림(보류 이유의 거리가 기준보다 커 보이지 않게, contracts 숫자 형식) */
fun Double.km1() = (floor(this * 10) / 10).one()


/** 운전 하나의 관측 지표(data-model §2). null = 그 신호를 받지 못해 없음 */
data class Metrics(
    val endedAt: Long = 0,
    val durationMs: Long = 0,
    val km: Double = 0.0,
    val movingMs: Long = 0,
    val hardAccel: Int? = 0,
    val hardDecel: Int? = 0,
    val brakes: Int? = null,
    val turns: Int? = null,
    val early: Int? = null,
    val quickSteers: Int? = null,
    val speedCv: Double? = null,
    val kwhPer100: Double? = null,
    val analyzed: Boolean = false,
    // 보조 태그(specs/002-tags data-model §2). null = 그 신호를 받지 못함
    val cruiseMs: Long? = null,
    val socStart: Int? = null,
    val socEnd: Int? = null,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val passenger: Boolean? = null,
    val wiperMs: Long? = null,
    val graph: Graph? = null, // 3차 운전 그래프(specs/003-drive-graph data-model §1). 1·2차 기록은 null
) {
    val avgKmh get() = if (movingMs > 0) km / (movingMs / 3_600_000.0) else 0.0

    /** 주행거리 값이 거칠어 속도를 추정하지 못함. 속도에서 나오는 지표(급가속·급감속·속도 변동)가 모두 없음 */
    val noSpeed get() = hardAccel == null
    fun per10(n: Int?) = if (km > 0 && n != null) n / km * 10 else 0.0

    /** 역동성 지수: 10km당 급가속·급감속·빠른 조향(축과 상황 태그가 같은 값을 본다) */
    val dynIdx get() = per10((hardAccel ?: 0) + (hardDecel ?: 0) + (quickSteers ?: 0))

    /** 로그용 한 줄. 위치는 기기 안 기록에만 두므로 좌표를 빼고 있음/없음만 쓴다(헌법 VI). 그래프는 칸 수만 */
    fun logLine() = "${copy(endLat = null, endLon = null, graph = null)} 그래프 ${graph?.speeds?.size ?: 0}칸 위치 ${if (endLat != null) "있음" else "없음"}"
}

/** 운전 하나의 속도 흐름(3차 data-model §1). speeds: 칸마다 평균 시속(1~40칸). marks: (종류, 칸) — 'a' 급가속, 'd' 급감속, 's' 빠른 조향 */
data class Graph(val speeds: List<Int>, val marks: List<Pair<Char, Int>>) {
    val avg get() = speeds.average()
    fun count(kind: Char) = marks.count { it.first == kind }

    companion object {
        const val KINDS = "ads"

        /** 칸 수 = min(40, 운전 초 올림). 칸마다 표본 평균, 빈 칸은 앞 칸 값, 첫 표본 전은 0(research R11). 표본이 없으면 null */
        fun of(start: Long, end: Long, trace: List<Pair<Long, Double>>, marks: List<Pair<Long, Char>>): Graph? {
            if (trace.isEmpty()) return null
            val d = (end - start).coerceAtLeast(1)
            val n = ceil(d / 1000.0).toInt().coerceIn(1, 40)
            fun bin(t: Long) = ((t - start) * n / d).toInt().coerceIn(0, n - 1)
            val sums = DoubleArray(n)
            val counts = IntArray(n)
            trace.forEach { (t, v) -> bin(t).let { sums[it] += v; counts[it]++ } }
            var last = 0
            val speeds = (0 until n).map { i -> (if (counts[i] > 0) (sums[i] / counts[i]).roundToInt() else last).also { last = it } }
            return Graph(speeds, marks.map { (t, k) -> k to bin(t) })
        }
    }
}

/**
 * 차 신호를 받아 운전 하나의 지표를 쌓는다(data-model §1·§2). 시계는 주입한다(테스트는 가짜 시계).
 * 첫 기어 값은 기준점이다. 운전 도중에 시작했다면 그 운전은 세지 않는다.
 */
class Tracker(private val clock: () -> Long) {
    private var parked: Boolean? = null
    var driving = false
        private set

    /** 기어가 P가 아님(세지 않는 운전 포함). 첫 기어 값 전에는 false */
    val notParked get() = parked == false

    // 지금 신호 상태. null = 아직 한 번도 받지 못함
    private var odo: Double? = null
    private var brakeOn: Boolean? = null
    private var turnSide: Int? = null // −1 왼쪽, 0 꺼짐, 1 오른쪽
    private var turnOnAt: Long? = null
    private val signalOn get() = turnSide.let { it != null && it != 0 }
    private var reverse = false
    private var kmh = 0.0 // 마지막 추정 속도
    private var steerDeg: Double? = null
    private var battery: Double? = null
    private var cruiseOn: Boolean? = null // null = 크루즈 값 없음(준비 안 됨 포함)
    private var wiperOn: Boolean? = null
    private var socNow: Int? = null
    private var seatNow: Boolean? = null
    private var lat: Double? = null
    private var lon: Double? = null

    // 운전 하나
    private var start = 0L
    private var km = 0.0
    private var movingMs = 0L
    private var hardAccel = 0
    private var hardDecel = 0
    private var brakes = 0
    private var turns = 0
    private var early = 0
    private var quick = 0
    private var inAccel = false
    private var inDecel = false
    private var inTurn = false
    private var lastQuick: Long? = null
    private var batteryStart: Double? = null
    private val cruise = OnTime()
    private val wiper = OnTime()
    private var socStart: Int? = null
    private var passenger: Boolean? = null
    private var coarse = false
    private val kmSamples = ArrayDeque<Pair<Long, Double>>()
    private val speeds = ArrayDeque<Pair<Long, Double>>()
    private val moving = ArrayList<Double>()
    private val steers = ArrayDeque<Pair<Long, Double>>()
    // 3차: 운전 중 추정 속도 표본(시각, km/h). ponytail: 약 1초에 하나라 2시간 운전이면 7천 개 안팎(수백 KB).
    // 아주 긴 운전이 문제가 되면 구간 수를 정한 뒤 초 단위 합으로 바꾼다
    private val trace = ArrayList<Pair<Long, Double>>()
    private val marks = ArrayList<Pair<Long, Char>>() // 3차: 급가속 'a'·급감속 'd'·빠른 조향 's'를 센 시각

    /** 기어. 운전이 끝나는 순간(P)에만 지표를 돌려준다. 후진 중 핸들 조작은 세지 않는다 */
    fun gear(isParked: Boolean, isReverse: Boolean = false): Metrics? {
        reverse = isReverse
        val prev = parked
        parked = isParked
        if (prev == null || prev == isParked) return null
        if (!isParked && prev) startDrive()
        return if (isParked && driving) endDrive() else null
    }

    /** 누적 주행거리(km). 2초 이상 앞선 표본과 비교해 속도를 낸다. 표본이 끊기면 다음 표본에서 그 사이 평균으로 본다 */
    fun km(v: Double) {
        val prev = odo
        odo = v
        if (!driving) return
        if (Math.ulp(v.toFloat()) > COARSE_ULP_KM) coarse = true
        val now = clock()
        if (prev != null && v < prev) { // 줄어든 값은 버리고 기준만 바꾼다
            kmSamples.clear(); speeds.clear(); kmSamples.addLast(now to v); return
        }
        if (prev != null) km += v - prev
        kmSamples.addLast(now to v)
        while (kmSamples.size > 2 && kmSamples[1].first <= now - 10_000) kmSamples.removeFirst()
        val base = kmSamples.lastOrNull { it.first <= now - 2_000 } ?: return
        speed(now, (v - base.second) / ((now - base.first) / 3_600_000.0))
    }

    private fun speed(now: Long, v: Double) {
        kmh = v
        trace += now to v
        if (v > MOVING_KMH) {
            speeds.lastOrNull()?.let { movingMs += now - it.first }
            moving += v
        }
        speeds.lastOrNull { it.first <= now - 2_000 }?.let { (t, v0) ->
            val a = (v - v0) / 3.6 / ((now - t) / 1000.0) // m/s²
            if (!inAccel && a >= HARD_ACCEL) { hardAccel++; marks += now to 'a'; inAccel = true } else if (inAccel && a < 1.0) inAccel = false
            if (!inDecel && a <= HARD_DECEL) { hardDecel++; marks += now to 'd'; inDecel = true } else if (inDecel && a > -1.0) inDecel = false
        }
        speeds.addLast(now to v)
        while (speeds.size > 2 && speeds[1].first <= now - 10_000) speeds.removeFirst()
    }

    fun brake(on: Boolean) {
        if (driving && on && brakeOn == false) brakes++
        brakeOn = on
    }

    /** 방향지시등: −1 왼쪽, 0 꺼짐, 1 오른쪽. 켜거나 방향을 바꾼 때부터 잰다 */
    fun turn(side: Int) {
        if (side != 0 && side != turnSide) turnOnAt = clock()
        turnSide = side
    }

    fun steer(deg: Double) {
        steerDeg = deg
        if (!driving) return
        val now = clock()
        // 1초 창. 창 시작 때의 각도는 그 직전 표본 값이다(각도는 바뀔 때 온다). 운전 시작 값은 startDrive가 넣는다
        steers.addLast(now to deg)
        while (steers.size > 1 && steers[1].first <= now - 1_000) steers.removeFirst()
        val a = abs(deg)
        if (inTurn && a < TURN_DEG / 2) inTurn = false
        if (reverse || kmh <= MOVING_KMH) return // 서 있거나 후진하며 돌린 핸들(주차)은 세지 않는다
        val span = steers.maxOf { it.second } - steers.minOf { it.second } // 부호를 넘는 빠른 조향도 잡는다
        if (span >= QUICK_DEG && lastQuick.let { it == null || now - it > 2_000 }) { quick++; marks += now to 's'; lastQuick = now }
        if (!inTurn && a >= TURN_DEG) {
            inTurn = true
            turns++
            val on = turnOnAt
            if (on != null && (signalOn || now - on <= 10_000) && now - on >= 1_500) early++
            if (!signalOn) turnOnAt = null // 꺼진 신호는 이 회전에서 쓰고 끝
        }
    }

    /** 크루즈: null = 준비 안 됨(값 없음), true = 작동(ACTIVATED), false = 그 밖(대기 등) */
    fun cruise(active: Boolean?) {
        cruiseOn = active
        if (driving) cruise.set(active, clock())
    }

    fun wiper(on: Boolean) {
        wiperOn = on
        if (driving) wiper.set(on, clock())
    }

    /**
     * 운전이 시작됐는데 시작 값이 없었으면(구독 값이 늦게 옴) 처음 온 값을 시작 값으로 본다.
     * [start]는 운전 시작 때 getter로 다시 읽은 값이다. 구독 첫 값이 엇갈려 와도(research R8) 이것이 시작 값이다
     */
    fun soc(pct: Int, start: Boolean = false) {
        socNow = pct
        if (driving && (start || socStart == null)) socStart = pct
    }

    fun seat(occupied: Boolean, start: Boolean = false) {
        seatNow = occupied
        if (driving && (start || passenger == null)) passenger = occupied
    }

    /** 마지막 위치 한 점만 든다. 경로는 남기지 않는다 */
    fun place(lat: Double, lon: Double) { this.lat = lat; this.lon = lon }

    fun batteryWh(v: Double) {
        battery = v
        if (driving && batteryStart == null) batteryStart = v
    }

    private fun startDrive() {
        driving = true
        start = clock()
        km = 0.0; movingMs = 0; hardAccel = 0; hardDecel = 0; brakes = 0; turns = 0; early = 0; quick = 0
        inAccel = false; inDecel = false; inTurn = abs(steerDeg ?: 0.0) >= TURN_DEG; lastQuick = null; kmh = 0.0; coarse = false
        kmSamples.clear(); speeds.clear(); moving.clear(); steers.clear(); trace.clear(); marks.clear()
        odo?.let { kmSamples.addLast(start to it) }
        steerDeg?.let { steers.addLast(start to it) }
        batteryStart = battery
        cruise.start(cruiseOn, start); wiper.start(wiperOn, start)
        socStart = socNow; passenger = seatNow
        lat = null; lon = null // 끝난 곳은 이번 운전 중에 받은 위치만. 못 받으면 "위치 없음"
        // 운전 시작 때 이미 켜져 있던 방향지시등·브레이크는 시작 때 켠 것. 운전 전에 켰다 끈 기록은 쓰지 않는다
        turnOnAt = if (signalOn) start else null
        if (brakeOn == true) brakes = 1
    }

    private fun endDrive(): Metrics {
        driving = false
        val end = clock()
        val used = batteryStart?.let { s -> battery?.let { s - it } }
        val steer = steerDeg != null
        return Metrics(
            durationMs = end - start,
            km = km,
            movingMs = movingMs,
            hardAccel = hardAccel.takeUnless { coarse },
            hardDecel = hardDecel.takeUnless { coarse },
            brakes = brakes.takeIf { brakeOn != null },
            turns = turns.takeIf { steer },
            early = early.takeIf { steer && turnSide != null },
            quickSteers = quick.takeIf { steer },
            speedCv = cv(moving).takeUnless { coarse },
            kwhPer100 = if (used != null && used > 0 && km > 0) used / 1000 / km * 100 else null,
            cruiseMs = cruise.end(end),
            socStart = socStart,
            socEnd = socNow,
            endLat = lat,
            endLon = lon,
            passenger = passenger,
            wiperMs = wiper.end(end),
            graph = if (coarse) null else Graph.of(start, end, trace, marks), // 거친 주행거리면 속도가 잡음이라 없다
        )
    }

    /** 켜진 시간 세기(크루즈·와이퍼). null이 아닌 값을 한 번도 못 받은 운전은 null */
    private class OnTime {
        private var on = false
        private var since = 0L
        private var total = 0L
        private var seen = false

        fun start(v: Boolean?, now: Long) { total = 0; seen = v != null; on = v == true; since = now }

        fun set(v: Boolean?, now: Long) {
            if (on) total += now - since
            if (v != null) seen = true
            on = v == true; since = now
        }

        fun end(now: Long): Long? {
            if (on) total += now - since
            on = false
            return total.takeIf { seen }
        }
    }

    companion object {
        const val MOVING_KMH = 5.0
        const val HARD_ACCEL = 2.5
        const val HARD_DECEL = -3.0
        const val TURN_DEG = 90.0
        const val QUICK_DEG = 90.0

        /** 주행거리 Float의 최소 단위(ulp)가 2m를 넘으면(32,768km 이상) 2초 창 속도가 잡음이 된다 */
        const val COARSE_ULP_KM = 0.002

        fun cv(xs: List<Double>): Double? {
            if (xs.size < 3) return null
            val mean = xs.average()
            return sqrt(xs.sumOf { (it - mean) * (it - mean) } / xs.size) / mean
        }
    }
}

/** 보류·짧은 운전 기준. 시연용(디버그) 빌드는 낮춘다(research R4) */
data class Limits(
    val minDriveMs: Long, val minDriveKm: Double, val minAxisKm: Double, val minMovingMs: Long, val minTurns: Int,
    // 보조 태그(specs/002-tags data-model §3)
    val longMs: Long, val longKmh: Double, val rainMs: Long, val rangeMin: Int, val situMin: Int,
) {
    companion object {
        val RELEASE = Limits(3 * MINUTE, 1.0, 2.0, 3 * MINUTE, 3, 30 * MINUTE, 60.0, 3 * MINUTE, 20, 3)
        val DEMO = Limits(30_000, 0.2, 0.3, 30_000, 2, MINUTE, 20.0, 30_000, 5, 2)
    }
}

/** 축(contracts §1). 값 −1은 앞 글자, +1은 뒤 글자 */
enum class AxisId(
    val title: String, val left: Char, val right: Char,
    val leftName: String, val rightName: String, val leftText: String, val rightText: String,
) {
    DYN("역동성", 'G', 'D', "부드러움", "역동적", "속도와 핸들을 부드럽게 바꿔요", "속도와 핸들을 시원시원하게 바꿔요"),
    RES("대응 방식", 'P', 'R', "예측형", "반응형", "회전 전에 방향지시등을 미리 켜요", "방향지시등을 회전에 가깝게 켜거나 생략해요"),
    RHY("주행 리듬", 'S', 'F', "안정형", "유동형", "속도를 고르게 유지해요", "흐름에 맞춰 속도를 자주 바꿔요"),
    PUR("운전 목적", 'E', 'C', "효율", "편안함", "에너지를 아껴 써요", "에너지보다 편안함을 택해요"),
}

/** 축 값 → 글자. 0 이상이면 뒤 글자, 보류(null)면 "·" */
fun AxisId.letter(v: Double?) = v?.let { if (it >= 0) right else left } ?: '·'

/** 축 판정. value null = 보류(이유는 withheld). fact = 관측 문장(지표가 없으면 null) */
data class Axis(val id: AxisId, val value: Double?, val fact: String?, val withheld: String?) {
    val letter get() = id.letter(value)
    val name get() = value?.let { if (it >= 0) id.rightName else id.leftName } ?: "보류"
}

private fun Double.clamp() = coerceIn(-1.0, 1.0)

object Judge {
    fun analyzed(m: Metrics, l: Limits) = m.durationMs >= l.minDriveMs && m.km >= l.minDriveKm

    /** [rates]가 false면 역동성·전력 줄을 환산 없이 센 수·쓴 양으로 쓴다(두 운전 비교: 두 줄의 단위를 맞춘다) */
    fun axes(m: Metrics, l: Limits, rates: Boolean = true): List<Axis> = listOf(dyn(m, l, rates), res(m, l), rhy(m, l), pur(m, l, rates))

    fun code(m: Metrics, l: Limits) = Types.code(axes(m, l).map { it.value })

    private const val COARSE = "주행거리 값이 거칠어 판정을 미뤘어요"

    private fun shortKm(m: Metrics, l: Limits) = if (m.km < l.minAxisKm) "거리가 짧아 판정을 미뤘어요(${m.km.km1()}km)" else null

    private fun dyn(m: Metrics, l: Limits, rates: Boolean): Axis {
        val q = m.quickSteers
        val idx = m.dynIdx
        val why = when {
            m.noSpeed -> COARSE
            q == null -> "조향 신호가 없어 판정을 미뤘어요"
            else -> shortKm(m, l)
        }
        val counts = listOfNotNull(m.hardAccel?.let { "급가속 ${it}번" }, m.hardDecel?.let { "급감속 ${it}번" }, q?.let { "빠른 조향 ${it}번" })
            .joinToString(" · ").ifEmpty { null }?.let {
                when { // 4차: 환산은 단위(10km)의 10분의 1 이상일 때만. 더 짧으면 센 수와 거리(contracts §4)
                    why != null -> it
                    rates && m.km >= 1.0 -> "$it (10km당 ${idx.one()})"
                    else -> "${m.km.km1()}km 동안 $it"
                }
            }
        val fact = listOfNotNull(counts, m.brakes?.let { "브레이크 ${it}번" }).joinToString(" · ").ifEmpty { null }
        return Axis(AxisId.DYN, if (why == null) ((idx - 3) / 3).clamp() else null, fact, why)
    }

    private fun res(m: Metrics, l: Limits): Axis {
        val t = m.turns; val e = m.early
        val why = when {
            t == null || e == null -> "조향·방향지시등 신호가 없어 판정을 미뤘어요"
            t < l.minTurns -> "큰 회전이 적어 판정을 미뤘어요(회전 ${t}번)"
            else -> null
        }
        val fact = t?.let { if (e != null && it > 0) "큰 회전 ${it}번 중 ${e}번은 방향지시등을 1.5초 이상 미리 켰어요" else "큰 회전 ${it}번" }
        return Axis(AxisId.RES, if (why == null) ((0.6 - e!!.toDouble() / t!!) / 0.4).clamp() else null, fact, why)
    }

    private fun rhy(m: Metrics, l: Limits): Axis {
        val cv = m.speedCv
        val fact = listOfNotNull(cv?.let { "속도 변동 ${(it * 100).roundToInt()}%" }, "평균 시속 ${m.avgKmh.roundToInt()}km").joinToString(" · ")
        val why = when {
            m.noSpeed -> COARSE
            cv == null || m.movingMs < l.minMovingMs -> "움직인 시간이 짧아 판정을 미뤘어요"
            else -> null
        }
        return Axis(AxisId.RHY, if (why == null) ((cv!! - 0.35) / 0.25).clamp() else null, fact, why)
    }

    private fun pur(m: Metrics, l: Limits, rates: Boolean): Axis {
        val k = m.kwhPer100
        val why = if (k == null) "배터리 사용량을 알 수 없어 판정을 미뤘어요" else shortKm(m, l)
        // 4차(코드 리뷰 반영): 판정한 축은 판정 근거인 100km당 환산을, 미룬 축(짧은 운전)은 쓴 양과 거리를 보인다
        return Axis(AxisId.PUR, if (why == null) ((k!! - 16) / 6).clamp() else null, k?.let { if (why == null && rates) "100km당 ${it.one()}kWh를 썼어요" else used(it, m.km) }, why)
    }

    private fun used(kwhPer100: Double, km: Double): String {
        val kwh = kwhPer100 * km / 100
        return if (kwh < 0.05) "0.1kWh보다 적게 썼어요(${km.km1()}km)" else "${kwh.one()}kWh를 썼어요(${km.km1()}km)"
    }
}

/** 유형 코드와 별칭(data-model §4, contracts §4) */
object Types {
    val ALIASES = linkedMapOf(
        "GPSE" to "차분한 계획가", "GPSC" to "여유로운 크루저", "GPFE" to "유연한 길잡이", "GPFC" to "느긋한 탐험가",
        "GRSE" to "담백한 실속파", "GRSC" to "편안한 동반자", "GRFE" to "가벼운 산책자", "GRFC" to "마음 편한 산책자",
        "DPSE" to "정확한 페이스메이커", "DPSC" to "노련한 투어러", "DPFE" to "민첩한 내비게이터", "DPFC" to "활기찬 탐험가",
        "DRSE" to "경쾌한 실속파", "DRSC" to "시원한 크루저", "DRFE" to "전략적 드라이버", "DRFC" to "자유로운 드라이버",
    )

    fun code(values: List<Double?>) = AxisId.entries.mapIndexed { i, a -> a.letter(values[i]) }.joinToString("")

    /** 별칭. 보류 축이 있으면 "{k}개 축은 판정 보류" */
    fun alias(code: String) = ALIASES[code] ?: "${code.count { it == '·' }}개 축은 판정 보류"

    /** 운전이 끝날 때 한 번 말하는 문장(contracts §8). 코드 글자는 띄어 읽는다 */
    fun speech(code: String) = if ('·' in code) "이번 운전 DNA가 나왔어요. ${code.count { it == '·' }}개 축은 판정을 미뤘어요. 근거는 화면에서 볼 수 있어요."
        else "이번 운전은 ${code.toList().joinToString(" ")}, ${alias(code)} 쪽이었어요. 근거는 화면에서 볼 수 있어요."

    /** 보류 축("·")은 어느 글자와도 맞는다 */
    fun matches(code: String, full: String) = code.indices.all { code[it] == '·' || code[it] == full[it] }
}

/** 나의 DNA(data-model §5): 분석된 최신 기록 10개의 축별 평균 */
data class Profile(val values: List<Double?>, val drives: Int) {
    val code get() = Types.code(values)

    companion object {
        fun of(analyzedNewestFirst: List<Metrics>, l: Limits): Profile {
            val recent = analyzedNewestFirst.take(10)
            val rows = recent.map { m -> Judge.axes(m, l).map { it.value } }
            val values = AxisId.entries.indices.map { i -> rows.mapNotNull { it[i] }.takeIf { it.isNotEmpty() }?.average() }
            return Profile(values, recent.size)
        }

        /** 이 운전과 그 전까지의 나의 DNA가 가장 크게(0.5 이상) 다른 축 한 줄. 비교할 것이 없으면 null */
        fun difference(axes: List<Axis>, before: Profile): String? {
            val (i, d) = axes.indices.mapNotNull { i ->
                val c = axes[i].value; val p = before.values[i]
                if (c != null && p != null) i to c - p else null
            }.maxByOrNull { abs(it.second) } ?: return null
            if (abs(d) < 0.5) return null
            val a = AxisId.entries[i]
            return "${a.title}이 평소보다 ${if (d > 0) a.rightName else a.leftName} 쪽이었어요."
        }
    }
}

/** 코칭 한 줄(data-model §6, contracts §5): 기준 대비 비율이 1 이상인 후보 중 가장 큰 것. 같으면 표 순서 */
object Coach {
    const val DEFAULT = "눈에 띄는 급한 조작 없이 운전을 마쳤어요."

    /** [energy]: 전력 축을 판정했을 때만 전력 환산 문장을 후보로 둔다(미룬 짧은 운전에서 부풀린 100km당을 말하지 않게) */
    fun line(m: Metrics, energy: Boolean = true): String {
        val t = m.turns ?: 0
        val missed = t - (m.early ?: t)
        val cv = m.speedCv ?: 0.0
        val k = m.kwhPer100 ?: 0.0
        return listOf(
            m.per10(m.hardDecel) to "급하게 속도를 줄인 순간이 ${m.hardDecel}번 있었어요. 앞차와 거리를 조금 더 두면 브레이크를 부드럽게 쓸 수 있어요.",
            m.per10(m.hardAccel) to "속도를 급하게 올린 순간이 ${m.hardAccel}번 있었어요. 출발할 때 페달을 천천히 밟으면 전력도 아낄 수 있어요.",
            m.per10(m.quickSteers) to "핸들을 빠르게 크게 돌린 순간이 ${m.quickSteers}번 있었어요. 회전 전에 속도를 먼저 줄이면 핸들이 부드러워져요.",
            (if (t >= 2 && m.early != null) missed.toDouble() / t / 0.4 else 0.0) to
                "큰 회전 ${t}번 중 ${missed}번은 방향지시등을 미리 켜지 않았어요. 회전 2~3초 전에 켜 두면 주변 차가 미리 알 수 있어요.",
            cv / 0.35 to "속도가 자주 오르내렸어요(변동 ${(cv * 100).roundToInt()}%). 흐름이 고른 길에서는 크루즈 컨트롤을 써 볼 수 있어요.",
            (if (energy) k / 18 else 0.0) to "100km당 ${k.one()}kWh를 썼어요. 가속을 부드럽게 하면 전력을 아낄 수 있어요.",
        ).filter { it.first >= 1.0 }.maxByOrNull { it.first }?.second ?: DEFAULT
    }
}

/** 운전 기록 저장 형식(data-model §7): 한 줄에 하나, 숫자만, 쉼표. 빈 칸 = 없음 */
object RecordFormat {
    fun encode(list: List<Metrics>) = list.joinToString("\n") { m ->
        listOf(m.endedAt, m.durationMs, m.km, m.movingMs, m.hardAccel, m.hardDecel, m.brakes, m.turns, m.early,
            m.quickSteers, m.speedCv, m.kwhPer100, if (m.analyzed) 1 else 0,
            m.cruiseMs, m.socStart, m.socEnd, m.endLat, m.endLon, m.passenger?.let { if (it) 1 else 0 }, m.wiperMs,
            // 3차 20·21번: 속도 칸과 사건, 칸 안 구분자는 "/"(research R12)
            m.graph?.speeds?.joinToString("/"), m.graph?.marks?.joinToString("/") { "${it.first}${it.second}" },
        ).joinToString(",") { it?.toString() ?: "" }
    }

    fun decode(s: String?): List<Metrics> = s.orEmpty().lines().mapNotNull { line ->
        val p = line.split(',')
        if (p.size != 13 && p.size != 20 && p.size != 22) return@mapNotNull null // 13칸은 1차, 20칸은 2차 형식
        fun int(i: Int) = p.getOrNull(i)?.ifEmpty { null }?.toInt()
        fun lng(i: Int) = p.getOrNull(i)?.ifEmpty { null }?.toLong()
        fun dbl(i: Int) = p.getOrNull(i)?.ifEmpty { null }?.toDouble()
        runCatching {
            Metrics(p[0].toLong(), p[1].toLong(), p[2].toDouble(), p[3].toLong(), int(4), int(5),
                int(6), int(7), int(8), int(9), dbl(10), dbl(11), p[12] == "1",
                lng(13), int(14), int(15), dbl(16), dbl(17), p.getOrNull(18)?.ifEmpty { null }?.let { it == "1" }, lng(19),
                graph(p.getOrNull(20), p.getOrNull(21)))
        }.getOrNull()
    }

    /** 그래프 칸이 어긋나면 그 줄은 남기고 그래프만 null이다(위임 결정 D10): 화면은 늘 범위 안의 칸만 읽는다 */
    private fun graph(speeds: String?, marks: String?): Graph? = runCatching {
        val v = speeds?.ifEmpty { null }?.split('/')?.map { it.toInt() } ?: return null
        if (v.isEmpty() || v.size > 40) return null
        val m = marks.orEmpty().ifEmpty { null }?.split('/')?.map { it[0] to it.substring(1).toInt() }.orEmpty()
        if (m.any { (k, i) -> k !in Graph.KINDS || i !in v.indices }) return null
        Graph(v, m)
    }.getOrNull()

    /** 오래된 것이 앞. 최신 100개만 남긴다 */
    fun add(old: List<Metrics>, m: Metrics) = (old + m).takeLast(100)
}

/* ---------- 보조 태그(specs/002-tags data-model §4·§5) ---------- */

/** 태그 종류. left·right는 판정 이름(value false·true) */
enum class TagId(val title: String, val left: String, val right: String) {
    CRUISE("크루즈", "직접 운전형", "크루즈 위임형"),
    CHARGE("충전", "한계 활용형", "여유 충전형"),
    RANGE("이동 범위", "루틴형", "탐험형"),
    PASSENGER("동승", "동승 한결형", "동승 맞춤형"),
    RAIN("빗길", "빗길 한결형", "빗길 맞춤형"),
}

/** 3차 두 운전 비교(data-model §3) */
object Compare {
    /** (다른 축, 같은 축, 보류 축). 보류 축 = 한쪽이라도 보류. 합은 4(위임 결정 D3) */
    fun counts(a: Metrics, b: Metrics, l: Limits): Triple<Int, Int, Int> {
        val pairs = Judge.axes(a, l).zip(Judge.axes(b, l))
        val held = pairs.count { (x, y) -> x.value == null || y.value == null }
        val diff = pairs.count { (x, y) -> x.value != null && y.value != null && x.letter != y.letter }
        return Triple(diff, 4 - held - diff, held)
    }
}

/** 3차 태그 바뀜(data-model §4): NEW = 새로 정해짐, CHANGED = 방금 운전으로 바뀜 */
enum class Change { NONE, CHANGED, NEW }

/** before: 지난 문장(지난 fact, 없으면 보류 이유, 전에 없던 태그면 "이전에는 없던 태그"). NONE이면 null */
data class TagView(val now: Tag, val change: Change, val before: String?)

/** value null = 보류(withheld가 이유). fact = 관측 문장(대상이 최소 수보다 적으면 null) */
data class Tag(val id: TagId, val value: Boolean?, val fact: String?, val withheld: String?) {
    val name get() = value?.let { if (it) id.right else id.left }
}

/** 같은편 Pick.kt의 xy와 같은 평면 근사(m). cos에는 첫 점의 위도 */
fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double) =
    Math.hypot((lon2 - lon1) * Math.cos(Math.toRadians(lat1)) * 111_320.0, (lat2 - lat1) * 110_540.0)

object Tags {
    private const val MIN = 3 // 크루즈·충전 최소 횟수
    private const val CHARGE_JUMP = 10 // 다음 시작 SOC − 앞 끝 SOC가 이만큼(%p) 이상이면 충전
    private const val PLACE_M = 150.0

    /** [oldestFirst]는 기록 전체(오래된 것부터). 숨긴 태그는 목록에 없다 */
    fun all(oldestFirst: List<Metrics>, l: Limits): List<Tag> {
        val a = oldestFirst.filter { it.analyzed } // 나의 DNA와 같은 운전 묶음(저장 때 정한 값)
        return listOfNotNull(
            cruise(a, l), charge(oldestFirst), range(a, l),
            situation(TagId.PASSENGER, "동승", "혼자", a, l) { it.passenger },
            situation(TagId.RAIN, "비", "맑은", a, l) { m -> m.wiperMs?.let { it >= l.rainMs } },
        )
    }

    /**
     * 가장 최근 분석 운전 앞까지 자른 기록(그 뒤의 짧은 운전도 빠짐, 위임 결정 D5)과 지금 기록의 태그를 견준다.
     * 분석 운전이 2개 미만이면 모두 NONE이다(위임 결정 D11)
     */
    fun changes(oldestFirst: List<Metrics>, l: Limits): List<TagView> {
        val now = all(oldestFirst, l)
        if (oldestFirst.count { it.analyzed } < 2) return now.map { TagView(it, Change.NONE, null) }
        return diff(all(oldestFirst.take(oldestFirst.indexOfLast { it.analyzed }), l), now)
    }

    /**
     * 태그마다: 지금 정해졌고 전에 없음·보류·다른 판정이면 NEW, 그 밖에 판정이나 근거 문장(fact)이 다르면 CHANGED.
     * 보류 이유만 바뀐 것("운전이 2번 쌓였어요" → "3번")은 근거가 아니라 쌓인 수라 NONE이다(구현 중 바뀜, approval-gate)
     */
    fun diff(before: List<Tag>, now: List<Tag>): List<TagView> = now.map { t ->
        val b = before.firstOrNull { it.id == t.id }
        val change = when {
            t.value != null && (b == null || b.value != t.value) -> Change.NEW
            b?.value != t.value || b?.fact != t.fact -> Change.CHANGED
            else -> Change.NONE
        }
        TagView(t, change, when {
            change == Change.NONE -> null
            b == null -> "이전에는 없던 태그"
            else -> b.fact ?: b.withheld
        })
    }

    private fun median(xs: List<Double>) = xs.sorted().let { s -> if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }

    private fun pct(x: Double) = (x * 100).roundToInt()

    /** 기준 사이(left ≤ … ≤ right 바깥이 판정)로 정하고 사이는 [middle] 이유로 보류 */
    private fun judge(id: TagId, v: Double, leftMax: Double, rightMin: Double, fact: String, middle: String) = when {
        v >= rightMin -> Tag(id, true, fact, null)
        v <= leftMax -> Tag(id, false, fact, null)
        else -> Tag(id, null, fact, middle)
    }

    private fun cruise(a: List<Metrics>, l: Limits): Tag? {
        if (a.none { it.cruiseMs != null }) return null // 크루즈 값이 없는 차
        val long = a.filter { it.cruiseMs != null && it.durationMs >= l.longMs && it.avgKmh >= l.longKmh }
        if (long.size < MIN) return Tag(TagId.CRUISE, null, null, "장거리 운전이 ${long.size}번뿐이에요(${MIN}번 필요)")
        val med = median(long.map { it.cruiseMs!!.toDouble() / it.durationMs })
        return judge(TagId.CRUISE, med, 0.15, 0.5, "장거리 운전 ${long.size}번의 크루즈 비율 중앙값 ${pct(med)}%", "크루즈를 쓰는 정도가 운전마다 달라요")
    }

    /** 짧은 운전도 이어 본다: 빼면 그 사이 충전을 놓친다 */
    private fun charge(all: List<Metrics>): Tag {
        val pre = all.zipWithNext().mapNotNull { (p, n) ->
            val e = p.socEnd; val s = n.socStart
            if (e != null && s != null && s - e >= CHARGE_JUMP) e.toDouble() else null
        }
        if (pre.size < MIN) return Tag(TagId.CHARGE, null, null, "충전이 ${pre.size}번뿐이에요(${MIN}번 필요)")
        val med = median(pre)
        return judge(TagId.CHARGE, med, 25.0, 40.0, "충전 ${pre.size}번의 충전 직전 배터리 중앙값 ${med.roundToInt()}%(집 충전 포함)", "충전하는 때가 그때그때 달라요")
    }

    private class Place(val lat: Double, val lon: Double, var count: Int = 1)

    private fun range(a: List<Metrics>, l: Limits): Tag {
        val pts = a.mapNotNull { m -> m.endLat?.let { lat -> m.endLon?.let { lat to it } } }
        if (pts.size < l.rangeMin) return Tag(TagId.RANGE, null, null, "운전이 ${pts.size}번 쌓였어요(${l.rangeMin}번 필요)")
        val places = mutableListOf<Place>()
        var fresh = 0
        for ((lat, lon) in pts) {
            // 기준점은 장소를 처음 만든 점(고정). 둘 이상 걸리면 가장 가까운 곳
            val near = places.map { it to meters(it.lat, it.lon, lat, lon) }.filter { it.second <= PLACE_M }.minByOrNull { it.second }?.first
            if (near != null) near.count++ else { places += Place(lat, lon); fresh++ }
        }
        val newRatio = fresh.toDouble() / pts.size
        val top2 = places.map { it.count }.sortedDescending().take(2).sum().toDouble() / pts.size
        return judge(TagId.RANGE, newRatio, 0.10, 0.30, "새 장소 ${pct(newRatio)}% · 자주 가는 2곳이 ${pct(top2)}%", "새 곳과 늘 가던 곳이 섞여 있어요")
    }

    /** 같은 사람의 평소와 그 상황을 비교한다. 변화의 크기만 보고 방향은 판정하지 않는다 */
    private fun situation(id: TagId, what: String, other: String, a: List<Metrics>, l: Limits, isSitu: (Metrics) -> Boolean?): Tag {
        val (s, n) = a.filter { it.hardAccel != null && isSitu(it) != null }.partition { isSitu(it) == true }
        if (s.size < l.situMin) return Tag(id, null, null, "$what 운전이 ${s.size}번뿐이에요(${l.situMin}번 필요)")
        if (n.size < l.situMin) return Tag(id, null, null, "$other 운전이 ${n.size}번뿐이에요(${l.situMin}번 필요)")
        val ms = s.map { it.dynIdx }.average(); val mn = n.map { it.dynIdx }.average()
        val c = if (mn > 0) (ms - mn) / mn else if (ms == 0.0) 0.0 else Double.POSITIVE_INFINITY
        val x = if (c.isInfinite()) 0 else pct(abs(c))
        val fact = when {
            c.isInfinite() -> "평소엔 급한 조작이 없었는데 $what 운전 ${s.size}번에선 있었어요"
            c == 0.0 -> "$what 운전 ${s.size}번의 역동성 지수가 평소와 같아요"
            abs(c) <= 0.10 -> "$what 운전 ${s.size}번의 역동성 지수가 평소와 비슷해요(차이 $x%)"
            else -> "$what 운전 ${s.size}번의 역동성 지수가 평소보다 $x% ${if (c < 0) "낮아요" else "높아요"}"
        }
        return judge(id, abs(c), 0.10, 0.25, fact, "평소와 차이가 애매해요(차이 $x%)")
    }
}
