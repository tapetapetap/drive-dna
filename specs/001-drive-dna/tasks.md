# Tasks: DRIVE DNA

**Input**: `specs/001-drive-dna/`(spec, plan, research, data-model, contracts/ui, quickstart, approval-gate)

**Tests**: 원칙 V에 따라 `Dna.kt`의 JUnit 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.

## Format: `[ID] [P?] [Story] Description`

- 경로는 저장소 루트(`drive-dna` 워크트리) 기준이다.
- 소스는 `app/src/main/java/kr/skku/drivedna/`, 테스트는 `app/src/test/java/kr/skku/drivedna/`다.
- **첫 앱**은 `/Users/jiwon/Desktop/현대자동차 공모전/unused-feature-app`이다. 읽기만 하고, 필요한 파일은 복사해 쓴다.
- 결정 D1~D14는 `approval-gate.md`다.

## Phase 1: Setup

- [X] T001 `app/src/main/AndroidManifest.xml` 시험판을 정식 앱으로 바꾼다.
  - 권한: `pleos.car.permission.CAR_INFO`·`CAR_DRIVE`·`CAR_LIGHTS`·`CAR_ENERGY`·`TTS_SERVICE`(D5), 포그라운드 서비스(`specialUse`), 부팅 수신, 알림
  - `WatchService`와 `BootReceiver`를 선언하고, 앱 아이콘을 `@mipmap/ic_launcher`로 한다.
  - 첫 앱 매니페스트를 본보기로 쓰되 공조·좌석·창·미러 권한은 뺀다.
- [X] T002 [P] 첫 앱의 `res/values/colors.xml`, `res/values-night/colors.xml`을 복사해 `app/src/main/res/`에 둔다. 차 그림·패널·점 색은 빼고, 지도 바탕 `map_top`·`map_bottom`은 `pane_top`·`pane_bottom`으로 이름을 바꾼다. `res/values/strings.xml`에는 앱 이름 "DRIVE DNA"를 둔다.
- [X] T003 [P] `app/src/main/res/drawable/`에 아이콘을 둔다.
  - 새로 그림: `ic_speed`(역동성), `ic_signal`(대응 방식), `ic_rhythm`(주행 리듬), `ic_energy`(운전 목적), `ic_dna`(유형·알림), `ic_list`, `ic_grid`(24dp 선 아이콘, 흰색, 굵기 1.8)
  - 첫 앱에서 복사: `ic_z_wheel` → `ic_wheel`(주행 중), `ic_clock`(짧은 운전)
  - 앱 아이콘(`ic_launcher_bg`, `ic_launcher_fg`, `mipmap-anydpi-v26/ic_launcher.xml`): 보라→파랑 그라데이션 위에 흰 이중 나선
- [X] T004 [P] `Ui.kt`를 만든다.
  - 첫 앱 `Screens.kt`의 `class Ui`(text, small, headline, body, round, dot, column, row, grid, card, icon, tile, chip, hero, banner, cardTitle)와 `enum Tile`, `day()`·`timeOfDay()`·`dayLabel()`, `Views.kt`의 `elapsed()`를 패키지만 바꿔 가져온다(D7).
  - 쓰지 않는 도우미(obj, moment, where, pulse, recent, tile의 배지)는 가져오지 않는다.
- [X] T005 `app/src/main/res/layout/activity_main.xml`(가로: 왼쪽 ScrollView 안 `dnaPane` 3 : 오른쪽 `scroll`/`screen` 5)과 `app/src/main/res/layout-port/activity_main.xml`(세로: 위 `scroll`/`screen` 1.4 : 아래 ScrollView 안 `dnaPane` 1)을 만든다(D9). 시험판 `MainActivity.kt`는 T013에서 바꾼다.

**Checkpoint**: `./gradlew :app:assembleDebug` 성공

---

## Phase 2: Foundational (판단 로직, 신호, 시연 스크립트)

