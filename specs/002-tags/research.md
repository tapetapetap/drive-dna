# Research: DRIVE DNA 보조 태그

1차 연구 R1~R7은 `specs/001-drive-dna/research.md`에 있다. 여기서는 R8부터 잇는다.

## R8. 태그 신호를 에뮬레이터에서 잼 (2026-11-10)

- **방법**: 디버그 전용 `ProbeReceiver`가 getter로 한 번씩 읽었다. Virtual Console에 값을 쓰고 2초 뒤에 읽었다.
- 등록(register) 방식은 broadcast 수신기 안에서는 오래 살지 못해 쓰지 않았다. 앱에서는 서비스 안에서 등록한다.

| 신호 | SDK | 콘솔 쓰기 | 받은 값 |
|---|---|---|---|
| 크루즈 상태 | `DrivingMode.getCruiseControlState` / `registerCruiseControlState` | `/vehicle/driving-mode?ad_mode_state=` | `not_ready` → null, `ready`·`longitudinal`·`lateral` → `ENABLED`, `full_autonomous` → `ACTIVATED` |
| 크루즈 목표 속도 | `getCruiseControlTargetSpeed` | `target_speed_mps` | 13.89(m/s) |
| 와이퍼 | `Wiper.getCurrentWiperState(WindowArea(FRONT_WINDSHIELD))` / `registerWiperState` | `/vehicle/body?front_wiper=Low\|Off` | `ON` / `OFF` |
| 배터리 SOC | `EvBattery.getEvBatteryStateOfCharge` / `registerEvBatteryStateOfCharge` | `/vehicle/ev-battery?field=soc&value=` | 그 값(정수 %) |
| 조수석 착좌 | `Seat.getCurrentSeatOccupancy(SeatArea(ROW_1_RIGHT))` / `registerSeatOccupancy` | `/vehicle/seat?kind=occupied&area=passenger&value=` | **반대 값**(콘솔 true → false) |

- **Decision**
  - 크루즈 작동은 `ACTIVATED`일 때만이다. `ENABLED`(대기)는 작동이 아니다.
  - 와이퍼 켜짐은 `ON`이다.
  - 착좌는 SDK 값을 그대로 믿는다. 시연 스크립트가 콘솔에 반대로 쓴다(같은편 research R5와 같은 결정).
- **권한**: 크루즈는 `CAR_DRIVE`(있음), SOC는 `CAR_ENERGY`(있음), 착좌는 `CAR_SEATS`(새로), 와이퍼는 `CAR_WINDOWS`(새로)다. 디버그 측정에 이 권한으로 값이 왔다.
- 1차 README의 "크루즈 상태는 에뮬레이터에서 확인되지 않아 쓰지 않습니다"는 이 측정으로 바뀐다.
  - 1차 research R1의 "driving-mode를 바꿔도 앱에 변화가 없었다"는 `ready`·`longitudinal`·`lateral`이 모두 `ENABLED`로 와서 값이 바뀌지 않았기 때문으로 보인다. 작동(`ACTIVATED`)은 `full_autonomous`일 때만이다.
- **등록(register) 경로**: 이 측정은 getter로만 했다. 앱은 1차와 같이 서비스 안에서 `register*`로 받는다. 1차의 기어·주행거리·브레이크·조향·방향지시등·배터리 Wh는 이 방식으로 왔다.
  - **측정 결과**(2026-11-10 01:44, T008): 서비스 안 register로 모두 변화가 왔다. 운전 하나에서 콘솔로 바꾼 값이 운전 끝 지표에 그대로 남았다.
    - 크루즈 `full_autonomous` 10초 → `cruiseMs`=10009
    - 와이퍼 Low 5초 → `wiperMs`=5007
    - SOC 70→65 → `socStart`=70, `socEnd`=65
    - 조수석 콘솔 false → `passenger`=true(반대로 옴)
    - 위치: geo fix → 로그 "위치 있음"
    - 그래서 D7의 2초 getter 대안은 쓰지 않는다. 운전 시작 때 getter 한 번만 둔다.
  - 운전 시작 때 값(SOC·착좌)은 운전이 시작될 때 getter로도 한 번 읽어 넣는다. getter 값이 구독 값보다 늦게 오면 시작 값을 덮는다(등록 직후 첫 콜백이 엇갈리는 일이 있고 getter가 안정적이었다, 코드 리뷰 반영).
  - register가 변화를 주지 않는 신호가 있으면, 그 신호만 운전 중 2초마다 getter로 읽는다(위임 결정 D7).
- **API 모양**(javap로 확인):
  - `DrivingMode.registerCruiseControlState(CruiseControlStateListener{ onCruiseControlStateUpdated(CruiseControl?) })`
  - `EvBattery.registerEvBatteryStateOfCharge(EvBatteryStateOfChargeListener{ onEvBatteryStateOfChargeUpdated(Int?) })`. 값은 정수 %이고 그대로 `soc(pct)`에 넣는다.
  - `Wiper.registerWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), WiperStateListener{ onWiperStateUpdated(WiperState?) })`
  - `Seat.registerSeatOccupancy(SeatArea(SeatValue.ROW_1_RIGHT), SeatOccupancyListener{ onSeatOccupancyUpdated(Boolean?) })`
  - 모두 `VehicleListener.onFailed(Exception)`을 함께 구현한다.

