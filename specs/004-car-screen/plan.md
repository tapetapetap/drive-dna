# Implementation Plan: DRIVE DNA 4차 — 차 화면 개선

**Branch**: `004-car-screen` (git 브랜치 `car-dna`, 되돌리기 태그 `backup/before-car-dna` = drive-dna a75c795) | **Date**: 2026-11-13 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/004-car-screen/spec.md`, 설계 `docs/superpowers/specs/2026-11-13-car-screen-design.md`(C1~C5, D1~D6)

## Summary

`Ui` 도우미와 색 토큰을 먼저 바꾸고, 페이지 버튼 배치·결과 화면 차례·상태 보존·거리 환산 글은 해당 자리만 고친다. 축 판정과 기록 형식은 건드리지 않는다.

| 바꿀 것 | 파일 | 요구 |
|---|---|---|
| `small`·`chip`·`banner` 24sp, 새 `action`(76dp) | `Ui.kt` | FR-044·046 |
| 화면 고정 글자 20·22sp → 24sp, 카드 제목 26 → 28sp | `Screens.kt` | FR-044 |
| 닫기·비교·← 운전 기록·두 운전 비교·태그 근거 링크 76dp, 페이지 버튼 셋 한 줄에 하나 | `Screens.kt`(`history`, `compare`, `types`, `tags`, `profile`), `MainActivity.kt`(`resultScreen`) | FR-046·047 |
| 축 막대 글자 24/20sp, 꺼진 쪽 글자 text3 | `AxisBar.kt` | FR-045 |
| `text3` 낮 #62656D, 새 `green_text`·`blue_text` | `values/colors.xml`, `values-night/colors.xml` | FR-048 |
| 태그 칩·새로 정해짐 칩·파랑 글자·각주 색 | `Screens.kt` | FR-048 |
| 설정 변화 때 화면 상태 보존 | `MainActivity.kt` | FR-049 |
| 거리 환산 규칙 | `Dna.kt`(`Judge.dyn`, `Judge.pur`, `Coach.line`) + `DnaTest.kt` | FR-050 |
| 결과 화면 차례, "이번 운전 네 축", "최근 운전 n번 평균" | `Screens.kt`(`result`, `profile`) | FR-051·052 |

## Technical Context

**Language/Version**: Kotlin(JDK 17), Android View, Python 3(시연). 1~3차와 같다

**Primary Dependencies**: 새 의존성 없음

**Storage**: 바뀌지 않음. 화면 상태는 설정 변화 동안만 `Bundle`에 둔다

**Testing**: 기존 단위 테스트 66개(`DnaTest` 54, `GraphTest` 12) + `DnaTest`에 환산 규칙 테스트 1개

**Target Platform**: Pleos Connect 에뮬레이터(AAOS API 34, 2560×1440, density 160)

**Project Type**: 차량용 안드로이드 앱(단일 모듈 `app`)

**Performance Goals**: 바뀌지 않음

**Constraints**: 앱 칸 가로 약 1,700dp(나의 DNA 칸은 그중 3/8, 약 640dp), 세로 1:1 약 850dp. 차 기준(spec 용어)

**Scale/Scope**: 앱 코드 5개 파일, 색 2개, 테스트 1개 파일

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판단 |
|---|---|
| I. 운전 중에는 보여 주지 않는다 | 통과. 주행 최적화를 선언하지 않고, 주행 중 동작이 그대로다 |
| II. 관측 사실이 먼저, 해석은 잠정으로 | 통과(좋아짐). 짧은 운전에서 환산 대신 센 수와 쓴 양(관측 사실)을 보인다 |
| III. 차 신호만 쓴다 | 해당 없음 |
| IV. 최소 구현 | 통과. 새 의존성·추상화 없음. 누르는 칩 도우미 `action` 하나(쓰는 곳 6곳 이상) |
| V. 판단 로직은 테스트로 | 통과. 환산 규칙을 단위 테스트로 지킨다 |
| VI. 기록은 차 안에만 | 해당 없음(저장 형식 그대로) |

Phase 1 뒤에도 같다. Complexity Tracking은 비운다.

## Project Structure

### Documentation (this feature)

```text
specs/004-car-screen/
├── spec.md, plan.md, research.md
├── contracts/ui.md
├── quickstart.md
├── checklists/requirements.md
└── tasks.md
```

data-model.md는 만들지 않는다. 기록 형식이 바뀌지 않고, 보존할 화면 상태는 contracts §6에 적는다.

### Source Code (repository root)

```text
app/src/main/
├── java/kr/skku/drivedna/
│   ├── Ui.kt            # small·chip·banner·action
│   ├── Screens.kt       # first, result, graphCard, caption, history, compare, types, tags, profile
│   ├── AxisBar.kt       # 끝 글자, 보류
│   ├── MainActivity.kt  # resultScreen 뒤로 칩, 상태 보존
│   └── Dna.kt           # Judge.dyn·pur 글, Coach.line
├── res/values/colors.xml, res/values-night/colors.xml
app/src/test/java/kr/skku/drivedna/DnaTest.kt
```

**Structure Decision**: 1~3차 구조 그대로. 새 파일은 없다.

## Phase 0: Research

[research.md](./research.md): R1 차 기준 값, R2 설정 변화 때 상태 보존, R3 거리 환산 규칙, R4 페이지 버튼 배치, R5 색 값.

## Phase 1: Design

- [contracts/ui.md](./contracts/ui.md): §1 도우미, §2 요소별 값, §3 색, §4 환산 글, §5 결과 차례와 칸 이름, §6 상태 보존.
- [quickstart.md](./quickstart.md): 빌드·테스트, 크기·대비, 낮·밤, 결과 화면, 시연 회귀.
- 에이전트 컨텍스트: 저장소의 `CLAUDE.md`·`HANDOFF.md`는 인수인계용이라 바꾸지 않는다.
