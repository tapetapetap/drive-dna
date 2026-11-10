# Goldfish Review — DRIVE DNA 3차 — 운전 그래프, 두 운전 비교, 눈에 띄는 변화

- 날짜: 2026-11-11
- 리뷰어 상태: comprehension 완료 · readiness NO-GO(재리뷰. 첫 판정도 NO-GO)

## Comprehension의 다시 쓰기

### 다시 쓰기
DRIVE DNA 3차는 1·2차의 판정(네 축, 유형, 보조 태그)과 그 문장은 그대로 두고, 운전이 어땠는지 보여 주는 표현만 더한다.
운전 중 추정 속도와 급가속·급감속·빠른 조향이 일어난 시각을 모아 최대 40칸짜리 속도 그래프로 줄인다. 이 그래프는 기록 한 줄에 두 칸을 더해(22칸) 저장하고, 결과 화면 맨 위, 운전 기록 썸네일, 다시 보기에 그린다.
운전 기록에서 두 운전을 고르면 비교 화면이 열린다. 유형 카드 두 장, 겹친 그래프, 축마다 두 점과 관측 문장을 어느 쪽이 낫다는 말 없이 나란히 보인다.
새 결과가 뜨면 네 글자를 차례로 보여 주는 효과와 음성 자막을 붙인다. 가장 최근 운전 때문에 바뀐 보조 태그 근거에는 칩과 지난 문장을 붙이고, 시연용 샘플 기록 24개에도 그래프를 넣는다.
네 글자와 숫자만으로는 운전의 모습, 두 운전의 차이, 새 결과와 바뀐 근거가 잘 보이지 않아서 만든다. 시연 장면을 보강하려는 목적도 있다.

### 기존 시스템
- Pleos Connect용 차량 앱이다(Kotlin, 단일 모듈). 서비스가 운전 중 차 신호를 지켜보다가 기어가 P가 되면 그 운전의 결과를 만든다. 화면은 서비스 상태를 정적 값으로 읽는다.
- 기준 길이를 넘은 "분석된 운전"은 네 축으로 판정한다. 결과 화면에는 네 글자 유형과 별명(예: GPSE 차분한 계획가, DRFC 자유로운 드라이버), 축마다 관측 문장이 나온다. 판정하지 못한 축에는 보류 이유가 나온다. 결과 문장은 음성으로 한 번 말한다. 짧은 운전은 말하지 않는다. 시연 빌드는 기준이 30초로 낮다.
- 속도는 주행거리의 2초 창으로 추정한다. 급가속·급감속·빠른 조향 수를 세어 결과 화면의 역동성 줄에 보인다. 주행거리 값이 거친 차는 속도를 추정하지 못한다.
- 운전 하나가 기록 한 줄이다(1차 13칸, 2차 20칸, 쉼표 구분). SharedPreferences `records`에 최근 100개까지 둔다.
- 2차 보조 태그(크루즈, 충전 등)는 쌓인 기록에서 판정과 근거 문장(또는 보류 이유)을 낸다. 계산은 순수 함수이고, 결과는 태그 화면과 나의 DNA 칸에 보인다.
- 운전 기록 목록은 줄마다 DNA 아이콘, 코드·별명, 시각·거리·시간을 보인다. 줄을 누르면 그 결과를 다시 본다. 이 목록에 짧은 운전도 나오는지는 문서에 없다.
- 결과·목록 조작은 P에서만 된다(헌법 I). 그래프, 비교, 공개 효과, 자막, 바뀜 표시는 아직 없다.
- 검증 수단은 단위 테스트 54개와 Python 시연 장면(`reset`, `gentle`, `dynamic`, `seed`(샘플 24개), `highway`, `tour`)이다.

