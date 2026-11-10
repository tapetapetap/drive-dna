# Quickstart: DRIVE DNA 3차 검증

1·2차 quickstart의 준비(에뮬레이터 Cold boot, `adb forward tcp:8088 tcp:8088`, Virtual Console 페이지 닫기)를 그대로 한다.

```sh
./gradlew :app:testDebugUnitTest        # 54 + 3차 GraphTest
./gradlew :app:assembleDebug && adb install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
```

| 장면 | 기대 |
|---|---|
| `reset` → `gentle` | 결과 맨 위 그래프 카드. 사건 줄 "급한 조작 표시 없음". 네 글자가 차례로 나타남. 제안 카드 아래 자막 "이번 운전은 G P S E…" |
| `dynamic` | 그래프에 ▲·▼·◆. 사건 줄 수 = 역동성 줄의 급가속·급감속·빠른 조향 수(SC-012) |
| `compare`(새) | 비교 화면에 두 유형 카드(DRFC 파랑, GPSE 초록 또는 고른 순서대로), 겹친 그래프, "다른 축 4개 · 같은 축 0개"(SC-013) |
| `seed` | 운전 기록 24줄 모두 썸네일(그래프 시연 준비값) |
| `seed` → `highway` | 나의 DNA 칸 "방금 운전으로 태그 근거 n개가 바뀌었어요". 태그 화면에 크루즈·충전 "방금 운전으로 바뀜"과 "지난: 장거리 운전 3번 … 70%" "지난: 충전 3번 … 55%"(SC-014) |
| `tour`(1·2차) | 1·2차와 같은 유형·문장(SC-015). 기록 목록이 썸네일 줄 |
| 움직임 줄이기 | `adb shell settings put global animator_duration_scale 0` 뒤 `reset` → `gentle`: 글자·링·막대가 움직이지 않고 바로 보인다. 끝나면 `... 1`로 되돌린다 |
| 다시 보기 | 운전 기록에서 `gentle`을 열면 그래프 카드는 있고, 공개 효과와 자막은 없다 |
| 비교 끄기·한 개 | 비교를 켜고 하나를 고른 뒤 끄면 체크가 사라진다. `reset` → `gentle` 하나뿐이면 "비교" 칩이 흐리다 |

1·2차 형식 기록(SC-016)과 그래프 없는 운전 비교(US2-3)는 단위 테스트로 본다. 13·20·22필드가 섞인 문자열을 읽어 개수와 그래프 null 여부를 확인하고, 그래프가 어긋난 줄은 그래프만 null임을 확인한다. 화면의 아이콘 줄과 "그래프를 남기기 전 운전" 문구는 `seed` 대신 20필드 줄 두 개를 앞에 둔 기록을 넣어 한 번 캡처한다(`adb` push, 시연 스크립트 밖). 성능 목표(기록 100개)는 목표로만 두고 재지 않는다.
