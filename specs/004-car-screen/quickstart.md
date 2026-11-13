# Quickstart: DRIVE DNA 4차 확인

작업 위치는 `/Users/jiwon/orca/workspaces/unused-feature-app/car-dna`(브랜치 `car-dna`)다. 에뮬레이터 준비와 콘솔 연결은 README "시연"과 같다. 에뮬레이터를 켤 때마다 `adb forward tcp:8088 tcp:8088`을 한다.

## 1. 단위 테스트와 빌드 (SC-020, SC-022)

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew -q :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- 기대: 기존 66개와 새 환산 규칙 테스트가 통과한다.

## 2. 글자, 누르는 칸, 대비 (SC-017, SC-018)

1. `python3 scripts/demo.py seed`(샘플 24개, 태그 다섯 판정).
2. 낮(`adb shell cmd uimode night no`)과 밤(`yes`)에서 캡처: 결과, 나의 DNA 칸 아래(버튼 셋), 운전 기록, 비교 고르기, 두 운전 비교, 16유형, 보조 태그, 기록 다시 보기.
3. 각 화면 `adb shell uiautomator dump`의 `clickable="true"` bounds가 contracts §2 누르는 칸 목록에서 모두 76×76 이상인지, 페이지 버튼 셋 사이가 24 이상인지 본다.
4. 코드 검사: `grep -nE '\b(1[0-9]|2[0-3])f\b' app/src/main/java/kr/skku/drivedna/Screens.kt app/src/main/java/kr/skku/drivedna/Ui.kt app/src/main/java/kr/skku/drivedna/MainActivity.kt`로 후보를 모두 뽑고 글자 크기 인자인 것만 본다(반지름·간격 인자는 무시). 남는 글자 크기가 없어야 한다. `AxisBar.kt`는 24f와 20f만.
4-1. 간격: 운전 기록의 UI 덤프 bounds에서 기록 카드끼리, 마지막 카드와 "두 운전 비교" 사이가 24 이상이다.
5. 가로 화면 seed 뒤 UI 덤프에서 버튼 셋이 머리 바로 아래, 스크롤 없이 화면 안(bounds가 앱 칸 안)에 있다. 1:1 분할(`adb shell input swipe 2547 720 1500 720 800`)에서도 나의 DNA 칸(아래)의 버튼 셋이 머리 바로 아래에 한 줄에 하나씩인지 캡처하고 분할을 푼다(`input swipe 1706 720 2550 720 800`).

## 3. 낮·밤 전환 (SC-019)

| 시작 | 동작 | 기대 |
|---|---|---|
| 운전 기록(아래로 스크롤) | `adb shell cmd uimode night yes` | 같은 목록, 같은 스크롤 |
| 비교 켬 + 한 운전 고름 | 낮·밤 바꿈 | 비교 켜짐, 그 운전 골라짐(파랑 원) |
| 16유형(결과에서 열어 파랑 표시) | 낮·밤 바꿈 | 16유형, 같은 파랑 칸 |
| 두 운전 비교 | 낮·밤 바꿈 | 두 운전 비교 |
| 다시 보기(기록 목록을 스크롤해 연 것) | 낮·밤 바꿈 → 뒤로 | 기록 목록, 다시 보기를 열기 전 스크롤 자리 |
| 운전 기록 | 홈 → 다시 열기 | 결과 화면(지금과 같음) |

## 4. 결과 화면과 환산 (SC-020, SC-021)

1. `python3 scripts/demo.py reset` 뒤 `python3 scripts/demo.py dynamic`(시연 빌드 약 0.6km). P 결과를 위에서부터 캡처한다.
2. 기대: 그래프 → 유형 → 한 가지 제안 → "이번 운전 네 축" 차례. 역동성 줄이 "0.6km 동안 …"이고 "10km당"이 없다. 전력 줄이 "n kWh를 썼어요(0.6km)"다(배터리 신호가 있으면).
3. 나의 DNA 칸 막대 위 "최근 운전 n번 평균".

## 5. 회귀와 점검

- `python3 scripts/demo.py compare`와 `tour`가 끝까지 돈다(누르기 글자 그대로).
- 시나리오 결과 유형이 바뀌지 않는다: `gentle` GPSE, `dynamic` DRFC, `city` GPFE, `straight` G·SE(대응 방식 보류).
- `/impeccable critique`와 `audit`를 다시 돌려 점수를 적는다(SC-023).
- 캡처는 `/Users/jiwon/Desktop/현대자동차 공모전/화면 점검 1113/고친 뒤/DRIVE DNA/`에 둔다.
