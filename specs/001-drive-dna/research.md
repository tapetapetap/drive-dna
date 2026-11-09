# Research: DRIVE DNA

## R1. 받을 수 있는 신호 (신호 시험판, 2026-11-09)

- **Decision**: 신호는 여섯 개를 쓴다.
  - 기어, 누적 주행거리(`DistanceDriven`), 브레이크 페달(Boolean), 조향각(Float), 방향지시등(OFF/LEFT/RIGHT), 배터리 잔량(Wh)
  - 권한은 CAR_DRIVE, CAR_LIGHTS, CAR_ENERGY다.
- **Rationale**: 시험판(받은 값을 로그로만 남기는 앱)을 에뮬레이터에 설치하고, Virtual Console HTTP로 값을 바꿔 받은 값을 확인했다.
  - 기어, 브레이크, 방향지시등은 바뀌면 바로 들어온다. 브레이크는 콘솔 값이 0보다 크면 밟음(true)이다. 방향지시등은 켜진 동안 깜빡임 없이 LEFT·RIGHT 그대로이고, 끄면 OFF다.
  - 조향각은 바뀌면 0.13~0.3초 뒤에 들어온다(쓰기마다 하나씩). 그대로면 약 3초마다 같은 값이 다시 온다. 콘솔과 부호가 반대다.
  - 주행거리는 쓰기마다 오지 않는다. 값이 바뀌는 동안 약 1초(1.02초)마다 그 순간의 값이 들어오고, 그대로면 3~4초마다 다시 온다. 소수 5자리까지 그대로 온다(0.76287).
  - 배터리 잔량과 용량은 Wh다(77400).
  - 측정 방법(2026-11-09): 콘솔에 쓴 시각과 시험판 로그에 찍힌 시각을 비교했다.
  - SDK 구현 클래스(`BrakeImpl` 등)의 권한 검사를 `javap -v`로 보니 모두 공개 조회 권한이었다.
- **Alternatives considered**:
  - 전비(`registerCurrentEvEfficiency`): 에뮬레이터에서 `ERROR_NOT_SUPPORTED_PROPERTY`였다.
  - 크루즈 상태: 콘솔 `driving-mode`를 바꿔도 앱에 변화가 없었다.
  - 차속: SDK에 API가 없다.
  - Vehicle Data API·ADAS: 공모전 제공이 확인되지 않았다(기획 보고서).

## R2. 차속과 가속도

- **Decision**: 주행거리 표본으로 속도를 추정한다(2초 이상 앞선 표본과 비교). 가속도도 2초 이상 앞선 속도와 비교해 추정한다.
- **Rationale**: 차속 API가 없다. 에뮬레이터에서 주행거리는 약 1초 주기로 갱신된다.
- **주의**: 실차의 갱신 주기와 해상도는 확인하지 못했다. 거칠면 급가속·급감속을 놓치거나 과장할 수 있어서, 화면에 "추정"이라고 적는다.
- **Alternatives considered**: FusedLocation의 속도는 SDK를 하나 더 들이고 위치 수집(원칙 VI)이 따른다.

## R3. 에뮬레이터에서 운전을 흉내 내는 방법

