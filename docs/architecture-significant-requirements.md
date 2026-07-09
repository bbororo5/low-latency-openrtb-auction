# Architecture Significant Requirements

이 문서는 RTB 경매 실행 구조를 바꾸는 핵심 요구사항만 선별한다.

ASR은 모든 기능 요구사항을 모아둔 목록이 아니다. 컴포넌트 분리, 데이터 분리, 동시 처리 방식, 저장소 사용 방식, 장애 처리, 성능 측정 방식에 영향을 주는 요구사항만 다룬다.

## 1. Context and Scope

이 프로젝트는 광고 슬롯 요청이 들어왔을 때 SSP가 여러 DSP에 입찰을 요청하고, 제한 시간 안에 도착한 유효한 응답만으로 winner 또는 no-winner를 결정하는 경매 실행 경로를 다룬다.

현재 ASR은 다음 범위에 집중한다.

- provider 요청을 경매에 사용할 수 있는 요청으로 바꾼다.
- 여러 DSP 응답을 제한 시간 안에서 모은다.
- 유효한 bid만 winner 후보로 인정한다.
- winner 또는 정상 no-winner를 결정한다.
- latency, timeout, 결과 분포를 측정할 수 있게 한다.

광고 렌더링, 사용자 추적, 리포팅, 실제 정산/원장 구현, 운영 배포 전략은 이 문서에서 다루지 않는다.

## 2. Architecture Drivers

이 프로젝트의 아키텍처를 움직이는 핵심 관심사는 세 가지다.

| Driver | 쉽게 말하면 |
|---|---|
| Correct auction decision | 빠르게 응답하더라도 경매 규칙에 맞지 않는 bid가 winner가 되면 안 된다. |
| Low-latency concurrent execution | 여러 DSP를 동시에 호출하더라도 제한 시간 안에 결과를 내야 한다. |
| Failure handling and result explanation | 일부 DSP가 실패해도 전체 경매를 실패로 만들지 않고, 왜 winner 또는 no-winner가 나왔는지 설명할 수 있어야 한다. |

이 세 가지는 후속 설계의 판단 기준이다. 특히 유효하지 않은 bid를 winner로 만드는 성능 최적화는 선택하지 않는다.

## 3. Key Requirements

아래 요구사항은 시스템 구조를 실제로 바꾸기 때문에 ASR로 남긴다.

| Requirement | 왜 구조가 달라지는가 |
|---|---|
| 경매는 제한 시간 안에 winner 또는 no-winner를 결정해야 한다. | DSP 응답을 무기한 기다릴 수 없고, 늦게 도착한 bid를 따로 분류해야 한다. |
| winner는 유효한 bid 후보에서만 결정되어야 한다. | DSP 응답을 받은 단계, bid를 검증하는 단계, winner를 고르는 단계를 분리해야 한다. |
| bid price는 실제 잔고 차감이나 원장 기록의 기준 데이터가 아니다. | 경매 결과, 낙찰 사실, 원장 기록을 서로 다른 데이터로 다뤄야 한다. |
| 일부 DSP timeout/error는 전체 경매 실패가 되면 안 된다. | DSP별 결과를 따로 모으고, 각 결과를 timeout, error, no-bid, valid bid 등으로 분류해야 한다. |
| 성능은 평균 latency만으로 판단하지 않는다. | p95/p99 latency, deadline 준수율, timeout 비율을 측정할 수 있어야 한다. |
| 중복 요청이나 중복 낙찰 사실은 안전하게 구분되어야 한다. | request, auction, win event가 같은 뜻으로 섞이면 안 된다. |
| 같은 입력과 같은 후보 집합은 같은 winner를 만들어야 한다. | 같은 상황에서 결과가 매번 달라지지 않도록 tie-break 규칙이 필요하다. |

## 4. Constraints and Invariants

아래 조건은 구현 방식이 달라져도 지켜야 한다.

| Rule | 쉽게 말하면 |
|---|---|
| DSP 응답은 검증 전까지 외부에서 온 응답일 뿐이다. | 응답을 받았다는 사실과 winner 후보가 된다는 사실은 다르다. |
| 제한 시간 이후 도착한 bid는 winner 후보가 아니다. | 높은 가격보다 시간 제한이 우선한다. |
| invalid bid, no-bid, timeout은 winner 후보가 아니다. | 후보가 아닌 결과가 winner 결정에 섞이면 안 된다. |
| 유효한 bid가 없으면 정상 no-winner다. | 비낙찰과 시스템 장애를 구분한다. |
| 경매 결과는 잔고 차감의 기준 데이터가 아니다. | auction result나 metrics를 돈 차감의 원본으로 사용하지 않는다. |
| 관측 데이터는 비즈니스 사실의 원본이 아니다. | metrics, logs, traces가 누락되거나 샘플링되어도 경매 결과가 바뀌면 안 된다. |

## 5. Quality Attribute Tradeoffs

이 프로젝트는 품질속성이 충돌할 때 아래 방향을 선택한다.

| Tradeoff | 선택 방향 |
|---|---|
| 더 많은 bid 기회 vs 제한 시간 준수 | 더 많은 bid를 기다리기보다 제한 시간을 지킨다. |
| 빠른 낙찰 판단 vs 돈 데이터 정확성 | 낙찰 판단은 빠르게 끝내되, 실제 돈 데이터는 별도 책임으로 둔다. |
| 높은 처리량 vs 자원 안정성 | 무제한으로 DSP를 호출하지 않고, 동시에 처리 중인 작업과 외부 호출 비용을 제한할 수 있게 한다. |
| 단순한 성공/실패 모델 vs 결과 정확성 | 단순 성공/실패보다 no-bid, timeout, late bid, invalid bid, no-winner 구분을 우선한다. |
| 자세한 관측 vs hot path 비용 | 관측은 필요하지만, 경매 결과를 바꾸지 않는 보조 데이터로 둔다. |

## 6. Design and Verification Implications

후속 설계와 검증은 아래 기준을 만족해야 한다.

| Area | 후속 설계 기준 |
|---|---|
| Component design | slot input, bid request creation, DSP fan-out, bid validation, winner decision 책임을 분리한다. |
| Data design | DSP 응답, 유효한 bid 후보, 경매 결과, 낙찰 사실, 원장 기록을 구분한다. |
| Runtime design | deadline, timeout, 진행 중인 DSP 호출, connection/thread/resource 사용량을 측정하고 제한할 수 있어야 한다. |
| API design | no-bid, timeout, late bid, invalid bid, no-winner 결과 분류가 SSP와 DSP 경계에서 일관되어야 한다. |
| Verification | late bid 제외, 유효 후보 경계, 정상 no-winner, 부분 실패 격리, p95/p99 latency, deadline 준수를 확인한다. |
