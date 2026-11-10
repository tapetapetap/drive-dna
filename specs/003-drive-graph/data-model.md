# Data Model: DRIVE DNA 3차

1·2차 data-model에 더한다. 표기는 `Dna.kt` 그대로다.

## §1 Graph와 수집

```kotlin
/** 운전 하나의 속도 흐름. speeds: 칸마다 평균 시속(정수, 1~40칸). marks: (종류, 칸) — 'a' 급가속, 'd' 급감속, 's' 빠른 조향 */
data class Graph(val speeds: List<Int>, val marks: List<Pair<Char, Int>>) {
    val avg get() = speeds.average()
    fun count(kind: Char) = marks.count { it.first == kind }
    companion object {
        fun of(start: Long, end: Long, trace: List<Pair<Long, Double>>, marks: List<Pair<Long, Char>>): Graph? // research R11
    }
}
```

- `Metrics`에 `val graph: Graph? = null`을 더한다(맨 끝).
- `logLine()` 형식: `"${copy(endLat = null, endLon = null, graph = null)} 그래프 ${graph?.speeds?.size ?: 0}칸 위치 ${if (endLat != null) "있음" else "없음"}"`. "위치 있음/없음"이 끝에 남아 1·2차 테스트(`endsWith`)가 그대로다.
- `Tracker`
  - 운전 중 `speed(now, v)`에서 `trace += now to v`
  - `hardAccel++` 옆에 `marks += now to 'a'`, `hardDecel++` 옆에 `'d'`, `quick++` 옆에 `'s'`
  - `startDrive`에서 둘 다 비운다.
  - `endDrive`에서 `graph = if (coarse) null else Graph.of(start, now, trace, marks)`
- 불변식: 사건 수 = 그 운전의 `hardAccel`·`hardDecel`·`quickSteers`(SC-012)

## §2 기록 형식(22칸)

| 칸 | 값 | 예 |
|---|---|---|
| 0~19 | 2차와 같음 | |
| 20 | 속도 칸 `/` 연결, 없으면 빈 칸 | `0/18/31/30/…` |
| 21 | 사건 `/` 연결, 없으면 빈 칸 | `a3/d17/s9` |

- `decode`는 13·20·22필드만 읽는다.
  - 20번 필드가 비면 그래프는 null이다.
  - 20번 필드가 있고 21번 필드가 비면 사건 없는 그래프다.
  - 숫자로 읽는 필드(0~11, 13~17, 19)가 숫자가 아니면 그 줄 전체를 버린다(1·2차와 같은 `runCatching`). 12·18번은 1·2차처럼 `"1"`과 비교만 한다(위임 결정 D16).
  - 그래프 필드가 어긋나면 그 줄은 남기고 그래프만 null이다(위임 결정 D10). 어긋남은 아래와 같다.
    - 속도 값이 숫자가 아님
    - 속도 구간 수가 0이거나 40을 넘음
    - 사건 글자가 a·d·s가 아님
    - 사건 구간 번호가 0 미만이거나 속도 구간 수 이상
    - 그래서 화면은 늘 범위 안의 구간만 읽는다.
- 그래프가 있는 줄은 22칸으로, 없는 줄도 22칸(빈 두 칸)으로 쓴다.

## §3 비교

```kotlin
object Compare {
    /** (다른 축, 같은 축, 보류 축). 보류 축 = 한쪽이라도 보류. 합은 4(위임 결정 D3) */
    fun counts(a: Metrics, b: Metrics, l: Limits): Triple<Int, Int, Int>
}
```

- 축 판정은 1차 `Judge.axes(m, l)`를 쓴다. 한쪽이라도 보류(value null)인 축은 다른·같은 수에서 빼고 보류 수로 센다.
- GPSE와 DRFC는 (4, 0, 0)이다(SC-013). G·SE와 GPSE는 (0, 3, 1)이다.

## §4 태그 바뀜

