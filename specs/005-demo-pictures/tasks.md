# Tasks: DRIVE DNA 5차 — 시연용 그림 바로잡기

**Input**: `specs/005-demo-pictures/`의 spec.md, plan.md, research.md, quickstart.md
**작업 위치**: `/Users/jiwon/orca/workspaces/unused-feature-app/car-dna`(브랜치 `car-dna`). 코드 폴더는 `app/src/main/java/kr/skku/drivedna/`(아래 `src/`), 테스트 폴더는 `app/src/test/java/kr/skku/drivedna/`(아래 `test/`)로 줄인다.
**Tests**: 샘플 그래프 규칙만 단위 테스트(헌법 V, research R2). 화면은 에뮬레이터 캡처로 확인한다.

## Phase 1: Setup

- [X] T001 브랜치 `car-dna`와 태그 `backup/before-pictures-dna`(= f23f97c)를 확인하고, 바꾸기 전 `./gradlew -q :app:testDebugUnitTest`(67개, JAVA_HOME은 quickstart §1)가 통과하는지 본다

## Phase 2: Foundational — 테스트 먼저

- [X] T002 `test/GraphTest.kt` `SeedGraphTest`(research R2): 파일 읽기를 클래스 값 `private val seed = RecordFormat.decode(File("../scripts/seed.txt").readText())`로 올린다. `everySeedLineHasAMatchingGraph`에 `assertEquals(40, g.speeds.size)`와 사건 칸 방향(종류 `'a'`인 칸 i는 `g.speeds[i] > g.speeds.getOrElse(i - 1) { 0 }`, `'d'`는 `<`)을 더한다. 새 `@Test fun seedGraphsDiffer()`: 그래프마다 `speeds.map { it / g.avg }`를 만들어 모든 쌍의 칸별 차이 절댓값 평균이 0.03 이상(`assertTrue("$j·$i", …)`, `kotlin.math.abs`), `(4..26).all { s[it] == s[it + 9] }`인 그래프가 없음. 두 검사는 결과(같은 모양 쌍 목록, 9칸 주기 줄 목록)를 먼저 모은 뒤 assert 하나로 판정하고 메시지에 둘 다 싣는다. 그래야 지금 파일에서 두 실패가 한 메시지에 함께 보인다. 테스트 수는 68개 그대로다. 돌려서 지금 seed.txt에서 사건 방향(20분 줄 `a7`), 모양 차이, 9칸 주기가 실패하는지 확인하고 실패 메시지를 verification.md에 남긴다

**Checkpoint**: `everySeedLineHasAMatchingGraph`·`seedGraphsDiffer` 둘만 실패하고 나머지 66개는 통과한다.

