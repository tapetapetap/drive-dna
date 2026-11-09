# 인수인계 — 현대차 Pleos 공모전 두 번째 앱

> 2026-11-09, 첫 번째 앱 "안 써본 기능"을 만든 세션이 썼다.
>
> 2026-11-09 이후: 두 번째 앱은 **DRIVE DNA**로 정해졌고 이름을 바꿨다. 이 문서의 `app-2`는 아래로 읽는다.
>
> 이 폴더(`/Users/jiwon/orca/workspaces/unused-feature-app/drive-dna`)는 Orca가 만든 워크트리다.
> - 첫 번째 앱 저장소(`unused-feature-app`)를 함께 쓰지만, 브랜치 `drive-dna`는 첫 앱과 기록을 공유하지 않는 별도 브랜치다.
> - `git push`는 GitHub `tapetapetap/drive-dna`(비공개)의 `main`으로 가도록 설정되어 있다(원격 `drivedna`). `origin`(첫 앱 저장소)으로는 푸시하지 않는다.
> - 앱 설명과 실행 방법은 `README.md`, 설계·검증 기록은 `specs/001-drive-dna/`다.
>
> 첫 번째 앱은 `/Users/jiwon/Desktop/현대자동차 공모전/unused-feature-app`(브랜치 `main`)에서 다른 세션이 계속 작업한다. GitHub는 `tapetapetap/unused-feature-app`(비공개)다. 그 폴더는 **읽기만** 하고 고치지 않는다.
>
> 커밋과 푸시는 사용자가 요청할 때만 한다.
>
> 아래에서 `../`는 이 폴더의 부모가 아니라 공모전 폴더 `/Users/jiwon/Desktop/현대자동차 공모전`을 가리킨다.

## 0. 먼저 할 일

1. 이 문서를 끝까지 읽는다.
2. 만들 앱을 정한다.
   - 상위 폴더의 `DRIVE_DNA_자동차_운전성향_보고서.pdf`(2026-11-08)가 후보로 보인다. 운전성향을 4개 축·16유형으로 보여 주는 기획이다.
   - 이 기획은 §4의 "차 신호로 실제 받을 수 있는 것"과 먼저 대조한다. 차속 신호가 없고 계기판 표시등도 못 읽는다.
3. §7 절차대로 진행한다. brainstorming으로 설계를 잡고, 이 폴더에서 `specify init --here --integration claude`를 실행한 뒤 `/speckit-constitution` → `/speckit-specify` … 순서로 간다.
4. 빌드 뼈대는 첫 번째 앱에서 복사한다(§2).

## 1. 공모전 핵심

원문은 `../공모전 정보.md`와 `../온라인 설명회 요약.md`에 있다.

- **마감은 2026-11-15(일) 23:59**다. 이메일로 접수한다(appdevchallenge@hyundai-partners.com).
  - 접수양식(기획서)은 필수다.
  - 시연 영상과 GitHub 저장소(소스 + APK)는 가산점이다.
- **복수 출품이 가능하고 개수 제한이 없다**(설명회 Q&A).
  - 작품은 앱명으로 구분한다.
  - 메일 제목에도 앱명을 넣는 것이 안전하다. 설명회 영상 안내 형식은 `[차량용 앱 개발 공모전] 앱 명_팀명_대표자명`이다.
- 일정:
  - 11/23: 1차 서류 발표(20팀)
  - 12/3~4: 2차 PT. **실제 동작하는 앱 시연**이 필수이고, 에뮬레이터 화면도 된다.
  - 12/18: 최종 10팀 발표
  - 2027.02: 전시·시상(현장 참석 의무)
- 필수 심사 항목은 네 개다. 기획서 2~6번이 여기에 그대로 대응한다.
  - 차량특화성
  - 창의성
  - 기술 활용성(Pleos SDK를 핵심 기능에 쓰는가)
  - 사업성(타깃 고객, 예상 MAU, Retention, 확대 계획)
- GitHub 제출 규칙:
  - 소스와 APK를 같은 저장소에 둔다. APK는 `apk/팀명_앱이름_release.apk`(release 빌드)다.
  - README에는 앱 이름과 한 줄 소개, 실행 방법, 테스트 환경과 설정, APK 위치를 쓴다.
  - Private 저장소면 심사용 계정을 Collaborator로 초대한다.
