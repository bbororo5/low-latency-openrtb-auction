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

### 2.1 Priority Quality Attributes

### 2.2 Quality Attribute Scenarios

### 2.3 Trade-off Notes

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