- [X] T006 `DnaTest.kt`를 먼저 쓰고 실패를 확인한다. 가짜 시계로 다음을 확인한다.
  - **Tracker**
    - 주행거리로 속도 추정(2초 이상 앞선 표본), 움직인 시간(직전 속도 표본 간격), 평균 속도
    - 줄어든 주행거리를 버리고 그 값부터 다시 잼
    - 급가속 2.5·급감속 −3.0 m/s²와 히스테리시스(1.0/−1.0), 부드러운 가감속은 0
    - 0.2초마다 쓰고 1.02초마다 받는 시속 50km 유지 → 급가속·급감속 0, 속도 변동 5% 미만(D3)
    - 큰 회전 90°/45° 재무장
    - 미리 켠 신호(1.5초 이상, 10초 창, 꺼진 신호는 한 번만), 운전 전부터 켜져 있던 방향지시등
    - 빠른 조향(창 시작 값 포함 1초 창, 부호 있는 변화 폭 90°, 마지막으로 센 때부터 2초)
    - 브레이크 횟수(운전 시작 때 밟고 있던 것 포함)
    - 100km당 전력(줄지 않으면 없음)
    - 운전 도중 시작한 운전은 세지 않음(`notParked`)
    - 신호 없음: 조향·방향지시등·브레이크·배터리 값이 한 번도 오지 않으면 그 지표가 없음(data-model §2 표)
  - **Judge**: 축 4개의 값·글자(0 이상이면 뒤 글자), 출시·시연용 보류 조건과 조건별 이유(신호 없음 포함, contracts §3), 없음인 지표를 뺀 관측 문장, 짧은 운전
  - **Types**: 보류 "·", 별칭 16개, 보류 안내 "{k}개 축은 판정 보류", 맞는 유형, 음성 문장 두 가지(contracts §8)
  - **Profile**: 최신 10개 평균, 축별 보류, 차이 축(0.5 이상, 같으면 축 순서)
  - **Coach**: 비율이 가장 큰 후보, 같으면 표 순서, 기본 문장
  - **RecordFormat**: 왕복(없음 포함), 깨진 줄 건너뛰기, 100개 유지
  - **시나리오 재생**: `scripts/scenarios/*.txt`를 재생해 gentle → GPSE·코칭 기본, dynamic → DRFC·코칭 급감속, city → GPFE, straight → G·SE·대응 방식 보류 이유, short → 짧은 운전(D4·D14)
- [X] T007 `Dna.kt`에 위 로직을 만든다(data-model §1~§7, contracts §2~§5·§8의 문장과 숫자 형식). `Tracker.gear(parked): Metrics?`, `notParked`(D1). T006이 통과해야 한다.
- [X] T008 [P] `Records.kt`: SharedPreferences "records"의 `drives`를 읽고 쓴다. `all()`(오래된 것이 앞), `add(m)`(읽을 수 있는 줄 + 새 줄, 마지막 100개)과 기준 선택 `Context.limits()`(D1·D6).
- [X] T009 `CarWatcher.kt`: 첫 앱 `CarWatcher.kt`의 초기화·재시도·try/catch 방식을 따른다. 초기화가 실패하면 `onNoSignal()`을 부르고 10초마다 다시 시도한다(contracts §6 연결 문제 띠).
  - 기어, `DistanceDriven`, `BrakePedalStatus`, `SteeringWheelAngle`, `TurnSignalState`, `EvBatteryLevel`을 구독해 Tracker로 넘긴다.
  - `onChange()`는 연결됐을 때와 기어가 바뀔 때만 부른다. 운전 끝(P)이면 그 전에 `onDriveEnd(metrics)`를 부른다(D12).