- 타 공모전 기수상작과 표절작은 심사에서 빠진다.
- 기획서 02(심사 자료)에는 이름·연락처 같은 개인정보를 쓰지 않는다.
- 팀 회의 기록은 두 가지다.
  - `../현대자동차 공모전 회의 1105/회의록 1105.md`: 컨셉 후보에 대한 팀 반응 포함
  - `../11 7 현차 공모전 회의.mp3`

## 2. 개발 환경 (첫 번째 앱에서 검증한 설정)

| 항목 | 값 |
|---|---|
| JDK | 17. `export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null \|\| echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)` |
| Gradle / AGP / Kotlin | wrapper 8.11.1 / 8.8.2 / 2.0.21 |
| Android | minSdk 28, targetSdk 34(에뮬레이터 = API 34), compileSdk 35 |
| Pleos SDK | Maven `https://nexus-playground.pleos.ai/repository/maven-releases/`. `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:TextToSpeech:3.3.1` |
| UI | Android View(Compose 아님). 화면은 코드로 만들고, 그림은 Canvas View로 그린다 |

- **복사할 파일**은 `../unused-feature-app/` 아래에 있다.
  - 루트: `settings.gradle.kts`(Pleos 저장소 설정이 들어 있음), `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `.gitignore`
  - `gradle/`: `libs.versions.toml`, `wrapper/`
  - `app/build.gradle.kts`
  - `local.properties`는 Android Studio가 다시 만든다.
- `app/build.gradle.kts`의 `applicationId`와 `namespace`는 **새 이름으로 바꾼다**. 첫 앱 `kr.skku.unusedfeature`와 같으면 에뮬레이터에서 첫 앱을 덮어쓴다.
- 빌드·테스트: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- 첫 앱이 manifest에 선언한 권한:
  - `pleos.car.permission.CAR_INFO`, `CAR_DRIVE`, `CAR_CLIMATE`, `CAR_SEATS`, `CAR_LIGHTS`, `CAR_WINDOWS`, `CAR_MIRRORS`, `TTS_SERVICE`
  - 새 앱은 쓰는 신호에 맞춰 고른다. `CAR_SAFETY`는 넣었다가 뺐다(§4).
- 첫 앱은 포그라운드 서비스(`specialUse`)와 부팅 수신기로 차 신호를 계속 지켜본다. Pleos App Market 심사에서 이 방식이 허용되는지는 확인하지 못했다.

## 3. 에뮬레이터와 Virtual Console

### 에뮬레이터

Pleos Connect Emulator v2.1.0 arm64(API 34), AVD 이름은 `pleos`다.

```bash
emulator -avd pleos -no-snapshot &      # Cold boot. Quick boot면 VHAL(차 신호)을 못 쓴다
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell am start --user 10 -n <패키지>/.MainActivity   # 앱은 사용자 10에서 돈다
adb shell run-as <패키지> --user 10 cat shared_prefs/<이름>.xml
adb forward tcp:8088 tcp:8088           # Virtual Console
```

- **화면**:
  - 160dpi이고, 가로 앱 창은 1682×1210dp다.
  - 시스템 글꼴은 Asta Sans다. 굵기 600은 Bold, 700은 ExtraBold로 그려진다.
  - 다크 모드는 `adb shell cmd uimode night yes`로 켜고 `no`로 끈다.
- **P가 아닌 기어에서는 앱이 가려진다.** 주행 중 사용 앱(DO)으로 선언하지 않은 앱에 해당한다.
  - D·N에서는 Pleos가 "주행 중에는 사용할 수 없습니다"를 덮고, R에서는 후방 카메라가 덮는다.
  - 가려진 앱의 글자는 `adb shell uiautomator dump /sdcard/ui.xml`로 읽을 수 있다.
  - 주행 중 화면이 꼭 필요하면 DO 선언 방법부터 Pleos 문서에서 확인한다. 첫 앱은 확인하지 않았다.
- **세로 화면**: `wm size`로는 앱 창이 다시 배치되지 않는다. 대신 `adb shell am task resize <taskId> 853 90 1653 1300`을 쓰고, `853 90 2535 1300`으로 원래대로 돌린다.
- **에뮬레이터 하나를 두 앱이 같이 쓴다.** 두 세션이 동시에 기어나 차 상태를 바꾸면 결과가 섞인다. 번갈아 쓰거나, AVD를 하나 더 만들어 `adb -s emulator-5556`처럼 구분한다.

### Virtual Console (차 신호 조작, `http://127.0.0.1:8088`)

