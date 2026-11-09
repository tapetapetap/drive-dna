# Quickstart: DRIVE DNA 검증

## 준비

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb forward tcp:8088 tcp:8088
python3 scripts/demo.py reset   # 첫 앱을 멈추고, 콘솔 신호를 0으로, 앱 기록을 지우고 앱을 연다
```

- 에뮬레이터 하나를 첫 앱과 같이 쓴다. `reset`이 첫 앱을 멈춰서(`am force-stop`) 시연 중에 첫 앱이 음성으로 끼어들지 않는다.
- 브라우저의 Virtual Console 페이지는 닫는다. 페이지가 열려 있으면 스크립트가 연결할 때 페이지 상태가 다시 들어간다(첫 앱 README).
- 음성 확인: `adb logcat -d | grep "doSpeak(kr.skku.drivedna"`
- 운전 끝 지표: `adb logcat -d -s WatchService`의 "운전 끝: Metrics(...)"
- 화면 글자: `adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*"'`
- 디버그 빌드는 보류·짧은 운전 기준이 낮다(30초, 0.2~0.3km, 회전 2번). 시계는 실제 시간이다.

## 시나리오 (`python3 scripts/demo.py <이름>`)

구간표는 `scripts/scenarios/<이름>.txt`다. DnaTest도 같은 표를 재생해 기대 결과를 지킨다. 스크립트는 주행거리를 0.2초마다 경과 시간으로 계산해 쓴다(research R3).

| 이름 | 조작 | 목표 지표(DnaTest 재생값) | 기대 결과 |
|---|---|---|---|
| `reset` | 첫 앱 정지, 콘솔 브레이크·조향 0, 방향지시등 `Off`, 배터리 60.0kWh, 앱 기록 삭제 후 앱 열기 | — | 첫 실행 화면, 나의 DNA "운전이 끝나면 여기에 쌓여요" |
| `gentle` | 약 2분 15초. 20초에 걸쳐 시속 50km → 유지 → 방향지시등을 켜고 40km로 감속, 초당 30°로 120°까지 돌렸다 풀기 2번 → 브레이크로 15초에 걸쳐 정지. 배터리 0.1kWh 감소 | 1.47km, 급가속·급감속·빠른 조향 0, 큰 회전 2번 모두 미리 켬, 속도 변동 0.29, 100km당 6.8kWh | **GPSE 차분한 계획가**. 코칭 기본("눈에 띄는 급한 조작 없이…"), 음성 한 번 |
| `dynamic` | 약 1분 5초. 6초에 시속 80km, 4초에 10km로 급정거를 되풀이하고, 방향지시등 없이 1초에 150°로 꺾기 2번. 배터리 0.2kWh 감소 | 0.65km, 급가속 5·급감속 5·빠른 조향 4, 큰 회전 2번 중 미리 켬 0, 속도 변동 0.52, 100km당 31kWh | **DRFC 자유로운 드라이버**. 코칭은 급감속(급가속과 같은 비율이면 표 순서). 나의 DNA는 운전 2번 기준, 차이 줄 "역동성이 평소보다 역동적 쪽이었어요." |
| `city` | 약 2분 10초. 시속 40km까지 부드럽게 서고 가기를 되풀이, 방향지시등을 켜고 초당 40°로 큰 회전 3번 | 0.68km, 급한 조작 0, 큰 회전 3번 모두 미리 켬, 속도 변동 0.45, 100km당 14.7kWh | **GPFE 유연한 길잡이**. 코칭은 속도 변동 |
| `straight` | 약 2분. 회전 없이 시속 50km로 곧게 달리고 정지 | 회전 0번, 속도 변동 0.3 이하 | **G·SE**. 대응 방식 "·"와 "큰 회전이 적어 판정을 미뤘어요(회전 0번)", 별칭 대신 "1개 축은 판정 보류", 보류 음성("1개 축은 판정을 미뤘어요"), 16유형에서 두 칸 강조 |
| `short` | 15초 운전 | 0.06km | "짧은 운전은 분석하지 않아요", "30초, 0.2km 넘게 달리면…", 음성 없음, 기록 목록에 없음 |

## 화면 확인

1. **설치 직후**: 첫 실행 화면과 비어 있는 나의 DNA
2. **시나리오 실행 중(D)**: Pleos가 앱을 가릴 수 있다. 덤프로 보면 "운전 중이에요"가 있고, 칩은 `enabled="false"`다.
3. **결과 화면**: `docs/screenshots/`에 캡처한다(첫 실행, 결과, 보류, 나의 DNA 변화, 기록, 다시 보기, 16유형, 다크, 세로).
4. **기록**: "운전 기록 ›" → 줄 → 그 운전 다시 보기 → "←  운전 기록"
5. **16유형**: 나의 유형은 초록 테두리, 열기 직전에 보던 운전의 유형은 파랑 테두리다.
6. **다크·세로**: `adb shell cmd uimode night yes|no`, 앱 창은 `am task resize <taskId> 853 90 1653 1300` → `853 90 2535 1300`. taskId는 `adb shell am stack list`에서 찾는다.
7. **재시작**: `adb shell am force-stop --user 10 kr.skku.drivedna` → `am start` 뒤에도 기록과 나의 DNA가 그대로다.

## 통과 기준

- `./gradlew :app:testDebugUnitTest` 실패 0
- `gentle` → GPSE, `dynamic` → DRFC, `city` → GPFE, `straight` → G·SE, `short` → 짧은 운전
- 운전 중 `doSpeak` 0건, `logcat -b crash`에 이 앱 0건
