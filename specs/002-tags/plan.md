# Implementation Plan: DRIVE DNA 보조 태그

**Branch**: `002-tags` (git 브랜치 `drive-dna`) | **Date**: 2026-11-10 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/002-tags/spec.md`, 설계 `docs/superpowers/specs/2026-11-10-drive-dna-tags-design.md`

## Summary

1차 구조를 잇는다.
- **신호**: `CarWatcher`가 신호 다섯 개(크루즈, 배터리 SOC, 와이퍼, 조수석 착좌, 위치)를 더 받아 `Tracker`에 넘긴다.
- **기록**: `Tracker`가 운전마다 새 값 일곱 칸을 `Metrics`에 더한다. 기록 형식은 13칸에서 20칸이 되고, 옛 13칸 줄도 읽는다.
- **판정**: 순수 `Tags` 객체가 기록 목록에서 태그 다섯 개(크루즈, 충전, 이동 범위, 동승, 빗길)를 판정한다. JUnit으로 지킨다.
- **화면**: 나의 DNA 칸에 판정된 태그 칩과 "태그 ›" 칩을 더하고, 태그 화면을 새로 둔다.
- **시연**: `scripts/demo.py`에 `seed`(샘플 기록 24개)와 `highway` 장면을 더한다.

## 현재 상태 (구현 전)

- 1차는 끝나 커밋·푸시돼 있다(main 300475f).
- 이번 측정을 위해 디버그 전용 `app/src/debug/AndroidManifest.xml`, `app/src/debug/java/kr/skku/drivedna/ProbeReceiver.kt`를 더해 두었다(research R8). 커밋은 하지 않았다.

## Technical Context

**Language/Version**: Kotlin 2.0.21(JDK 17), Python 3(시연, 표준 라이브러리)
**Primary Dependencies**: Pleos Vehicle 2.3.2(새로 `DrivingMode`·`EvBattery` SOC·`Wiper`·`Seat`), **새로** Pleos FusedLocation 1.1.3(같은편과 같은 버전), TTS 3.3.1
**Storage**: SharedPreferences `records` 키 `drives`(1차) — 한 줄 20칸
**Testing**: JUnit(`DnaTest.kt`에 묶음 추가), 에뮬레이터 장면
**Target Platform**: Pleos Connect 에뮬레이터(API 34)
**Project Type**: 차량 앱(단일)
**Constraints**: 주행 중 화면·조작 없음(1차 원칙 I), 기록은 기기 안에만
**Scale/Scope**: 기록 최대 100개, 태그 5개

## Constitution Check

| 원칙 | 판정 |
|---|---|
| I. 운전 중에는 보여 주지 않는다 | 통과. 태그 화면은 P에서만 연다. 음성은 1차 그대로 한 번이다 |
| II. 관측 사실이 먼저, 해석은 잠정으로 | 통과. 태그 칸마다 근거 문장(횟수·비율)을 먼저 둔다. 모자라면 보류하고, 화면 아래에 잠정·우열 없음을 적는다 |
| III. 차 신호만 쓴다 | 통과. 새 신호는 모두 SDK에서 받고 에뮬레이터로 쟀다(research R8). 받지 못한 값은 "없음"이다 |
| IV. 최소 구현 | 통과. 새 소스 파일은 디버그 측정용 둘(`app/src/debug/AndroidManifest.xml`, `ProbeReceiver.kt`, 디버그 빌드에만 들어가고 측정 도구로 남긴다)과 시연 데이터 둘(`scripts/scenarios/highway.txt`, `scripts/seed.txt`)이다. 판정은 `Dna.kt`의 `Tags`, 화면은 `Screens.kt`·`Ui.kt`에 더한다 |
| V. 판단 로직은 테스트로 | 통과. 태그 판정·충전 찾기·장소 묶기·상황 비교·기록 형식은 JUnit을 먼저 쓴다 |
| VI. 기록은 차 안에만 | **바꿈**(아래 Complexity Tracking). 위치는 운전이 끝난 곳 한 점만 기기 안 기록에 둔다. 서버·외부 전송은 1차처럼 없다 |

헌법은 1.1.0으로 올린다.
- VI을 "운전 기록은 기기 안에만 저장한다. 서버·계정·외부 전송·경로 수집을 두지 않는다. 위치는 운전이 끝난 곳 한 점만 기기 안에 두고, 화면에는 비율만 보인다"로 바꾼다.
- Constraints의 권한 목록에 `CAR_SEATS`, `CAR_WINDOWS`, `FUSED_LOCATION`을 더한다.

## Project Structure

### Documentation (this feature)

```text
specs/002-tags/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/ui.md     # 나의 DNA 칸·태그 화면 문구
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/java/kr/skku/drivedna/
├── Dna.kt          # Metrics 7칸, Tracker 입력 다섯, Limits 태그 기준, Tags·TagId·Tag, RecordFormat 20칸
├── CarWatcher.kt   # 크루즈·SOC·와이퍼·조수석 구독, FusedLocation 위치
├── Screens.kt      # profile 칩 줄·"태그  ›", tags 화면
├── Ui.kt           # round()에 점선 인자, 칩 격자
├── WatchService.kt # 운전 끝 로그에서 좌표 빼기
└── MainActivity.kt # 태그 화면 열고 닫기(16유형과 같은 방식), FUSED_LOCATION 런타임 요청
app/src/main/res/values/colors.xml, values-night/colors.xml  # green_soft
app/src/debug/AndroidManifest.xml, app/src/debug/java/kr/skku/drivedna/ProbeReceiver.kt  # 측정 도구(디버그 전용, 남김)
app/src/main/AndroidManifest.xml   # CAR_SEATS, CAR_WINDOWS, FUSED_LOCATION
gradle/libs.versions.toml, app/build.gradle.kts  # pleos-location
app/src/test/java/kr/skku/drivedna/DnaTest.kt     # 새 묶음
scripts/demo.py, scripts/scenarios/highway.txt, scripts/seed.txt   # seed(24줄 고정 파일), highway, 시나리오 지시어
.specify/memory/constitution.md                  # 1.1.0
README.md, 사용설명서.md                          # 태그 소개, 위치 문구 고침
```

## Complexity Tracking

| 위반 | 왜 필요한가 | 더 단순한 대안을 버린 이유 |
|---|---|---|
| 원칙 VI의 "위치 수집 없음"을 좁힘 | 사용자 문서가 이동 범위(H/X) 태그를 요구한다. 끝난 곳 없이는 셀 수 없다 | 이동 범위를 빼는 안이 있다. 하지만 사용자가 문서의 기능을 끝까지 구현하라고 지시했고, 문서가 이미 대응(기기 안에만, 비율만 표시)을 정했다. 끝난 곳 한 점만 남겨 경로는 여전히 모으지 않는다. 되돌릴 때는 `Tags`의 이동 범위와 위치 구독만 지우면 된다 |
| 새 의존성 FusedLocation | 위치를 받을 SDK다. 같은 공모전의 같은편이 같은 버전을 쓴다 | 안드로이드 `LocationManager`는 Pleos 권한 체계 밖이라 차에서 막힐 수 있다 |