- **쓰기 전에 하트비트가 필요하다.** `POST /browser/session?client=<이름>`을 1초마다 보낸다. 3초 넘게 끊기면 쓰기가 `emulator_inactive`로 거부된다.
- 쓰기는 `PUT /vehicle/<영역>?키=값`, 읽기는 `GET /vehicle/state`다. 예:
  - `gear?gear=Drive` (`Park`, `Neutral`, `Reverse`도 됨)
  - `hvac?fan_speed=4`, `hvac?driver_temp=27`
  - `body?front_wiper=Low`
  - `lights?feature=high_beam&value=true`
  - `seat?kind=occupied&area=passenger&value=true`
  - `odometer?traveled_distance_km=1234`
- 시나리오 스크립트 예시는 `../unused-feature-app/scripts/demo.sh`다. 하트비트, 단계별 출력, 차 상태 초기화(`reset`)가 들어 있다.
- 함정:
  - 주행거리를 줄이는 쓰기는 무시된다. 늘 지금 값에서부터 늘린다.
  - 브라우저에 콘솔 페이지를 열어 둔 채 스크립트를 돌리면, 스크립트가 연결할 때 페이지의 토글 상태가 다시 들어간다(앞·뒤 제상이 켜진 적이 있다). 스크립트로 시연할 때는 페이지를 닫는다. 스크립트를 멈춘 상태에서 `curl 127.0.0.1:8088/browser/session`의 `clients`가 0이면 닫힌 것이다.
  - 콘솔의 전조등 "Auto"와 앰비언트 스위치는 SDK 값에 반영되지 않는다.
  - 뒷좌석 착좌는 콘솔 값과 상관없이 늘 "앉음"으로 온다.

## 4. Vehicle SDK 2.3.2 — 확인한 사실

- **차속 신호가 없다.**
  - 첫 앱은 주행거리 표본으로 1분 평균 시속을 계산했다(`Coach.kt`의 `distance()`).
  - 가속, 제동, 조향처럼 운전 성향에 쓸 신호가 있는지는 직접 확인해야 한다(아래 확인 방법).
- **계기판 표시등(`getTelltalesInfo`)은 외부 앱이 못 읽는다.** 읽기에도 내부 전용 권한 `PRIVATE_CAR_SAFETY`가 필요하다.
  - 에뮬레이터에서 SecurityException이 났고, SDK의 `SafetyImpl`을 `javap -v`로 보고 확인했다.
  - 그래서 첫 앱은 오토 홀드, 차로 유지 보조, i-PEDAL을 뺐다.
- `register*`는 구독 직후 현재값을 한 번 보낸다. 따로 `get*`을 부를 필요가 없다. 첫 값은 "변화"가 아니라 기준점으로 다룬다.
- `check…Capabilities`로 그 차에 기능이 있는지 먼저 확인한다. 이 에뮬레이터에서는:
  - MAX A/C와 주행 모드가 `ERROR_NOT_SUPPORTED_PROPERTY`다.
  - 전조등·하이빔 스위치 선택지가 `[OFF, ON]`이다(AUTO 없음).
- 리스너는 `VehicleListener`를 상속하고 `onFailed`를 구현한다. 구독 호출 자체가 예외를 던지는 차도 있어서, 영역마다 try/catch로 감싼다.
- 구독 API가 없는 값(와이퍼 스위치, 전조등·하이빔 스위치)은 운전 중 3초마다 `get…`으로 읽는다.
- 좌석·창·미러는 Area를 골라서 구독한다(`CarWatcher.kt`의 `pickArea`).
- **확인 방법**: SDK AAR은 `~/.gradle/caches/modules-2/files-2.1/ai.pleos.playground/Vehicle/2.3.2/*/Vehicle-2.3.2.aar`에 있다. 안의 `classes.jar`를 풀어 `javap -c -p`로 `…api.implement.*Impl` 클래스를 보면 권한 검사와 값 변환을 알 수 있다.
- 첫 앱에서 참고할 코드(`../unused-feature-app/app/src/main/java/kr/skku/unusedfeature/`):
  - `CarWatcher.kt`: 구독, 능력 확인, 주기 읽기
  - `Coach.kt`: 안드로이드 없이 도는 판단 로직과 가짜 시계. 테스트는 `app/src/test/.../CoachTest.kt`
  - `WatchService.kt`: 포그라운드 서비스, TTS
