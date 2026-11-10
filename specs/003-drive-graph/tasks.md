# Tasks: DRIVE DNA 3차 — 운전 그래프, 두 운전 비교, 눈에 띄는 변화

**Input**: `specs/003-drive-graph/`의 spec.md, plan.md, research.md, data-model.md, contracts/ui.md, quickstart.md
**Prerequisites**: 1·2차 구현과 리뷰 완료(902abdf, 단위 테스트 54개)

**Tests**: 헌법 V에 따라 계산(`Dna.kt`)은 `app/src/test/java/kr/skku/drivedna/GraphTest.kt`(새)를 먼저 쓰고 실패를 본 뒤 구현한다.

**경로 기준**: 저장소 루트 `/Users/jiwon/orca/workspaces/unused-feature-app/drive-dna`. 빌드는 `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 1·2차 상태를 확인한다. `./gradlew :app:testDebugUnitTest`가 54개 통과해야 한다(바꾸기 전 기준).

---

## Phase 2: Foundational (그래프 값과 기록 형식)

- [X] T002 [P] `GraphTest.kt`에 그래프 계산 테스트를 쓴다(data-model §1·§2, research R11·R12).
  - `Graph.of`: 칸 수 `min(40, ceil(초))`, 칸 평균, 빈 칸 앞 값 채움, 첫 표본 전 0, 사건 칸 번호와 경계(끝 시각은 마지막 칸), 표본 없음 → null
  - `RecordFormat` 왕복(22필드), 13·20·22필드가 섞인 문자열 읽기(SC-016), 숫자로 읽는 필드(0~11·13~17·19)가 숫자가 아님 → 그 줄 버림, 그래프 필드 어긋남 네 가지 → 그 줄은 남고 그래프만 null(data-model §2)
  - `logLine()` 형식(data-model §1): "그래프 n칸"이 "위치 있음/없음" 앞
- [X] T003 `app/src/main/java/kr/skku/drivedna/Dna.kt`를 고친다. `DnaTest.kt`의 `recordFormatHas20FieldsAndReadsOld13`는 기대 필드 수를 22로 바꾸고 이름을 `recordFormatHas22FieldsAndReadsOld13`로 고친다. T002와 1·2차 53개 + 고친 1개가 통과해야 한다(SC-015).
  - `Graph`, `Graph.of`, `Metrics.graph`
  - `RecordFormat` 22칸
  - `logLine`
- [X] T004 `Dna.kt`의 `Tracker`를 고친다(data-model §1).
  - `trace`·`marks` 수집, `startDrive`에서 비우기, `endDrive`의 `graph`(coarse면 null)
  - `GraphTest`에 가짜 시계로 급가속 한 번·급감속 한 번·빠른 조향 한 번 운전을 넣고, 사건 수가 `hardAccel`·`hardDecel`·`quickSteers`와 같음을 본다(SC-012 불변식).

**Checkpoint**: 새 운전이 그래프와 함께 저장된다.

---

## Phase 3: User Story 1 - 운전의 모습을 그래프로 본다 (Priority: P1) 🎯 MVP

**Independent Test**: `gentle`·`dynamic` 결과, 운전 기록 화면

- [X] T005 [US1] `app/src/main/java/kr/skku/drivedna/Graph.kt`(새)에 `GraphView`를 쓴다. 모드는 결과, 썸네일, 겹침이다(research R13, contracts/ui.md §1~§3, data-model §6 값).
  - data-model §6 색 표대로 `colors.xml`·`values-night/colors.xml`에 여섯 색을 더한다.
- [X] T006 [US1] `app/src/main/java/kr/skku/drivedna/Screens.kt`의 `result()`에 그래프 카드를 넣는다(contracts/ui.md §1). 머리, 그래프, 처음·끝 시각, 사건 줄, 추정 문구를 넣고, 그래프가 null이면 대체 문구를 쓴다.
- [X] T007 [US1] `Screens.kt`의 `history()` 줄에 썸네일을 넣고, 그래프가 없으면 DNA 타일을 쓴다.

---

## Phase 4: User Story 2 - 두 운전을 겹쳐 비교한다 (Priority: P1)

**Independent Test**: 장면 `compare`

- [X] T008 [P] [US2] `GraphTest.kt`에 `Compare.counts` 테스트를 쓴다(data-model §3). GPSE·DRFC는 (4, 0, 0), 같은 운전은 (0, 4, 0), G·SE·GPSE는 (0, 3, 1)이다.
- [X] T009 [US2] `Dna.kt`에 `Compare.counts`를 쓴다. T008이 통과해야 한다.
- [X] T010 [US2] `app/src/main/java/kr/skku/drivedna/AxisBar.kt`에 `second`·`compare` 인자를 더한다(data-model §6 D4). `compare = false`면 1·2차 그대로다.
- [X] T011 [US2] 비교 고르기와 화면을 만든다. 대상은 `Screens.kt`와 `app/src/main/java/kr/skku/drivedna/MainActivity.kt`다.
  - 기록 머리 "비교" 칩, 체크, 안내, "두 운전 비교"(contracts/ui.md §2)
  - COMPARE 화면과 `Ui.compare(a, b, l, onClose)`(contracts/ui.md §3)
  - 고른 운전은 `endedAt` 목록으로 `MainActivity`가 든다. 세 번째를 고르면 첫 것이 풀린다.
  - 닫기·뒤로 가기·비우기와 범례는 data-model §6 D2대로 한다.

---

## Phase 5: User Story 3 - 새 결과와 바뀐 근거가 눈에 띈다 (Priority: P2)

**Independent Test**: `gentle` 결과, `seed` → `highway`

- [X] T012 [P] [US3] `GraphTest.kt`에 `Tags.changes` 테스트를 쓴다(data-model §4).
  - 2차 seed 기록 + highway 같은 운전 하나에서 크루즈·충전이 CHANGED이고 `before`가 지난 문장이다.
  - 분석 운전이 하나뿐이면 모두 NONE이다.
  - 보류였다가 정해지면 NEW다. 판정 → 다른 판정도 NEW다. 판정됨 → 보류는 CHANGED(before = 지난 fact)다.
  - fact가 있는 보류 → 판정이면 NEW이고 before가 지난 fact다.
  - 이전에 없던 태그가 판정으로 나타나면 NEW, 근거가 있는 보류로 나타나면 CHANGED이고, 둘 다 before가 "이전에는 없던 태그"다.
  - 보류 이유만 바뀌면(쌓인 운전 수) NONE이다.
  - 분석 운전이 0개·1개면 모두 NONE이다(0개에서 멈추지 않는다).
  - 최근 분석 운전 뒤에 짧은 운전이 붙어도 결과가 같다(앞까지 자름).
- [X] T013 [US3] `Dna.kt`에 `Change`, `TagView`, `Tags.changes`를 쓴다. T012가 통과해야 한다.
- [X] T014 [US3] 바뀜 표시를 넣는다(contracts/ui.md §4·§5). 대상은 `Screens.kt`의 `tags()`·`profile()`과 `MainActivity.kt`다.
  - 태그 카드의 바뀜 칩과 "지난: …"
  - 나의 DNA 한 줄(누르면 태그)
- [X] T015 [US3] 자막을 넣는다(data-model §5, research R15).
  - `app/src/main/java/kr/skku/drivedna/WatchService.kt`의 `Spoken`, `lastSpoken`(`onDriveEnd`에서 `speak` 직전)
  - `Graph.kt`의 `SoundBars`(data-model §6 값)
  - 스피커 아이콘 `app/src/main/res/drawable/ic_speaker.xml`(새, Material Icons volume_up, Apache 2.0)
  - `Screens.kt` 결과 자막 줄(다시 보기에는 없다: `openRecord == null`, data-model §5)
- [X] T016 [US3] 공개 효과를 넣는다(research R16).
  - `Graph.kt`의 `PulseRing`
  - `Screens.kt` `result(fresh)`의 글자 네 개 애니메이션
  - `MainActivity.kt` companion의 `animatedAt`과 fresh 판단(data-model §5 D1·D15)
  - 링 틀 배치와 `clipChildren`(data-model §6 D14)
  - 움직임 줄이기면 효과를 끈다.

---

## Phase 6: User Story 4 - 샘플 기록에도 그래프가 있다 (Priority: P3)

- [X] T017 [US4] `scripts/seed.txt` 24줄에 22칸(속도·사건)을 붙인다(research R17). 머리 `#` 줄에 "그래프는 시연 준비값"이라 적는다. `ScenarioTest`(`DnaTest.kt`)가 그대로 통과해야 한다. 기록 평균 시속 ±10%와 사건 수를 `GraphTest`에 한 줄로 확인한다.
- [X] T018 [US4] `scripts/demo.py`에 장면 `compare`를 더하고, docstring에 한 줄 적는다.

