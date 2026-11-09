# Goldfish Review — DRIVE DNA 보조 태그 — 크루즈·충전·이동 범위·상황 적응

- 날짜: 2026-11-10
- 리뷰어 상태: comprehension 완료 · readiness GO

## Comprehension의 다시 쓰기

DRIVE DNA는 운전이 끝나면 4축 16유형으로 운전 성향을 보여 주는 차량 앱이다. 여기에 보조 태그 5개를 더한다. 태그는 한 번의 운전이 아니라 쌓인 기록(최대 100개)으로 정하며, 크루즈를 맡기는지(위임형/직접 운전형), 충전 습관(여유형/한계형), 이동 범위(루틴형/탐험형), 동승자나 비가 있을 때 운전이 달라지는지(맞춤형/한결형)를 본다.
이를 위해 운전마다 다섯 가지를 더 저장한다. 크루즈 작동 시간, 시작·끝 SOC, 끝난 곳 한 점, 조수석에 사람이 앉은 채 시작했는지, 와이퍼 시간이다. 근거가 모자라거나 기준 사이에 있으면 "보류"로 둔다.
판정된 태그는 나의 DNA 칸에 칩으로 보인다. P에서만 여는 새 태그 화면에는 태그마다 판정, 근거 문장, 보류 이유가 나온다. 결과 화면과 음성은 그대로다.
목적은 사용자 문서가 요구한 5~8번 축을 차 신호로 채우는 것이다. 이동 범위를 세려면 위치가 필요해서 1차의 "위치 수집 없음" 원칙을 좁힌다. 끝난 곳 한 점만 기기 안에 두고, 화면에는 비율만 보인다.

**기존 시스템**

(대부분 1차 문서를 따라가야 알 수 있었다.)
- 상시 서비스가 Pleos 신호 6개(기어, 누적 주행거리, 브레이크, 조향각, 방향지시등, 배터리 Wh)를 받는다. P → 다른 기어 → P를 운전 하나로 보고, 끝날 때 Metrics를 만든다.
- 3분 또는 1km 미만(시연 빌드는 30초·0.2km)은 짧은 운전이다. 저장만 하고 분석하지 않는다. 운전 도중에 앱이 켜지면 그 운전은 세지 않는다.
- 기록은 SharedPreferences `records`/`drives`에 한 줄 13칸으로 최신 100개까지 기기 안에만 둔다. 위치와 경로는 모으지 않는다(1차 FR-016). 크루즈 상태는 에뮬레이터에서 확인되지 않아 쓰지 않았다.
- P에 놓으면 상황 칸에 이번 운전의 4축 판정, 16유형 코드, 근거, 코칭이 나오고 음성이 한 번 나온다.
- 나의 DNA 칸에는 최근 분석 운전 10번의 유형, 축 막대, "운전 기록 ›"·"16유형 ›" 칩이 있다. 기어가 P가 아니면 칩은 흐리고 눌리지 않는다.
- 시연은 `scripts/demo.py`가 Virtual Console에 신호를 써서 장면을 돌린다.
- plan에 따르면 1차는 완료·푸시됐다. 신호 측정용 디버그 `ProbeReceiver`가 커밋하지 않은 채 추가돼 있다.

