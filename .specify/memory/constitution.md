# DRIVE DNA Constitution

## Core Principles

### I. 운전 중에는 보여 주지 않는다
결과, 유형 카드, 조작을 이끄는 화면은 기어가 P일 때만 보인다. 음성은 운전이 끝난 순간 한 번, 짧게만 말한다. 속도 경쟁이나 과격한 조작을 부르는 미션·점수·순위를 만들지 않는다.

### II. 관측 사실이 먼저, 해석은 잠정으로
화면과 음성은 먼저 관측한 사실(횟수, 비율, 거리)을 말하고, 성향은 그 뒤에 잠정 해석으로 보인다. 근거가 모자란 축은 판정을 보류하고 그렇게 표시한다. 성향에 우열을 두지 않으며, 성향을 안전 수준과 동일시하지 않는다. 주변 상황을 모르므로 원인을 단정하지 않는다.

### III. 차 신호만 쓴다
지표는 Pleos Vehicle SDK가 실제로 주는 신호로만 만든다. 에뮬레이터나 차가 주지 않는 신호는 추측하지 않고, 그 축은 보류한다. 추정값(예: 주행거리로 계산한 속도)은 추정이라고 밝힌다.

### IV. 최소 구현 (ponytail)
YAGNI → 표준 라이브러리 → 플랫폼 기능 → 이미 있는 의존성 → 한 줄 → 최소 구현 순서로 고른다. 쓰이지 않는 추상화, 설정, 확장 지점을 만들지 않는다.

### V. 판단 로직은 테스트로 지킨다
지표 계산, 축 판정, 유형, 누적, 코칭 문장 선택은 안드로이드 없이 돌아가는 순수 코드로 두고, 각각 실패하면 깨지는 JUnit 테스트를 하나 이상 둔다.

### VI. 기록은 차 안에만
운전 기록은 기기 안에만 저장한다. 서버, 계정, 외부 전송, 경로 수집을 두지 않는다. 위치는 운전이 끝난 곳 한 점만 기기 안 기록에 두고, 화면에는 비율만 보이며 로그에도 남기지 않는다(1.1.0, 보조 태그의 이동 범위).

## Constraints

- 플랫폼: Pleos Connect (AAOS, API 34 에뮬레이터), Kotlin, JDK 17, Android View
- Vehicle SDK 권한은 실제로 쓰는 조회 권한만 선언한다(CAR_DRIVE, CAR_LIGHTS, CAR_ENERGY, CAR_SEATS, CAR_WINDOWS, FUSED_LOCATION, TTS_SERVICE).
- 기준 수치는 검증 전 잠정값이며, 문서와 화면에 그렇게 적는다.

## Development Workflow

brainstorming(설계 문서) → /speckit-specify → /speckit-plan → /speckit-tasks → goldfish 리뷰 → 실사용 변화 제시(승인 게이트) → /speckit-implement(ponytail full) → ponytail-review → /code-review.

## Governance

이 문서는 plan의 Constitution Check 기준이다. 원칙을 어기는 설계는 plan의 Complexity Tracking에 이유를 적는다.

**Version**: 1.1.0 | **Ratified**: 2026-11-09 | **Last Amended**: 2026-11-10
