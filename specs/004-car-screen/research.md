# Research: DRIVE DNA 4차 — 차 화면 개선

## R1. 차 기준 값

- **Decision**: 누르는 칸 76×76dp 이상, 이웃한 누르는 칸 사이 24dp 이상, 화면 글자 24sp 이상, 카드 제목 28sp 이상, 축 막대 끝 글자 24sp·"보류" 20sp, 글자 대비 4.5:1 이상(파랑 바탕 위 24sp 이상 흰 글자는 큰 글자 3:1).
- **Rationale**: Android for Cars(https://developers.google.com/cars/design/design-foundations/visual-principles, https://developers.google.com/cars/design/automotive-os/design-system/sizing). 세 앱이 같은 값을 쓴다(안 써본 기능 6차 research R1). 축 막대는 높이 40dp(비교 64dp) 그림이라 "보류"를 20sp로 둔다.
- **Alternatives considered**: 막대 높이를 키워 "보류"도 24sp(나의 DNA 칸 막대 넷이 길어져 버튼이 아래로 밀림).

## R2. 설정 변화 때 화면 상태 보존

- **Decision**: `onSaveInstanceState`에 `page`(State 이름), `openRecord`, `comparing`, `picked`(LongArray), `typesFor`, `listY`, `scrollY`, `seenAt`을 넣고 `onCreate`에서 되살린다. `onPause`의 페이지 닫기는 `isChangingConfigurations`가 거짓일 때만 한다.
- **Rationale**: 낮·밤 전환은 액티비티를 새로 만든다. 지금은 `onPause`가 페이지를 닫고, 새 인스턴스의 `seenAt = null`이 `render`의 "새 기록이면 닫기"에 걸린다. `animatedAt`·`revealAt`은 이미 companion(프로세스 범위)이라 공개 효과가 다시 나오지 않는다(3차 D1·D15).
- **Alternatives considered**: `configChanges="uiMode"`(나의 DNA 칸 바탕 그라데이션과 레이아웃 색을 손으로 다시 칠해야 함), SharedPreferences(다른 앱에서 돌아올 때도 남아 spec US3-4와 어긋남).

## R3. 거리 환산 규칙

- **Decision**: 환산은 거리가 환산 단위의 10분의 1 이상일 때만 보인다. 역동성 "(10km당 n)"은 1km 이상, 전력 "100km당 n kWh"는 10km 이상. 짧으면 역동성 글 앞에 "<거리>km 동안 "을 붙여 센 수만, 전력은 "<n>kWh를 썼어요(<거리>km)"(0.05kWh 미만은 "0.1kWh보다 적게 썼어요(<거리>km)"). `Coach.line`은 10km 미만에서 전력 후보를 빼고 고른다. 축 판정은 그대로 환산값으로 한다.
- **Rationale**: 시연 빌드(`Limits.DEMO`, 축 최소 0.3km)의 `dynamic` 운전은 약 0.6km라 역동성 줄이 "10km당 221.3"으로 보였다(점검 캡처 `화면 점검 1113/DRIVE DNA/09 운전 끝 - 결과(dynamic).png`). 환산은 거리가 짧을수록 몇 개의 사건을 크게 부풀린다. 단위의 10분의 1이면 부풀림이 10배 이하로 묶이고, 출시 빌드(축 최소 2km)의 역동성은 늘 지금처럼 환산이 보인다. 전력은 출시 빌드에서도 10km 미만 운전에서 쓴 양으로 보이는데, 그것도 관측 사실이라 헌법 II에 맞다. 쓴 양은 `kwhPer100 × km / 100`으로 되돌려 구한다(기록 형식에 쓴 양이 따로 없다).
- **Alternatives considered**: 판정을 미룬 축에만 적용(시연 빌드 0.6km는 판정되므로 문제가 그대로), 환산을 아예 빼기(긴 운전끼리 비교하는 단서를 잃음), Limits의 축 최소 거리를 기준으로(빌드마다 글이 달라짐).

## R4. 페이지 버튼 배치

- **Decision**: "운전 기록  ›", "16유형  ›", "태그  ›"를 한 줄에 하나씩 칸 폭(`MATCH_PARENT`)의 `action`으로, 사이 24dp. 가로·세로 화면 모두 머리 바로 아래(사용자 결정 2026-11-13). 세로·가로 분기가 없어진다. 글자는 그대로 둔다.
- **Rationale**: 24sp에 76dp 높이로 키우면 셋이 한 줄에 약 680dp가 필요한데 나의 DNA 칸 안쪽 폭은 약 570dp다. 한 줄에 하나가 가장 단순하고 차 화면 목록 모양과 같다. 글자가 같아 `demo.py`의 `tap("운전 기록")` 등이 그대로 맞는다.
- **Alternatives considered**: 두 줄 격자(2+1, 마지막 줄이 비어 보임), 글자 줄이기(차 기준 위반).

## R5. 색 값

- **Decision**: 낮 `text3` #62656D. 새 `green_text`(낮 #017A40, 밤 #00E074), `blue_text`(낮 #0050D8, 밤 #6CB4FF). 각주의 `text4` → `text3`. 보류 축의 40sp 글자는 `text4` 그대로(큰 글자, 낮 카드 3.24 · 밤 3.27).
- **Rationale**: WCAG 상대 휘도.

| 글자 / 바탕 | 낮 | 밤 |
|---|---|---|
| green_text / green_soft(#E3F7EC · 밤 #143726) | 4.87 | 7.43 |
| blue_text / blue_soft | 5.90 | 6.72 |
| text3 / 카드 · 바탕 · 회색 칩 | 5.83 · 5.49 · 5.03 | 6.14 · 6.79 · 5.15 |
| 흰 글자 / blue("방금 운전으로 바뀜", 켜진 "비교", "두 운전 비교", 24sp) | 4.92 | 3.73(spec 용어의 큰 글자 예외 3:1) |
| 사건 기호 ▲(mark_accel #E5484D)·▼(mark_decel #E08A12) / 카드 | 3.9 · 2.7 | — (spec 용어의 사건 기호 예외: 그래프 표시와 같은 색의 범례 기호) |

  지금 값: 태그 칩 green #02C265 / green_soft 2.11, 각주 text4 3.05~3.24.
- **Alternatives considered**: "새로 정해짐" 칩(흰 글자 / green, 낮 2.36·밤 1.75)을 진한 초록 바탕으로(밤에 맞는 진한 초록이 없음) → green_text / green_soft + 초록 테두리로.
