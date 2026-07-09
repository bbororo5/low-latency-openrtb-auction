# Architecture Significant Requirements

이 문서는 구현 기능 목록이 아니라, 아키텍처 선택을 강하게 제약하는 요구사항과 리스크를 정리한다. 설계 결론은 `architecture-description.md`, 데이터 기준은 `data-architecture.md`, API 계약은 `api-interface-specification.md`에서 다룬다.

ASR은 “무엇을 만들어야 하는가”보다 “어떤 제약 때문에 구조가 단순 CRUD처럼 설계될 수 없는가”를 설명한다.

## 1. Scope

대상 범위는 Provider Slot Request가 들어와 SSP가 DSP들로 BidRequest를 보내고, 제한 시간 안에서 winner 또는 no-winner를 결정하는 RTB hot path다.

범위 밖:

- 광고 렌더링
- tracking
- billing, settlement, ledger
- reporting
- external SSP/DSP integration
- Kubernetes operation

## 2. Significant Requirements

| Requirement | Why architecture-significant |
|---|---|
| 경매 결과는 deadline 안에서 결정되어야 한다. | deadline은 단순 timeout 설정이 아니라 winner 후보 자격을 결정하는 도메인 경계다. |
| deadline 이후 도착한 bid는 가격과 무관하게 사용할 수 없다. | 높은 가격보다 시간 경계가 우선하므로 결과 수집과 후보 판단이 분리되어야 한다. |
| 일부 DSP 실패가 전체 경매 실패가 되면 안 된다. | DSP별 결과를 독립적으로 관찰하고 분류해야 한다. |
| no-bid와 no-winner는 시스템 장애가 아닐 수 있다. | 비즈니스 결과와 시스템 오류를 분리해야 한다. |
| invalid bid는 winner가 될 수 없다. | bid validation과 winner selection이 분리되어야 한다. |
| hot path는 source store 동기 조회에 의존하면 안 된다. | p95/p99 latency와 장애 전파에 직접 영향을 준다. |
| 관측 데이터는 business event나 ledger의 대체물이 아니다. | metrics/logs/traces의 누락이나 샘플링이 비즈니스 진실을 바꾸면 안 된다. |

## 3. Quality Attributes

| Priority | Quality attribute | Measure |
|---|---|---|
| 1 | Deadline compliance | 제한 시간 안에 winner/no-winner가 결정된 비율 |
| 2 | Tail latency | Provider Slot Request부터 AuctionResult까지 p95/p99 |
| 3 | Auction correctness | invalid/late bid가 winner가 되지 않는지 |
| 4 | Failure isolation | 일부 DSP timeout/error가 전체 장애로 번지지 않는지 |
| 5 | Explainability | no-bid, timeout, late bid, invalid bid, no-winner가 구분되는지 |

## 4. Quality Attribute Scenarios

| ID | Scenario | Expected response | Measure |
|---|---|---|---|
| QA-001 | 일부 DSP가 deadline 안에 응답하지 않는다. | 해당 DSP만 timeout으로 분류하고 나머지 결과로 경매를 끝낸다. | timeout count, deadline compliance |
| QA-002 | 가장 높은 bid가 deadline 이후 도착한다. | late bid로 분류하고 winner 후보에서 제외한다. | late bid count, winner correctness |
| QA-003 | BidResponse가 원 요청과 맞지 않는다. | invalid bid로 분류하고 winner 후보에서 제외한다. | invalid bid count, invalid reason |
| QA-004 | 모든 DSP가 no-bid 또는 non-candidate 결과를 낸다. | 정상 no-winner 결과를 반환한다. | no-winner rate, result distribution |
| QA-005 | 동시 요청 수 또는 DSP 수가 증가한다. | p95/p99와 timeout 비율 변화를 관찰한다. | latency p95/p99, timeout rate |
| QA-006 | serving copy가 준비되지 않았다. | 요청 의미를 추측하지 않고 해당 경로를 중단한다. | rejected request count, startup readiness |

## 5. Architecture Constraints

제약조건은 단순한 범위 선언이 아니라 설계 선택지를 실제로 줄이는 조건이다. 아래 조건을 어기면 컴포넌트 경계, 데이터 경계, 테스트 전략이 달라진다.

| Constraint | Architectural consequence |
|---|---|
| 경매 판단은 deadline에 종속된다. | 응답 수집은 무기한 wait가 될 수 없고, late bid는 높은 가격이어도 candidate가 될 수 없다. |
| DSP는 부분 실패하는 외부 참여자로 취급한다. | 한 DSP 호출 실패를 전체 transaction rollback처럼 모델링하지 않고, DSP별 관찰 결과로 분리해야 한다. |
| Hot path는 inventory/campaign source store를 매 요청 동기 조회하지 않는다. | source of truth와 serving copy를 분리해야 하며, serving copy 미준비 상태를 별도 실패 표면으로 다뤄야 한다. |
| Provider-facing input은 DSP-facing OpenRTB BidRequest와 다르다. | SSP 안에 slot ingress와 BidRequest creation 책임이 필요하고, prebuilt BidRequest를 기본 입력으로 삼을 수 없다. |
| 현재 경매 단위는 single impression이다. | multi-imp winner allocation, partial fill, per-imp pricing은 현재 컴포넌트 책임에서 제외된다. |
| 현재 monetary state는 hot path에 없다. | bid/win 판단을 budget reservation이나 ledger write와 같은 강한 transaction으로 묶지 않는다. |
| Observability data는 비즈니스 사실의 원본이 아니다. | metrics/logs/traces의 retention, sampling, 누락이 AuctionResult나 future ledger truth를 바꾸면 안 된다. |

## 6. Risks

| Risk | Impact | Follow-up |
|---|---|---|
| tie-break rule이 명확하지 않다. | 같은 입력에서 winner 재현성이 흔들릴 수 있다. | deterministic tie-break를 테스트로 고정한다. |
| no-bid 표현 허용 범위가 모호하다. | DSP 응답 해석이 Gateway와 Bid Judge 사이에서 흔들릴 수 있다. | API 계약에서 허용 표현을 확정한다. |
| malformed response 분류 경계가 모호하다. | transport error와 invalid bid 의미가 섞일 수 있다. | 책임 owner와 테스트를 고정한다. |
| serving copy freshness 정책이 없다. | 원본 변경과 hot path 판단 사이 불일치 허용 범위가 불명확하다. | Data Architecture 후속 결정으로 다룬다. |

## 7. Verification Focus

ASR 검증은 구현 기능 완성보다 아키텍처 의미가 깨지지 않는지에 초점을 둔다.

| Requirement | Verification |
|---|---|
| deadline 이후 bid 제외 | late bid 시나리오 테스트 |
| invalid bid winner 방지 | request id, imp id, floor, currency, media mismatch 테스트 |
| no-winner 정상 처리 | all no-bid, all timeout, all invalid 시나리오 테스트 |
| partial failure isolation | 일부 DSP timeout/error가 전체 500으로 번지지 않는지 확인 |
| result explainability | AuctionResult와 metrics에서 원인 분류가 유지되는지 확인 |