```kotlin
enum class Change { NONE, CHANGED, NEW }
data class TagView(val now: Tag, val change: Change, val before: String?) // before: 지난 근거 문장(없으면 지난 보류 이유)
object Tags { fun changes(oldestFirst: List<Metrics>, l: Limits): List<TagView> }  // research R14
```

- 차례는 `Tags.all(oldestFirst, l)`와 같다.
- 분석 운전이 2개 미만(0개 포함)이면 모두 NONE, before null이다. 0개일 때 `take(-1)`을 부르지 않는다(위임 결정 D11).
- 지난 목록은 가장 최근 분석 운전 "앞까지" 자른 것(`take(index)`)이다. 그 뒤의 짧은 운전도 함께 빠진다(위임 결정 D5).
- 종류(위임 결정 D11)
  - NEW: 지금 `value != null`이고, 지난 태그가 없거나 지난 `value`가 null(보류)이거나 다른 값
  - CHANGED: 그 밖에 `value`나 `fact`가 다름. 정해짐 → 보류, 근거(fact)가 있는 보류로 처음 나타남, 같은 판정에 문장만 바뀜이 여기다.
  - NONE: `value`와 `fact`가 같음. `withheld`만 바뀐 것("운전이 2번 쌓였어요" → "3번")도 NONE이다(구현 중 바뀜)
- before(NONE이면 null)
  - 지난 태그가 없으면(이전에는 보이지 않던 태그) "이전에는 없던 태그"
  - 아니면 지난 `fact`, 없으면 지난 `withheld`(태그 카드가 먼저 보이던 문장)
- 나의 DNA 줄의 n = `count { it.change != NONE }`

## §5 자막과 공개

- `WatchService.lastSpoken: Spoken?`, `data class Spoken(val text: String, val at: Long, val endedAt: Long)`
  - `speak` 직전에 그 운전의 `endedAt`과 함께 둔다.
- 결과 화면
  - `openRecord == null && lastSpoken?.endedAt == m.endedAt`이면 자막을 보인다. 다시 보기에는 없다.
  - `now − at < 6000`이면 소리 막대가 움직인다.
- `lastSpoken`은 `onDriveEnd`가 `speak` 직전에 둔다(TTS 준비와 무관).
- fresh: `openRecord == null && now − m.endedAt < 10_000 && m.endedAt != animatedAt`(위임 결정 D1). `animatedAt`은 `MainActivity` companion(프로세스 범위)에 둔다(위임 결정 D15). 다시 보기는 늘 false다.

## §6 그리기 값(위임 결정 D4·D6·D8·D9)

**색(밝은 / 다크)**

| 이름 | 밝은 | 다크 | 쓰는 곳 |
|---|---|---|---|
| `graph_fill` | #D6E4FF | #1E3157 | 그래프 면 |
| `graph_line` | #0064FF | #4D8DFF | 그래프 선, 비교 앞 운전 |
| `graph_line2` | #00A86B | #3DDC97 | 비교 뒤 운전 |
| `mark_accel` | #E5484D | #FF6B6F | ▲ |
| `mark_decel` | #E08A12 | #F2A33A | ▼ |
| `mark_steer` | #8E4EC6 | #B07CE8 | ◆ |

**GraphView**(위임 결정 D12)
- 구간이 40개보다 적어도 그래프 폭 전체에 고르게 편다. 비교는 두 운전 모두 폭 전체다.
- 구간 i의 x는 가운데 `(i + 0.5) / n × 폭`이다. 선은 구간 가운데 점들을 잇고, 면은 그 선 아래를 채운다.
- 세로: 그림 영역은 위 여백(결과 30dp, 비교 24dp, 썸네일 4dp)과 아래 여백 4dp를 뺀 높이다. 값 0이 아래, `최고 × 1.15`가 위다.
- 처음·끝 시각 글과 사건 줄은 그래프 아래 TextView다(그래프는 그림만).
- 선 두께: 결과 3dp, 비교 3dp, 썸네일 2dp. 면: 결과·썸네일 `graph_fill`, 비교는 각 계열 색 알파 40(/255)
- 평균 점선(결과만): 1.5dp, 대시 8dp·간격 6dp, `text3` 알파 160
- 사건 표시
  - 결과 18dp, 비교 14dp 기호(▲·▼·◆), 썸네일 지름 6dp 점
  - 결과·비교는 그 구간 선 위 10dp(비교 8dp)에 둔다. 같은 구간에 둘 이상이면 종류 순서(a, d, s)로 위로 16dp(비교 12dp)씩 비킨다.
  - 위로 넘치면 기호 가운데를 그림 위 끝(위 여백 안)에 맞춘다. 겹쳐도 그린다.
  - 썸네일 점은 선 위에 바로 그리고 비키지 않는다.

