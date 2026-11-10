package kr.skku.drivedna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 3차 운전 그래프(specs/003-drive-graph data-model §1·§2, research R11·R12) */
class GraphTest {
    @Test fun bucketsAverageFillForwardAndMarkTheirBucket() {
        // 10초 운전 = 10칸. 2.5초·2.7초 표본은 같은 칸(평균 15), 첫 표본 전은 0, 빈 칸은 앞 값. 끝 시각 사건은 마지막 칸
        val g = Graph.of(0, 10_000, listOf(2_500L to 10.0, 2_700L to 20.0, 6_000L to 30.0), listOf(6_000L to 'a', 10_000L to 'd'))!!
        assertEquals(listOf(0, 0, 15, 15, 15, 15, 30, 30, 30, 30), g.speeds)
        assertEquals(listOf('a' to 6, 'd' to 9), g.marks)
    }

    @Test fun atMostFortyBucketsAndNothingWithoutSamples() {
        assertEquals(40, Graph.of(0, 600_000, listOf(1_000L to 50.0), emptyList())!!.speeds.size)
        assertEquals(3, Graph.of(0, 2_500, listOf(100L to 5.0), emptyList())!!.speeds.size) // 2.5초 → 3칸
        assertNull(Graph.of(0, 10_000, emptyList(), emptyList()))
    }

    @Test fun recordFormatHas22FieldsAndRoundTripsGraphs() {
        val a = Metrics(endedAt = 7, durationMs = 60_000, km = 1.0, graph = Graph(listOf(0, 18, 31), listOf('a' to 0, 's' to 2)))
        val b = Metrics(endedAt = 8, durationMs = 15_000, km = 0.1)
        val text = RecordFormat.encode(listOf(a, b))
        assertEquals(listOf(22, 22), text.lines().map { it.split(',').size })
        assertTrue(text.lines()[0].endsWith(",0/18/31,a0/s2"))
        assertEquals(listOf(a, b), RecordFormat.decode(text))
    }

    @Test fun readsMixed13And20And22FieldLines() { // SC-016
        val old13 = "5,15000,0.08,0,0,0,,,,,,,0"
        val old20 = RecordFormat.encode(listOf(Metrics(endedAt = 6))).split(',').take(20).joinToString(",")
        val g = Graph(listOf(1, 2), emptyList())
        val got = RecordFormat.decode(listOf(old13, old20, RecordFormat.encode(listOf(Metrics(endedAt = 7, graph = g)))).joinToString("\n"))
        assertEquals(listOf(5L, 6L, 7L), got.map { it.endedAt })
        assertEquals(listOf(null, null, g), got.map { it.graph })
    }

    @Test fun brokenNumberDropsTheLineButBrokenGraphOnlyDropsTheGraph() {
        val ok = RecordFormat.encode(listOf(Metrics(endedAt = 9, graph = Graph(listOf(5, 6), listOf('a' to 1)))))
        assertEquals(emptyList<Metrics>(), RecordFormat.decode("x" + ok.drop(1)))
        val head = ok.split(',').take(20).joinToString(",")
        // 숫자가 아닌 속도, 종류 글자, 칸 번호가 범위 밖(위·아래), 속도 없이 사건, 41칸
        for (tail in listOf("5/x,", "5/6,b1", "5/6,a2", "5/6,a-1", ",a0", (1..41).joinToString("/") + ",")) {
            val m = RecordFormat.decode("$head,$tail").single()
            assertNull(tail, m.graph)
            assertEquals(9L, m.endedAt)
        }
    }

    @Test fun logLineCountsBucketsBeforeThePlace() {
        val line = Metrics(endedAt = 1, endLat = 37.28, endLon = 126.98, graph = Graph(listOf(1, 2, 3), emptyList())).logLine()
        assertTrue(line.endsWith("그래프 3칸 위치 있음"))
        assertFalse(line.contains("speeds"))
        assertTrue(Metrics(endedAt = 1).logLine().endsWith("그래프 0칸 위치 없음"))
    }

