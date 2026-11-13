# Goldfish Review — DRIVE DNA 4차 — 차 화면 개선

- 날짜: 2026-11-13
- 리뷰어 상태: comprehension 완료 · readiness GO

## Comprehension의 다시 쓰기

DRIVE DNA는 운전이 끝나 P에 놓으면 이번 운전을 네 축 글자 유형(GPSE 등)으로 보여 주는 차량용 안드로이드 앱이다. 4차에서는 2026-11-13 UI 점검이 찾은 P1·P2 문제를 고친다. 첫째, 팔 길이에서 읽고 누를 수 있도록 화면 글자를 24sp 이상(카드 제목 28sp)으로, 누르는 칸을 76×76dp·간격 24dp로 키운다. 둘째, 흐린 태그 칩과 각주 색을 낮·밤 모두 4.5:1 이상으로 바꾼다. 셋째, 낮·밤 전환으로 액티비티가 다시 만들어져도 열린 페이지·고른 운전·스크롤이 남게 한다. 넷째, 짧은 운전(역동성 1km, 전력 10km 미만)에서는 부풀려진 거리 환산 숫자 대신 센 수와 쓴 kWh를 보인다. 다섯째, 결과 화면에서 한 가지 제안을 축 카드 위로 올리고, 두 막대 묶음에 "최근 운전 n번 평균"·"이번 운전 네 축"이라는 이름을 붙인다. 판정·음성·저장 형식·주행 중 동작은 그대로 둔다.

### 기존 시스템

- 화면은 두 칸이다. 왼쪽(세로 화면은 아래)은 나의 DNA 칸이다. 분석된 최근 운전 최대 10번의 축 평균 막대 넷, 보조 태그 칩, 페이지 버튼 셋이 있고, 버튼 셋은 한 줄에 놓인 높이 48dp 칩이다. 오른쪽은 상황 화면이다. 결과일 때 그래프 카드, 유형 카드, 축 카드, 한 가지 제안이 나오고 그 뒤에 자막·차이 줄·각주가 온다. 제안은 스크롤해야 보인다. 지금 차례가 정확히 어떤지는 문서에 없다.
- 페이지(운전 기록, 16유형, 보조 태그, 두 운전 비교, 기록 다시 보기)는 정차 중에 연다.
  - 다른 앱에 갔다 오면 결과 화면부터 다시 나온다.
  - 낮·밤 전환은 액티비티를 새로 만든다. 이때 `onPause`가 페이지를 닫고, 새 인스턴스의 `seenAt = null`이 "새 기록이면 닫기"에 걸려 열린 페이지가 닫힌다.
  - 공개 효과는 프로세스 범위 변수라 다시 나오지 않는다.
- 글자는 20~22sp다. 누르는 칸은 "비교" 72×43dp, "✕ 닫기" 99×46dp, 페이지 버튼 높이 48dp다. 대비는 태그 칩(green / green_soft) 2.11:1, 각주(text4) 3.05~3.24:1이다.
- 축 판정은 환산값으로 한다. 판정한 역동성 줄은 거리와 상관없이 "(10km당 x)"를, 전력 줄은 "100km당 x kWh를 썼어요"를 보인다. 그래서 시연 빌드(축 최소 0.3km)의 0.6km 운전에 "10km당 221.3"이 나온다. 출시 빌드의 축 최소는 2km다. 한 가지 제안(`Coach.line`)은 전력 환산 문장을 고를 수 있다.
- 앱이 주행 최적화를 선언하지 않았으므로 주행 중에는 Pleos가 앱을 가린다.

## 지적과 처리

