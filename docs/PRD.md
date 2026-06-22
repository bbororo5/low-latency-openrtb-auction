# PRD: OpenRTB 기반 저지연 RTB 입찰 시스템

## 1. Overview

이 프로젝트는 OpenRTB 2.6의 `BidRequest` / `BidResponse` 흐름을 기반으로, 실시간 광고 입찰 요청을 처리하고 제한 시간 안에 낙찰자를 결정하는 저지연 RTB 입찰 시스템을 정의한다.

프로젝트의 범위는 광고 플랫폼 전체가 아니라 **입찰 요청 처리부터 bid/no-bid 판단, 입찰 응답 생성, 낙찰자 결정까지의 핵심 hot path**다.

실제 광고 렌더링, 노출/클릭 추적, 과금, 리포팅, 광고 운영 백오피스는 포함하지 않는다.

이 문서는 시스템이 해결해야 할 문제, 기능 요구사항, 실패 케이스, 성능 측정 기준을 정의한다. 기술 스택, 세부 아키텍처, 배포 방식, 최적화 구현은 Tech Spec과 ADR에서 다룬다.

## 2. Problem Statement

RTB 환경에서는 하나의 광고 요청이 제한된 시간 안에 처리되어야 한다. 요청을 받은 시스템은 광고 슬롯, 지면, 디바이스, 최소 입찰가 등의 정보를 해석하고, 여러 Bidder의 응답을 수집한 뒤 유효한 입찰 중 낙찰자를 결정해야 한다.

하지만 Bidder는 항상 정상 응답을 반환하지 않는다. 어떤 Bidder는 no-bid를 반환할 수 있고, 어떤 Bidder는 제한 시간을 초과하거나 잘못된 응답을 반환할 수 있다. 제한 시간 이후 도착한 bid는 가격이 높더라도 낙찰 후보에 포함되면 안 된다.

이 프로젝트의 핵심 문제는 다음과 같다.

> 대용량 입찰 요청 상황을 가정하고, 제한 시간 안에서 bid/no-bid 판단과 여러 BidResponse 수집을 수행한 뒤, timeout, invalid bid, late bid를 제외하고 유효한 bid 중 낙찰자를 결정하는 것.

## 3. Scope in OpenRTB Ecosystem

OpenRTB는 Sell-Side의 SSP/Exchange와 Buy-Side의 DSP/Bidder가 실시간 입찰 요청과 응답을 주고받기 위한 프로토콜이다.

이 프로젝트는 전체 SSP, Exchange, DSP를 구현하지 않는다. 대신 OpenRTB 기반 RTB 입찰 시스템의 핵심 경로를 축소 모델링한다.

포함하는 논리 역할:

- OpenRTB 요청 수신 및 검증
- Bidder의 bid/no-bid 의사결정
- 간단한 광고 타게팅 및 campaign matching
- BidResponse 생성
- 여러 BidResponse 수집
- 낙찰자 결정

제외하는 역할:

- 실제 매체사 연동
- 실제 외부 DSP 연동
- 광고 운영 백오피스
- 광고 노출/클릭/전환 측정
- 정산 및 과금
- 리포팅 시스템

## 4. Goals

이 프로젝트는 다음 목표를 가진다.

- OpenRTB 2.6의 핵심 입찰 요청/응답 흐름을 기반으로 한다.
- BidRequest를 수신하고 필수 필드를 검증한다.
- 광고 슬롯, 광고 타입, 최소 입찰가 등 입찰 판단에 필요한 정보를 해석한다.
- Bidder가 요청을 평가해 bid 또는 no-bid를 결정한다.
- 여러 Bidder 응답을 제한 시간 안에 수집한다.
- 정상 bid, no-bid, timeout, invalid response, late bid를 구분한다.
- 유효한 bid 중 낙찰자를 결정한다.
- 유효한 bid가 없는 경우 no-winner 결과를 반환한다.
- 동시 요청 수와 Bidder 수 증가가 latency에 미치는 영향을 측정한다.
- p95/p99 latency와 deadline 내 응답률을 주요 성능 척도로 사용한다.
- 성능 개선은 추측이 아니라 측정 결과를 기반으로 설명한다.

## 5. Non-Goals

이 프로젝트는 다음을 목표로 하지 않는다.

- 전체 광고 서버 구현
- 실제 광고 렌더링
- impression tracking
- click tracking
- conversion tracking
- billing
- reporting
- 광고 운영 백오피스
- full OpenRTB 2.6 specification coverage
- 실제 외부 DSP/SSP 연동
- production-grade ad exchange 구현
- Kubernetes 기반 운영 검증
- 클라우드/무료 배포 환경에서의 절대 성능 증명

## 6. Actors and Core Concepts

`Auction Client`

입찰 요청을 보내는 주체다. 실제 publisher나 SSP를 구현하지 않고, 이 프로젝트에서는 테스트 가능한 요청 생성 주체로 간주한다.

