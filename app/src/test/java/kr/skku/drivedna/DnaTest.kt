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

    @Test fun titleSaysJustNowOnlyForRecentDrives() {
        val now = 1_794_000_000_000
        assertEquals("방금 끝난 운전", driveTitle(now - 5 * MINUTE, now))
        assertTrue(driveTitle(now - 60 * MINUTE, now).endsWith(" 운전"))
        assertFalse(driveTitle(now - 60 * MINUTE, now).startsWith("방금"))
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
        var socEnd: Int? = null; var place: Pair<Double, Double>? = null
        for (line in lines) {
            val p = line.split(Regex("\\s+"))
            when (p[0]) { // 보조 태그 지시어(specs/002-tags quickstart)
                "kwh" -> { kwh = p[1].toDouble(); continue }
                "soc" -> { r.t.soc(p[1].toInt()); socEnd = p[2].toInt(); continue }
                "seat" -> { r.t.seat(p[1] == "1"); continue }
                "place" -> { place = p[1].toDouble() to p[2].toDouble(); continue }
            }
            val secs = p[0].toDouble(); val v1 = p[1].toDouble(); val s1 = p[2].toDouble()
            r.t.brake(p[3] == "1"); r.t.turn(when (p[4]) { "Left" -> -1; "Right" -> 1; else -> 0 })
            val flags = p.getOrElse(5) { "" }
            r.t.cruise('C' in flags); r.t.wiper('W' in flags)
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
        socEnd?.let { r.t.soc(it) }
        place?.let { r.t.place(it.first, it.second) }
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

    @Test fun highwayUsesCruiseAndCharges() {
        val m = replay("highway")
        assertEquals(85, m.socStart); assertEquals(78, m.socEnd)
        assertTrue(m.cruiseMs!!.toDouble() / m.durationMs >= 0.5)
        assertEquals(false, m.passenger)
        assertEquals(37.2839144, m.endLat!!, 1e-9)
        assertTrue(Judge.analyzed(m, Limits.DEMO))
    }

    /** scripts/seed.txt(시연 샘플 24개)가 태그 다섯을 모두 판정하고, highway를 더해도 판정이 그대로인지 */
    @Test fun seedJudgesAllFiveTags() {
        val seed = RecordFormat.decode(File("../scripts/seed.txt").readText())
        assertEquals(24, seed.size)
        val want = mapOf(TagId.CRUISE to "크루즈 위임형", TagId.CHARGE to "여유 충전형", TagId.RANGE to "루틴형",
            TagId.PASSENGER to "동승 맞춤형", TagId.RAIN to "빗길 한결형")
        assertEquals(want, Tags.all(seed, Limits.DEMO).associate { it.id to it.name })
        val after = Tags.all(seed + replay("highway").copy(endedAt = seed.last().endedAt + MINUTE, analyzed = true), Limits.DEMO)
        assertEquals(want, after.associate { it.id to it.name })
        assertTrue(after.single { it.id == TagId.CRUISE }.fact!!.startsWith("장거리 운전 4번"))
        assertTrue(after.single { it.id == TagId.CHARGE }.fact!!.startsWith("충전 4번"))
    }
}

/** 보조 태그용 새 신호(specs/002-tags data-model §1·§2·§6, tasks T004) */
class TagSignalTest {
    @Test fun cruiseTimeAccumulatesUntilParked() {
        val m = Road().start().at(10_000) { cruise(true) }.at(20_000) { cruise(false) }.at(5_000) { cruise(true) }.at(5_000) {}.park()
        assertEquals(25_000L, m.cruiseMs) // 20초 + 끝까지 5초
    }

    @Test fun cruiseOnBeforeDriveCountsFromStart() {
        assertEquals(10_000L, Road().at(0) { cruise(true) }.start().at(10_000) {}.park().cruiseMs)
    }

    @Test fun cruiseValueRules() {
        assertNull(Road().start().at(1_000) { cruise(null) }.park().cruiseMs) // 준비 안 됨(null)만 → 값 없음
        assertNull(Road().start().go(36.0, 5).park().cruiseMs) // 아무 값도 없음
        assertEquals(0L, Road().start().at(1_000) { cruise(false) }.park().cruiseMs) // 대기(ENABLED) → 0
    }

    @Test fun wiperTimeLikeCruise() {
        val m = Road().start().at(1_000) { wiper(true) }.at(30_000) { wiper(false) }.park()
        assertEquals(30_000L, m.wiperMs)
        assertNull(Road().start().park().wiperMs)
    }

    @Test fun socPassengerAndEndPlace() {
        val m = Road().at(0) { soc(80); seat(true); place(37.30, 126.97) }.start()
            .at(10_000) { soc(75); seat(false); place(37.2839, 126.9896) }.park()
        assertEquals(80, m.socStart); assertEquals(75, m.socEnd)
        assertEquals(true, m.passenger) // 운전 시작 때 값
        assertEquals(37.2839, m.endLat!!, 1e-9); assertEquals(126.9896, m.endLon!!, 1e-9)
        val none = Road().start().park()
        assertNull(none.socStart); assertNull(none.socEnd); assertNull(none.passenger); assertNull(none.endLat)
    }

    @Test fun startGetterWinsAndAPlaceFromBeforeTheDriveIsDropped() {
        // 구독 첫 값이 엇갈려 와도 운전 시작 때 getter로 다시 읽은 값이 시작 값이다(코드 리뷰 반영)
        val m = Road().at(0) { soc(50); seat(false); place(37.30, 126.97) }.start()
            .at(100) { soc(85, start = true); seat(true, start = true) }.at(10_000) {}.park()
        assertEquals(85, m.socStart); assertEquals(true, m.passenger)
        assertNull(m.endLat) // 이번 운전 중에 받은 위치가 없다
    }

    @Test fun lateStartValuesFillIn() {
        val m = Road().start().at(500) { soc(66); seat(false) }.at(10_000) { soc(60); seat(true) }.park()
        assertEquals(66, m.socStart); assertEquals(60, m.socEnd); assertEquals(false, m.passenger)
    }

    @Test fun recordFormatHas20FieldsAndReadsOld13() {
        val a = Metrics(endedAt = 7, durationMs = 60_000, km = 1.0, cruiseMs = 30_000, socStart = 80, socEnd = 72,
            endLat = 37.2839144, endLon = 126.9896135, passenger = true, wiperMs = 0)
        val b = Metrics(endedAt = 8, durationMs = 15_000, km = 0.1) // 새 값 없음
        val text = RecordFormat.encode(listOf(a, b))
        assertEquals(20, text.lines()[0].split(',').size)
        assertEquals(listOf(a, b), RecordFormat.decode(text))
        val old = "5,15000,0.08,0,0,0,,,,,,,0" // 1차 13칸
        assertEquals(listOf(Metrics(endedAt = 5, durationMs = 15_000, km = 0.08, movingMs = 0)), RecordFormat.decode(old))
        assertEquals(emptyList<Metrics>(), RecordFormat.decode("1,2,3,4,5,6,7,8,9,10,11,12,1,9"))
    }

    @Test fun logLineHasNoCoordinates() {
        val line = Metrics(endedAt = 1, km = 2.0, endLat = 37.2839144, endLon = 126.9896135).logLine()
        assertFalse(line.contains("37.28")); assertFalse(line.contains("126.98"))
        assertTrue(line.endsWith("위치 있음"))
        assertTrue(Metrics(endedAt = 1).logLine().endsWith("위치 없음"))
    }
}

/** 보조 태그 판정(data-model §4·§4-1·§5, tasks T006·T013·T015·T017). 모두 Limits.RELEASE */
class TagTest {
    private val l = Limits.RELEASE
    private fun tags(list: List<Metrics>) = Tags.all(list, l)
    private fun tag(list: List<Metrics>, id: TagId) = tags(list).single { it.id == id }

    /** 분석되는 보통 운전(10분·10km). 역동성 지수 = hardAccel(10km라 10km당 개수와 같다) */
    private fun drive(idx: Int = 10) = Metrics(durationMs = 10 * MINUTE, km = 10.0, movingMs = 10 * MINUTE, hardAccel = idx, hardDecel = 0, quickSteers = 0, analyzed = true)
    /** 장거리(40분·50km, 평균 시속 75km), 크루즈 비율 [ratio] */
    private fun long(ratio: Double) = Metrics(durationMs = 40 * MINUTE, km = 50.0, movingMs = 40 * MINUTE, hardAccel = 0, hardDecel = 0,
        quickSteers = 0, cruiseMs = (ratio * 40 * MINUTE).toLong(), analyzed = true)

    // ---------- 크루즈 (T006) ----------
    @Test fun cruiseExamples() {
        val a = tag(listOf(0.70, 0.65, 0.80).map(::long), TagId.CRUISE)
        assertEquals("크루즈 위임형", a.name)
        assertEquals("장거리 운전 3번의 크루즈 비율 중앙값 70%", a.fact)
        assertEquals("직접 운전형", tag(listOf(0.05, 0.0, 0.10).map(::long), TagId.CRUISE).name)
        val c = tag(listOf(0.60, 0.05, 0.30).map(::long), TagId.CRUISE)
        assertNull(c.value); assertEquals("크루즈를 쓰는 정도가 운전마다 달라요", c.withheld)
    }

    @Test fun cruiseNeedsThreeLongDrivesAndHidesWithoutSignal() {
        val two = tag(listOf(long(0.7), long(0.7), drive().copy(cruiseMs = 0)), TagId.CRUISE)
        assertNull(two.value); assertNull(two.fact); assertEquals("장거리 운전이 2번뿐이에요(3번 필요)", two.withheld)
        assertTrue(tags(listOf(drive(), drive())).none { it.id == TagId.CRUISE }) // 크루즈 값이 없는 차
        val short = Metrics(durationMs = 20_000, km = 0.1, cruiseMs = 20_000) // 분석 안 되는 운전은 빠진다
        assertEquals("크루즈 위임형", tag(listOf(long(0.7), long(0.7), long(0.7), short, short), TagId.CRUISE).name)
        assertEquals("장거리 운전 4번의 크루즈 비율 중앙값 40%", tag(listOf(0.1, 0.2, 0.6, 0.7).map(::long), TagId.CRUISE).fact) // 짝수 중앙값
    }

    // ---------- 충전 (T013) ----------
    /** 충전 직전 배터리가 [pre]가 되도록 이어진 운전들 */
    private fun charges(vararg pre: Int): List<Metrics> {
        val out = mutableListOf(drive().copy(socStart = 90, socEnd = pre[0]))
        pre.forEachIndexed { i, p -> out += drive().copy(socStart = p + 30, socEnd = pre.getOrElse(i + 1) { 50 }) }
        return out
    }

    @Test fun chargeExamples() {
        val a = tag(charges(55, 48, 60), TagId.CHARGE)
        assertEquals("여유 충전형", a.name)
        assertEquals("충전 3번의 충전 직전 배터리 중앙값 55%(집 충전 포함)", a.fact)
        assertEquals("한계 활용형", tag(charges(18, 12, 22), TagId.CHARGE).name)
        assertEquals("충전하는 때가 그때그때 달라요", tag(charges(45, 15, 30), TagId.CHARGE).withheld)
    }

    @Test fun chargeDetectionRules() {
        val nine = listOf(drive().copy(socEnd = 40), drive().copy(socStart = 49)) // +9%p는 충전이 아님
        assertEquals("충전이 0번뿐이에요(3번 필요)", tag(nine, TagId.CHARGE).withheld)
        val gap = listOf(drive().copy(socEnd = 40), drive(), drive().copy(socStart = 90)) // SOC 없는 운전이 끼면 찾지 않음
        assertEquals("충전이 0번뿐이에요(3번 필요)", tag(gap, TagId.CHARGE).withheld)
        val withShort = listOf(drive().copy(socEnd = 30), Metrics(durationMs = 20_000, km = 0.1, socStart = 80, socEnd = 79)) // 짧은 운전도 이어 본다
        assertEquals("충전이 1번뿐이에요(3번 필요)", tag(withShort, TagId.CHARGE).withheld)
    }

    // ---------- 이동 범위 (T015) ----------
    private val home = 37.2839 to 126.9896
    private val work = 37.3003 to 126.9712
    private fun at(p: Pair<Double, Double>) = drive().copy(endLat = p.first, endLon = p.second)
    /** home에서 동쪽으로 [m]미터 */
    private fun east(m: Double) = home.first to home.second + m / (Math.cos(Math.toRadians(home.first)) * 111_320)
    private fun others(n: Int) = (1..n).map { at(37.25 + it * 0.01 to 127.05) }

    @Test fun rangeExamples() {
        val a = tag(List(15) { at(home) } + List(15) { at(work) }, TagId.RANGE)
        assertEquals("루틴형", a.name); assertEquals("새 장소 7% · 자주 가는 2곳이 100%", a.fact)
        val b = tag(List(10) { at(home) } + List(8) { at(work) } + others(12), TagId.RANGE)
        assertEquals("탐험형", b.name); assertEquals("새 장소 47% · 자주 가는 2곳이 60%", b.fact)
        val c = tag(List(14) { at(home) } + List(12) { at(work) } + others(4), TagId.RANGE)
        assertNull(c.value); assertEquals("새 곳과 늘 가던 곳이 섞여 있어요", c.withheld)
    }

    @Test fun rangeClusteringAndMinimum() {
        assertEquals(149.0, meters(home.first, home.second, east(149.0).first, east(149.0).second), 0.5)
        val same = List(19) { at(home) } + at(east(149.0))
        assertEquals("새 장소 5% · 자주 가는 2곳이 100%", tag(same, TagId.RANGE).fact) // 149m는 같은 장소: 새 장소 1번
        val far = List(19) { at(home) } + at(east(151.0))
        assertEquals("새 장소 10% · 자주 가는 2곳이 100%", tag(far, TagId.RANGE).fact)
        // 기준점은 첫 점: 100m, 200m 점이 차례로 오면 200m 점은 새 장소(100m 점을 기준으로 삼지 않음)
        val chain = List(18) { at(home) } + at(east(100.0)) + at(east(200.0))
        assertEquals("새 장소 10% · 자주 가는 2곳이 100%", tag(chain, TagId.RANGE).fact)
        // 둘 다 150m 안이면 가까운 곳에 더한다: 140m 점은 집(140m)이 아니라 250m 장소(110m)에 → 상위 2곳 9+8
        val farAway = 37.26 to 127.05
        val two = List(3) { at(home) } + List(8) { at(east(250.0)) } + List(8) { at(farAway) } + at(east(140.0))
        assertEquals("새 장소 15% · 자주 가는 2곳이 85%", tag(two, TagId.RANGE).fact)
        assertEquals("운전이 12번 쌓였어요(20번 필요)", tag(List(12) { at(home) }, TagId.RANGE).withheld)
        assertNull(tag(List(12) { at(home) }, TagId.RANGE).fact)
        val text = tags(List(20) { at(home) }).joinToString { "${it.fact} ${it.withheld}" }
        assertFalse(text.contains("37.") || text.contains("126.")) // 좌표는 어디에도 없다
    }

    // ---------- 상황 (T017) ----------
    private fun withP(idx: Int, p: Boolean) = drive(idx).copy(passenger = p)

    @Test fun passengerExamples() {
        val a = tag(List(3) { withP(100, false) } + List(3) { withP(70, true) }, TagId.PASSENGER)
        assertEquals("동승 맞춤형", a.name); assertEquals("동승 운전 3번의 역동성 지수가 평소보다 30% 낮아요", a.fact)
        val b = tag(List(3) { withP(100, false) } + List(3) { withP(97, true) }, TagId.PASSENGER)
        assertEquals("동승 한결형", b.name); assertEquals("동승 운전 3번의 역동성 지수가 평소와 비슷해요(차이 3%)", b.fact)
        val mid = tag(List(3) { withP(100, false) } + List(3) { withP(82, true) }, TagId.PASSENGER)
        assertNull(mid.value); assertEquals("평소와 차이가 애매해요(차이 18%)", mid.withheld)
        assertEquals("동승 운전 3번의 역동성 지수가 평소보다 18% 낮아요", mid.fact)
    }

    @Test fun zeroBaselineAndMinimums() {
        val same = tag(List(3) { withP(0, false) } + List(3) { withP(0, true) }, TagId.PASSENGER)
        assertEquals("동승 한결형", same.name); assertEquals("동승 운전 3번의 역동성 지수가 평소와 같아요", same.fact)
        val inf = tag(List(3) { withP(0, false) } + List(3) { withP(5, true) }, TagId.PASSENGER)
        assertEquals("동승 맞춤형", inf.name); assertEquals("평소엔 급한 조작이 없었는데 동승 운전 3번에선 있었어요", inf.fact)
        val short = tag(List(1) { withP(100, false) } + List(2) { withP(70, true) }, TagId.PASSENGER)
        assertEquals("동승 운전이 2번뿐이에요(3번 필요)", short.withheld) // 상황 집단 먼저
        val alone = tag(List(2) { withP(100, false) } + List(3) { withP(70, true) }, TagId.PASSENGER)
        assertEquals("혼자 운전이 2번뿐이에요(3번 필요)", alone.withheld)
        val noSpeed = List(3) { withP(100, false) } + List(3) { withP(70, true).copy(hardAccel = null) } // 속도 없는 운전은 빠진다
        assertEquals("동승 운전이 0번뿐이에요(3번 필요)", tag(noSpeed, TagId.PASSENGER).withheld)
    }

    @Test fun rainUsesWiperTime() {
        fun w(idx: Int, ms: Long) = drive(idx).copy(wiperMs = ms)
        val a = tag(List(3) { w(100, 0) } + List(3) { w(70, 3 * MINUTE) }, TagId.RAIN)
        assertEquals("빗길 맞춤형", a.name); assertEquals("비 운전 3번의 역동성 지수가 평소보다 30% 낮아요", a.fact)
        val brief = tag(List(3) { w(100, 0) } + List(3) { w(70, 3 * MINUTE - 1) }, TagId.RAIN) // 3분 미만은 비가 아님
        assertEquals("비 운전이 0번뿐이에요(3번 필요)", brief.withheld)
        assertEquals("맑은 운전이 1번뿐이에요(3번 필요)", tag(listOf(w(100, 0)) + List(3) { w(70, 4 * MINUTE) }, TagId.RAIN).withheld)
    }

    @Test fun demoLimitsAreLower() {
        val d = Limits.DEMO
        val shortLong = Metrics(durationMs = 2 * MINUTE, km = 1.2, movingMs = 2 * MINUTE, hardAccel = 0, cruiseMs = MINUTE, analyzed = true) // 시속 36km, 2분
        assertEquals("크루즈 위임형", Tags.all(List(3) { shortLong.copy(cruiseMs = 90_000) }, d).single { it.id == TagId.CRUISE }.name)
        assertEquals(5, d.rangeMin); assertEquals(2, d.situMin); assertEquals(30_000L, d.rainMs)
    }
}
