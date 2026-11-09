# 검증 기록: DRIVE DNA 보조 태그

- 날짜: 2026-11-10 (사용자 위임으로 밤사이 진행)
- 환경: Pleos Connect 에뮬레이터(API 34), Virtual Console, 디버그 APK(시연 기준 `Limits.DEMO`)

## 단위 테스트

`./gradlew :app:testDebugUnitTest` → 53개 통과, 실패 0.
- SC-007: data-model §4-1 판정 예시 표를 `TagTest`로 그대로 넣었고, 표의 기대 판정이 나온다.
- 태그 신호(`TagSignalTest`): 크루즈·와이퍼 시간, 시작·끝 SOC의 늦은 채움, 조수석 착좌, 끝난 곳 한 점
- 시나리오(`ScenarioTest`): `highway`가 크루즈와 충전 근거를 바꾸고, `seed`가 다섯 태그를 모두 판정한다.

## 에뮬레이터

| 확인 | 결과 |
|---|---|
| 신호 측정(T008·T012, register 경로) | 크루즈 full_autonomous 10초 → `cruiseMs=10009`, 와이퍼 5초 → `wiperMs=5007`, SOC 70 → 65, 콘솔 조수석 false → `passenger=true`(1차 같은편 research R5와 같은 반전), 운전 끝 로그 끝에 "위치 있음" |
| SC-008 `seed` | 나의 DNA 칸에 칩 다섯(크루즈 위임형, 여유 충전형, 루틴형, 동승 맞춤형, 빗길 한결형). 태그 화면 카드 다섯 장이 모두 판정됐다. 근거: "장거리 운전 3번의 크루즈 비율 중앙값 70%", "충전 3번의 충전 직전 배터리 중앙값 55%(집 충전 포함)", "새 장소 8% · 자주 가는 2곳이 100%", 동승 "…평소보다 39% 낮아요", 빗길 "…평소와 비슷해요(차이 5%)" |
| SC-009 `highway` | 크루즈 근거가 "장거리 운전 4번의 크루즈 비율 중앙값 69%"로, 충전 근거가 "충전 4번의 충전 직전 배터리 중앙값 53%"로 바뀌었다. 운전 끝 로그: `cruiseMs=90198, socStart=85, socEnd=78, passenger=false, wiperMs=0, endLat=null, endLon=null) 위치 있음` |
| SC-011 `tour`(reset → 1차 장면 다섯) | gentle GPSE, dynamic DRFC, city GPFE, straight G·SE(1개 축 판정 보류), short "짧은 운전은 분석하지 않아요". 운전 기록 목록도 같다. 1차와 같다 |
| SC-010 좌표 없음 | `tour` 출력에 위도·경도 숫자 0건. 운전 끝 로그 다섯 줄 모두 `endLat=null, endLon=null`과 "위치 있음/없음"만. 나의 DNA 칸·태그 화면 덤프에는 비율만 있다("새 장소 8% · 자주 가는 2곳이 100%") |
| `logcat -b crash` | 이 앱 0건 |

관찰: `tour` 뒤 나의 DNA 칸에 "직접 운전형" 칩이 보였다. 에뮬레이터 크루즈 값이 null이 아닌 0으로 와서(D1 "값 있음") 장거리 운전 4번의 크루즈 비율 중앙값 0%로 판정된 것이다. 규칙대로다.

화면: `docs/screenshots/17-tags-profile.png`(칩 줄), `docs/screenshots/18-tags-screen.png`(태그 화면)

## 리뷰

- ponytail-review(과설계): 디버그 ProbeReceiver에서 쓰지 않는 크루즈 목표속도 읽기 한 줄을 지웠다. 나머지는 결정 D1~D7이 요구하는 만큼이었다.
- /code-review(high) 10건 중 9건을 고쳤다. 테스트 54개(새로 `startGetterWinsAndAPlaceFromBeforeTheDriveIsDropped`).
  - 위치 권한 창이 떠 있는 동안 서비스가 먼저 떠서, 허용한 뒤에도 위치를 켜지 않던 것: 권한이 없으면 10초마다 다시 확인(같은편에도 같은 수정)
  - 운전 중 위치를 못 받으면 앞 운전의 끝 점이 들어가 루틴형 쪽으로 기울던 것: 운전 시작 때 위치를 비움
  - Vehicle 재시도마다 위치 재시도가 겹쳐 늘던 것, 지원하지 않는 환경에서 끝없이 재시도하던 것
  - 운전 시작 때 getter 값이 구독 값 뒤에 와서 버려지던 것: getter 값이 시작 값을 덮음
  - 태그가 나의 DNA와 다른 운전 묶음을 쓰던 것: 저장된 analyzed 사용
  - 역동성 지수 식 중복, 상황 태그의 세 번 거르기, 디버그 수신기 시간 상한
  - 고치지 않음: 와이퍼 `OTHER`를 비로 볼지(research R9 끝, 실차에서 잴 것)
- 보안 점검: `/security-review`는 작업 디렉터리가 git 저장소가 아니라 돌지 않아 같은 관점으로 직접 봤다. 위치 한 점은 앱 전용 저장소(백업 꺼짐)에만 있고, 로그·화면·시연 출력에 좌표가 없다. 고칠 취약점은 없었다.
- 리뷰 반영 뒤 다시 돌림(02:13): `reset` → `seed` → `highway`. 운전 끝 로그 `cruiseMs=90078, socStart=85, socEnd=78, passenger=false, wiperMs=0, endLat=null, endLon=null) 위치 있음`(이번 운전 중 받은 위치만 남음)

## 확인하지 못한 것

- 실차의 크루즈·와이퍼·조수석 착좌·위치 값. 특히 조수석 착좌는 에뮬레이터에서 반대로 온다. 실차에서는 반대가 아닐 수 있다
- 몇 주 동안 실제로 쌓인 기록에서 태그가 얼마나 자주 보류되는지. 시연은 샘플 기록 24개다
- 위치 권한(`FUSED_LOCATION`)을 거부한 흐름. 위치가 없는 운전은 이동 범위에서 세지 않으므로 "운전이 n번 쌓였어요(20번 필요)" 보류로 남는다(`rangeClusteringAndMinimum`의 최소 수 검사). 권한 거부 화면은 에뮬레이터에서 돌리지 않았다
