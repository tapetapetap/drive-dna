# Quickstart: DRIVE DNA 보조 태그 검증

준비(에뮬레이터 Cold boot, 설치, `adb forward tcp:8088 tcp:8088`, 콘솔 페이지 닫기)는 1차 README와 같다.

## 장면

| 명령 | 하는 일 | 기대 결과 |
|---|---|---|
| `python3 scripts/demo.py seed` | 앱 기록을 샘플 24개로 바꾸고 앱을 연다 | 나의 DNA 칸에 태그 칩 다섯("크루즈 위임형", "여유 충전형", "루틴형", "동승 맞춤형", "빗길 한결형"). "태그 ›"를 누르면 카드 다섯과 근거 문장 |
| `python3 scripts/demo.py highway` | `seed` 뒤에 돈다. 시작 전 SOC를 85%로 올리고(앞 운전 끝 50% → 충전 1번), 크루즈를 켜고(`full_autonomous`) 약 2분 시속 80km, 끝에서 `geo fix`로 집 근처에 선다 | P 뒤 결과 화면(1차와 같음). 태그 화면의 크루즈 근거가 "장거리 운전 4번…", 충전 근거가 "충전 4번…"으로 바뀐다 |
| 1차 장면 `gentle`, `dynamic`, `city`, `straight`, `short` | 1차와 같다 | 결과 유형이 1차와 같다(SC-011) |

## 시나리오 파일에 더하는 지시어 (`scripts/scenarios/*.txt`)

- `soc 85 78`: 운전 시작 전 SOC 85, 끝나기 직전 78
- `place 37.30 126.97`: 끝나기 직전 위치
- `seat 1`: 시작 전 조수석에 앉음(콘솔에는 반대로 쓴다, research R8)
- 구간 줄의 여섯째 칸(선택): `C`면 크루즈 작동, `W`면 와이퍼 켜짐, `CW`면 둘 다. 없으면 둘 다 꺼짐
- 1차 시나리오는 그대로 읽힌다(새 지시어가 없음)

## 단위 테스트

`./gradlew :app:testDebugUnitTest` — 1차 33개 + 새 묶음(`TagTest`, 기록 형식, Tracker 새 입력, 시나리오 `highway`)

## 통과 기준

- SC-007~SC-011
- SC-010: 화면 덤프, `adb logcat -s WatchService`의 "운전 끝" 줄, `demo.py tour` 출력에 좌표·주소가 없다("위치 있음/없음"만)
- `logcat -b crash`에 이 앱 0건