## 지적과 처리

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 1 | comprehension | Major | spec › SC-007 | 검증 입력·기대가 저장소 밖 문서에만 있고 기호가 정의되지 않음 | 수정 | 설계 문서 §8 "문서의 판정 예시 표를 그대로 테스트로"가 리뷰 전 결정. data-model §4-1에 태그 이름으로 옮기고 SC-007이 그 표를 가리킴 |
| 2 | comprehension | Major | spec Edge ↔ data-model §4 | 충전 찾기에 짧은 운전을 쓰는지 두 가지로 읽힘 | 수정 | data-model의 "충전만 예외"가 리뷰 전 결정. spec Edge Cases·FR-020에 예외와 이유를 적음 |
| 3 | comprehension | Major | spec US1 AS5·Edge·FR-019 | 크루즈 숨김 조건이 세 문장으로 다르고 null의 뜻이 없음 | 수정(사용자 결정) | 위임 결정 D1. "크루즈 값 있음" = 운전 중 null이 아닌 값을 받음. 숨김은 분석된 운전 전체에 값이 없을 때만(spec AS5·Edge·FR-019, data-model §1) |
| 4 | comprehension | Major | spec › SC-010 | "기록 내보내기"가 없는 기능이고 로그에 좌표가 찍힘 | 수정(사용자 결정) | 위임 결정 D2. 운전 끝 로그에서 좌표를 빼고 "위치 있음/없음"만, SC-010은 화면·로그·시연 출력으로(spec FR-030·SC-010, data-model §6, quickstart, T005·T019) |
| 5 | comprehension | Major | plan·research R8·R9·data-model §4 | "같은편"이 정의되지 않았고 위치 수신을 이 앱에서 재지 않음 | 수정 | research R9에 같은편(경로)과 FusedLocation API를 적고, 이 앱에서의 수신은 T008에서 재서 R9에 적는다고 고침. data-model의 "1차·같은편" 근사를 같은편 `Pick.kt` 기준으로 고침 |
| 6 | comprehension | Major | data-model §5 | 한결형 근거 문장이 두 가지로 읽힘 | 수정(사용자 결정) | 위임 결정 D4. 네 경우의 완성 문장과 부호 규칙(data-model §5, contracts/ui.md 표) |
| 7 | comprehension | Nit | spec › SC-008 | "숫자로 시작하는"이 예문과 맞지 않음 | 수정 | 예문("장거리 운전 3번의…")과 어긋나는 사실 오류. "횟수·비율이 든 관측 사실"로 고침 |
| 8 | comprehension | Nit | spec › US1 | "정차 중"은 신호 대기까지 포함해 읽힘 | 수정 | FR-025(기어 P)와 어긋남. "P에 놓은 뒤"로 고침 |
| 9 | comprehension | Nit | spec FR-026·contracts | "칸"이 화면 영역과 태그 하나를 함께 가리킴 | 수정 | 태그 하나는 "카드", 상황 칸은 1차 오른쪽 화면이라고 밝힘(spec FR-026~028, contracts/ui.md) |
| 10 | comprehension | Nit | plan › Constitution IV | "새 파일 하나"가 실제와 다름 | 수정 | 디버그 측정 두 파일(남김)과 시연 데이터 두 파일을 적고 Project Structure에 더함 |
| 11 | comprehension | Nit | spec › FR-028 | "(집 충전 포함)"의 이유 없음 | 기록 | — |
| 12 | readiness | Major | data-model §4 표 | 대상이 모자랄 때 fact를 낼지, 두 집단이 모두 모자랄 때 순서 | 수정(사용자 결정) | 위임 결정 D3. 최소 수보다 적으면 fact null·보류 이유만, 상황 집단 문장 먼저(data-model §4, T006·T017) |
| 13 | readiness | Major | data-model §5·SC-008 | 한결형 문장, 부호, SC-008 문구 | 수정(사용자 결정) | 위임 결정 D4(#6과 같음)와 SC-008 문구 수정(#7) |
| 14 | readiness | Major | data-model §4 장소 묶음 | 거리 함수가 1차에 없고 cos 위도·여러 장소·기준점 규칙이 없음 | 수정(사용자 결정) | 위임 결정 D5. `Dna.kt`에 `meters` 새로 둠, 기준점은 첫 점 고정, cos는 기준점 위도, 여럿이면 가장 가까운 장소(data-model §4, T015) |
| 15 | readiness | Major | contracts/ui.md·plan | green_soft·줄바꿈·점선 테두리·칩 색·아이콘·문구 조건이 정해지지 않음 | 수정(사용자 결정) | 위임 결정 D6. green_soft 두 테마 값, `Ui.grid(2)`, `setStroke` 점선 2dp·6dp·4dp, 판정·보류 칩 색, `ic_dna`, `Profile.drives ≥ 1`. plan 파일 목록에 colors·Ui.kt 더함 |
| 16 | readiness | Major | research R8·T008 | register 경로를 재지 않았고 대안이 없음 | 수정(사용자 결정) | 위임 결정 D7. 운전 시작 때 getter로 한 번 더 읽음, T008에서 register를 재서 R8에 적고, 변화가 없는 신호만 운전 중 2초 getter. API 모양을 R8에 적음 |
| 17 | readiness | Major | research R9·T008 | FusedLocation API·권한·초기화 위치가 없음 | 수정 | 같은편 코드에서 API 확인, `dumpsys package`로 `FUSED_LOCATION`이 런타임 권한임을 확인(MainActivity 요청 추가), `watchLocation()`을 Vehicle 재시도 밖에서 한 번(R9, T003·T008) |
| 18 | readiness | Major | research R10·T011 | 샘플 24줄이 정해지지 않았고 자동 검사가 없음 | 수정 | 설계 §6 "샘플 기록 24개, 태그 다섯 판정"이 리뷰 전 결정. `scripts/seed.txt` 고정 파일, 제약 목록, DnaTest 검사를 T011에 적음 |
| 19 | readiness | Major | spec › SC-010·plan | 로그로 좌표가 새는 경로를 다루지 않음 | 수정(사용자 결정) | 위임 결정 D2(#4와 같음) |
| 20 | readiness | Nit | research › R10 | 1차 research R4 참조가 틀림 | 수정 | 1차 R4는 "시계". `scripts/demo.py`의 `reset()`으로 고침 |
| 21 | readiness | Nit | spec › FR-025 | 칩 띄어쓰기가 계약과 다름 | 수정 | 기존 칩("운전 기록  ›")과 같이 두 칸으로 |
| 22 | readiness | Nit | tasks › T021 | README 문장이 크루즈와 전비를 묶어 전비까지 지워질 수 있음 | 수정 | README 75행 확인. 크루즈만 빼고 전비는 남긴다고 적음 |

## 집계

지적 22개 중 문서를 바꾼 것 21개
