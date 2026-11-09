# Implementation Plan: DRIVE DNA

**Branch**: `001-drive-dna` (git 브랜치 `drive-dna`) | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/001-drive-dna/spec.md`, 설계 `docs/superpowers/specs/2026-11-09-drive-dna-design.md`

## Summary

운전 중에는 차 신호를 지켜보고, P에 놓으면 이번 운전의 지표 → 네 개 성향 축 → 유형·코칭을 보여 준다. 차 신호는 주행거리, 브레이크, 조향각, 방향지시등, 배터리 잔량이다.

판단은 모두 순수 Kotlin(`Dna.kt`)이고 JUnit으로 지킨다. 서비스는 신호를 넘기고 결과를 저장·발화만 한다.

화면은 첫 앱 "안 써본 기능"의 부품과 배치(지도 칸 → 나의 DNA 칸)를 다시 쓴다. 시연은 Virtual Console HTTP로 주행거리를 0.2초마다 시계 기준으로 쓰는 스크립트(`scripts/demo.py`)로 한다. 시나리오 구간표는 스크립트와 단위 테스트가 함께 읽는다.

## 현재 상태 (구현 전)

- 이름 바꾸기(app-2 → DRIVE DNA)는 끝났다.
  - Orca 워크트리와 git 브랜치는 `drive-dna`, GitHub 저장소는 `tapetapetap/drive-dna`(비공개)다.
  - Gradle `rootProject.name = "drive-dna"`, 패키지와 applicationId는 `kr.skku.drivedna`, 앱 이름은 "DRIVE DNA"다.
- 빌드 뼈대(Gradle 설정·래퍼, 의존성 목록)는 첫 앱에서 복사했다.
- `app/src/main/java/kr/skku/drivedna/MainActivity.kt`는 신호 시험판이다. 받은 값을 로그로만 남기고, T013에서 정식 화면으로 바꾼다.
- `app/src/main/AndroidManifest.xml`은 시험판용이다(권한과 MainActivity만). T001에서 정식 앱 매니페스트로 바꾼다.

## 첫 앱에서 가져오는 것

첫 앱은 같은 공모전에 낸 "안 써본 기능"이다. 경로는 `/Users/jiwon/Desktop/현대자동차 공모전/unused-feature-app`(GitHub `tapetapetap/unused-feature-app`, 패키지 `kr.skku.unusedfeature`)이다. 읽기만 하고, 필요한 파일은 복사한 뒤 패키지를 바꾼다.

| 가져오는 것 | 첫 앱 위치 | 하는 일 |
|---|---|---|
| 색 토큰 | `res/values/colors.xml`, `res/values-night/colors.xml` | Pleos 라이트·다크 색. 이름은 contracts 머리에 있다 |
| `class Ui` | `Screens.kt` | 화면을 코드로 조립하는 도우미. 글자(text·small·headline·body), 둥근 바탕(round), 배치(column·row·grid), 카드(card, cardTitle), 아이콘(icon), 아이콘 타일(tile), 칩(chip), 큰 제목 줄(hero: 타일 + 큰 제목 + 아래 글자), 경고 띠(banner) |
| `enum Tile` | `Screens.kt` | 아이콘 타일의 바탕 그라데이션과 아이콘 색 조합(BLUE·GREEN·VIOLET·SOFT·GREY) |
| 날짜 글자 | `Screens.kt`의 `day()` | "M월 d일" |
| 신호 받기 | `CarWatcher.kt` | SDK 초기화가 실패하면 10초마다 다시 시도, 구독마다 try/catch, 콜백을 메인 스레드로 옮기기 |
| 서비스 | `WatchService.kt`, `BootReceiver.kt` | 포그라운드 서비스(`specialUse`), 부팅 뒤 시작, 온디바이스 TTS, 알림 |
| 배치 | `res/layout/activity_main.xml`, `res/layout-port/activity_main.xml` | 가로는 왼쪽 칸 3 : 오른쪽 상황 화면 5, 세로는 위 상황 화면 / 아래 칸. 첫 앱의 왼쪽 칸은 차 지도이고, 이 앱은 나의 DNA 칸이다 |

## Technical Context

**Language/Version**: Kotlin 2.0.21, JDK 17, AGP 8.8.2, Gradle 8.11.1(첫 앱과 같음)

**Primary Dependencies**: Pleos Vehicle SDK 2.3.2, Pleos TextToSpeech 3.3.1, androidx core-ktx. 새 의존성 없음

**Storage**: SharedPreferences "records" 키 `drives`(data-model §7)

**Testing**: JUnit 4(`app/src/test/java/kr/skku/drivedna/DnaTest.kt`, 시연 시나리오 재생 포함), 에뮬레이터 시나리오(quickstart)

**시연 스크립트**: Python 3 표준 라이브러리만 쓴다(`scripts/demo.py`). 0.2초 간격의 실수 계산 때문이다(approval-gate D4)

**Target Platform**: Pleos Connect Emulator v2.1.0(API 34), 실차 Pleos Connect

**Project Type**: mobile-app (Android 단일 모듈 `app`, 패키지 `kr.skku.drivedna`)

**Performance Goals**: P 뒤 3초 안에 결과(SC-002). 운전 하나의 표본은 수천 개 이하라 메모리에 둔다

**Constraints**:
- 주행 중 안내 금지(원칙 I)
- 기기 밖 전송 금지(원칙 VI)
- 잠정 기준을 밝힌다(원칙 II)

**Scale/Scope**: 기록 100개, 화면 상태 7개, 소스 파일 약 9개

## Constitution Check

| 원칙 | 판단 | 근거 |
|---|---|---|
| I. 운전 중에는 보여 주지 않는다 | 통과 | P가 아니면 상황 칸은 DRIVING이 최우선이고, 음성은 운전 끝 한 번뿐이다. 나의 DNA 칸은 주행 중에도 보이지만 지난 운전들의 요약이라 운전 중에 바뀌지 않고, 버튼은 비활성이라 조작을 이끌지 않는다. 이번 운전 결과는 P에서만 나온다. 점수·순위·미션이 없다 |
| II. 관측 사실이 먼저 | 통과 | 축 줄은 관측 문장 → 글자 순서다. 보류 축은 "·"다. 코칭은 사실 → 제안이고 원인을 단정하지 않는다 |
| III. 차 신호만 | 통과 | 시험판으로 확인한 신호 6개만 쓴다(research R1). 추정 속도라고 밝힌다 |
| IV. 최소 구현 | 통과 | 새 의존성이 없다. 첫 앱 부품을 다시 쓰고, 축 막대는 Canvas View 하나다 |
| V. 판단 로직 테스트 | 통과 | `Dna.kt` 전체(지표·축·유형·누적·코칭·저장)에 JUnit을 둔다 |
| VI. 기록은 차 안에만 | 통과 | SharedPreferences만 쓰고, 위치는 쓰지 않는다 |

설계를 마친 뒤 다시 검토했고 판단은 같다.

## Project Structure

### Documentation (this feature)

```text
specs/001-drive-dna/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/ui.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code

