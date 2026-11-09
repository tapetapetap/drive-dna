package kr.skku.drivedna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 가짜 시계로 신호를 흘려 넣는 운전 하나 */
private class Road(startKm: Double = 100.0) {
    var now = 0L
    var odo = startKm
    val t = Tracker { now }

    init {
        t.gear(true); t.km(odo); t.brake(false); t.turn(0); t.steer(0.0); t.batteryWh(60_000.0)
    }

    fun start() = apply { now += 1000; t.gear(false) }

    /** 시속 [kmh]로 [secs]초. 주행거리는 1초마다 그 순간 값으로 온다 */
    fun go(kmh: Double, secs: Int) = apply {
        repeat(secs) { now += 1000; odo += kmh / 3600; t.km(odo) }
    }

    /** 1초마다 [from]에서 [to]까지 고르게 */
    fun ramp(from: Double, to: Double, secs: Int) = apply {
        for (i in 1..secs) { now += 1000; odo += (from + (to - from) * i / secs) / 3600; t.km(odo) }
    }

    fun at(ms: Long, block: Tracker.() -> Unit) = apply { now += ms; t.block() }
    fun park() = t.gear(true)!!
}

class TrackerTest {
    @Test fun speedFromOdometer() {
        val m = Road().start().go(36.0, 60).park()
        assertEquals(0.6, m.km, 1e-6)
        assertEquals(58_000, m.movingMs) // 첫 2초는 비교할 표본이 없다
        assertEquals(37.2, m.avgKmh, 0.1)
        assertEquals(0.0, m.speedCv!!, 1e-6)
    }

    @Test fun decreasingOdometerIsIgnored() {
        val r = Road().start().go(36.0, 5)
        r.at(1000) { km(50.0) } // 거꾸로 간 값
        r.odo = 50.0
        val m = r.go(36.0, 5).park()
        assertEquals(0.1, m.km, 1e-6)
    }

    @Test fun hardAccelAndDecelWithHysteresis() {
        val m = Road().start()
            .ramp(0.0, 72.0, 4) // 5 m/s²
            .go(72.0, 6)
            .ramp(72.0, 0.0, 4) // −5 m/s²
            .go(0.0, 6)
            .ramp(0.0, 72.0, 4).go(72.0, 6) // 다시 급가속
            .ramp(72.0, 36.0, 10) // −1 m/s²: 급감속 아님
            .park()
        assertEquals(2, m.hardAccel)
        assertEquals(1, m.hardDecel)
    }

    @Test fun gentleChangesAreNotHard() {
        val m = Road().start().ramp(0.0, 50.0, 20).go(50.0, 30).ramp(50.0, 0.0, 15).park()
        assertEquals(0, m.hardAccel)
        assertEquals(0, m.hardDecel)
    }

    /** 0.2초마다 쓰고 1.02초마다 그때까지 쓴 값이 온다(research R1·R3): 시속 50km 유지는 흔들려도 급가감속이 아니다 */
    @Test fun steadySpeedWithOffsetDelivery() {
        val r = Road().start()
        var written = r.odo; var nextWrite = r.now; var nextDelivery = r.now + 1020
        while (r.now < 120_000) {
            r.now += 10
            if (r.now >= nextWrite) { written = 100.0 + 50.0 / 3600 * (r.now - 1000) / 1000; nextWrite += 200 }
            if (r.now >= nextDelivery) { r.t.km(written); nextDelivery += 1020 }
        }
        val m = r.park()
        assertEquals(0, m.hardAccel)
        assertEquals(0, m.hardDecel)
        assertTrue(m.speedCv!! < 0.05)
    }

    @Test fun bigTurnsRearmBelow45() {
        val r = Road().start().go(30.0, 3)
        listOf(100.0, 60.0, 100.0, 30.0, -95.0).forEach { d -> r.at(2000) { steer(d) } }
        assertEquals(2, r.park().turns)
    }