## R9. 위치

- **같은편**: 같은 공모전에 낸 세 번째 앱이다. 픽업 지점 앱이고, 저장소는 `/Users/jiwon/orca/workspaces/unused-feature-app/pickup`이다. 그 앱의 `PickupService.watchLocation()`이 FusedLocation 1.1.3을 쓴다.
- **Decision**: 같은 Pleos FusedLocation 1.1.3을 쓴다.
  - API: `FusedLocation(context)`, `isSupported(): Boolean`, `initialize()`(동기), `registerFusedLocationCallback(FusedLocationListener{ onChanged(LocationData?) ; onError(Exception) })`, `release()`. `LocationData`에는 `latitude`·`longitude`(Double)가 있다.
  - **권한**: `pleos.car.permission.FUSED_LOCATION`은 **런타임 권한**이다(2026-11-10 `dumpsys package`의 runtime permissions에 있음). MainActivity가 `POST_NOTIFICATIONS`와 함께 요청한다. 시연 설치는 1차처럼 `-g`로 미리 허용된다.
  - **첫 실행**: 권한 창이 떠 있는 동안 서비스가 먼저 뜬다. 서비스는 권한이 없으면 10초마다 다시 확인하고, 허용되면 그때 위치를 켠다. `isSupported()`가 false면 다시 시도하지 않고 위치 없이 동작한다(코드 리뷰 반영).
  - **와이퍼 값**: SDK `WiperState`는 `OTHER`·`OFF`·`ON`·`SERVICE`다(Android VHAL `WindshieldWipersState`와 같은 모양). 작동 중은 `ON`만 본다. `OTHER`가 실차에서 간헐·자동 모드로 오는지는 확인하지 못했다(코드 리뷰 #5, 실차에서 잴 것).
  - **초기화 위치**: `CarWatcher.start()`는 Vehicle 초기화가 실패하면 10초마다 다시 불린다. 위치는 그 재시도와 따로, `CarWatcher` 생성 뒤 한 번만 `watchLocation()`으로 시작한다. 실패하면 같은 10초 간격으로 자기만 다시 시도한다(같은편 `watchLocation`과 같음).
  - 서비스가 `registerFusedLocationCallback`으로 받는다. 위도·경도가 오면 `Tracker`에 마지막 값만 넘긴다.
  - 운전이 끝날 때 그 값을 끝난 곳으로 남긴다.
  - 권한은 `pleos.car.permission.FUSED_LOCATION`이다.
- **에뮬레이터**: `adb emu geo fix <경도> <위도>`로 바뀐다. 같은편 research R2·R3에서 1초마다 따라옴을 확인했다. 이 앱에서도 geo fix 뒤 운전을 끝내자 끝난 곳이 남았다(2026-11-10 T008, 로그 "위치 있음").
- **경로는 남기지 않는다.** `Tracker`는 마지막 한 점만 들고, 운전 중 위치 목록을 만들지 않는다.

## R10. 시연 기록 넣기(seed)

- **Decision**: `scripts/demo.py seed`가 SharedPreferences `records.xml`을 직접 쓴다.
  - 1차 `scripts/demo.py`의 `reset()`이 `run-as`로 파일을 지우는 것과 같은 방식이다. 그 뒤 앱을 다시 연다.
  - 24줄은 `scripts/seed.txt`에 고정한다(기록 20칸 형식 그대로). `DnaTest`가 같은 파일을 읽어 `Tags.all(…, Limits.DEMO)`이 다섯 판정을 내는지 지킨다.
  - 샘플 24개는 아래를 담는다. 태그 다섯이 모두 판정된다.
    - 장거리 크루즈: 70·65·80%
    - 충전 직전 배터리: 55·48·60%
    - 두 장소 위주(새 장소 2/24)
    - 동승 3번은 역동성이 낮음, 비 3번은 평소와 비슷함
- 사용자 문서도 "PT 시연에서는 샘플 이력으로 판정 화면을 보여준다"고 했다.

## R11. 잠정 수치

| 값 | 릴리스 | 시연(디버그) | 근거 |
|---|---|---|---|
| 장거리 운전 | 30분·평균 시속 60km | 1분·시속 20km | 문서 임시 기준 |
| 크루즈 판정 | 중앙값 50%↑ A, 15%↓ M, 3번 | 같음 | 문서 |
| 충전 찾기 | 다음 시작 SOC − 앞 끝 SOC ≥ 10%p | 같음 | 잠정 |
| 충전 판정 | 중앙값 40%↑ B, 25%↓ L, 3번 | 같음 | 문서(내연기관 값을 잠정으로) |
| 장소 묶음 | 150m | 같음 | 잠정 |
| 이동 범위 | 새 장소 10%↓ H, 30%↑ X, 20번 | 5번 | 문서 |
| 비 운전 | 와이퍼 3분 | 30초 | 문서(지속 시간 조건) |
| 상황 적응 | 차이 25%↑ 맞춤형, 10%↓ 한결형, 양쪽 3번 | 양쪽 2번 | 잠정(문서 미정) |