```text
app/src/main/
├── AndroidManifest.xml          # 권한 CAR_DRIVE·CAR_LIGHTS·CAR_ENERGY·TTS_SERVICE, 서비스, 부팅 수신기
├── java/kr/skku/drivedna/
│   ├── Dna.kt                   # 순수 Kotlin: Tracker(지표), Axis 판정, TypeCode·별칭, Profile, Coach, Records 형식
│   ├── CarWatcher.kt            # SDK 구독(기어·주행거리·브레이크·조향·방향지시등·배터리)
│   ├── WatchService.kt          # 포그라운드 서비스: 운전 끝 → 판정·저장·음성·알림
│   ├── BootReceiver.kt          # 부팅 뒤 서비스 시작
│   ├── Records.kt               # SharedPreferences 저장·읽기(최근 100개), 기준 선택 limits()
│   ├── Ui.kt                    # 첫 앱 Screens.kt의 Ui 도우미·Tile 가져옴
│   ├── Screens.kt               # 상황 화면 7종과 나의 DNA 칸
│   ├── AxisBar.kt               # 축 막대 Canvas View
│   └── MainActivity.kt          # 상태 우선순위, 페이지 열고 닫기
└── res/
    ├── layout/activity_main.xml, layout-port/activity_main.xml
    ├── values/colors.xml, values-night/colors.xml, values/strings.xml
    └── drawable/ (아이콘: 브레이크·조향·신호·속도·전력·DNA, 앱 아이콘)
app/src/test/java/kr/skku/drivedna/DnaTest.kt
scripts/demo.py                  # reset, gentle, dynamic, city, straight, short
scripts/scenarios/*.txt          # 시나리오 구간표. DnaTest도 읽는다
```

**Structure Decision**: 첫 앱과 같은 단일 모듈이다. 판단은 `Dna.kt` 한 파일에 모은다(안드로이드 의존 없음).

## 핵심 설계

1. **Tracker(clock)**: 신호 입력 메서드(`gear`, `km`, `brake`, `steer`, `turn`, `batteryWh`)가 data-model §1·§2대로 지표를 쌓는다. `gear(parked): Metrics?`는 운전이 끝나는 순간에만 지표를 돌려준다(첫 값과 운전 도중 시작은 null). 기어가 P가 아닌지는 `notParked`로 공개한다. 시계(elapsedRealtime)는 주입하고, 테스트는 가짜 시계를 쓴다.
2. **Judge(limits)**: `Metrics` → 축 4개(`Axis`), 유형(`Types`), 짧은 운전 여부를 낸다. `limits`는 출시와 시연용 두 벌이고, `Context.limits()`가 `FLAG_DEBUGGABLE`로 고른다(서비스와 화면이 같이 쓴다).
3. **Profile**: 최신 분석 기록 10개의 축 평균과 차이 축을 낸다(§5).
4. **Coach**: 후보 비율이 가장 큰 문장 하나를 고른다(§6, contracts §5).
5. **기록**: 순수 형식 `RecordFormat`(encode·decode·add)과 저장 클래스 `Records`(SharedPreferences, `all()`·`add(m)`, 100개 유지)로 나눈다.
6. **WatchService**: CarWatcher가 신호를 Tracker에 넘긴다. 운전 끝 → `endedAt`(벽시계)·`analyzed`를 채움 → `Log.i` → 저장 → 분석된 운전이면 음성·알림. `driving`·`recording`·`noSignal`(연결 실패)을 공개하고, 화면 갱신(`onUpdate`)은 주행 상태나 연결 문제 띠가 바뀔 때만 부른다(저장 직후에는 기어 변화로 불린다).
7. **화면**: 상황 칸은 상태 우선순위(data-model §8)에 따라 하나를 그린다. 나의 DNA 칸은 늘 그린다(`Ui.profile(p, driving, onHistory, onTypes)`가 칩까지 만든다). 페이지(기록, 다시 보기, 16유형)는 한 번에 하나이고, 운전 시작, 새 기록, onPause에 닫는다.
8. **시연 스크립트**: 하트비트 → 지금 주행거리부터 시작 → 구간표대로 속도·조향을 고르게 바꾸며 0.2초마다 경과 시간으로 계산한 주행거리를 쓴다. 구간마다 브레이크·방향지시등을 바꾸고, 끝에 배터리 잔량을 줄인 뒤 P.

## Complexity Tracking

없음.