    @Test fun earlySignal() {
        val r = Road().start().go(30.0, 3)
        r.at(1000) { turn(-1) }.at(3000) { steer(60.0) }.at(1000) { steer(100.0) } // 4초 전 켬: 미리
        r.at(1000) { steer(0.0); turn(0) }
        r.at(5000) { turn(-1) }.at(1000) { steer(100.0) } // 1초 전: 늦음
        r.at(1000) { steer(0.0); turn(0) }
        val m = r.park()
        assertEquals(2, m.turns)
        assertEquals(1, m.early)
    }

    @Test fun signalTurnedOffIsUsedOnce() {
        val r = Road().start().go(30.0, 3)
        r.at(1000) { turn(-1) }.at(2000) { turn(0) }.at(1000) { steer(100.0) } // 3초 전에 켰다 끔: 미리
        r.at(1000) { steer(0.0) }.at(1000) { steer(100.0) } // 신호 없이 다시: 앞 신호를 또 쓰지 않는다
        assertEquals(1, r.park().early)
    }

    @Test fun signalOnBeforeDriveCountsFromStart() {
        val r = Road()
        r.at(20_000) { turn(-1) }
        r.start().go(30.0, 3).at(100) { steer(100.0) }
        assertEquals(1, r.park().early)
    }

    @Test fun signalTappedBeforeDriveIsNotEarly() {
        val r = Road()
        r.at(1000) { turn(-1) }.at(1000) { turn(0) } // P에서 켰다 끔
        r.start().go(30.0, 2).at(100) { steer(100.0) }
        val m = r.park()
        assertEquals(1, m.turns)
        assertEquals(0, m.early)
    }

    @Test fun switchingSideRestartsTiming() {
        val r = Road().start().go(30.0, 3)
        r.at(0) { turn(-1) }.at(30_000) { turn(1) }.at(500) { steer(-100.0) } // 왼쪽 30초 → 오른쪽으로 바꾸고 0.5초 뒤 회전
        assertEquals(0, r.park().early)
    }

    @Test fun parkingSteeringIsNotCounted() {
        val r = Road().start()
        r.at(1000) { steer(300.0) }.at(1000) { steer(-300.0) } // 서서 돌림
        r.at(0) { gear(false, isReverse = true) }.go(8.0, 4).at(500) { steer(200.0) } // 후진하며 돌림
        r.at(500) { steer(0.0) }.at(0) { gear(false) }.go(30.0, 3).at(500) { steer(100.0) } // 앞으로 달리며 회전
        val m = r.park()
        assertEquals(1, m.turns)
        assertEquals(1, m.quickSteers)
    }

    @Test fun coarseOdometerWithholdsSpeedAxes() {
        val m = Road(startKm = 40_000.0).start().go(50.0, 120).park() // Float 최소 단위 3.9m
        assertTrue(m.noSpeed)
        assertNull(m.speedCv)
        val a = Judge.axes(m, Limits.DEMO)
        assertEquals("주행거리 값이 거칠어 판정을 미뤘어요", a[0].withheld)
        assertEquals("주행거리 값이 거칠어 판정을 미뤘어요", a[2].withheld)
        assertEquals(0, Road(startKm = 30_000.0).start().go(50.0, 60).park().hardAccel) // 2m 이하는 그대로
    }

    @Test fun quickSteerIn1sWindowWith2sDedupe() {
        val r = Road().start().go(30.0, 3)
        r.at(3000) { steer(150.0) } // 한 번에 150°: 창 시작 값 0°와 비교
        r.at(1500) { steer(0.0) } // 2초 안: 중복
        r.at(3000) { steer(-60.0) }.at(500) { steer(60.0) } // 부호를 넘어 120°
        listOf(30.0, 60.0, 90.0, 120.0).forEach { d -> r.at(1000) { steer(d) } } // 초당 30°: 아님
        assertEquals(2, r.park().quickSteers)
    }

