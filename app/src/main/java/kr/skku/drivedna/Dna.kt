package kr.skku.drivedna

import java.util.Locale
import kotlin.math.abs
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
) {
    val avgKmh get() = if (movingMs > 0) km / (movingMs / 3_600_000.0) else 0.0

    /** 주행거리 값이 거칠어 속도를 추정하지 못함. 속도에서 나오는 지표(급가속·급감속·속도 변동)가 모두 없음 */
    val noSpeed get() = hardAccel == null
    fun per10(n: Int?) = if (km > 0 && n != null) n / km * 10 else 0.0
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
    private var coarse = false
    private val kmSamples = ArrayDeque<Pair<Long, Double>>()
    private val speeds = ArrayDeque<Pair<Long, Double>>()
    private val moving = ArrayList<Double>()
    private val steers = ArrayDeque<Pair<Long, Double>>()

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
        if (v > MOVING_KMH) {
            speeds.lastOrNull()?.let { movingMs += now - it.first }
            moving += v
        }
        speeds.lastOrNull { it.first <= now - 2_000 }?.let { (t, v0) ->
            val a = (v - v0) / 3.6 / ((now - t) / 1000.0) // m/s²
            if (!inAccel && a >= HARD_ACCEL) { hardAccel++; inAccel = true } else if (inAccel && a < 1.0) inAccel = false
            if (!inDecel && a <= HARD_DECEL) { hardDecel++; inDecel = true } else if (inDecel && a > -1.0) inDecel = false
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
        if (span >= QUICK_DEG && lastQuick.let { it == null || now - it > 2_000 }) { quick++; lastQuick = now }
        if (!inTurn && a >= TURN_DEG) {
            inTurn = true
            turns++
            val on = turnOnAt
            if (on != null && (signalOn || now - on <= 10_000) && now - on >= 1_500) early++
            if (!signalOn) turnOnAt = null // 꺼진 신호는 이 회전에서 쓰고 끝
        }
    }

    fun batteryWh(v: Double) {
        battery = v
        if (driving && batteryStart == null) batteryStart = v
    }

    private fun startDrive() {
        driving = true
        start = clock()
        km = 0.0; movingMs = 0; hardAccel = 0; hardDecel = 0; brakes = 0; turns = 0; early = 0; quick = 0
        inAccel = false; inDecel = false; inTurn = abs(steerDeg ?: 0.0) >= TURN_DEG; lastQuick = null; kmh = 0.0; coarse = false
        kmSamples.clear(); speeds.clear(); moving.clear(); steers.clear()
        odo?.let { kmSamples.addLast(start to it) }
        steerDeg?.let { steers.addLast(start to it) }
        batteryStart = battery
        // 운전 시작 때 이미 켜져 있던 방향지시등·브레이크는 시작 때 켠 것. 운전 전에 켰다 끈 기록은 쓰지 않는다
        turnOnAt = if (signalOn) start else null
        if (brakeOn == true) brakes = 1
    }

    private fun endDrive(): Metrics {
        driving = false
        val used = batteryStart?.let { s -> battery?.let { s - it } }
        val steer = steerDeg != null
        return Metrics(
            durationMs = clock() - start,
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
        )
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
data class Limits(val minDriveMs: Long, val minDriveKm: Double, val minAxisKm: Double, val minMovingMs: Long, val minTurns: Int) {
    companion object {
        val RELEASE = Limits(3 * MINUTE, 1.0, 2.0, 3 * MINUTE, 3)
        val DEMO = Limits(30_000, 0.2, 0.3, 30_000, 2)
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

    fun axes(m: Metrics, l: Limits): List<Axis> = listOf(dyn(m, l), res(m, l), rhy(m, l), pur(m, l))

    fun code(m: Metrics, l: Limits) = Types.code(axes(m, l).map { it.value })

    private const val COARSE = "주행거리 값이 거칠어 판정을 미뤘어요"

    private fun shortKm(m: Metrics, l: Limits) = if (m.km < l.minAxisKm) "거리가 짧아 판정을 미뤘어요(${m.km.km1()}km)" else null

    private fun dyn(m: Metrics, l: Limits): Axis {
        val q = m.quickSteers
        val idx = m.per10((m.hardAccel ?: 0) + (m.hardDecel ?: 0) + (q ?: 0))
        val why = when {
            m.noSpeed -> COARSE
            q == null -> "조향 신호가 없어 판정을 미뤘어요"
            else -> shortKm(m, l)
        }
        val counts = listOfNotNull(m.hardAccel?.let { "급가속 ${it}번" }, m.hardDecel?.let { "급감속 ${it}번" }, q?.let { "빠른 조향 ${it}번" })
            .joinToString(" · ").ifEmpty { null }?.let { if (why == null) "$it (10km당 ${idx.one()})" else it }
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

    private fun pur(m: Metrics, l: Limits): Axis {
        val k = m.kwhPer100
        val why = if (k == null) "배터리 사용량을 알 수 없어 판정을 미뤘어요" else shortKm(m, l)
        return Axis(AxisId.PUR, if (why == null) ((k!! - 16) / 6).clamp() else null, k?.let { "100km당 ${it.one()}kWh를 썼어요" }, why)
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

    fun line(m: Metrics): String {
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
            k / 18 to "100km당 ${k.one()}kWh를 썼어요. 가속을 부드럽게 하면 전력을 아낄 수 있어요.",
        ).filter { it.first >= 1.0 }.maxByOrNull { it.first }?.second ?: DEFAULT
    }
}

/** 운전 기록 저장 형식(data-model §7): 한 줄에 하나, 숫자만, 쉼표. 빈 칸 = 없음 */
object RecordFormat {
    fun encode(list: List<Metrics>) = list.joinToString("\n") { m ->
        listOf(m.endedAt, m.durationMs, m.km, m.movingMs, m.hardAccel, m.hardDecel, m.brakes, m.turns, m.early,
            m.quickSteers, m.speedCv, m.kwhPer100, if (m.analyzed) 1 else 0).joinToString(",") { it?.toString() ?: "" }
    }

    fun decode(s: String?): List<Metrics> = s.orEmpty().lines().mapNotNull { line ->
        val p = line.split(',')
        if (p.size != 13) return@mapNotNull null
        fun int(i: Int) = p[i].ifEmpty { null }?.toInt()
        fun dbl(i: Int) = p[i].ifEmpty { null }?.toDouble()
        runCatching {
            Metrics(p[0].toLong(), p[1].toLong(), p[2].toDouble(), p[3].toLong(), int(4), int(5),
                int(6), int(7), int(8), int(9), dbl(10), dbl(11), p[12] == "1")
        }.getOrNull()
    }

    /** 오래된 것이 앞. 최신 100개만 남긴다 */
    fun add(old: List<Metrics>, m: Metrics) = (old + m).takeLast(100)
}