**사건 줄 빼기(D6)**: 횟수가 0이거나 그 신호가 없으면(null) 그 종류를 뺀다. 모두 빠지면 "급한 조작 표시 없음"이다.

**그래프 없는 결과 문구(D6)**
- `m.noSpeed`면 "이 운전은 속도를 추정하지 못해 그래프가 없어요"
- 아니면 "이 기록은 그래프를 남기기 전 운전이에요"(1·2차 형식)

**AxisBar 비교(D4·D13)**
- `AxisBar(ctx, axis, value, color, second: Double? = null, compare: Boolean = false)`. `compare`가 false면 1·2차 그대로다(높이 40dp).
- `compare`면 높이 64dp다. 막대는 위에서 24dp, 그 아래가 "보류" 글자 자리다.
- 두 점을 그린다. 앞 운전은 `graph_line`, 뒤 운전은 `graph_line2`다. 점 반지름 10dp.
- 보류인 쪽은 막대 가운데에 그 색 테두리 원(채우지 않음, 선 2.5dp)을 그리고, 원 아래 작은 글(16sp, `text3`) "보류"를 둔다.
  - 둘 다 보류면 두 원을 가운데에서 왼쪽·오른쪽으로 14dp씩 비켜 그리고, "보류"는 가운데에 한 번만 쓴다.
- 끝 글자: `compare` 모드에서는 늘 둘 다 흐린 색(`text4`)이다(어느 쪽 판정인지는 점 색으로 본다).

**효과(D9·D14)**
- 링 배치: 타일을 FrameLayout으로 감싸고, 그 안 가운데에 `PulseRing`(타일의 1.6배 크기)과 타일을 겹쳐 둔다. 틀의 레이아웃 크기는 타일 크기 그대로이고, `PulseRing`은 사방 음수 여백(타일의 0.3배)으로 틀 밖까지 그린다. 틀, 틀이 든 줄, 유형 카드는 `clipChildren = false`다. 그래서 카드 높이는 1·2차와 같고 fresh 여부와 무관하게 늘 같다. 링 끝이 카드 밖으로 나가는 몇 dp는 잘리지만 그때 링은 거의 투명하다.
  - 링은 `blue` 2dp, 반지름은 타일 반에서 1.6배까지, 0.9초다.
  - 두 번을 이어서 그린다(1.8초 뒤 멈춤).
- 글자: 0.25초 동안 위로 12dp, 알파 0 → 1, 지연 i × 150ms
- 소리 막대
  - 너비 4dp, 간격 3dp, 높이 6~20dp
  - 0.6초 주기 사인이고, 막대마다 위상이 1/3씩 다르다.
  - 움직임 줄이기 설정에서는 멈춘 모습(높이 6, 14, 10dp)이다.

**비교 화면 이동(D2)**
- COMPARE는 HISTORY·TYPES·TAGS와 같은 칸(1차 data-model §8 화면 목록)의 화면이다.
- "✕  닫기"와 뒤로 가기는 HISTORY로 돌아간다. 비교 모드와 고른 둘은 유지한다.
- onPause, 새 분석 기록, 운전 시작 때는 1·2차 화면처럼 닫히고, 비교 모드와 고른 것도 비운다.
- 범례: 그래프 카드 머리 아래 "● 앞에 고른 운전 시각  ● 뒤에 고른 운전 시각"(각 색)