`RTB Bidding System`

BidRequest를 받아 bid/no-bid 판단, BidResponse 수집, 낙찰자 결정을 수행하는 시스템이다.

`Bidder`

BidRequest를 평가하고 bid 또는 no-bid를 반환하는 주체다. 이 프로젝트에서는 실제 외부 DSP가 아니라 시스템 내부 또는 테스트 환경에서 시뮬레이션 가능한 Bidder로 다룬다.

`BidRequest`

OpenRTB 기반 입찰 요청이다. impression, 광고 타입, 지면, 디바이스, 최소 입찰가, 응답 제한 시간 등의 정보를 포함한다.

`BidResponse`

Bidder가 특정 impression에 대해 입찰 의사를 표현하는 응답이다.

`No-Bid`

Bidder가 해당 요청에 입찰하지 않는 결과다.

`Auction Deadline`

Bidder 응답 수집과 낙찰자 결정을 완료해야 하는 제한 시간이다.

`Late Bid`

제한 시간 이후 도착한 bid다. 가격이 높더라도 낙찰 후보에 포함하지 않는다.

`Invalid Bid`

필수 정보 누락, 잘못된 impression ID, bidfloor 미만 가격 등으로 인해 낙찰 후보에 포함할 수 없는 bid다.

`Winner`

유효한 bid 중 auction rule에 따라 선택된 최종 낙찰 bid다.

## 7. Main Scenario

1. Auction Client가 OpenRTB BidRequest를 보낸다.
2. 시스템은 BidRequest의 필수 필드를 검증한다.
3. 시스템은 impression, 광고 타입, bidfloor, 지면/디바이스 정보를 해석한다.
4. 여러 Bidder가 요청을 평가한다.
5. 각 Bidder는 bid 또는 no-bid를 반환한다.
6. 시스템은 auction deadline 안에 도착한 응답만 수집한다.
7. 시스템은 invalid bid와 late bid를 제외한다.
8. 시스템은 유효한 bid 중 낙찰자를 선택한다.
9. 낙찰자가 있으면 winner result를 반환한다.
10. 유효한 bid가 없으면 no-winner result를 반환한다.

## 8. Functional Requirements

`FR-001: BidRequest 수신`

시스템은 OpenRTB 2.6 subset 형태의 BidRequest를 받을 수 있어야 한다.

`FR-002: BidRequest 검증`

시스템은 필수 필드가 누락되거나 처리할 수 없는 요청을 invalid request로 분류해야 한다.

`FR-003: 광고 요청 정보 해석`

시스템은 impression, 광고 타입, 광고 크기, bidfloor, 지면 또는 앱 정보를 입찰 판단에 사용할 수 있어야 한다.

`FR-004: Bidder 의사결정`

Bidder는 요청 정보를 기반으로 bid 또는 no-bid를 결정할 수 있어야 한다.

`FR-005: BidResponse 생성`

Bidder는 입찰 가능한 경우 OpenRTB subset에 맞는 BidResponse를 생성해야 한다.

`FR-006: 여러 Bidder 응답 수집`

시스템은 하나의 BidRequest에 대해 여러 Bidder 응답을 수집할 수 있어야 한다.

`FR-007: 응답 제한 시간 적용`

시스템은 auction deadline 안에 도착한 응답만 낙찰 후보에 포함해야 한다.

`FR-008: Bidder 결과 분류`

시스템은 Bidder 결과를 bid, no-bid, timeout, invalid response, late bid로 구분해야 한다.

`FR-009: BidResponse 검증`

시스템은 BidResponse가 원 요청의 impression과 일치하고, bidfloor 등 기본 제약을 만족하는지 검증해야 한다.

`FR-010: 낙찰자 결정`

시스템은 유효한 bid 중 auction rule에 따라 winner를 결정해야 한다.

`FR-011: No-Winner 결과 반환`

유효한 bid가 없으면 시스템은 no-winner 결과를 반환해야 한다.

`FR-012: 결과 설명 가능성`

시스템은 테스트와 디버깅을 위해 winner, no-winner, timeout, invalid bid 등 주요 판단 결과를 확인할 수 있어야 한다.

## 9. Failure and Edge Cases

다음 케이스를 요구사항 수준에서 다룬다.

- BidRequest에 impression이 없음
- 지원하지 않는 광고 타입
- bidfloor가 누락되거나 비정상 값임
- 모든 Bidder가 no-bid
- 모든 Bidder가 timeout
- 일부 Bidder만 timeout
- Bidder가 malformed response 반환
- BidResponse의 impression ID가 원 요청과 다름
- bid price가 bidfloor보다 낮음
- bid price가 0 이하
- deadline 이후 가장 높은 bid가 도착함
- 유효한 bid가 여러 개 존재함
- 동일 가격 bid가 여러 개 존재함
- Bidder 수가 증가함
- 동시 요청 수가 증가함

