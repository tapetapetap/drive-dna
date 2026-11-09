# Tasks: DRIVE DNA 보조 태그

**Input**: `specs/002-tags/`의 spec.md, plan.md, research.md, data-model.md, contracts/ui.md, quickstart.md
**Prerequisites**: 1차(`specs/001-drive-dna/`) 구현·커밋 완료

**Tests**: 원칙 V에 따라 판정·기록·Tracker 입력은 JUnit(`app/src/test/java/kr/skku/drivedna/DnaTest.kt`)을 먼저 쓰고 실패를 본 뒤 구현한다.

**경로 기준**: 저장소 루트 `/Users/jiwon/orca/workspaces/unused-feature-app/drive-dna`

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [X] T001 1차 상태를 확인한다.
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug`(33개 통과)
  - 디버그 전용 `app/src/debug/AndroidManifest.xml`·`ProbeReceiver.kt`가 빌드에 들어간다.
- [X] T002 [P] 헌법을 1.1.0으로 고친다(`.specify/memory/constitution.md`, plan Constitution Check의 문구).
  - VI: 위치는 끝난 곳 한 점만 기기 안에 둔다.
  - Constraints: 권한 목록에 `CAR_SEATS`, `CAR_WINDOWS`, `FUSED_LOCATION`을 더한다.
  - Last Amended 2026-11-10
- [X] T003 [P] 의존성과 권한을 더한다.
  - `gradle/libs.versions.toml`: `pleosLocation = "1.1.3"`, `pleos-location = { group = "ai.pleos.playground", name = "FusedLocation", version.ref = "pleosLocation" }`. 같은편 `gradle/libs.versions.toml`과 같다.
  - `app/build.gradle.kts`: `implementation(libs.pleos.location)`
  - `app/src/main/AndroidManifest.xml`: `pleos.car.permission.CAR_SEATS`, `pleos.car.permission.CAR_WINDOWS`, `pleos.car.permission.FUSED_LOCATION`
  - `FUSED_LOCATION`은 런타임 권한이다(research R9). `MainActivity`의 권한 요청에 `POST_NOTIFICATIONS`와 함께 넣는다. 거부돼도 앱은 돌고, 위치 없는 운전으로 남는다.
  - `app/src/main/res/values/colors.xml`·`values-night/colors.xml`에 `green_soft`(#FFE3F7EC / #FF143726)를 더한다.

---

## Phase 2: Foundational

- [X] T004 `DnaTest.kt`에 먼저 쓴다(data-model §1·§2·§6).
  - **Tracker**
    - 크루즈 작동 시간 누적: 켜짐→꺼짐, 운전 끝에서 마감, 운전 전 켜짐은 시작부터
    - 크루즈 값 없음: null만 받은 운전과 아무 값도 받지 못한 운전은 `cruiseMs` null, `ENABLED`(false)를 받은 운전은 0
    - 와이퍼 시간(같은 방식)
    - 시작·끝 SOC
    - 시작 때 착좌
    - 끝 위치(마지막 한 점)
  - **기록 형식**: 20칸 왕복, 13칸 옛 줄 읽기(새 칸 null), 다른 칸 수 건너뜀
  - **로그 줄**: 좌표를 지운 줄에 "위치 있음/없음"(data-model §6)
- [X] T005 `app/src/main/java/kr/skku/drivedna/Dna.kt`에 만들어 T004를 통과시킨다.
  - `Metrics`의 새 칸 일곱(기본 null)
  - `Tracker`의 `cruise`·`soc`·`wiper`·`seat`·`place`, `startDrive`·`endDrive`의 새 값
  - `RecordFormat` 20칸
  - `app/src/main/java/kr/skku/drivedna/WatchService.kt`의 "운전 끝" 로그를 좌표 없는 줄로 바꾼다

---

## Phase 3: User Story 1 - 크루즈 (P1) 🎯 MVP

**Goal**: 크루즈 태그 판정, 나의 DNA 칩, "태그 ›" 칩, 태그 화면
**Independent Test**: `seed` 뒤 태그 화면 크루즈 칸, `highway` 뒤 근거의 운전 수 증가

- [X] T006 [US1] `DnaTest.kt`에 `TagTest`(크루즈)를 먼저 쓴다.
  - data-model §4-1의 크루즈 세 줄(갑·을·병)
  - 장거리 2번이면 보류 문구
  - 크루즈 값이 모두 null이면 태그 목록에 크루즈가 없다.
  - 장거리 조건(DEMO 1분·20km/h, RELEASE 30분·60km/h)
  - 짧은 운전(분석 안 됨)은 빠진다.
  - 근거 문장 "장거리 운전 3번의 크루즈 비율 중앙값 70%"
  - 장거리가 2번일 때 `fact`가 null이고 `withheld`만 있다(data-model §4 "최소 수보다 적을 때")
  - 짝수 개 중앙값(두 가운데 값의 평균)
- [X] T007 [US1] `Dna.kt`에 `TagId`·`Tag`·`Tags.all`(크루즈 갈래)과 `Limits`의 새 칸(data-model §3)을 만든다. T006을 통과시킨다.
- [X] T008 [US1] `app/src/main/java/kr/skku/drivedna/CarWatcher.kt`를 고친다(research R8·R9).
  - **구독**: 크루즈·SOC·와이퍼·조수석 구독을 더한다. 1차 `watch*`와 같은 방식으로 `ui { tracker.… }`로 옮기고, 하나가 실패해도 나머지를 지켜본다.
  - **운전 시작 값**: 운전이 시작될 때(기어가 P에서 바뀐 때) SOC·착좌·크루즈를 getter로 한 번 더 읽어 넣는다(R8 "운전 시작 때 값").
  - **register 측정**: 에뮬레이터에서 콘솔 값을 바꿔 크루즈(`full_autonomous`↔`ready`)·와이퍼·SOC·착좌 register가 변화를 주는지 재서 R8에 적는다.
    - 변화를 주지 않는 신호가 있으면 그 신호만 운전 중 2초마다 getter로 읽게 한다(위임 결정 D7).
  - **위치**: `watchLocation()`을 따로 두고 `start()` 밖에서 한 번만 부른다. Vehicle 재시도와 겹치지 않게 하기 위해서다.
    - `FusedLocation(context)`
    - `isSupported()`가 거짓이면 로그만 남긴다.
    - `initialize()` 뒤 `registerFusedLocationCallback`이다. `onChanged`에서 위도·경도를 `tracker.place`에 넣는다.
    - 실패하면 10초 뒤 자기만 다시 시도한다.
    - `stop()`에서 `release()`한다.
    - 같은편 `PickupService.watchLocation()`과 같은 모양이다.
  - 이 앱에서 위치가 오는지(geo fix → `place`) 재서 R9에 적는다.
- [X] T009 [P] [US1] `app/src/main/java/kr/skku/drivedna/Screens.kt`와 `Ui.kt`를 고친다(contracts/ui.md, 위임 결정 D6).
  - `Ui.round()`에 점선 인자를 더한다(`dash`, `gap`, 기본 0).
  - 칩은 `Ui.grid(2, …)`로 놓는다.
  - `profile`: 유형 아래 판정 태그 칩 줄 또는 "보조 태그는 운전이 더 쌓이면 보여요", 버튼 줄의 "태그  ›"
  - 새 `tags(list: List<Tag>, onClose)` 화면: 카드, 보류 점선, 맨 아래 문구, "✕  닫기"
- [X] T010 [US1] `app/src/main/java/kr/skku/drivedna/MainActivity.kt`가 태그 화면을 16유형 화면과 같은 방식으로 열고 닫는다. 태그는 `Tags.all(records.all(), limits())`로 계산한다.
- [X] T011 [US1] `scripts/demo.py`를 고친다(quickstart.md).
  - `seed`: `scripts/seed.txt`(24줄, data-model §6의 20칸 형식, 새로 만듦)를 `records.xml`에 쓰고 앱을 연다(research R10).
    - 24줄은 아래 제약을 지켜 직접 정한다.
    - 크루즈 값은 장거리 3번(70·65·80%)에만 둔다. 나머지는 `cruiseMs` 빈 칸이거나 장거리가 아닌 운전(시연 기준 1분 미만)이다.
    - 충전은 정확히 3번(직전 55·48·60%)이다. 마지막 줄 `socEnd`는 50이다.
    - 끝난 곳은 두 장소 위주(새 장소 2/24)이고, 한 곳은 `highway`의 `place`(37.2839, 126.9896)에서 150m 안이다.
    - 동승 3번은 지수가 평소보다 40% 낮고, 비 3번은 평소와 4% 차이다. 맑은 운전의 `wiperMs`는 0(빈 칸 아님)이다. 모든 줄의 `hardAccel`은 빈 칸이 아니다.
    - `endedAt`은 2026-10-20부터 하루 한두 번이다.
  - `DnaTest`에 `seed.txt`를 읽어 `Tags.all(…, Limits.DEMO)`이 다섯 판정(크루즈 위임형, 여유 충전형, 루틴형, 동승 맞춤형, 빗길 한결형)을 내는지 확인하는 테스트를 더한다.
  - 시나리오 지시어 `soc`·`place`·`seat`와 구간 여섯째 칸 `C`·`W`
    - 크루즈는 `driving-mode?ad_mode_state=full_autonomous`, 끔은 `ready`
    - 와이퍼는 `body?front_wiper=Low|Off`
    - 착좌는 콘솔에 반대로 쓴다.
    - 위치는 `adb emu geo fix`
  - `reset`에 크루즈 `ready`, 와이퍼 `Off`를 더한다.
  - 새 시나리오 `scripts/scenarios/highway.txt`
    - 첫 줄 설명
    - `soc 85 78`, `place 37.2839 126.9896`
    - 시속 80km로 약 2분이고 그중 1분 30초 `C`
  - `DnaTest`의 `ScenarioTest` 파서가 새 지시어와 여섯째 칸을 Tracker에 넣게 고친다(크루즈·와이퍼·SOC·위치·착좌).
  - `highway` 재생 테스트: 크루즈 비율 ≥ 0.5, `socStart` 85
- [X] T012 [US1] 에뮬레이터에서 `seed` → 태그 화면 → `highway` → 태그 화면을 확인하고 메모한다.

---

## Phase 4: User Story 2 - 충전 (P1)

- [X] T013 [US2] `TagTest`(충전)를 먼저 쓴다.
  - data-model §4-1의 충전 세 줄
  - 충전 찾기(+10%p 이상만, 9%p는 아님)
  - SOC가 없는 운전이 끼면 그 앞뒤로 찾지 않는다.
  - 근거 "충전 3번의 충전 직전 배터리 중앙값 55%(집 충전 포함)"
- [X] T014 [US2] `Tags.all`에 충전 갈래를 만든다. T013을 통과시킨다.

---

## Phase 5: User Story 3 - 이동 범위 (P2)

- [X] T015 [US3] `TagTest`(이동 범위)를 먼저 쓴다.
  - 150m 묶음: 149m 같은 장소, 151m 새 장소, 기준점은 첫 점 고정, 둘이 걸리면 가장 가까운 장소(위임 결정 D5)
  - data-model §4-1의 이동 범위 세 줄
  - 20번 미만 보류(시연 5번)
  - 근거 "새 장소 3% · 자주 가는 2곳이 85%"
  - 근거와 보류 문장에 숫자(소수점 좌표)가 들어가지 않는다.
- [X] T016 [US3] `Tags.all`에 이동 범위 갈래를 만든다. T015를 통과시킨다.

---

## Phase 6: User Story 4 - 동승·빗길 (P3)

- [X] T017 [US4] `TagTest`(상황)를 먼저 쓴다.
  - data-model §4-1의 동승·빗길 줄
  - 근거 문장 네 경우(data-model §5, contracts/ui.md 표), 상황 집단이 모자랄 때 그 문장이 먼저(§4)
  - 사이(−18%) 보류
  - 평소 0·상황 0 한결형, 평소 0·상황 >0 맞춤형
  - 양쪽 최소 횟수(릴리스 3, 시연 2)
  - 비 기준(와이퍼 3분, 시연 30초)
  - `hardAccel` null(속도 없음) 운전 제외
- [X] T018 [US4] `Tags.all`에 동승·빗길 갈래를 만든다. T017을 통과시킨다.

---

## Phase 7: Polish & 검증

- [X] T019 에뮬레이터에서 아래를 확인한다.
  - `reset` → 1차 장면 다섯(SC-011)
  - `seed` → 태그 화면(SC-008), `highway`(SC-009)
  - 좌표가 없다(SC-010): `uiautomator` 덤프, `adb logcat -s WatchService`의 "운전 끝" 줄, `demo.py tour` 출력
  - `logcat -b crash`
  - 새 화면(칩 줄, 태그 화면)을 `docs/screenshots/`에 더한다.
- [X] T020 [P] `specs/002-tags/verification.md`를 쓴다.
  - 테스트 수, 장면 결과, SC-007~011
  - 확인하지 못한 것: 실차 크루즈·와이퍼·착좌·위치, 실제 몇 주 기록
- [X] T021 [P] `README.md`와 `사용설명서.md`를 고친다.
  - 태그 소개
  - "위치는 모으지 않습니다"를 "끝난 곳 한 점만 차 안에"로 고친다.
  - README 75행의 "크루즈 상태와 전비 신호는 에뮬레이터에서 확인되지 않아 쓰지 않습니다"에서 크루즈만 빼고 전비 부분은 남긴다.
  - 시연 명령 `seed`·`highway`, 한계

---

## Dependencies & Execution Order

- Setup → Foundational(T004·T005) → US1(화면·시연 포함) → US2 → US3 → US4 → Polish
- `Dna.kt`·`DnaTest.kt`를 모든 이야기가 고치므로 순서대로 한다. 화면(T009)은 판정(T007)과 동시에 할 수 있다.

## Implementation Strategy

1. MVP: Foundational + US1(크루즈와 태그 화면 전체)
2. US2 충전 → US3 이동 범위 → US4 상황
3. 워크플로: 구현 뒤 ponytail-review → /code-review → /security-review(위치를 다루므로)