- **Decision**: 시연 스크립트가 Virtual Console HTTP로 주행거리를 0.2초마다, 경과 시간으로 계산한 값으로 쓴다. 같은 방식으로 기어, 브레이크, 조향, 방향지시등, 배터리 잔량을 바꾼다.
  - 1초마다 쓰면 약 1.02초 주기 전달과 어긋나, 2초 창에 쓰기가 1번 또는 3번 들어가는 순간이 생긴다. 그러면 일정한 속도에서도 추정 속도가 튀어 가짜 급가속·급감속이 나온다. 0.2초마다 쓰면 시속 50km가 49.8~50.1km/h로 추정된다(2026-11-09 측정).
  - 기준 주소는 `http://127.0.0.1:8088`이다. 에뮬레이터를 켤 때마다 `adb forward tcp:8088 tcp:8088`을 한다.
  - 하트비트: `POST /browser/session?client=demo`를 1초마다 보낸다. 3초 넘게 끊기면 쓰기가 거부된다(`emulator_inactive`).
  - 읽기: `GET /vehicle/state`의 `odometer.traveled_distance_km`(km)와 `ev_battery.current_energy_kwh`(kWh)
  - 쓰기(`PUT`). 값 형식은 `GET /vehicle/catalog`로 확인했다.

    | 신호 | 주소 | 값 |
    |---|---|---|
    | 기어 | `/vehicle/gear?gear=` | `Park`·`Reverse`·`Neutral`·`Drive` |
    | 주행거리 | `/vehicle/odometer?traveled_distance_km=` | km. 줄어드는 값은 무시된다 |
    | 브레이크 | `/vehicle/controls?brake=` | 0~100(%) |
    | 조향 | `/vehicle/controls?steering=` | −470~500(°) |
    | 속도(콘솔 화면 표시용) | `/vehicle/controls?speed=` | 정수만 받는다(소수는 `invalid_speed`). 주행거리는 늘지 않는다 |
    | 방향지시등 | `/vehicle/turn-signal?mode=` | `Off`·`BlinkLeft`·`BlinkRight`·`FlashLeft`·`FlashRight` |
    | 배터리 잔량 | `/vehicle/ev-battery?field=battery_level&value=` | kWh, 0.1 단위. SDK에는 Wh로 온다(60.0 → 60000) |
- **Rationale**: 콘솔은 `controls?speed=60`으로 15초를 달려도 주행거리가 0.0 그대로였다(콘솔 페이지의 ACCEL 페달도 같다). 쓰기 전에는 첫 앱처럼 하트비트(`POST /browser/session`)가 필요하다.
- **Alternatives considered**: 콘솔 페이지에서 손으로 하는 방법은 주행거리를 매초 바꿀 수 없어 속도 지표가 생기지 않는다.

## R4. 시계

- **Decision**: 실제 시간을 쓴다. 시연용(디버그) 빌드는 보류·짧은 운전 기준만 낮춘다(30초, 0.2~0.3km, 회전 2번).
- **Rationale**: 속도를 주행거리 ÷ 시간으로 계산하므로 시계를 빠르게 하면 속도가 틀어진다. 첫 앱의 10배 시계는 시간 조건만 보는 기능에 맞는 방법이었다.

## R5. 저장 형식

- **Decision**: SharedPreferences 문자열 하나에 운전을 한 줄씩 저장한다. 필드는 숫자뿐이고 쉼표로 나눈다. 축·유형·문장은 저장하지 않고 다시 계산한다.
- **Rationale**: 텍스트 필드가 없어 이스케이프가 필요 없다. 기준을 고치면 지난 기록도 같은 기준으로 다시 보인다. 순수 Kotlin이라 JUnit으로 왕복을 지킬 수 있다. org.json은 로컬 JVM 테스트에서 빈 껍데기(stub)라 테스트하려면 의존성을 더해야 하고, Room/SQLite는 기록 100개에 비해 과하다. 그래서 둘 다 쓰지 않는다(첫 앱 `specs/003-recommendation-history/research.md` R1과 같은 판단).

## R6. 화면과 부품

- **Decision**: 첫 앱의 화면 부품(`Ui` 도우미, `Tile`, 색 토큰, 칩·카드·hero)을 가져와 쓴다. 축 막대는 작은 Canvas View 하나로 그린다.
- **Rationale**: 같은 공모전, 같은 Pleos 디자인 가이드이고 이미 검증된 부품이다.

## R7. 기준 수치

- **Decision**: 보고서에 수치가 없어 data-model §3의 값을 잠정 기준으로 둔다.
  - 급가속 2.5 m/s², 급감속 −3.0 m/s²: 운전 습관 연구·보험 텔레매틱스에서 흔히 쓰는 범위다(이번에 따로 조사하지 않았다)
  - 10km당 3회(브레이크 제외), 미리 켠 신호 60%, 속도 변동 0.35, 100km당 16kWh: 시연 시나리오가 양쪽으로 갈리도록 정한 값이다
- **Rationale**: 검증 전이라 화면에 "잠정 기준"이라고 밝힌다(원칙 II).
