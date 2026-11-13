# 2-1 승인 게이트 — DRIVE DNA 4차 차 화면 개선

- 일시: 2026-11-13 21:40
- 결과: 사용자 승인("승인, 구현 시작"). 세 앱(안 써본 기능, 같은편, DRIVE DNA)을 한 게이트에서 함께 승인받았다.
- 근거로 보인 것: 실사용 변화(세 앱 공통·앱별, 없어지거나 불편해지는 것), Figma 페이지 "차 화면 개선 6차 (1113)" 프레임 ⑨~⑯·⑭-b(https://www.figma.com/design/mme1NBmcgJhFEvmRzMh4IB/?node-id=24-69), goldfish 요약, 소요 시간(구현·확인·리뷰 6~7시간, 영상 다시 녹화 3~4시간).
- 이 앱의 사용자 결정: 나의 DNA 칸 페이지 버튼 셋은 가로·세로 모두 머리 바로 아래
- goldfish: goldfish 19개 중 19개, readiness GO
- 범위: 설계 docs/superpowers/specs/2026-11-13-car-screen-design.md와 이 폴더의 spec·plan·contracts·tasks. 커밋은 로컬만, main 합치기와 푸시는 따로 묻는다.

## 승인 뒤 구현 중 바뀐 것

- **전력 줄 규칙을 바꿨다**(spec FR-050의 "10km 이상일 때만 100km당"): 전력 축을 판정했으면 판정 근거인 "100km당 n kWh"를, 거리가 짧아 판정을 미뤘으면 "n kWh를 썼어요(거리)"를 보인다. 한 가지 제안의 전력 문장도 전력 축을 판정했을 때만 후보다. /code-review: 출시 빌드에서 2~10km 운전은 전력 축이 "C"로 판정되는데 줄에는 쓴 양만 있어 판정 근거가 화면에 없다. 역동성의 1km 규칙("0.6km 동안 …")은 그대로다(처음 문제였던 "10km당 221.3"은 역동성 줄이었다). 시연 빌드 `dynamic`(0.6km)은 이제 전력 줄이 "100km당 33.3kWh"쯤(판정함), 역동성 줄이 "0.6km 동안 …"이다.
- 두 운전 비교는 두 줄의 단위를 맞추려고 역동성·전력 줄을 환산 없이(센 수·쓴 양과 거리) 쓴다(`Judge.axes(…, rates = false)`). /code-review: 한쪽은 환산, 다른 쪽은 쓴 양이면 비교할 수 없다.
- 비교 막대의 "보류" 글자를 4dp 내렸다(20sp에서 테두리 원과 닿음, /code-review).
- `used`를 `Judge` 안의 private 함수로, "새로 정해짐" 칩 테두리는 `chip(line =)` 인자로, 태그 근거 링크의 최소 높이는 `minHeight`로 맞췄다. 쓸모없던 `setOnClickListener(null)`을 지웠다(/code-review).
- 받아들이지 않은 /code-review 지적: 스크롤 되살리기(`restoreY`)가 ScrollView의 자체 저장과 겹친다는 것(첫 그리기의 `scrollTo(0, 0)`이 자체 저장을 지우므로 둔다), "두 운전 비교" 버튼을 `action`으로(알약이 아니라 칸 폭 사각 버튼이라 둔다).