---

## Phase 7: Polish & 검증

- [X] T019 단위 테스트(54 + 3차), `assembleDebug`, 설치
- [X] T020 에뮬레이터 장면을 돌린다(SC-012~015, quickstart 표).
  - `compare`(안에서 `reset` → `gentle` → `dynamic`을 돈다)
  - `seed` → `highway`, `tour`
  - 움직임 줄이기, 다시 보기, 비교 끄기·한 개
  - 옛 형식 기록: `reset` 뒤 20필드 줄 두 개와 22필드 줄 하나(`gentle`처럼 그래프 있음)를 SharedPreferences `records`의 키 `drives`로 넣고(`adb shell run-as`로 xml을 쓴다) 앱을 다시 연다. 기록 목록의 DNA 타일 줄(SC-016), 옛 기록 결과의 "이 기록은 그래프를 남기기 전 운전이에요", 그래프 있는 운전과 옛 운전의 비교(선 하나, "이 운전은 그래프가 없어요", 축 비교 유지, US2-3)를 캡처한다. 넣는 줄은 `scripts/seed.txt`의 1·2번째 줄 앞 20필드와 그 파일의 22필드 줄 하나다.
  - 캡처는 `docs/screenshots/` 19~27번이다.
- [X] T021 ponytail-review로 과설계를 점검하고 반영한다.
- [X] T022 /code-review(high)로 정확성을 점검하고 반영한다.
- [X] T023 보안을 직접 점검한다. 새 저장 값(속도만, 위치 없음), 로그(그래프 빼고 칸 수), 화면 글을 본다.
- [X] T024 문서를 고친다.
  - `README.md`·`사용설명서.md`에 3차 기능과 `compare` 장면
  - `specs/003-drive-graph/verification.md`
  - `specs/003-drive-graph/approval-gate.md`(2-1 게이트 때 새로 만든다)의 "승인 뒤 구현 중 바뀐 것"
- [X] T025 저장소 밖: DRIVE DNA 전 장면(+ `compare`)을 다시 녹화하고, 자막과 함께 "DRIVE DNA 주행 영상" 아티팩트를 갱신한다.

---

## Dependencies & Execution Order

- Phase 2(그래프 값) → US1(그리기) → US2(겹침, `GraphView` 재사용) → US3(독립, 태그·자막·효과) → US4(seed) → Phase 7
- `Dna.kt`·`Screens.kt`·`MainActivity.kt`를 고치는 작업은 순서대로 한다.

## Parallel Example

```text
T002·T008·T012 GraphTest 묶음(같은 파일이라 한 번에 써도 된다)
T010 AxisBar 두 번째 값
```

## Implementation Strategy

- MVP는 Phase 2 + US1이다. 그래프가 저장되고 결과·기록에 보이면, 같은편 "카드 안 지도"에 해당하는 것이 선다.
- 그다음 비교(US2), 눈에 띄는 변화(US3), 시연 데이터(US4)를 차례로 더한다.
