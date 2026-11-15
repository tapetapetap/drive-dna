# Implementation Plan: DRIVE DNA 5차 — 시연용 그림 바로잡기

**Branch**: `005-demo-pictures` (git 브랜치 `car-dna`, 되돌리기 태그 `backup/before-pictures-dna` = drivedna/main f23f97c) | **Date**: 2026-11-15 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/005-demo-pictures/spec.md`, 설계 `docs/superpowers/specs/2026-11-14-demo-pictures-design.md`(D1~D3)

## Summary

샘플 파일의 그래프 두 칸을 다시 만들고 단위 테스트로 지킨다. 화면은 그래프 카드의 글 한 줄과 나의 DNA 칸 막대 아래 한 줄만 고친다. 판정, 기록 형식, 축 막대 그림은 건드리지 않는다.

| 바꿀 것 | 파일 | 요구 |
|---|---|---|
| 샘플 24줄의 20·21번 칸, 머리 `#` 줄 | `scripts/seed.txt` | FR-054~058 |
| 칸 40개·사건 칸 방향 검사, 새 `seedGraphsDiffer` | `app/src/test/…/GraphTest.kt`(`SeedGraphTest`) | SC-024·025 |
| 그래프 카드 맨 아래 줄 | `Screens.kt`(`graphCard`) | FR-059 |
| 막대 아래 양 끝 뜻 한 줄 | `Screens.kt`(`profile`의 `bars`) | FR-060 |

## Technical Context

**Language/Version**: Kotlin(JDK 17), Android View. 샘플 생성은 저장소 밖 일회용 Python 3

**Primary Dependencies**: 새 의존성 없음

**Storage**: 바뀌지 않음(기록 22칸 형식 그대로, 샘플 파일의 값만 바뀜)

**Testing**: 기존 단위 테스트 67개(`DnaTest` 55, `GraphTest` 12) + `SeedGraphTest.seedGraphsDiffer` 1개. 기존 `everySeedLineHasAMatchingGraph`에 검사를 더한다

**Target Platform**: Pleos Connect 에뮬레이터(AAOS API 34, 2560×1440, density 160)

**Project Type**: 차량용 안드로이드 앱(단일 모듈 `app`)

**Performance Goals**: 바뀌지 않음

**Constraints**: 나의 DNA 칸 안쪽 폭 558dp(가로)·785dp(1:1 세로), 그래프 카드 안쪽 폭 872dp(가로)·688dp(1:1 세로), 24sp 한 줄 30dp(research R3·R4). 시연 그림은 출시 APK에 들어가지 않는다(R5)

**Scale/Scope**: 앱 코드 1개 파일 2곳, 테스트 1개 파일, 샘플 파일 1개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 판단 |
|---|---|
| I. 운전 중에는 보여 주지 않는다 | 통과. 주행 중 화면·음성이 그대로다. 그래프에는 여전히 숫자가 없고(최고 시속 숫자를 그리지 않음, Graph.kt 20행), 점선 설명은 "평균 속도"라는 이름뿐이다. 미션·점수·순위가 없다 |
| II. 관측 사실이 먼저, 해석은 잠정으로 | 통과. 점선 설명은 그림 요소의 뜻이다. 뜻 단어는 16유형 화면과 같은 축 이름이라 우열이 없다 |
| III. 차 신호만 쓴다 | 통과. "속도는 주행거리로 추정했어요"를 그대로 둔다. 샘플 그래프는 시연 준비값이라고 파일 머리에 적고, 디버그 앱에만 들어간다 |
| IV. 최소 구현 | 통과. 새 파일·의존성·추상화 없음. 생성 스크립트는 한 번 쓰고 저장소에 두지 않는다(설계 결정) |
| V. 판단 로직은 테스트로 | 통과. 판단 로직은 바뀌지 않는다. 샘플 그래프 규칙은 `SeedGraphTest`가 지킨다 |
| VI. 기록은 차 안에만 | 해당 없음(저장 형식 그대로, 샘플 위치 칸 16·17도 그대로) |

Phase 1 뒤에도 같다. Complexity Tracking은 비운다.

## Project Structure

### Documentation (this feature)

```text
specs/005-demo-pictures/
├── spec.md, plan.md, research.md, quickstart.md
├── checklists/requirements.md
└── tasks.md
```

data-model.md와 contracts/는 만들지 않는다. 기록 형식(22칸)과 화면 도우미·축 막대가 그대로이고, 바뀌는 글 두 곳은 spec FR-059·060에 정확히 적었다.

### Source Code (repository root)

```text
scripts/seed.txt                                 # 20·21번 칸, 머리 # 줄
app/src/main/java/kr/skku/drivedna/Screens.kt    # graphCard 마지막 줄, profile의 bars
app/src/test/java/kr/skku/drivedna/GraphTest.kt  # SeedGraphTest
```

**Structure Decision**: 1~4차 구조 그대로. 새 파일은 없다(생성 스크립트는 저장소 밖).

## Phase 0: Research

[research.md](./research.md): R1 샘플 그래프 만드는 법, R2 새 시험, R3 점선 설명, R4 막대 아래 뜻 단어(폭·높이·대비), R5 시연 장면과 샘플 파일.

## Phase 1: Design

- [quickstart.md](./quickstart.md): 빌드·테스트·0~19번 칸 확인, 에뮬레이터 캡처(다른 앱과 차례로), 회귀, 화면 점검.
- 에이전트 컨텍스트: 저장소의 `CLAUDE.md`·`HANDOFF.md`는 인수인계용이라 바꾸지 않는다.
- 기술 스택 그림: 새 서비스·외부 의존성이 없어 만들지 않는다.