- [X] T010 `WatchService.kt`, `BootReceiver.kt`: 첫 앱과 같은 포그라운드 서비스(`specialUse`)와 TTS(`Mode.ON_DEVICE`)를 쓴다.
  - 운전이 끝나면 `endedAt`(벽시계)·`analyzed`를 채워 `Log.i`로 남기고 `Records.add`를 한다. 분석된 운전이면 음성 한 번과 알림을 보낸다(contracts §8·§9).
  - `driving`(기어 ≠ P), `recording`(Tracker가 운전을 세는 중), `noSignal`(연결 실패)을 공개한다. 연결되면 `noSignal`을 끈다.
  - `onUpdate`는 `driving`·`recording`이 바뀌거나 `noSignal`이 바뀔 때만 부른다(D12·D13).
  - Phase 2 확인용으로 시험판 `MainActivity.onCreate`에 `WatchService.start(this)` 한 줄을 넣는다(D11).
- [X] T018 `scripts/demo.py`와 `scripts/scenarios/`(gentle·dynamic·city·straight·short): 첫 앱 `scripts/demo.sh`의 하트비트·`reset`을 본보기로 Python 3 표준 라이브러리로 쓴다(D3·D4, Phase 8에서 옮겨 옴).
  - 구간표는 "초 시속 조향 브레이크 방향지시등"이고 앞 구간 끝값에서 고르게 바뀐다. `kwh N`은 운전이 끝날 때 줄일 배터리다.
  - 주행거리는 지금 값부터 0.2초마다 경과 시간으로 계산해 쓴다. 콘솔 속도는 정수로 보낸다(research R3).
  - `reset`은 첫 앱 정지, 콘솔 브레이크·조향 0, 방향지시등 `Off`, 배터리 60.0kWh, 앱 기록 삭제, 앱 재시작이다.

**Checkpoint**: 테스트 통과. 설치 후 앱을 열면 서비스가 시작되고, `python3 scripts/demo.py short` 뒤 `adb logcat -d -s WatchService`에 "운전 끝: Metrics(...)"가 남는다.

---

## Phase 3: User Story 1 - 운전이 끝나면 이번 운전 DNA (P1) 🎯 MVP

**Goal**: P에서 이번 운전 결과와 짧은 운전 안내, 첫 실행 화면을 보인다.

**Independent Test**: `python3 scripts/demo.py gentle` → GPSE 결과 화면과 음성 한 번.

- [X] T011 [P] [US1] `AxisBar.kt`: −1~+1 위치에 점을 찍는 막대 View다.
  - 양 끝에 글자를 두고, 판정된 쪽 글자를 진하게 한다.
  - 보류면 회색 점선에 가운데 "·"를 둔다. 점 색은 받는다(결과는 파랑, 나의 DNA는 초록).
- [X] T012 [US1] `Screens.kt`에 다음을 만든다(contracts §1~§6).
  - `Ui.first()`
  - `Ui.result(m, axes, diff, title, top)`: 작은 제목, 유형 카드(아이콘 타일·코드·별칭·한 줄 설명 D8), 축 줄 4개(축 아이콘·이름, 관측 문장과 보류 이유 → 글자, `AxisBar`), "한 가지 제안" 카드, 차이 줄, "잠정 기준 · 속도는 주행거리로 추정했어요"
  - `Ui.short(m, limits)`: 안내 숫자는 limits에서(D7)
- [X] T013 [US1] `MainActivity.kt`: 시험판을 바꾼다.
  - 서비스를 시작하고, 첫 앱처럼 알림 권한을 런타임으로 요청한다(D5). `onUpdate`로 다시 그린다.
  - 상태 FIRST, SHORT, RESULT를 그린다(data-model §8). `limits()`로 기준을 고른다.

**Checkpoint**: `gentle`, `short`로 결과·짧은 운전 화면 확인

---

## Phase 4: User Story 2 - 운전 중에는 보여 주지 않는다 (P1)

- [X] T014 [US2] `Screens.kt`의 `Ui.driving(recording)`과 `MainActivity.kt`의 DRIVING 최우선 상태를 만든다.
  - 세지 않는 운전이면 "이번 운전은 기록하지 않아요. 다음 운전부터 지켜볼게요."(D13)
  - 주행 중에는 화면을 다시 그리지 않는다.
  - 나의 DNA 칸의 칩은 `isEnabled = false`이고 흐리게 둔다.
  - 운전 시작, 새 기록(마지막 기록의 `endedAt` 변화), onPause에 열린 페이지를 닫는다(data-model §8).