- **TextToSpeech SDK**: `TextToSpeech(applicationContext, Mode.ON_DEVICE)`를 만든 뒤 `initialize(OnInitializeListener)`를 부른다. 실제로 말했는지는 `adb logcat -d | grep "doSpeak(<패키지>"`로 본다.
- **시연 시간 압축**: 디버그 빌드(`ApplicationInfo.FLAG_DEBUGGABLE`)에서는 앱 시계를 10배로 돌렸다. 1분 조건이 실제 6초가 된다.
- 공모전 SDK에는 이 밖에 FusedLocation(위치), NaviHelper(길 안내), SpeechToText가 있다. 첫 앱은 쓰지 않아서 에뮬레이터에서 어떻게 동작하는지 모른다.

## 5. 화면 (Pleos 디자인 가이드)

| 토큰 | 라이트 | 다크 |
|---|---|---|
| 배경 | `F7F8FA` | `131417` |
| 카드 | `FFFFFF` | `1D1E21` |
| 선 | `E3E5EA` | — |
| 글자 | `131417` | — |
| 파랑 | `0064FF` | `0082FF` |
| 초록 | `02C265` | `00E074` |
| 빨강 | `FE3D16` | — |

- 색 파일은 `../unused-feature-app/app/src/main/res/values/colors.xml`과 `values-night/colors.xml`이다.
- 글자는 18sp보다 작게 쓰지 않는다.
- 사용자가 첫 앱의 초기 화면을 "밤티 디자인"이라고 해서, 2차에서 시각화를 크게 손봤다. 시안은 `../unused-feature-app/docs/design/`, 캡처는 `docs/screenshots/`에 있다.

## 6. 아이디어·조사 자료 (상위 폴더)

- 자료조사 파일:
  - `자료조사 - 기획 선행지식과 레퍼런스.md`
  - `자료조사 2 - 국내 사례·수상작·학술 연구.md`
  - `자료조사 3 - 기술 실현성·사업성 숫자·앱 생태계·PT.md`
  - `자료조사 4 - 유사 공모전 회차별 수상작.md`
  - `자료조사 5 - 경쟁 출품작 공개 저장소 전수 조사.md`
  - `자료조사 6 - 후보 아이디어 3개 기존 사례.md`
- 아이디어 발산: `아이디어 발산.md`, `아이디어 발산 2 - 빈 칸 지도와 상위 3개.md`
- `DRIVE_DNA_자동차_운전성향_보고서.pdf`: 운전성향 4개 축·16유형 기획이다. 신호를 실제로 받을 수 있는지는 §4와 대조한다.
- 다른 팀의 공개 저장소는 읽기만 한다. 이슈, PR, 스타, 포크는 하지 않는다.

## 7. 개발 절차 (사용자 CLAUDE.md 요약)

1. 아이디어는 superpowers `brainstorming` 스킬로만 구체화한다.
2. spec-kit으로 `/speckit-specify` → `/speckit-plan` → `/speckit-tasks`를 진행한다.
3. `/goldfish-review`를 돌린다. 대화를 모르는 리뷰어 2명이 문서만 읽고 검토한다.
4. 구현 전 승인 게이트: 실사용에서 무엇이 달라지는지 보여 주고 사용자 승인을 받는다.
5. `/speckit-implement`를 ponytail(full)과 함께 진행한다.
6. `ponytail-review`, 그다음 `/code-review`(필요하면 `/security-review`)를 돌린다.

- superpowers의 `writing-plans`와 `using-git-worktrees`는 쓰지 않는다.
- 커밋과 푸시는 사용자가 요청할 때만 한다.
- spec-kit 초기화는 이 폴더에서 `specify init --here --integration claude`로 한다. 첫 앱과 같은 0.12.2다.
- 원칙은 `/speckit-constitution`으로 새로 정한다. 첫 앱의 원칙 I~V(`../unused-feature-app/.specify/memory/constitution.md`)를 참고한다.

## 8. 첫 번째 앱 현황 (참고)

- **"안 써본 기능"**: 운전을 마치고 P에 놓으면, 이번 운전에서 쓸 만했는데 안 쓴 차 기능 하나를 음성과 화면으로 권한다.
  - 기능은 18개이고, 이 에뮬레이터에서 지원되는 것은 14개다.
  - 화면은 차 기능 지도, 도감 진행 원, 근거 타임라인으로 되어 있다.
- 문서:
  - `README.md`
  - `specs/002-redesign-21-features/`: `verification.md`에 플랫폼 사실, `goldfish-review.md`에 리뷰 결과
- 진행 중인 작업은 추천 기록 페이지다(명세 단계, `specs/003-recommendation-history`).
- 두 앱이 같은 아이디어 영역(운전 습관 피드백 등)을 겹치지 않게 한다. 첫 앱은 "안 쓴 차 기능 권하기"에 집중한다.
