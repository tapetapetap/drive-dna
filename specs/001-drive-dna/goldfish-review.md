# Goldfish Review — DRIVE DNA — 운전이 끝나면 이번 운전의 성향을 보여 준다

- 날짜: 2026-11-09
- 리뷰어 상태: comprehension 완료 · readiness NO-GO(재리뷰도 NO-GO)

## Comprehension의 다시 쓰기

현대차 공모전용으로 Pleos Connect(차량용 안드로이드)에서 도는 "DRIVE DNA" 앱을 만든다. 포그라운드 서비스가 운전 한 번(P → 다른 기어 → P) 동안 기어, 누적 주행거리, 브레이크, 조향각, 방향지시등, 배터리 잔량을 받는다. 차속 API가 없어서 속도와 가속도는 주행거리로 추정하고, 급가속·급감속·빠른 조향 횟수, 큰 회전 전에 방향지시등을 미리 켠 비율, 속도 변동, 100km당 전력 같은 지표를 만든다.
P에 놓으면 이 지표로 네 축(역동성 G/D, 대응 방식 P/R, 주행 리듬 S/F, 운전 목적 E/C)을 잠정 기준으로 판정한다. 3초 안에 네 글자 유형과 별칭, 축마다 "관측 사실 → 판정 글자", 코칭 한 줄을 보여 주고 음성으로 한 번 말한다. 근거가 모자란 축은 "·"로 보류하고, 3분 또는 1km 미만 운전은 분석하지 않는다.
최근 분석된 운전 10번의 축 평균을 "나의 DNA"로 쌓고, 이번 운전과 가장 크게 다른 축을 알린다. 기록은 100개까지 기기 안에만 두고, 기록 목록, 다시 보기, 16유형 표를 제공한다.
왜 만드는가: 차의 숫자를 운전자가 알아듣는 말로 바꾸기 위해서다. 한 번의 운전은 흔들리므로 누적해서 성향을 보이고, 주의 분산을 막으려고 주행 중에는 말하지도 보여 주지도 않는다. 에뮬레이터에서는 주행거리를 매초 늘리는 스크립트로 시연하고 검증한다.

**기존 시스템**

- 같은 Pleos Connect 에뮬레이터(v2.1.0, API 34)에 첫 앱 "안 써본 기능"(`kr.skku.unusedfeature`)이 있다. 문서에 따르면 이 앱은 가로 화면에서 왼쪽 지도 칸·오른쪽 상황 화면으로 배치하고, Ui 도우미·Tile·색 토큰·칩·카드·hero 부품, 10배 빠른 시계, Virtual Console 하트비트를 쓰며, 음성으로 말한다. 코드가 어디 있는지는 적혀 있지 않다.
- 이 앱은 신호 시험판(받은 값을 로그로만 남기는 앱)으로 쓸 신호 6개를 확인한 단계다.
  - 기어, 브레이크, 방향지시등은 바로 들어온다.
  - 주행거리는 약 1초 주기로 들어온다.
  - 조향각은 콘솔과 부호가 반대이고 약 3초마다 들어온다.
  - 배터리 잔량은 Wh 단위다.
  - 차속 API는 없다. 전비는 에뮬레이터에서 지원되지 않고, 크루즈 상태는 바뀌지 않는다.
- Virtual Console은 HTTP로 브레이크, 조향, 방향지시등, 배터리를 바꿀 수 있다. 하지만 속도를 줘도 주행거리는 늘지 않는다. 쓰기 전에 하트비트가 필요하고, 브라우저 콘솔 페이지가 열려 있으면 그 페이지의 상태가 다시 들어간다.
- DO가 아닌 앱은 기어가 P가 아니면 Pleos가 가린다.
- 이 저장소에 지금 무엇이 있고 무엇을 바꾸는지는 문서에 없다(지적 7 참고).