**구현 기준**: 2-0.5(구현 전에 바뀌는 화면을 Figma에 그려 2-1 승인의 근거로 쓰는 단계)에서 그린 프레임은 Figma 페이지 "시연용 그림 7차 (1114)"의 ㉑ 나의 DNA 칸(https://www.figma.com/design/mme1NBmcgJhFEvmRzMh4IB/?node-id=33-73), ㉒ 결과 그래프 카드(https://www.figma.com/design/mme1NBmcgJhFEvmRzMh4IB/?node-id=33-159), ㉓ 운전 기록 썸네일(https://www.figma.com/design/mme1NBmcgJhFEvmRzMh4IB/?node-id=33-187)이다. 글과 배치는 T005·T006과 같다. ㉒·㉓의 그래프 모양은 메인 세션 생성기로 그린 예시이고, 실제 값은 research R1과 T003으로 만든다.

## Phase 3: User Story 1 - 샘플 기록이 실제 운전처럼 보인다 (P1) 🎯 MVP

**Goal**: FR-054~058. **Independent Test**: T002 시험, quickstart §1, §2의 1~3.

- [X] T003 [US1] 저장소 밖 임시 폴더(예: 세션 scratchpad)에 일회용 Python 3 스크립트를 두고, research R1 규칙대로 24줄의 20·21번 칸을 만들어 `scripts/seed.txt`에 쓴다. 씨앗은 기록 차례 n(1~24)의 `random.Random(n)`. 0~19번 칸, 줄 차례, 파일 끝 줄바꿈은 그대로 둔다. 머리 `#` 줄은 research R1의 예처럼 만든 방법을 적은 쉼표 없는 한 줄로 바꾼다. 스크립트는 저장소에 넣지 않는다
- [X] T004 [US1] `./gradlew -q :app:testDebugUnitTest`로 T002 검사, `ScenarioTest.seedJudgesAllFiveTags`(`test/DnaTest.kt`), `TagChangeTest`가 통과하는지, quickstart §1의 diff로 `scripts/seed.txt` 0~19번 칸이 같은지 본다. 실패하면 T003의 생성만 고친다(시험 기준은 바꾸지 않는다)

## Phase 4: User Story 2 - 그래프의 점선이 무엇인지 안다 (P2)

**Goal**: FR-059. **Independent Test**: quickstart §2의 1.

- [X] T005 [P] [US2] `src/Screens.kt` `graphCard()`의 마지막 줄 `text("속도는 주행거리로 추정했어요", 24f, R.color.text3)`를 `text("점선은 평균 속도 · 속도는 주행거리로 추정했어요", 24f, R.color.text3)`로 바꾼다. `result()`의 "잠정 기준 · …"(88행)과 `compare()`는 그대로 둔다

## Phase 5: User Story 3 - 막대 양 끝의 뜻을 칸 안에서 읽는다 (P2)

**Goal**: FR-060·061. **Independent Test**: quickstart §2의 4~5.

- [X] T006 [P] [US3] `src/Screens.kt` `profile()`의 `bars`를 `column(2, text(a.title, 24f, R.color.text3), AxisBar(ctx, a, p.values[i], R.color.green), row(0, text(a.leftName, 24f, R.color.text3), text(a.rightName, 24f, R.color.text3), grow = 0))`로 바꾼다(research R4). `src/AxisBar.kt`, `result()`의 축 줄(76행), `compare()`의 막대(225행)는 그대로 둔다

## Phase 6: 빌드

- [X] T007 `./gradlew :app:testDebugUnitTest :app:assembleDebug`(quickstart §1): 68개 통과, `scripts/seed.txt` 0~19번 칸 diff 없음, 머리 줄 쉼표 0. `app/build/outputs/apk/debug/app-debug.apk`가 생긴다

## Phase 7: 에뮬레이터 확인 — 다른 앱과 차례로 쓴다

에뮬레이터 하나를 세 앱이 함께 쓴다. 메인 세션이 정한 차례에 이 묶음을 한 번에 하고, 끝나면 화면 나누기를 풀어 둔 채 넘긴다(끄지 않는다). 사실: Pleos 2560×1440, density 160(1dp = 1px), adb `$HOME/Library/Android/sdk/platform-tools/adb`, 앱 사용자 `--user 10`, 부팅 뒤 `adb forward tcp:8088 tcp:8088`, 화면 나누기 `adb shell input swipe 2547 720 1500 720 800`, 되돌리기 `adb shell input swipe 1706 720 2550 720 800`. 캡처는 `/Users/jiwon/Desktop/현대자동차 공모전/화면 점검 1115/DRIVE DNA/`에 둔다.

- [X] T008 quickstart §2 머리: `adb forward`, `adb install -r app/build/outputs/apk/debug/app-debug.apk`, `python3 scripts/demo.py seed`
- [X] T009 quickstart §2의 1~3: 캡처 `01 결과 그래프`(점선 설명 줄, 샘플 그래프 모양), `02 운전 기록`(썸네일이 줄마다 다름), `03 두 운전 비교`(샘플 둘의 겹친 그래프)
- [X] T010 quickstart §2의 4~6: 캡처 `04 나의 DNA 칸 - 가로`, `05 나의 DNA 칸 - 1대1 세로`(막대 아래 뜻 단어, 위 칸 점선 설명 줄), 화면 나누기 되돌리기와 흐림 풀기
- [X] T011 quickstart §3: `python3 scripts/demo.py compare`가 끝까지 돌고 "다른 축 4개"가 나온다(`scripts/demo.py` 누르기 글자 그대로)

## Phase 8: 점검·리뷰·기록

- [X] T012 `/impeccable audit`(화면이 바뀐 것: `src/Screens.kt` `graphCard`·`profile`, 캡처 01·04·05). 새 P1이 있으면 `/impeccable polish`로 승인 프레임 범위 안에서 고치고 T007·T010을 다시 한다
- [X] T013 ponytail-review(`git diff backup/before-pictures-dna -- app scripts`) 반영
- [X] T014 `/code-review`(high) 반영(`src/Screens.kt`, `test/GraphTest.kt`, `scripts/seed.txt`)
- [X] T015 `/security-review`(이 워크트리에서). 돌지 않으면 같은 관점으로 직접 점검한다: 새 권한·네트워크·로그·인텐트 없음, `scripts/seed.txt` 위치 칸(16·17번) 그대로
- [X] T016 `specs/005-demo-pictures/verification.md`: SC-024~029 결과, 테스트 수, 캡처 경로, audit 결과, 리뷰 반영, Figma 프레임과 다른 점, 2-1 결정(주행 영상 다시 녹화 여부)
- [X] T017 로컬 커밋(브랜치 `car-dna`, 푸시하지 않음): `src/Screens.kt`, `test/GraphTest.kt`, `scripts/seed.txt`, `specs/005-demo-pictures/`, `.specify/feature.json`, 설계 `docs/superpowers/specs/2026-11-14-demo-pictures-design.md`. 생성 스크립트와 캡처는 넣지 않는다

## Dependencies & Execution Order

- T001 → T002 → T003 → T004. T005·T006은 T002 뒤 아무 때나 하되 같은 파일이라 차례로 한다. 병렬: T005·T006(`src/Screens.kt`)과 T003~T004(`scripts/seed.txt`).
- T007은 T004~T006 뒤. Phase 7은 T007 뒤 메인 세션이 정한 차례에. T012는 캡처 뒤, T013~T017은 차례로.
- 전략: MVP는 US1(샘플 그래프, 영상에서 가장 눈에 띔)이다. US2·US3은 한 줄씩이다.