## 지적과 처리

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 1 | comprehension | Major | spec.md Input, US1~US4 Why | 같은편·첫 앱 정의가 없어 우선순위 근거를 따라갈 수 없음 | 수정 | 리뷰 전 사용자 요청(2026-11-11 같은편 요청 + "나머지 두 앱도 비슷하게"). 용어 블록을 더하고 Why를 DRIVE DNA 문제로 다시 씀 |
| 2 | comprehension | Major | spec.md FR-038, US3 AC1 | 공개 효과 기준이 문서마다 다름 | 수정(사용자 결정) | D1: 기록 `endedAt` 기준 10초, 한 결과에 한 번 |
| 3 | comprehension | Major | spec.md FR-031·FR-036, Edge Cases | 분석된 운전과 짧은 운전의 경계, 짧은 운전 그래프 | 수정(사용자 결정) | D7: 용어 "분석된 운전", FR-031 짧은 운전도 그래프를 모으고 화면은 분석된 운전만 |
| 4 | comprehension | Major | spec.md FR-041, US3 AC4 | 주행 중 바뀜 줄을 보이는지 | 수정(사용자 결정) | D7: 1·2차 칩처럼 흐리게 보이고 눌리지 않음 |
| 5 | comprehension | Nit | spec.md FR-031~033, Edge Cases | "칸"이 세 뜻 | 수정 | 용어 "기록 필드"·"그래프 구간"으로 나눔(FR-032 남은 "40칸"도 고침) |
| 6 | comprehension | Nit | spec.md US1 AC3, FR-035·038 | 같은 그림의 이름이 셋 | 수정 | "DNA 타일(유형 카드 왼쪽 아이콘)"로 맞춤 |
| 7 | comprehension | Nit | spec.md FR-039, US3 AC2 | 자막 위치·6초 기준이 다른 문서와 다름 | 수정 | data-model §5와 같게 "한 가지 제안 카드 아래", "말하기 시작한 때부터 6초" |
| 8 | comprehension | Nit | spec.md US4 AC1 | 빠른 조향이 빠짐 | 수정 | FR-042와 같게 더함 |
| 9 | comprehension | Nit | spec.md FR-031, Edge Cases | 구간 수 규칙이 R11에만 있음 | 수정 | FR-031에 `n = min(40, 운전 초 올림)` |
| 10 | readiness | Blocker | tasks T001·T003·T019, data-model §2, SC-015 | 기록 형식을 바꾸면 기존 테스트 하나가 반드시 깨짐 | 수정 | DnaTest.kt:443 확인. T003에 테스트 갱신, SC-015·T001은 53 + 1. 재리뷰에서 다시 지적 없음 |
| 11 | readiness | Major | data-model §1, T002 | logLine "그래프 n칸" 자리와 null 표기 | 수정 | Dna.kt:50, DnaTest.kt:453-454 확인. 형식을 data-model §1에 고정 |
| 12 | readiness | Major | spec FR-038, data-model §5, T016 | fresh 기준이 둘이고 코드에 "새 결과 받음" 사건이 없음 | 수정(사용자 결정) | D1. 재지적(재리뷰 #7: animatedAt이 Activity 범위) → D15 companion |
| 13 | readiness | Major | T011, contracts §2·§3 | 비교 상태 순서·닫기·비우기·범례 | 수정(사용자 결정) | D2 |
| 14 | readiness | Major | contracts §3, data-model §3 | "보류 k개"의 k | 수정(사용자 결정) | D3 `Compare.counts` 세 값, 합 4 |
| 15 | readiness | Major | T010 | AxisBar 두 번째 값이 없음과 보류를 못 가름 | 수정(사용자 결정) | D4 `compare` 인자. 재지적(재리뷰 #5: 높이·둘 다 보류) → D13 |
| 16 | readiness | Major | research R14, spec FR-040 | 태그 바뀜 경계 세 가지 | 수정(사용자 결정) | D5 `take(index)`. 수정 후 재지적(재리뷰 Blocker #1: 바뀜 종류·before 규칙이 문서마다 다름) → 사용자 판단 → 위임 결정 D11로 spec FR-040·US3-3, R14, data-model §4, T012를 한 규칙으로 |
| 17 | readiness | Major | contracts §1 | 옛 형식 기록의 그래프 없음 문구, 사건 줄 빼기 기준 | 수정(사용자 결정) | D6 |
| 18 | readiness | Major | contracts §1, research R15 | 스피커 아이콘 자원이 없고 lastSpoken 자리가 TTS 준비에 묶임 | 수정 | drawable 폴더·WatchService.kt:133-137 확인. `ic_speaker.xml`(새), R15를 onDriveEnd speak 직전으로 |
| 19 | readiness | Major | research R13, T005 | 그래프 색·표시 위치·시각 글·폭 | 수정(사용자 결정) | D8 색 표. 재지적(재리뷰 #4: 표시 세로 위치·겹침·x 식·선 값) → D12 |
| 20 | readiness | Major | research R15·R16 | 효과 값과 링 배치 | 수정(사용자 결정) | D9. 재지적(재리뷰 #3: R13·R16이 §6과 다름, 틀을 쓰는 범위) → R13·R16을 §6에 맞추고 D14 |
| 21 | readiness | Major | data-model §2 | 그래프 필드 어긋남 처리 | 수정(사용자 결정) | D10 그 줄은 남기고 그래프만 null |
| 22 | readiness | Major | quickstart, T004·T011·T016 | 움직임 줄이기·다시 보기·비교 끄기 등 검증 공백 | 수정 | 리뷰 전 결정 범위의 검증 행을 quickstart에 더하고 T004에 감속. 재지적(재리뷰 #8: US2-3·SC-016 화면 검증 task 없음) → T020에 옛 형식 기록 장면 |
| 23 | readiness | Nit | T005 | 겹침 근거 §3 누락 | 수정 | "§1~§3" |
| 24 | readiness | Nit | T020 | compare 순서 표기 | 수정 | "compare(안에서 reset → gentle → dynamic)" |
| 25 | readiness | Nit | contracts §3 | "오전 0:42" 형식 | 수정 | `timeOfDay` 형식 "오전 12:42" |
| 26 | readiness | Nit | plan Constitution IV | 새 파일 수 | 수정 | GraphTest.kt도 적음 |
| 27 | readiness | Nit | spec Assumptions | "막대 하나" | 수정 | "구간 하나" |
| 28 | readiness | Major | data-model §4 지난 목록(재리뷰 #2) | 분석 운전 0개면 take(-1)로 멈춤 | 수정(사용자 결정) | D11: 2개 미만이면 모두 NONE, T012에 0개 |
| 29 | readiness | Major | data-model §5 자막(재리뷰 #6) | 다시 보기 제외가 빠져 contracts와 어긋남 | 수정 | contracts §1·quickstart "다시 보기에는 자막 없음". §5·R15·T015에 `openRecord == null` |
| 30 | readiness | Major | tasks T002(재리뷰 #9) | "0~19번 숫자"가 decode 동작과 다름 | 수정(사용자 결정) | D16: Dna.kt:470-471 확인, 1·2차 동작 그대로(숫자 필드 0~11·13~17·19) |
| 31 | readiness | Major | tasks T024(재리뷰 #10) | approval-gate.md 경로가 없음 | 수정 | specs/003에 없음 확인. 2-1 게이트 때 `specs/003-drive-graph/approval-gate.md`를 만든다고 적음 |
| 32 | readiness | Major | plan Summary(재리뷰 #11) | 비교 함수 이름이 다름 | 수정 | data-model §3·T008과 같게 `Compare.counts` |
| 33 | readiness | Nit | contracts §1(재리뷰 #12) | "속도는 주행거리로 추정했어요"가 두 번 | 기록 | 그래프 카드 글과 1·2차 끝 줄을 그대로 둔다 |
| 34 | readiness | Nit | research R11(재리뷰 #13) | 표본 간격 근거가 사실과 다름 | 수정 | demo.py docstring·ScenarioTest `nextKm += 1020` 기준으로 약 1초로 고침 |
| 35 | readiness | Nit | spec Assumptions(재리뷰 #14) | 위임 결정 번호 범위가 다름 | 수정 | "approval-gate.md의 위임 결정 D1~D16" |
| 36 | readiness | Nit | spec Key Entities(재리뷰 #15) | 엔터티 이름이 다름 | 수정 | `TagView`·`Change`로 맞춤 |

## 재리뷰

- 대상: readiness(첫 리뷰의 Blocker #10을 `수정`해서)
- 판정: NO-GO → NO-GO
- 재리뷰 Blocker 1개(바뀜 종류·before 규칙)는 첫 리뷰 #16과 같은 문제라 행을 더하지 않고 `수정 후 재지적` → 사용자 판단으로 두었다. 사용자 위임에 따라 위임 결정 D11로 정해 문서를 고쳤다(`수정(사용자 결정)`).
- `재지적` 행: #12, #15, #16, #19, #20, #22
- 새 지적 행: #28~#36
- 더 재리뷰하지 않는다(리뷰어당 1회).

## 집계

지적 36개 중 문서를 바꾼 것 35개
