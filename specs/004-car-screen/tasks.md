# Tasks: DRIVE DNA 4차 — 차 화면 개선

**Input**: `specs/004-car-screen/`의 spec.md, plan.md, research.md, contracts/ui.md, quickstart.md
**작업 위치**: `/Users/jiwon/orca/workspaces/unused-feature-app/car-dna`(브랜치 `car-dna`). 코드 폴더는 `app/src/main/java/kr/skku/drivedna/`(아래 `src/`로 줄임).
**Tests**: 거리 환산 규칙만 새 단위 테스트(헌법 V). 화면은 에뮬레이터로 확인한다.

## Phase 1: Setup

- [X] T001 브랜치 `car-dna`, 태그 `backup/before-car-dna`(= drive-dna a75c795)를 확인하고 바꾸기 전 `./gradlew -q :app:testDebugUnitTest`(66개)가 통과하는지 본다

## Phase 2: Foundational

- [X] T002 환산 규칙 테스트를 먼저 쓴다: `app/src/test/java/kr/skku/drivedna/DnaTest.kt`의 `JudgeTest` 클래스(`base`가 있는 곳)에 `@Test fun ratesNeedDistance()` — `base.copy(km = 0.6, hardAccel = 3, kwhPer100 = 221.3)`를 `Limits.DEMO`로 판정해 역동성 fact가 "0.6km 동안 급가속 3번"으로 시작하고 "10km당"이 없음, 전력 fact가 "1.3kWh를 썼어요(0.6km)", `Coach.line`이 "100km당"으로 시작하지 않음. `base.copy(km = 12.0, kwhPer100 = 30.0)`(급한 조작 0, 다른 코칭 후보 비율이 1 미만)이면 역동성에 "(10km당", 전력 "100km당 30.0kWh를 썼어요", `Coach.line`이 "100km당 30.0kWh"로 시작. `kwhPer100 = 5.0, km = 0.6`이면 "0.1kWh보다 적게 썼어요(0.6km)". 급한 조작이 없는 `base.copy(km = 0.6, kwhPer100 = 221.3)`이면 `assertEquals(Coach.DEFAULT, Coach.line(...))`(지금 코드는 전력 문장을 골라 실패한다). 돌려서 실패를 확인한다
- [X] T003 [P] 색 토큰(contracts §3): `app/src/main/res/values/colors.xml`에 `text3` #FF62656D, 새 `green_text` #FF017A40, `blue_text` #FF0050D8. `values-night/colors.xml`에 새 `green_text` #FF00E074, `blue_text` #FF6CB4FF
- [X] T004 `src/Ui.kt`(contracts §1): `small` 24sp, `chip` 24sp·여백 20·10·22·10·아이콘 26, `banner` 24sp, 새 `action(label, fg, bg, iconRes = 0, onClick)` = `chip` + `minimumHeight`·`minHeight`·`minimumWidth`·`minWidth` 76dp, `gravity = CENTER`, 가로 여백 28, `setOnClickListener`

**Checkpoint**: 앱 빌드는 되고, 단위 테스트는 T002의 기대(새 글·전력 후보 제외)만 실패한다.

## Phase 3: User Story 1 - 차에서 읽고 누를 수 있다 (P1) 🎯 MVP

**Goal**: FR-044~047.
**Independent Test**: quickstart §2.

- [X] T005 [US1] `src/Screens.kt` `profile`: "운전 n번 기준" 24sp, "보조 태그는 …" 24sp, 막대 이름 24sp, 태그 근거 링크 24sp·`minimumHeight` 76dp·`gravity = CENTER_VERTICAL`, 페이지 버튼 셋을 `action(..., R.color.blue_text, R.color.blue_soft, icon) { … }`로 만들어 `column(24, …)`에 칸 폭으로, 가로·세로 모두 머리 바로 아래에 둔다(세로 분기 삭제, 사용자 결정). 주행 중 흐림·비활성 규칙 그대로
- [X] T006 [US1] `src/Screens.kt` `history`: "비교"·"✕  닫기"를 `action`으로(비교 꺼짐 글자 `blue_text`), 둘 사이 `row(24, …)`, 바깥 `column(20, …)` → `column(24, …)`, 줄 시각 24sp, "두 운전 비교"를 `minimumHeight` 76dp·여백 가로 28·세로 0으로
- [X] T007 [US1] `src/Screens.kt` `compare`·`types`·`tags`의 "✕  닫기"를 `action`으로. `src/MainActivity.kt` `resultScreen`의 "←  운전 기록"을 `ui.action(...)`으로
- [X] T008 [US1] `src/Screens.kt` 나머지 고정 글자(contracts §2): `first` 단계 설명, `driving` 칩, `result` 유형 카드 축 이름 줄·축 줄 제목·보류 이유, `graphCard` 제목 28·시각 24·사건 24, `caption` 문장, `compare` 카드 날짜·범례·그래프 제목 28·축 줄 제목·글, `types` 범례 줄(제목 칸 170dp)·칸 별명·표시 설명, `tags` 보류 이유
- [X] T009 [P] [US1] `src/AxisBar.kt`: 끝 글자 24sp, "보류" 20sp

