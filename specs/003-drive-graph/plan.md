# Implementation Plan: DRIVE DNA 3차 — 운전 그래프, 두 운전 비교, 눈에 띄는 변화

**Branch**: `003-drive-graph` (git 브랜치 `drive-dna`) | **Date**: 2026-11-11 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/003-drive-graph/spec.md`, 설계 `docs/superpowers/specs/2026-11-11-drive-dna-details-design.md`

## Summary

1·2차 구조를 그대로 잇는다.

| 기능 | 판단·계산(Dna.kt, 순수) | 실행·화면 |
|---|---|---|
| 운전 그래프 | `Tracker`가 속도 표본·사건 시각을 모으고, `Graph.of`가 40칸으로 줄인다. `RecordFormat` 22칸 | `GraphView`(새 `Graph.kt`). 결과 카드, 기록 썸네일, 다시 보기 |
| 두 운전 비교 | `Compare.counts(a, b, l)`(다른·같은·보류 축 수) | 기록 "비교" 고르기(`MainActivity`), `Ui.compare` |
| 결과 공개 효과 | 없음 | `Ui.result`의 글자 네 개 애니메이션, 타일 링 |
| 바뀐 근거 | `Tags.changes(records, l)`: 최근 분석 운전을 빼고 넣고 비교 | 태그 카드 칩·지난 문장, 나의 DNA 한 줄 |
| 음성 자막 | 없음 | `WatchService`가 말한 문장·시각을 들고, 결과 화면이 보인다 |
| 샘플 그래프 | 없음 | `scripts/seed.txt` 22칸(한 번 만들어 넣음) |

## 현재 상태 (구현 전)

- 1·2차가 끝나 `drive-dna` main(902abdf)에 올라가 있다. 단위 테스트 54개가 통과한다.
- 11/10 밤 전 장면 녹화로 "DRIVE DNA 주행 영상" 아티팩트를 올렸다(9장면 통과).

## Technical Context

**Language/Version**: Kotlin 2.0.21(JDK 17), Python 3(시연). 1·2차와 같다
**Primary Dependencies**: 1·2차와 같다. 새 의존성 없음(`android.graphics`)
**Storage**: SharedPreferences `records`의 한 줄 형식에 두 칸 더함(22칸). 1·2차 형식 읽기 유지
**Testing**: JUnit(`DnaTest.kt`, 새 `GraphTest.kt`), 에뮬레이터 장면
**Target Platform**: Pleos Connect 에뮬레이터(API 34)
**Project Type**: 차량 앱(단일 모듈)
**Performance Goals**: 기록 100개 × 두 번 태그 계산, 썸네일 100개 그리기가 화면 전환 안에 끝난다(눈에 띄는 멈춤 없음)
**Constraints**: 헌법 I(P에서만, 속도 경쟁 없음: 최고 시속 숫자 없음), II(우열 없음), III(추정 속도라고 밝힘), VI(경로 없음)
**Scale/Scope**: 운전 기록 최대 100개, 그래프 40칸

## Constitution Check

| 원칙 | 판정 |
|---|---|
| I. 운전 중에는 보여 주지 않는다 | 통과. 그래프·비교·자막은 결과·기록 화면(P)에만 있다. 나의 DNA 칸의 바뀜 줄은 주행 중 1·2차 칩처럼 흐리고 눌리지 않는다. 최고 시속 숫자를 보이지 않아 속도 경쟁을 부르지 않는다 |
| II. 관측 사실이 먼저 | 통과. 그래프는 관측(추정 속도·횟수)이다. 비교 화면은 우열 없이 두 사실을 나란히 둔다 |
| III. 차 신호만 | 통과. 속도는 1차와 같은 주행거리 추정이고, 그래프 카드에 그렇게 적는다. 주행거리가 거친 차는 그래프 없음 |
| IV. 최소 구현 | 통과. 새 코드 파일은 `Graph.kt`(그리기 View) 하나, 새 자원은 `ic_speaker.xml` 하나, 새 테스트는 `GraphTest.kt`다. 계산은 `Dna.kt`에 더한다 |
| V. 판단 로직은 테스트로 | 통과. 40칸 줄이기, 사건 칸, 기록 왕복, 다른 축 수, 태그 바뀜은 `GraphTest`를 먼저 쓴다 |
| VI. 기록은 차 안에만 | 통과. 속도만 기기 안 기록에 둔다. 위치 흐름은 모으지 않는다 |

## Project Structure

### Documentation (this feature)

```text
specs/003-drive-graph/
├── spec.md, plan.md, research.md(R11~R17), data-model.md, quickstart.md
├── contracts/ui.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/java/kr/skku/drivedna/
├── Dna.kt          # Graph, Graph.of, Tracker 수집, Metrics.graph, RecordFormat 22칸, Compare, Tags.changes
├── Graph.kt        # 새: GraphView(결과·썸네일·겹침), PulseRing, SoundBars
├── AxisBar.kt      # 두 번째 값(비교)
├── Screens.kt      # result(그래프·공개·자막), history(썸네일·비교 고르기), compare, tags(바뀜), profile(한 줄)
├── MainActivity.kt # 비교 상태, 자막 넘기기
└── WatchService.kt # 말한 문장·시각
app/src/test/java/kr/skku/drivedna/GraphTest.kt   # 새
scripts/seed.txt, scripts/demo.py(compare 장면)
```

**Structure Decision**: 1·2차와 같은 한 모듈이다. 그리기만 `Graph.kt`로 나눈다. `Screens.kt`는 화면 조립, `AxisBar.kt`는 막대를 맡는다.

## Phase 0: Research

research.md R11~R17

## Phase 1: Design

data-model.md(§1 Graph·수집, §2 기록 형식, §3 비교, §4 태그 바뀜, §5 자막·공개), contracts/ui.md, quickstart.md

## Complexity Tracking

위반 없음.