    @Test fun energyPer100km() {
        val r = Road().start().go(60.0, 60)
        r.at(1000) { batteryWh(59_900.0) }
        assertEquals(10.0, r.park().kwhPer100!!, 0.01)
        assertNull(Road().start().go(60.0, 60).park().kwhPer100) // 줄지 않음
    }

    @Test fun driveStartedWhileDrivingIsNotCounted() {
        var now = 0L
        val t = Tracker { now }
        t.gear(false) // 첫 값이 D
        assertTrue(t.notParked)
        now += 60_000
        assertNull(t.gear(true))
        assertFalse(t.notParked)
    }

    @Test fun missingSignalsAreNone() {
        var now = 0L
        val t = Tracker { now }
        t.gear(true); t.km(100.0)
        now += 1000; t.gear(false)
        repeat(60) { now += 1000; t.km(100.0 + (it + 1) * 0.01) }
        val m = t.gear(true)!!
        assertNull(m.turns); assertNull(m.early); assertNull(m.quickSteers); assertNull(m.brakes); assertNull(m.kwhPer100)
    }

    @Test fun brakePresses() {
        val r = Road().start()
        r.at(1000) { brake(true) }.at(1000) { brake(true) }.at(1000) { brake(false) }.at(1000) { brake(true) }
        assertEquals(2, r.park().brakes)
        val held = Road().at(0) { brake(true) }.start() // 운전 시작 때 밟고 있던 브레이크는 시작 때 밟은 것
        assertEquals(1, held.park().brakes)
    }
}

class JudgeTest {
    private val base = Metrics(durationMs = 10 * MINUTE, km = 5.0, movingMs = 9 * MINUTE, hardAccel = 0, hardDecel = 0,
        brakes = 4, turns = 5, early = 4, quickSteers = 0, speedCv = 0.2, kwhPer100 = 12.0)

    private fun code(m: Metrics, l: Limits = Limits.RELEASE) = Types.code(Judge.axes(m, l).map { it.value })

    @Test fun lettersFromThresholds() {
        assertEquals("GPSE", code(base))
        assertEquals("DRFC", code(base.copy(hardAccel = 1, hardDecel = 1, early = 1, speedCv = 0.5, kwhPer100 = 20.0)))
        // 경계: 10km당 3번이면 D(0 이상), 미리 켠 신호 60%면 R 쪽 0
        assertEquals('D', Judge.axes(base.copy(km = 10.0, hardAccel = 3), Limits.RELEASE)[0].letter)
        assertEquals(0.0, Judge.axes(base.copy(turns = 5, early = 3), Limits.RELEASE)[1].value!!, 1e-9)
    }

    @Test fun withheldWithReasons() {
        val short = base.copy(km = 1.5, turns = 2, early = 2, movingMs = 2 * MINUTE)
        val a = Judge.axes(short, Limits.RELEASE)
        assertEquals("····", Types.code(a.map { it.value }))
        assertEquals("거리가 짧아 판정을 미뤘어요(1.5km)", a[0].withheld)
        assertEquals("큰 회전이 적어 판정을 미뤘어요(회전 2번)", a[1].withheld)
        assertEquals("움직인 시간이 짧아 판정을 미뤘어요", a[2].withheld)
        assertEquals("거리가 짧아 판정을 미뤘어요(1.5km)", a[3].withheld)
        assertEquals("거리가 짧아 판정을 미뤘어요(0.2km)", Judge.axes(short.copy(km = 0.29), Limits.DEMO)[0].withheld) // 내림
        // 시연용 기준이면 판정한다
        assertEquals("GPSE", code(short, Limits.DEMO))
    }