**Checkpoint**: 시나리오 실행 중 덤프에 "운전 중이에요"와 `enabled="false"`, 운전 중 음성 로그 0건

---

## Phase 5: User Story 3 - 나의 DNA와 차이 (P2)

- [X] T015 [US3] `Screens.kt`의 `Ui.profile(p, driving, onHistory, onTypes)`(contracts §7, D9)를 `MainActivity`가 `dnaPane`에 늘 그린다. 결과 화면의 차이 줄은 이번 운전 직전까지의 Profile로 낸다(data-model §5).

**Checkpoint**: `gentle` → `dynamic` 뒤 나의 DNA "운전 2번 기준"과 차이 줄 "역동성이 평소보다 역동적 쪽이었어요."

---

## Phase 6: User Story 4 - 지난 운전 다시 보기 (P2)

- [X] T016 [US4] `Screens.kt`의 `Ui.history(list, limits, onOpen, onClose)`를 만든다. 날짜 머리 아래 운전마다 카드 하나("{코드} · {별칭}", "{시각} · {km}km · {길이}")이고, 없으면 안내 문구다.
  - `MainActivity`에 HISTORY·RECORD 상태를 둔다. 페이지는 한 번에 하나다(D10).
  - 다시 보기는 `Ui.result`를 쓰고, 작은 제목은 그때 시각이며, 위쪽에 "←  운전 기록" 칩을 둔다. 차이 줄은 그 운전보다 앞선 분석된 기록으로 만든 Profile과 비교한다(data-model §5).
  - 나의 DNA 칸의 "운전 기록 ›" 칩으로 연다.

**Checkpoint**: 운전 두 번 뒤 기록 두 줄, 줄을 누르면 그 운전 결과, "←  운전 기록"으로 목록

---

## Phase 7: User Story 5 - 16유형 (P3)

- [X] T017 [US5] `Screens.kt`의 `Ui.types(mine, current, onClose)`를 만든다. 축 설명 4줄과 4×4 격자(코드·별칭), 색 설명이다.
  - 나의 유형은 초록 테두리, 열기 직전에 보던 결과·다시 보기의 유형은 파랑 테두리다. 같은 칸이면 초록이다. 보류 축이 있는 코드는 맞는 유형을 모두 강조한다(D10).
  - `MainActivity` TYPES 상태로 두고, "16유형 ›" 칩으로 연다.

**Checkpoint**: `gentle` 뒤 16유형 표에서 GPSE 칸이 초록 테두리, `straight` 결과에서 열면 G·SE에 맞는 두 칸이 강조된다.

---

## Phase 8: Polish & 검증

- [X] T019 에뮬레이터에서 quickstart를 검증하고, 화면을 `docs/screenshots/`에 저장한다.
- [X] T020 [P] `specs/001-drive-dna/verification.md`: 테스트 결과, 시나리오별 결과, 확인하지 못한 것.
- [X] T021 [P] `README.md`: 공모전 README 필수 항목(앱 이름·한 줄 소개, 실행 방법, 테스트 환경·설정, APK 위치)과 시연 방법, 한계.

---

## Dependencies & Execution Order

- Phase 1 → Phase 2(T006 → T007 → T008~T010, T018) → US1 → US2 → US3 → US4 → US5 → Phase 8
- `Screens.kt`와 `MainActivity.kt`를 고치는 T012~T017은 차례로 한다.
- T002·T003·T004는 동시에 할 수 있다.

## Implementation Strategy

1. 판단 로직(Phase 2)을 먼저 테스트로 굳힌다. 시연 시나리오 재생 테스트가 SC-001을 지킨다.
2. MVP: US1과 US2. P에서 결과가 나오고, 주행 중에는 조용하다.
3. US3~US5로 누적·기록·유형을 더한다.
4. 에뮬레이터 검증과 README.
