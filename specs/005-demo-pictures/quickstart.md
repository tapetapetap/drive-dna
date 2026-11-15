# Quickstart: DRIVE DNA 5차 확인

작업 위치는 `/Users/jiwon/orca/workspaces/unused-feature-app/car-dna`(브랜치 `car-dna`)다. 명령은 모두 이 폴더에서 한다.

## 1. 단위 테스트, 빌드, 샘플 칸 (SC-024~026)

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testDebugUnitTest :app:assembleDebug
diff <(git show backup/before-pictures-dna:scripts/seed.txt | grep -v '^#' | cut -d, -f1-20) \
     <(grep -v '^#' scripts/seed.txt | cut -d, -f1-20) && echo "0~19번 칸 같음"
head -1 scripts/seed.txt | grep -c ,
```

- 기대: 테스트 68개(기존 67 + `seedGraphsDiffer`) 통과, "0~19번 칸 같음", 마지막 줄 0(머리 줄에 쉼표 없음).

## 2. 에뮬레이터 캡처 (SC-027·028) — 다른 앱과 차례로 쓴다

에뮬레이터 하나를 세 앱(안 써본 기능, 같은편, DRIVE DNA)이 함께 쓴다. 메인 세션이 정한 차례에만 쓰고, 끝나면 화면 나누기를 풀어 둔 채 다음 앱에 넘긴다(끄지 않는다). Pleos 2560×1440, density 160(1dp = 1px), 앱 사용자 `--user 10`.

```sh
ADB=$HOME/Library/Android/sdk/platform-tools/adb
OUT="/Users/jiwon/Desktop/현대자동차 공모전/화면 점검 1115/DRIVE DNA"; mkdir -p "$OUT"
shot() { $ADB exec-out screencap -p > "$OUT/$1.png"; }
tap() { python3 -B -c "import sys; sys.path[:0] = ['scripts']; from demo import tap; tap(sys.argv[1])" "$1"; }  # demo.py의 tap: UI 덤프에서 글자로 찾아 누른다
$ADB forward tcp:8088 tcp:8088          # 부팅마다
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
python3 scripts/demo.py seed            # 샘플 24개, 결과 화면은 10월 31일 오후 5:40 운전
```

1. `shot "01 결과 그래프"`: 그래프 카드 맨 아래가 "점선은 평균 속도 · 속도는 주행거리로 추정했어요" 한 줄이다. 그래프는 처음·끝이 0 근처, 신호 대기 2~4번, ▲ 2개가 오르는 곳에 있다.
2. `tap '운전 기록  ›'` → `shot "02 운전 기록"`: 썸네일이 줄마다 다르다(크루즈 운전은 평평한 고속 구간).
3. `tap '비교'` → `tap '10.0km'` → `tap '50.0km'` → 목록 끝까지 내리기(`$ADB shell input swipe 2000 1150 2000 250 200`를 여섯 번) → `tap '두 운전 비교'` → `shot "03 두 운전 비교"`: 두 샘플 그래프가 다른 모양으로 겹친다(점선 없음).
4. 뒤로 두 번(`$ADB shell input keyevent 4`: 비교 → 운전 기록 → 결과. 운전 기록은 목록 끝 스크롤로 돌아와 "✕  닫기"가 화면 밖이다) → 왼쪽 칸 끝까지 내리기(`$ADB shell input swipe 1167 1200 1167 300 700`) → `shot "04 나의 DNA 칸 - 가로"`: 막대 넷 아래에 뜻 단어가 한 줄씩, 왼쪽·오른쪽 끝에 붙어 있다.
5. 화면 나누기 `$ADB shell input swipe 2547 720 1500 720 800` → 아래 칸(나의 DNA)을 막대가 보일 때까지 내리기(`$ADB shell input swipe 1270 1250 1270 850 700` 되풀이) → `shot "05 나의 DNA 칸 - 1대1 세로"`: 같은 단어가 잘림 없이 한 줄씩이다. 위 칸 그래프 카드의 점선 설명 줄도 한 줄인지 본다. 나누기 중에는 UI 덤프가 포커스 창만 잡으므로 `tap` 대신 좌표로 한다(앱 칸 x 853~1690, y 90~1300).
6. 나누기를 푼다: `$ADB shell input swipe 1706 720 2550 720 800`. 앱이 P에서도 흐리게 가려진 채 남으면 `$ADB shell am force-stop --user 10 ai.umos.homescreen` → `$ADB shell input keyevent KEYCODE_HOME` → `$ADB shell am start --user 10 -n kr.skku.drivedna/.MainActivity`.

## 3. 회귀

- `python3 scripts/demo.py compare`(약 4분, 기록을 지우므로 §2 뒤에): 끝까지 돌고 비교 화면에 "다른 축 4개"가 나온다(누르기 글자 그대로).
- 시나리오 유형(`gentle` GPSE, `dynamic` DRFC, `city` GPFE, `straight` G·SE)과 샘플 태그 다섯은 §1의 단위 테스트(`ScenarioTest`)로 본다.

## 4. 화면 점검 (SC-029)

- `/impeccable audit`: 대상은 `Screens.kt`의 `graphCard`·`profile`, 근거는 §2 캡처 01·04·05다. 점수와 P1을 verification.md에 적는다. 고칠 것은 `/impeccable polish`로, 승인된 Figma 프레임과 어긋나지 않는 범위에서만 고친다.