    @Test fun missingSignalReasonsAndFacts() {
        val a = Judge.axes(base.copy(quickSteers = null, turns = null, early = null, brakes = null, kwhPer100 = null), Limits.RELEASE)
        assertEquals("··S·", Types.code(a.map { it.value }))
        assertEquals("조향 신호가 없어 판정을 미뤘어요", a[0].withheld)
        assertEquals("급가속 0번 · 급감속 0번", a[0].fact)
        assertEquals("조향·방향지시등 신호가 없어 판정을 미뤘어요", a[1].withheld)
        assertNull(a[1].fact)
        assertEquals("배터리 사용량을 알 수 없어 판정을 미뤘어요", a[3].withheld)
        assertEquals("급가속 0번 · 급감속 0번 · 빠른 조향 0번 (10km당 0.0) · 브레이크 4번", Judge.axes(base, Limits.RELEASE)[0].fact)
    }

    @Test fun shortDrive() {
        assertTrue(Judge.analyzed(base, Limits.RELEASE))
        assertFalse(Judge.analyzed(base.copy(durationMs = 2 * MINUTE), Limits.RELEASE))
        assertFalse(Judge.analyzed(base.copy(km = 0.9), Limits.RELEASE))
        assertTrue(Judge.analyzed(base.copy(durationMs = 40_000, km = 0.3), Limits.DEMO))
    }
}

class TypesProfileCoachTest {
    @Test fun aliasesAndWithheldCodes() {
        assertEquals(16, Types.ALIASES.values.toSet().size)
        assertEquals("차분한 계획가", Types.alias("GPSE"))
        assertEquals("1개 축은 판정 보류", Types.alias("GP·E"))
        assertTrue(Types.matches("GP·E", "GPFE"))
        assertFalse(Types.matches("GP·E", "GRSE"))
        assertEquals("이번 운전은 G P S E, 차분한 계획가 쪽이었어요. 근거는 화면에서 볼 수 있어요.", Types.speech("GPSE"))
        assertEquals("이번 운전 DNA가 나왔어요. 2개 축은 판정을 미뤘어요. 근거는 화면에서 볼 수 있어요.", Types.speech("G··E"))
    }

    private fun m(cv: Double, kwh: Double) = Metrics(durationMs = 10 * MINUTE, km = 5.0, movingMs = 9 * MINUTE,
        brakes = 0, turns = 5, early = 5, quickSteers = 0, speedCv = cv, kwhPer100 = kwh, analyzed = true)

    @Test fun profileAveragesLatestTen() {
        val list = List(10) { m(0.2, 12.0) } + List(5) { m(0.6, 25.0) } // 앞이 최신
        val p = Profile.of(list, Limits.RELEASE)
        assertEquals(10, p.drives)
        assertEquals("GPSE", p.code)
        assertNull(Profile.of(listOf(m(0.2, 12.0).copy(kwhPer100 = null)), Limits.RELEASE).values[3])
    }

    @Test fun differenceOverHalf() {
        val before = Profile.of(listOf(m(0.2, 12.0)), Limits.RELEASE)
        assertEquals("주행 리듬이 평소보다 유동형 쪽이었어요.", Profile.difference(Judge.axes(m(0.6, 12.0), Limits.RELEASE), before))
        assertNull(Profile.difference(Judge.axes(m(0.3, 12.0), Limits.RELEASE), before))
        // 같으면 축 순서(역동성 먼저)
        val wild = m(0.6, 30.0).copy(hardAccel = 5, early = 0)
        assertEquals("역동성이 평소보다 역동적 쪽이었어요.", Profile.difference(Judge.axes(wild, Limits.RELEASE), before))
    }

    @Test fun coachPicksLargestRatioThenTableOrder() {
        val calm = m(0.2, 12.0)
        assertEquals(Coach.DEFAULT, Coach.line(calm))
        assertTrue(Coach.line(calm.copy(hardAccel = 2)).startsWith("속도를 급하게 올린 순간이 2번"))
        assertTrue(Coach.line(calm.copy(hardAccel = 2, hardDecel = 2)).startsWith("급하게 속도를 줄인 순간이 2번"))
        assertTrue(Coach.line(calm.copy(early = 1)).startsWith("큰 회전 5번 중 4번은"))
    }