각 케이스의 구체적인 구현 방식은 Tech Spec에서 정의한다. PRD에서는 시스템이 어떤 결과를 내야 하는지만 정의한다.

## 10. Performance and Measurement Requirements

이 프로젝트의 성능 요구사항은 특정 배포 환경에서 절대 RPS를 달성하는 것이 아니다. 실행 환경에 따라 처리량은 크게 달라질 수 있으므로, 처리량은 고정 목표가 아니라 통제된 환경에서 관찰되는 지표로 다룬다.

성능 검증은 문서화된 로컬 환경에서 수행한다. 목표는 보편적인 처리량 수치를 증명하는 것이 아니라, 같은 조건에서 동시 요청 수, Bidder 수, 응답 지연이 latency와 deadline 내 응답률에 어떤 영향을 주는지 측정하는 것이다.

### Primary Metrics

- auction latency p95/p99
- deadline compliance rate

### Secondary Metrics

- observed throughput
- bidder timeout rate
- invalid bid rate
- no-winner rate

### Diagnostic Metrics

- CPU usage
- memory usage
- active threads
- connection usage
- GC pause

### Performance Requirements

`PERF-001: Latency 측정`

시스템은 auction latency를 p50, p95, p99로 측정할 수 있어야 한다.

`PERF-002: Deadline 내 응답률 측정`

시스템은 설정된 auction deadline 안에 결과를 반환한 비율을 측정할 수 있어야 한다.

`PERF-003: Observed Throughput 기록`

처리량은 고정 목표가 아니라, 특정 latency/deadline 조건에서 관찰된 결과로 기록한다.

`PERF-004: 동시 요청 수 변화 측정`

성능 테스트는 동시 요청 수 증가에 따른 latency와 deadline compliance 변화를 측정해야 한다.

`PERF-005: Bidder 수 변화 측정`

성능 테스트는 Bidder 수 증가에 따른 latency와 deadline compliance 변화를 측정해야 한다.

`PERF-006: 테스트 환경 기록`

성능 테스트 결과에는 실행 환경, 테스트 조건, 요청 수, 동시성 수준, Bidder 수가 함께 기록되어야 한다.

## 11. Assumptions

초기 PRD는 다음 가정을 둔다.

- OpenRTB 2.6 전체가 아니라 subset을 사용한다.
- 하나의 BidRequest는 최소 하나의 impression을 가진다.
- 초기 광고 타입은 제한적으로 다룬다.
- 실제 광고 렌더링과 과금은 다루지 않는다.
- 실제 외부 DSP/SSP와 통신하지 않는다.
- Bidder는 테스트 가능한 형태로 구성된다.
- 성능 측정은 로컬 통제 환경에서 수행한다.
- 구체적인 기술 스택은 Tech Spec에서 결정한다.
- 논리 컴포넌트와 물리 서비스 분리는 Tech Spec에서 결정한다.

## 12. Milestones

`M1: Project Goal Finalization`

프로젝트 목표와 범위를 확정한다.

`M2: PRD Finalization`

기능 요구사항, 실패 케이스, 성능 측정 기준을 확정한다.

`M3: Tech Spec`

구현 구조, 기술 스택, API 계약, 테스트 전략을 결정한다.

`M4: ADR`

주요 의사결정과 trade-off를 기록한다.

`M5: Core Bid Flow`

BidRequest 수신, bid/no-bid 판단, BidResponse 생성 흐름을 구현한다.

`M6: Auction Flow`

여러 Bidder 응답 수집, timeout 처리, winner selection을 구현한다.

`M7: Failure Case Handling`

no-bid, timeout, invalid bid, late bid, no-winner 시나리오를 구현한다.

`M8: Performance Measurement`

로컬 통제 환경에서 latency, deadline compliance, observed throughput을 측정한다.

`M9: Optimization`

측정 결과를 바탕으로 병목을 개선한다.

`M10: Retrospective`

구현 결과, 성능 결과, 설계 trade-off, 배운 점을 정리한다.

## 13. Open Questions

아직 PRD 단계에서 확정하지 않을 질문들이다.

- OpenRTB 2.6 subset의 정확한 필드 범위는 어디까지인가?
- 초기 광고 타입은 banner만 다룰 것인가?
- Bidder는 내부 시뮬레이션으로 시작할 것인가, 별도 실행 단위로 둘 것인가?
- 여러 Bidder는 동일 로직의 여러 인스턴스인가, 서로 다른 응답 특성을 가진 모의 Bidder인가?
- auction rule은 first-price만 지원할 것인가?
- 동일 가격 bid의 tie-breaker는 어떻게 정의할 것인가?
- campaign matching은 어느 수준까지 구현할 것인가?
- 성능 테스트의 로컬 기준 환경은 어떻게 문서화할 것인가?
- 성능 최적화 전후 비교 기준은 무엇으로 둘 것인가?
- Kubernetes나 외부 배포는 후속 확장으로도 다루지 않을 것인가?