    /** SC-012 불변식: 사건 표시 수 = 그 운전의 급가속·급감속·빠른 조향 수 */
    @Test fun trackerMarksMatchItsCounters() {
        var now = 0L
        var odo = 100.0
        val t = Tracker { now }
        t.gear(true); t.km(odo); t.steer(0.0)
        now += 1000; t.gear(false)
        fun go(kmh: Double, secs: Int) = repeat(secs) { now += 1000; odo += kmh / 3600; t.km(odo) }
        fun ramp(a: Double, b: Double, secs: Int) { for (i in 1..secs) { now += 1000; odo += (a + (b - a) * i / secs) / 3600; t.km(odo) } }
        go(0.0, 3); ramp(0.0, 72.0, 4); go(72.0, 10) // 급가속 한 번
        now += 300; t.steer(100.0); now += 300; t.steer(0.0) // 빠른 조향 한 번
        go(72.0, 5); ramp(72.0, 0.0, 4); go(0.0, 5) // 급감속 한 번
        val m = t.gear(true)!!
        val g = m.graph!!
        assertEquals(listOf(1, 1, 1), listOf(m.hardAccel, m.hardDecel, m.quickSteers))
        assertEquals(listOf(1, 1, 1), listOf(g.count('a'), g.count('d'), g.count('s')))
        assertEquals(minOf(40, ((m.durationMs + 999) / 1000).toInt()), g.speeds.size)
    }
}

/** 3차 비교(data-model §3, 위임 결정 D3) */
class CompareTest {
    private val l = Limits.DEMO
    private fun m(dyn: Int, turns: Int, early: Int, cv: Double, kwh: Double) = Metrics(durationMs = 6 * MINUTE, km = 5.0, movingMs = 5 * MINUTE,
        hardAccel = dyn, hardDecel = dyn, quickSteers = dyn, turns = turns, early = early, speedCv = cv, kwhPer100 = kwh, analyzed = true)
    private val gpse = m(0, 4, 4, 0.1, 10.0)
    private val drfc = m(10, 4, 0, 0.9, 25.0)
    private val gSe = m(0, 1, 1, 0.1, 10.0) // 회전 1번: 대응 방식 보류

    @Test fun countsDifferentSameAndWithheldAxes() {
        assertEquals("GPSE", Judge.code(gpse, l)); assertEquals("DRFC", Judge.code(drfc, l)); assertEquals("G·SE", Judge.code(gSe, l))
        assertEquals(Triple(4, 0, 0), Compare.counts(gpse, drfc, l))
        assertEquals(Triple(0, 4, 0), Compare.counts(gpse, gpse, l))
        assertEquals(Triple(0, 3, 1), Compare.counts(gSe, gpse, l))
    }
}

/** 3차 태그 바뀜(data-model §4, 위임 결정 D5·D11) */
class TagChangeTest {
    private val l = Limits.DEMO
    private fun tag(id: TagId, v: Boolean?, fact: String?, why: String? = null) = Tag(id, v, fact, why)
    private fun one(before: List<Tag>, now: List<Tag>) = Tags.diff(before, now).single()