**Checkpoint**: quickstart §2의 1~5가 통과한다.

## Phase 4: User Story 2 - 낮·밤 모두 또렷하다 (P1)

**Goal**: FR-048.
**Independent Test**: quickstart §2의 낮·밤 캡처.

- [X] T010 [US2] `src/Screens.kt`: 태그 칩(`profile`, `tags`)을 `chip(it, R.color.green_text, R.color.green_soft)`로, "새로 정해짐"을 `green_text / green_soft` + `background = round(c(R.color.green_soft), 100f, c(R.color.green))`로, `first` 3단계 번호 원을 `green_soft` 바탕·`green_text` 숫자로
- [X] T011 [US2] `src/Screens.kt` 각주의 `R.color.text4` → `text3`(24sp): `result` "잠정 기준 …", `graphCard` "속도는 주행거리로 추정했어요", `compare` "이 운전은 그래프가 없어요", `tags` "지난: …". 보류 축 결과 글자(40sp)는 그대로. `src/AxisBar.kt` 꺼진 쪽 끝 글자 `text4` → `text3`
- [X] T012 [US2] 파랑 글자 → `blue_text`: `profile` 태그 근거 링크(T005와 같이 해도 됨). 남은 `text(..., R.color.blue …)` 글자를 `grep -n 'R.color.blue,\|R.color.blue)' src/Screens.kt`로 찾아 글자만 바꾼다(점·막대·테두리는 그대로)

## Phase 5: User Story 3 - 낮·밤이 바뀌어도 보던 페이지가 그대로다 (P2)

**Goal**: FR-049, contracts §6.
**Independent Test**: quickstart §3.

- [X] T013 [US3] `src/MainActivity.kt`: `onSaveInstanceState`에 `page`(이름), `openRecord`, `comparing`, `picked`(LongArray), `typesFor`, `listY`, `scrollY`, `seenAt`(있을 때만)을 넣고 `onCreate`에서 되살린다. 되살린 `scrollY`는 첫 `render` 뒤 한 번 `scroll.post { scrollTo(0, y) }`
- [X] T014 [US3] `src/MainActivity.kt` `onPause`: 페이지·비교 닫기를 `if (!isChangingConfigurations)` 안으로

## Phase 6: User Story 4 - 숫자와 화면 구분이 맞는다 (P2)

**Goal**: FR-050~052.
**Independent Test**: quickstart §4, T002 테스트.

- [X] T015 [US4] `src/Dna.kt` `Judge.dyn`: 판정했고 `m.km < 1.0`이면 counts 앞에 "${m.km.km1()}km 동안 "을 붙이고 "(10km당 …)"을 뺀다. `Judge.pur`: `m.km < 10.0`이면 fact를 "${y.one()}kWh를 썼어요(${m.km.km1()}km)"(y = k × km / 100, y < 0.05면 "0.1kWh보다 적게 썼어요(${m.km.km1()}km)"). `Coach.line`: 전력 후보 비율을 `if (m.km >= 10.0) k / 18 else 0.0`으로. T002 통과
- [X] T016 [US4] `src/Screens.kt` `result`: 차례를 그래프 → 유형 → 한 가지 제안(제목 28sp) → 축 카드(첫 줄 "이번 운전 네 축" 28sp 600) → 자막 → 차이 줄 → 각주로(contracts §5)
- [X] T017 [US4] `src/Screens.kt` `profile`: 막대 넷 바로 위에 `small("최근 운전 ${p.drives}번 평균")`(분석된 운전이 없으면 없음)

## Phase 7: Polish & 검증

- [X] T018 빌드·테스트·설치(quickstart §1)
- [X] T019 에뮬레이터 확인 quickstart §2~4. 캡처를 `/Users/jiwon/Desktop/현대자동차 공모전/화면 점검 1113/고친 뒤/DRIVE DNA/`에, UI 덤프 크기표를 verification.md에
- [X] T020 회귀 quickstart §5: `compare`, `tour`, 시나리오 유형
- [X] T021 `/impeccable audit`·`critique` 다시, 점수를 verification.md에. 남은 P1은 `/impeccable polish`(contracts 범위 안)
- [X] T022 ponytail-review(`git diff backup/before-car-dna -- app`) 반영
- [X] T023 `/code-review`(high) 반영, 보안 직접 점검(새 권한·전송·로그 없음)
- [X] T024 `specs/004-car-screen/verification.md`: SC-017~023, 캡처 경로, 점수 전후, 리뷰 반영
- [X] T025 로컬 커밋(브랜치 `car-dna`, 푸시하지 않음)

## Dependencies & Execution Order

- T001 → T002~T004 → 이야기들. T015는 T002 뒤.
- US1·US2·US4의 Screens.kt 작업은 차례로 한다(같은 파일). US3(MainActivity)은 T007 뒤.
- Phase 7은 모두 뒤.

### Parallel Opportunities

- T003(색 XML), T009(AxisBar.kt)는 다른 파일이다.
- T015(Dna.kt)는 Screens.kt 작업과 동시에 할 수 있다.

## Implementation Strategy

- MVP는 US1(글자와 누르는 칸)이다. 안 써본 기능·같은편과 같은 도우미 값이다.