    @Test fun recordsRoundTrip() {
        val a = m(0.2, 12.0).copy(endedAt = 1_794_000_000_000)
        val b = Metrics(endedAt = 5, durationMs = 15_000, km = 0.08) // 신호 없음·짧은 운전
        val c = b.copy(hardAccel = null, hardDecel = null) // 주행거리가 거친 운전
        val text = RecordFormat.encode(listOf(a, b, c))
        assertEquals(listOf(a, b, c), RecordFormat.decode(text))
        assertEquals(listOf(a), RecordFormat.decode(text.lines()[0] + "\n1,2,x\n"))
        assertEquals(emptyList<Metrics>(), RecordFormat.decode(null))
        val many = (1..105).fold(emptyList<Metrics>()) { acc, i -> RecordFormat.add(acc, b.copy(endedAt = i.toLong())) }
        assertEquals(100, many.size)
        assertEquals(6L, many.first().endedAt)
    }
}

/** scripts/scenarios의 시연 시나리오를 SDK처럼 흘려 넣는다: 주행거리는 1.02초마다, 조향은 바뀔 때 */
class ScenarioTest {
    private fun replay(name: String): Metrics {
        val lines = File("../scripts/scenarios/$name.txt").readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
        val r = Road()
        r.start()
        var v = 0.0; var steer = 0.0; var nextKm = r.now + 1020; var sentSteer = 0.0; var kwh = 0.0
        for (line in lines) {
            val p = line.split(Regex("\\s+"))
            if (p[0] == "kwh") { kwh = p[1].toDouble(); continue }
            val secs = p[0].toDouble(); val v1 = p[1].toDouble(); val s1 = p[2].toDouble()
            r.t.brake(p[3] == "1"); r.t.turn(when (p[4]) { "Left" -> -1; "Right" -> 1; else -> 0 })
            val v0 = v; val s0 = steer; val steps = (secs / 0.2).toInt()
            for (i in 1..steps) {
                r.now += 200
                v = v0 + (v1 - v0) * i / steps; steer = s0 + (s1 - s0) * i / steps
                r.odo += v * 0.2 / 3600
                if (Math.round(steer).toDouble() != sentSteer) { sentSteer = Math.round(steer).toDouble(); r.t.steer(-sentSteer) }
                if (r.now >= nextKm) { r.t.km(r.odo); nextKm += 1020 }
            }
        }
        r.t.turn(0)
        r.at(200) { batteryWh(60_000.0 - kwh * 1000) }
        r.at(2000) { km(r.odo) }
        return r.park()
    }

    private fun code(m: Metrics) = Types.code(Judge.axes(m, Limits.DEMO).map { it.value })

    @Test fun gentleIsGPSE() {
        val m = replay("gentle")
        assertTrue(Judge.analyzed(m, Limits.DEMO))
        assertEquals("GPSE", code(m))
        assertEquals(Coach.DEFAULT, Coach.line(m))
    }

    @Test fun dynamicIsDRFC() {
        val m = replay("dynamic")
        assertEquals("DRFC", code(m))
        assertTrue(Coach.line(m), Coach.line(m).startsWith("급하게 속도를 줄인"))
    }

    @Test fun cityIsGPFE() = assertEquals("GPFE", code(replay("city")))

    @Test fun straightWithholdsResponse() {
        val m = replay("straight")
        val axes = Judge.axes(m, Limits.DEMO)
        assertEquals("G·SE", code(m))
        assertEquals("큰 회전이 적어 판정을 미뤘어요(회전 0번)", axes[1].withheld)
        assertEquals("1개 축은 판정 보류", Types.alias(code(m)))
        assertEquals("큰 회전 0번", axes[1].fact)
    }

    @Test fun shortIsNotAnalyzed() = assertFalse(Judge.analyzed(replay("short"), Limits.DEMO))
}