## 지적과 처리

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 1 | comprehension | Major | spec US2·FR-014 | 주행 중 화면이 "운전 중이에요"만인지, 나의 DNA 칸도 보이는지 두 가지로 읽힌다 | 수정 | 리뷰 전 결정: plan 핵심 설계 7 "나의 DNA 칸은 늘 그린다", contracts §7 "주행 중에는 흐리게 하고 비활성". spec US2·AC1·FR-014, contracts §6·§7을 맞추고 plan Constitution Check I에 근거를 적음 |
| 2 | comprehension | Major | plan Summary·Source Code | 첫 앱이 어디 있는지, 가져올 부품이 무엇인지 없다 | 수정 | 첫 앱 경로는 리뷰 전 tasks.md에 있었음. plan "첫 앱에서 가져오는 것" 표, contracts 머리의 색 토큰 이름·글자 크기 표, research R5 이유를 풀어 씀 |
| 3 | comprehension | Major | research R3 | 주행거리 쓰기·기어 바꾸기·하트비트 방법이 없다 | 수정 | 첫 앱 `scripts/demo.sh`와 시험판에서 확인한 사실. R3에 기준 주소, 하트비트 주기, 읽기·쓰기 표 |
| 4 | comprehension | Major | data-model §1·§2·§3 | spec의 신호 없음 규칙이 데이터 모델에 없고, PUR 보류 이유가 조건과 맞지 않는다 | 수정 | spec FR-002·Edge Cases(리뷰 전 결정). data-model §1 받음 판단, §2 없음 표, §3 보류 조건, contracts §2·§3, T006 |
| 5 | comprehension | Major | research R1 | 신호가 바뀔 때 오는지 주기로 오는지, 주행거리 자릿수, 끊김 처리가 없다 | 수정 | 에뮬레이터에서 쓰기 시각과 전달 시각을 비교해 확인한 사실을 R1에 적음(조향은 바뀌면 0.13~0.3초, 주행거리는 약 1.02초마다 그때 값, 소수 5자리). 끊김 처리는 §2 추정 방식에서 따라 나오는 것을 한 줄로 적음 |
| 6 | comprehension | Major | spec US4 AC2 | 다시 보기의 "그때 값"과 차이 줄의 비교 대상이 두 가지로 읽힌다 | 수정 | research R5·data-model §7(읽을 때 다시 계산), §5(바로 전까지의 Profile). US4 AC2, Key Entities, contracts §6 다시 보기, data-model §5 |
| 7 | comprehension | Major | plan Project Structure | 저장소의 현재 상태와 이름 변경 범위가 없다 | 수정 | 리뷰 전 사실(`settings.gradle.kts`, `app/build.gradle.kts`, 시험판 MainActivity·매니페스트). plan "현재 상태" |
| 8 | comprehension | Nit | spec Assumptions | "비DO 앱" 풀이가 없다 | 기록 | |
| 9 | comprehension | Nit | spec SC-001 | 어느 빌드 기준인지 없다 | 기록 | quickstart는 디버그 빌드로 검증한다 |
| 10 | comprehension | Nit | data-model §6 | 코칭 후보 비율이 같을 때 규칙이 없다 | 기록 | 15와 같은 문제. D2⑦로 정함 |
| 11 | comprehension | Nit | contracts §2·§3 | 보류 줄 구성과 막대 유무가 갈린다 | 기록 | 21과 같은 문제. D7로 정함 |
| 12 | comprehension | Nit | contracts §6 16유형 | "이번 운전 유형"이 무엇인지 없다 | 기록 | 25와 같은 문제. D10으로 정함 |
| 13 | readiness | Blocker | data-model §1·§2·§3 | 신호를 받지 못했을 때의 "없음"과 보류가 없어 근거 없는 판정(예: 방향지시등 없는 차의 R)이 나온다 | 수정 | 4와 같은 수정. spec FR-002·Edge Cases(리뷰 전 결정) |
| 14 | readiness | Major | plan 핵심 설계 1 | Tracker의 운전 끝 인터페이스와 `endedAt`을 누가 채우는지 정해지지 않았다 | 수정(사용자 결정) | 게이트 D1(승인 위임에 따른 추천안). plan 1·2·5·6, data-model §2, T007·T008·T010. 재지적 |
| 15 | readiness | Major | data-model §2·§5·§6 | movingMs, 표준편차, 빠른 조향 창, 회전 시작, 동점 규칙이 비었다 | 수정(사용자 결정) | D2. data-model §1·§2·§5·§6, contracts §2, T006. 재지적 |
| 16 | readiness | Major | research R1·R2 | 1초 쓰기와 약 1초 전달이 엇갈리면 가짜 급가속·급감속이 생긴다 | 수정(사용자 결정) | D3(0.2초마다 경과 시간으로 쓰기, 측정 49.8~50.1km/h). research R3, plan 8, T006·T018. 재리뷰에서 Blocker로 재지적(수정 전이었음) |
| 17 | readiness | Major | quickstart 시나리오·T018 | 초 단위 표가 없어 기대 유형이 기준 근처에서 갈린다 | 수정(사용자 결정) | D4(구간표 파일을 스크립트와 DnaTest가 함께 읽음, 목표 수치). quickstart, T006·T018. 재지적 |
| 18 | readiness | Major | research R3 | 방향지시등 값, 조향 범위, 배터리 단위가 없다 | 수정 | `GET /vehicle/catalog`로 확인한 사실. R3 쓰기 표 |
| 19 | readiness | Major | tasks T001·T013 | CAR_INFO를 넣을지, 알림 권한 요청이 빠졌다 | 수정(사용자 결정) | D5(CAR_INFO 유지, 런타임 요청). T001·T013. 재지적 |
| 20 | readiness | Major | data-model §2·§3·§7 | 기록 타입(`analyzed`), 짧은 운전 저장, 100개 자르기, Key Entities가 정해지지 않았다 | 수정(사용자 결정) | D6. data-model §2·§3·§7, T008(Key Entities는 6에서 수정). 재지적 |
| 21 | readiness | Major | contracts §2·§6, T004 | 숫자 형식, 짧은 운전 길이와 문구, 보류 줄, 가져올 함수가 없다 | 수정(사용자 결정) | D7. contracts 머리·§2·§5·§6, T004·T012. 재지적 |
| 22 | readiness | Major | contracts §3 | 보류 조건은 여럿인데 축마다 이유가 하나다 | 수정 | data-model §3의 조건마다 contracts §3 문장을 짝지음(PUR 거리 조건은 DYN과 같은 문장) |
| 23 | readiness | Major | contracts §4 | 유형 카드의 한 줄 설명이 없다 | 수정(사용자 결정) | D8. spec FR-006, contracts §4·§6, T012. 재지적 |
| 24 | readiness | Major | tasks T005·T015 | 나의 DNA 칸의 인터페이스와 세로 배치가 없다 | 수정(사용자 결정) | D9. contracts §7, T005·T015. 재지적 |
| 25 | readiness | Major | data-model §8 | 페이지끼리 열고 닫는 규칙, 이번 운전 유형, 같은 칸 테두리가 없다 | 수정(사용자 결정) | D10. data-model §8, contracts §6, T016·T017. 재지적 |
| 26 | readiness | Major | tasks 체크포인트 | 스크립트가 맨 끝에 생겨 체크포인트를 실행할 수 없다 | 수정(사용자 결정) | D11. T018을 Phase 2로, T010 로그, Phase 2·7 체크포인트. 재지적 |
| 27 | readiness | Major | tasks T009·T010 | 화면 갱신 조건이 없다 | 수정(사용자 결정) | D12. plan 6, T009·T010. 재지적 |
| 28 | readiness | Nit | spec FR-014·US2-1 | "운전 중이에요만"이 contracts와 다르게 읽힌다 | 기록 | 1의 수정에 포함됨 |
| 29 | readiness | Nit | plan 핵심 설계 5 | 순수 형식과 저장 클래스 이름이 같다 | 기록 | D1에서 `RecordFormat`으로 정함 |
| 30 | readiness | Nit | tasks Phase 7 | Checkpoint 줄이 없다 | 기록 | D11에서 더함 |
| 31 | readiness | Major | contracts §6·T014 | 세지 않는 운전의 주행 중 문구와 `recording` 공개가 없다 | 수정(사용자 결정) | D13. spec Edge Cases, contracts §6, data-model §8, T010·T014 |
| 32 | readiness | Major | contracts 머리·T009·T010 | 차 연결 실패 띠의 문구·조건·콜백이 없다 | 수정 | T009 "첫 앱 CarWatcher.kt의 초기화·재시도 방식을 따른다"와 plan 표의 banner: 첫 앱 `CarWatcher.kt` 48·113행 `NO_SIGNAL`. contracts §6 연결 문제 띠, T009·T010 |
| 33 | readiness | Major | quickstart·tasks 검증 | 보류 축이 생기는 시나리오가 없어 SC-003을 에뮬레이터에서 확인할 수 없다 | 수정(사용자 결정) | D14(`straight` → G·SE). quickstart, T006·T017·T018 |
| 34 | readiness | Nit | tasks T018 | 방향지시등 값은 0이 아니라 `Off`다 | 수정 | 사실 오류: research R3 표 `Off` |
| 35 | readiness | Nit | spec US1 AC2 | 예시 "10km당 급감속 1.2번"이 contracts §2 형식과 다르다 | 기록 | |
| 36 | readiness | Nit | contracts §6 이번 운전 | 다시 시작한 뒤에도 "방금 끝난 운전"으로 나온다 | 기록 | |

## 재리뷰

- 대상: readiness(Blocker 13을 수정했으므로 1회)
- VERDICT: NO-GO → NO-GO
- 재리뷰 Blocker는 16과 같은 문제(쓰기·전달 주기)라 행을 더하지 않고 16에 적음. 16은 리뷰 때 이미 사용자 판단이었고 게이트에서 D3로 정함
- 새 지적: 31~36
- `재지적` 행: 14, 15, 17, 19, 20, 21, 23, 24, 25, 26, 27

## 집계

지적 36개 중 문서를 바꾼 것 26개(수정 12, 수정(사용자 결정) 14). 사용자 결정 14개는 사용자의 승인 위임("끝까지 내 승인 없이")에 따라 구현자가 추천안으로 정했다(`approval-gate.md` D1~D14).