| # | 리뷰어 | 심각도 | 위치 | 요지 | 처리 | 근거·변경 |
|---|---|---|---|---|---|---|
| 1 | comprehension | Major | spec FR-048·SC-018, contracts §2 | 사건 기호 ▲▼◆가 대비 기준에 드는지 두 가지로 읽힘 | 수정 | 리뷰 전 결정(contracts §2 "기호 색은 그래프 표시와 같은 색으로 둔다")이 FR에 빠짐. 용어에 "사건 기호 예외"를 넣고 FR-048·SC-018·US2-1이 가리키게, research R5 표에 기호 줄 |
| 2 | comprehension | Major | research R2, contracts §6 | `listY` 정의 없음, 목록 복귀 요구가 숨어 있음 | 수정 | Key Entities에 listY·scrollY를 나눠 정의, US3-5 시나리오와 quickstart §3 줄 추가 |
| 3 | comprehension | Major | spec FR-051, contracts §5 | 자막·차이 줄 정의 없음, 지금 차례 없음 | 수정 | 용어에 둘과 나오는 조건을 정의(MainActivity.kt 169행, Dna.kt `Profile.difference`), FR-051·contracts §5에 지금 차례 |
| 4 | comprehension | Major | spec Edge Cases | 운전 0번이어도 막대는 그려짐 | 수정 | Screens.kt `profile()`과 AxisBar 보류 모양 확인. "막대는 점선 그대로, 이름 줄만 넣지 않음" |
| 5 | comprehension | Major | spec FR-050, contracts §4 | 판정을 미룬 짧은 운전의 역동성 줄이 두 가지로 읽힘 | 수정 | 리뷰 전 결정(contracts §4 "판정 미룸 → 지금 그대로")에 맞춰 FR-050에 "판정한 줄에만" |
| 6 | comprehension | Nit | spec 용어 | 23dp와 24dp, 글자 단위 dp | 수정 | 사실 불일치. 용어에 "23dp(이 명세는 24dp)", 글자는 sp |
| 7 | comprehension | Nit | spec FR-044 | 뒤의 "제목"이 무엇인지 | 수정 | 같은 줄을 고치며 "큰 제목(headline 44~56sp)"으로 이름을 나눔 |
| 8 | comprehension | Nit | spec 용어·US2-1, research R5 | "큰 글자" 정의가 없음 | 수정 | 문서 사이 불일치. 용어 "큰 글자 예외" 한 곳에 정의 |
| 9 | comprehension | Nit | spec Input·용어·SC-023, research R1 | "안 써본 기능 6차 spec"·impeccable 설명 없음 | 수정 | 사실 오류(이 저장소 specs/에 없음). 용어에 다른 저장소 절대 경로와 impeccable 설명 |
| 10 | comprehension | Nit | contracts §2 그래프 카드·두 운전 비교 | 색 열이 모호하고 지금 색이 틀림 | 수정 | Screens.kt 198·209행 확인. 색이 바뀌는 칸만 따로 적음 |
| 11 | readiness | Major | tasks T002 | 0.6km `Coach.line` 검사가 지금 코드에서도 통과 | 수정 | Dna.kt 476·481행 계산 확인. 급한 조작 없는 픽스처로 `Coach.DEFAULT` 기대, 위치는 `JudgeTest` |
| 12 | readiness | Major | contracts §2, T006, quickstart §2 | 운전 기록 목록 간격 20dp | 수정 | Screens.kt 185행 확인. 리뷰 전 결정(설계 C2 "이웃한 누르는 칸 사이 24dp")대로 24dp와 간격 측정 추가 |
| 13 | readiness | Major | quickstart §2-4 | grep이 괄호 있는 호출을 놓침 | 수정 | 사실 오류. 후보를 모두 뽑고 글자 크기 인자만 보는 절차로 |
| 14 | readiness | Major | plan Constraints, research R4 | 가로 나의 DNA 칸 높이 예산 없음, 맨 아래 버튼이 화면 밖으로 밀림 | 수정(사용자 결정) | 가로 화면에서도 페이지 버튼 셋을 머리 바로 아래로 올릴까요(세로와 같은 자리, 스크롤 없이 보임), 아니면 지금처럼 맨 아래에 두고 칸을 스크롤하게 둘까요? |
| 15 | readiness | Major | spec Edge Cases | 막대 0번 문장이 코드와 다름 | 수정 | #4와 같은 변경 |
| 16 | readiness | Major | spec FR-048·SC-018 ↔ contracts §2 | 기호 예외가 FR에 없음 | 수정 | #1과 같은 변경 |
| 17 | readiness | Nit | tasks T012 | [P]가 같은 파일 규칙과 맞지 않음 | 수정 | 사실 불일치. [P] 뺌 |
| 18 | readiness | Nit | spec 용어 | 23dp | 수정 | #6과 같은 변경 |
| 19 | readiness | Nit | spec US2-3 | "지난: …"은 태그 페이지에 있음 | 수정 | Screens.kt 279행 확인. 목록 이름에 "태그" |

## 집계

지적 19개 중 문서를 바꾼 것 19개

게이트(2026-11-13 21:40) 사용자 결정: #14는 "머리 바로 아래" → spec FR-047·Edge, research R4, contracts §2·§5, T005, quickstart §2의 5에 반영.