    @Test fun kindsAndBeforeTexts() {
        val c = TagId.CRUISE
        // 보류 → 판정: NEW, before는 지난 fact가 있으면 그것
        assertEquals(TagView(tag(c, true, "b"), Change.NEW, "a"), one(listOf(tag(c, null, "a", "운전마다 달라요")), listOf(tag(c, true, "b"))))
        assertEquals(TagView(tag(c, true, "b"), Change.NEW, "3번뿐이에요"), one(listOf(tag(c, null, null, "3번뿐이에요")), listOf(tag(c, true, "b"))))
        // 판정 → 다른 판정: NEW
        assertEquals(Change.NEW, one(listOf(tag(c, false, "a")), listOf(tag(c, true, "b"))).change)
        // 판정 → 보류: CHANGED, before = 지난 fact
        assertEquals(TagView(tag(c, null, "b", "애매해요"), Change.CHANGED, "a"), one(listOf(tag(c, true, "a")), listOf(tag(c, null, "b", "애매해요"))))
        // 같은 판정에 문장만: CHANGED
        assertEquals(Change.CHANGED, one(listOf(tag(c, true, "a")), listOf(tag(c, true, "b"))).change)
        // 그대로: NONE, before 없음
        assertEquals(TagView(tag(c, true, "a"), Change.NONE, null), one(listOf(tag(c, true, "a")), listOf(tag(c, true, "a"))))
        // 전에 없던 태그: 판정이면 NEW, 근거가 있는 보류면 CHANGED, before는 "이전에는 없던 태그"
        assertEquals(TagView(tag(c, true, "b"), Change.NEW, "이전에는 없던 태그"), one(emptyList(), listOf(tag(c, true, "b"))))
        assertEquals(TagView(tag(c, null, "b", "애매해요"), Change.CHANGED, "이전에는 없던 태그"), one(emptyList(), listOf(tag(c, null, "b", "애매해요"))))
        // 보류 이유의 쌓인 수만 바뀜: NONE(근거가 아니다)
        assertEquals(Change.NONE, one(emptyList(), listOf(tag(c, null, null, "3번뿐이에요"))).change)
        assertEquals(Change.NONE, one(listOf(tag(c, null, null, "2번뿐이에요")), listOf(tag(c, null, null, "3번뿐이에요"))).change)
    }

    private fun drive(at: Long, analyzed: Boolean = true) = Metrics(endedAt = at, durationMs = 2 * MINUTE, km = 3.0, movingMs = 2 * MINUTE,
        hardAccel = 0, hardDecel = 0, quickSteers = 0, cruiseMs = 100_000, socStart = 95, socEnd = 90, analyzed = analyzed)

    @Test fun fewerThanTwoAnalyzedDrivesShowNoChange() {
        assertTrue(Tags.changes(emptyList(), l).isEmpty() || Tags.changes(emptyList(), l).all { it.change == Change.NONE })
        assertTrue(Tags.changes(listOf(drive(1)), l).all { it.change == Change.NONE && it.before == null })
        assertTrue(Tags.changes(listOf(drive(1, analyzed = false)), l).all { it.change == Change.NONE })
    }

    private val seed = RecordFormat.decode(File("../scripts/seed.txt").readText())

    @Test fun highwayAfterSeedChangesCruiseAndCharge() { // SC-014
        val now = seed + drive(seed.last().endedAt + 3_600_000)
        val ch = Tags.changes(now, l).associateBy { it.now.id }
        val before = Tags.all(seed, l).associateBy { it.id }
        for (id in listOf(TagId.CRUISE, TagId.CHARGE)) {
            assertTrue(id.name, ch.getValue(id).change != Change.NONE)
            assertEquals(before.getValue(id).fact, ch.getValue(id).before)
        }
        // 최근 분석 운전 뒤에 짧은 운전이 붙어도 같다(앞까지 자름)
        val short = drive(now.last().endedAt + 60_000, analyzed = false).copy(socStart = 99)
        assertEquals(Tags.changes(now, l), Tags.changes(now + short, l).map { v -> v.copy(now = Tags.all(now + short, l).single { it.id == v.now.id }) })
    }
}

/** 3차 샘플 기록 그래프(research R17, FR-042) */
class SeedGraphTest {
    @Test fun everySeedLineHasAMatchingGraph() {
        val seed = RecordFormat.decode(File("../scripts/seed.txt").readText())
        assertEquals(24, seed.size)
        seed.forEach { m ->
            val g = m.graph!!
            assertEquals(1.0, g.avg / m.avgKmh, 0.1)
            assertEquals(listOf(m.hardAccel, m.hardDecel, m.quickSteers), listOf(g.count('a'), g.count('d'), g.count('s')))
        }
    }
}
