# Architecture: OpenRTB 기반 저지연 RTB 입찰 시스템

이 문서는 PRD의 요구사항을 바탕으로 시스템의 아키텍처 특성, 품질 시나리오, 시스템 경계, 주요 런타임 흐름을 정의한다.

API 세부 계약, OpenRTB subset 필드 범위, 내부 컴포넌트 상세 설계, 데이터 모델, 테스트 구현 방식은 Tech Spec에서 다룬다.

## 1. Introduction & Context

### 1.1 Purpose

이 문서는 PRD에서 정의한 RTB 입찰 시스템을 어떤 구조로 바라볼지 정리한다.

여기서는 시스템이 OpenRTB 흐름에서 어떤 역할을 맡는지, 어떤 품질을 우선해야 하는지, 요청이 어떤 흐름으로 처리되는지, 실패 상황을 어디에서 다룰지 정의한다.

API 세부 계약, OpenRTB subset 필드 범위, 내부 컴포넌트 상세 설계, 데이터 모델, 테스트 구현 방식은 Tech Spec에서 다룬다.

### 1.2 Architecture Drivers

이 시스템의 아키텍처는 다음 제약과 문제에서 출발한다.

- OpenRTB는 Sell-Side와 Buy-Side가 입찰 요청과 응답을 주고받기 위한 프로토콜이다.
- RTB 요청은 제한 시간 안에 처리되어야 한다.
- Bidder 응답은 항상 정상이라고 가정할 수 없다.
- 낙찰자는 가장 높은 가격만으로 결정할 수 없고, 유효한 bid만 후보가 되어야 한다.
- 저지연 처리는 추측이 아니라 p95/p99 latency, deadline 내 응답률 같은 지표로 확인해야 한다.

### 1.3 Scope & Boundaries

이 아키텍처는 RTB 입찰 시스템의 핵심 hot path만 다룬다.

포함하는 범위:

- OpenRTB BidRequest 수신
- BidRequest 검증
- 입찰 판단에 필요한 요청 정보 해석
- Bidder의 bid/no-bid 판단
- BidResponse 생성
- 여러 BidResponse 수집
- timeout, invalid response, late bid 분류
- winner/no-winner 결정
- 성능 측정 지표 수집

제외하는 범위:

- 실제 광고 렌더링
- impression, click, conversion tracking
- billing
- reporting
- 광고 운영 백오피스
- 실제 외부 DSP/SSP 연동
- Kubernetes 기반 운영 검증

이 문서에서 말하는 컴포넌트는 책임을 설명하기 위한 논리적 경계다. 실제 물리 실행 단위와 기술 스택은 Tech Spec 또는 ADR에서 결정한다.

## 2. Quality Attributes & Scenarios

이 섹션은 RTB 입찰 시스템에서 중요하게 다뤄야 할 품질 속성을 정의한다. 품질 속성은 추상적인 목표가 아니라, RTB 도메인의 제약과 시스템 구조를 연결하는 기준이다.

### 2.1 Priority Quality Attributes

| 우선순위 | 품질 속성 | 비즈니스/도메인 이유 | 아키텍처 관점 |
|---|---|---|---|
| 1 | 제한 시간 내 응답 | RTB 경매에서는 응답 제한 시간이 지나면 bid의 가격이 높더라도 경매에 사용할 수 없다. | deadline을 시스템의 중요한 경계로 보고, 제한 시간 이후 도착한 응답은 낙찰 후보에서 제외한다. |
| 2 | 낮은 지연 시간 | 광고 요청 처리 시간이 길어질수록 auction deadline을 넘길 가능성이 커지고, 유효한 bid가 있어도 사용할 수 없게 된다. | 입찰 요청 처리부터 낙찰자 결정까지의 hot path를 작게 유지하고, p95/p99 latency를 측정한다. |
| 3 | 낙찰 결과의 일관성 | 잘못된 bid가 낙찰되면 광고 품질, 정산, 시스템 신뢰성에 문제가 생길 수 있다. | winner selection 전에 bid 유효성을 검증하고, invalid bid는 낙찰 후보에서 제외한다. |
| 4 | 실패 격리 | 일부 Bidder의 timeout이나 잘못된 응답이 전체 auction 실패로 번지면 안 된다. | Bidder별 응답 결과를 분리해 처리하고, 유효한 bid가 없으면 no-winner를 정상 결과로 반환한다. |
| 5 | 관측 가능성 | 지연, timeout, invalid bid가 발생했을 때 원인을 설명할 수 있어야 성능 분석과 개선이 가능하다. | latency, deadline 내 응답률, timeout 수, invalid bid 수, no-winner 비율을 측정 가능한 지표로 남긴다. |

### 2.2 Quality Attribute Scenarios

#### QA-001: 일부 Bidder가 제한 시간 안에 응답하지 않는 경우

- 도메인 상황: RTB 경매에서는 여러 Bidder가 동시에 응답할 수 있지만, 모든 Bidder가 제한 시간 안에 응답한다는 보장은 없다.
- 시스템 응답: 제한 시간 안에 응답하지 않은 Bidder는 timeout으로 분류하고, deadline 안에 도착한 응답만으로 winner 또는 no-winner를 결정한다.
- 관찰 지표: deadline 내 응답률, auction latency p95/p99, bidder timeout 수

#### QA-002: 제한 시간 이후 가장 높은 bid가 도착하는 경우

- 도메인 상황: 가장 높은 가격을 제시한 bid라도 deadline 이후 도착하면 RTB 경매에서는 사용할 수 없다.
- 시스템 응답: 해당 bid를 late bid로 분류하고 낙찰 후보에서 제외한다.
- 관찰 지표: late bid 수, winner decision log

#### QA-003: 잘못된 BidResponse가 도착하는 경우

- 도메인 상황: Bidder가 반환한 응답이 원 요청의 impression과 일치하지 않거나, bidfloor보다 낮은 가격을 제시할 수 있다.
- 시스템 응답: 잘못된 BidResponse는 invalid bid로 분류하고 낙찰 후보에서 제외한다.
- 관찰 지표: invalid bid 수, invalid reason

#### QA-004: 유효한 bid가 하나도 없는 경우

- 도메인 상황: 모든 Bidder가 no-bid를 반환하거나 timeout될 수 있다.
- 시스템 응답: 시스템은 이를 장애로 보지 않고 no-winner 결과를 반환한다.
- 관찰 지표: no-winner 비율, no-bid 수, timeout 수

#### QA-005: 동시 요청 수 또는 Bidder 수가 증가하는 경우

- 도메인 상황: 광고 입찰 시스템은 요청량 증가와 Bidder 수 증가에 따라 응답 지연이 커질 수 있다.
- 시스템 응답: 시스템은 동시 요청 수와 Bidder 수 변화에 따른 latency와 deadline 내 응답률을 측정할 수 있어야 한다.
- 관찰 지표: auction latency p95/p99, deadline 내 응답률, observed throughput

## 3. Architectural Views

### 3.1 C1: System Context View

### 3.2 C2: Container View

## 4. Runtime Architecture

### 4.1 Core Runtime Flow

### 4.2 Performance-Critical Path

### 4.3 Failure Boundaries

## 5. Cross-Cutting Concerns

### 5.1 Data & Serialization Strategy

### 5.2 Observability & Measurement Strategy

## 6. Deferred Decisions
