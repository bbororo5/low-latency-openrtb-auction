# Architecture: OpenRTB 기반 저지연 RTB 입찰 시스템

이 문서는 PRD의 요구사항을 바탕으로 시스템의 주요 품질 기준, 시스템 경계, 실행 흐름을 정의한다.

API 세부 계약, 지원할 광고 요청 필드, 내부 컴포넌트 상세 설계, 데이터 모델, 테스트 구현 방식은 Tech Spec에서 다룬다.

## 1. Introduction, Context & Scope

### 1.1 Purpose

이 문서는 PRD에서 정의한 RTB 입찰 시스템을 어떤 구조로 바라볼지 정리한다.

여기서는 시스템이 OpenRTB 흐름에서 어떤 역할을 축소해서 다루는지, 어디까지를 시스템 범위로 볼지, 이후 품질 기준과 실행 흐름을 어떤 관점에서 다룰지 정의한다.

API 세부 계약, 지원할 광고 요청 필드, 내부 컴포넌트 상세 설계, 데이터 모델, 테스트 구현 방식은 Tech Spec에서 다룬다.

### 1.2 OpenRTB Context

OpenRTB는 광고 지면을 판매하는 쪽과 광고를 구매하는 쪽이 실시간 입찰 요청과 응답을 주고받기 위한 표준 규약이다.

이 프로젝트는 OpenRTB 생태계 전체를 구현하지 않는다. 대신 입찰 요청이 들어오고, 입찰 여부를 판단하며, 유효한 입찰 응답 중 낙찰자를 결정하는 핵심 흐름을 축소해서 다룬다.

이 프로젝트는 실제 SSP와 DSP 전체를 구현하지 않는다. 대신 광고 판매 측의 경매 흐름과 광고 구매 측의 입찰 판단 흐름을 작게 모델링해, OpenRTB 요청/응답 기반 경매 핵심 경로를 검증한다.

실제 시스템에서 판매 측은 광고 지면을 판매하고 경매를 수행하며, 구매 측은 광고주를 대신해 입찰 여부와 가격을 결정한다.

### 1.3 Scope & Boundaries

이 아키텍처는 RTB 입찰 시스템의 핵심 실행 경로만 다룬다.

포함하는 범위:

- OpenRTB BidRequest 수신
- BidRequest 검증
- 입찰 판단에 필요한 요청 정보 해석
- 입찰 여부 판단
- BidResponse 생성
- 여러 BidResponse 수집
- 낙찰/낙찰 없음 결정
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

제한 시간 내 응답, 응답 시간 초과(timeout), 잘못된 입찰 응답(invalid bid), 늦게 도착한 입찰 응답(late bid), 관찰 가능성처럼 구조에 영향을 주는 품질 기준은 다음 섹션에서 정의한다.

## 2. Quality Attributes & Scenarios

이 섹션은 RTB 입찰 시스템에서 중요하게 다뤄야 할 품질 기준을 정의한다. 품질 기준은 추상적인 목표가 아니라, RTB 도메인의 제약과 시스템 구조를 연결하는 기준이다.

### 2.1 Priority Quality Attributes

| 우선순위 | 품질 기준 | 비즈니스/도메인 이유 | 아키텍처 관점 |
|---|---|---|---|
| 1 | 제한 시간 내 응답 | RTB 경매에서는 응답 제한 시간이 지나면 입찰가가 높더라도 경매에 사용할 수 없다. | 제한 시간을 시스템의 중요한 경계로 보고, 제한 시간 이후 도착한 응답은 낙찰 후보에서 제외한다. |
| 2 | 낮은 지연 시간 | 광고 요청 처리 시간이 길어질수록 응답 제한 시간을 넘길 가능성이 커지고, 유효한 입찰 응답이 있어도 사용할 수 없게 된다. | 입찰 요청 처리부터 낙찰자 결정까지의 핵심 실행 경로를 작게 유지하고, p95/p99 응답 시간을 측정한다. |
| 3 | 낙찰 결과의 일관성 | 잘못된 입찰 응답이 낙찰되면 유효하지 않은 광고가 선택되어 시스템 신뢰성이 떨어질 수 있다. | 낙찰자 결정 전에 입찰 응답을 검증하고, 잘못된 응답은 낙찰 후보에서 제외한다. |
| 4 | 실패 격리 | 일부 입찰 참여자(Bidder)의 응답 시간 초과나 잘못된 응답이 전체 경매 실패로 번지면 안 된다. | 입찰 참여자별 응답 결과를 분리해 처리하고, 유효한 입찰 응답이 없으면 낙찰 없음 결과를 정상적으로 반환한다. |
| 5 | 관찰 가능성 | 지연, 응답 시간 초과, 잘못된 입찰 응답이 발생했을 때 원인을 설명할 수 있어야 성능 분석과 개선이 가능하다. | 응답 시간, 제한 시간 내 응답률, 응답 시간 초과(timeout) 수, 잘못된 입찰 응답(invalid bid) 수, 낙찰 없음(no-winner) 비율을 측정 가능한 지표로 남긴다. |

### 2.2 Quality Attribute Scenarios

#### QA-001: 일부 입찰 참여자가 제한 시간 안에 응답하지 않는 경우

- 도메인 상황: RTB 경매에서는 여러 입찰 참여자가 동시에 응답할 수 있지만, 모든 참여자가 제한 시간 안에 응답한다는 보장은 없다.
- 시스템 응답: 제한 시간 안에 응답하지 않은 참여자는 응답 시간 초과(timeout)로 분류하고, 제한 시간 안에 도착한 응답만으로 낙찰 또는 낙찰 없음을 결정한다.
- 관찰 지표: 제한 시간 내 응답률, 전체 경매 응답 시간 p95/p99, 입찰 참여자 timeout 수

#### QA-002: 제한 시간 이후 가장 높은 입찰 응답이 도착하는 경우

- 도메인 상황: 가장 높은 가격을 제시한 입찰 응답이라도 제한 시간 이후 도착하면 RTB 경매에서는 사용할 수 없다.
- 시스템 응답: 해당 응답을 늦게 도착한 입찰 응답(late bid)으로 분류하고 낙찰 후보에서 제외한다.
- 관찰 지표: late bid 수, 낙찰 결정 로그

#### QA-003: 잘못된 BidResponse가 도착하는 경우

- 도메인 상황: 입찰 응답이 원 요청의 광고 노출 기회와 일치하지 않거나, 최소 입찰가보다 낮은 가격을 제시할 수 있다.
- 시스템 응답: 잘못된 BidResponse는 잘못된 입찰 응답(invalid bid)으로 분류하고 낙찰 후보에서 제외한다.
- 관찰 지표: invalid bid 수, invalid reason

#### QA-004: 유효한 입찰 응답이 하나도 없는 경우

- 도메인 상황: 모든 입찰 참여자가 입찰하지 않거나 제한 시간 안에 응답하지 못할 수 있다.
- 시스템 응답: 시스템은 이를 장애로 보지 않고 낙찰 없음(no-winner) 결과를 반환한다.
- 관찰 지표: no-winner 비율, no-bid 수, timeout 수

#### QA-005: 동시 요청 수 또는 입찰 참여자 수가 증가하는 경우

- 도메인 상황: 광고 입찰 시스템은 요청량 증가와 입찰 참여자 수 증가에 따라 응답 지연이 커질 수 있다.
- 시스템 응답: 시스템은 동시 요청 수와 입찰 참여자 수 변화에 따른 응답 시간과 제한 시간 내 응답률을 측정할 수 있어야 한다.
- 관찰 지표: 전체 경매 응답 시간 p95/p99, 제한 시간 내 응답률, 관찰된 처리량

## 3. Architectural Views

이 섹션은 C4 모델의 C1/C2 관점으로 시스템 경계와 큰 실행 단위를 설명한다.

C1은 외부 역할과 시스템 경계를 보여준다. C2는 시스템 내부의 큰 실행 단위와 데이터 저장 경계를 보여준다.

이 문서에서 말하는 Container는 C4 모델의 실행 단위를 의미하며, Docker/Kubernetes 컨테이너를 의미하지 않는다. 실제 물리 실행 단위, 배포 방식, 기술 스택은 Tech Spec 또는 ADR에서 결정한다.

### 3.1 C1: System Context View

![C1 System Context](../assets/diagrams/c1-system-context.svg)

`RTB Bidding System`은 OpenRTB 기반 입찰 요청을 받아 입찰 여부 판단, BidResponse 생성/수집, 낙찰자 결정을 수행하는 시스템이다.

`Performance Test Runner / Mock SSP`는 실제 광고 판매 플랫폼을 구현하지 않고, OpenRTB BidRequest와 부하 요청을 생성하는 외부 역할이다.

`Campaign Data Seeder`는 테스트용 광고 캠페인과 타겟팅 데이터를 시스템에 준비하는 외부 역할이다. 실제 운영 백오피스나 광고주 캠페인 관리 시스템은 이 프로젝트 범위에 포함하지 않는다.

<details>
<summary>Mermaid source</summary>

```mermaid
flowchart LR
    tester["Performance Test Runner / Mock SSP"]
    seeder["Campaign Data Seeder"]
    system["RTB Bidding System"]

    tester -->|"OpenRTB BidRequest / load test traffic"| system
    system -->|"Winner / No-Winner Result"| tester

    seeder -->|"Campaign / targeting test data"| system
```

</details>

### 3.2 C2: Container View

![C2 Container View](../assets/diagrams/c2-container-view.svg)

`RTB Bidding Application`은 입찰 요청 처리부터 낙찰자 결정까지의 핵심 실행 경로를 담당한다.

주요 책임:

- OpenRTB BidRequest 수신 및 검증
- 입찰 판단에 필요한 요청 정보 해석
- 입찰 여부 판단
- BidResponse 생성 및 수집
- 잘못된 입찰 응답(invalid bid), 늦게 도착한 입찰 응답(late bid), 응답 시간 초과(timeout) 분류
- 낙찰/낙찰 없음 결정
- 응답 시간(latency), 응답 시간 초과(timeout), 잘못된 입찰 응답(invalid bid), 낙찰 없음(no-winner) 지표 노출

`Campaign Data Store`는 입찰 여부 판단에 필요한 광고 캠페인과 타겟팅 데이터를 제공한다.

이 문서에서는 저장소의 구체적인 구현 기술을 확정하지 않는다. 초기 데이터 적재 방식, 저장소 종류, 인메모리 캐시 전략은 Tech Spec 또는 ADR에서 결정한다.

실제 OpenRTB 생태계에서 DSP/Bidder는 광고 구매 측 외부 시스템이다. 이 프로젝트는 RTB 입찰 핵심 경로를 작게 검증하기 위해 Bidder 역할을 `RTB Bidding Application` 내부 논리 책임으로 축소해서 다룬다.

<details>
<summary>Mermaid source</summary>

```mermaid
flowchart TB
    tester["Performance Test Runner / Mock SSP"]
    seeder["Campaign Data Seeder"]

    subgraph boundary["RTB Bidding System"]
        app["RTB Bidding Application"]
        store[("Campaign Data Store")]
    end

    tester -->|"OpenRTB BidRequest"| app
    app -->|"Winner / No-Winner Result"| tester

    seeder -->|"Seed campaign / targeting data"| store
    app -->|"Read campaign / targeting data"| store
```

</details>

## 4. Runtime Architecture

이 섹션은 OpenRTB BidRequest 한 건이 들어왔을 때, 시스템이 낙찰 또는 낙찰 없음을 결정하기까지의 실행 흐름을 설명한다.

### 4.1 Core Runtime Flow

1. `Performance Test Runner / Mock SSP`가 OpenRTB BidRequest를 보낸다.
2. `RTB Bidding Application`은 요청 형식과 필수 필드를 검증한다.
3. 요청에서 광고 노출 기회, 최소 입찰가, 지면, 디바이스, 사용자 등 입찰 판단에 필요한 정보를 추출한다.
4. 사전에 준비된 데이터에서 요청 조건에 맞는 후보 광고 캠페인을 찾는다.
5. 후보 광고 캠페인을 기준으로 입찰 여부 판단을 수행한다.
6. 생성된 BidResponse를 수집하고, timeout, late bid, invalid bid를 낙찰 후보에서 제외한다.
7. 유효한 입찰 응답 중 낙찰 기준에 맞는 응답을 낙찰자(winner)로 선택한다.
8. 유효한 입찰 응답이 없으면 낙찰 없음(no-winner)을 정상 결과로 반환한다.
9. latency, timeout, invalid bid, no-winner 지표를 기록한다.

### 4.2 Performance-Critical Path

성능상 중요한 경로는 BidRequest 수신부터 낙찰/낙찰 없음 결정까지다.

성능 핵심 경로(hot path)에 포함되는 작업:

- BidRequest parsing
- 필수 필드 validation
- 입찰 판단에 필요한 request context 생성
- 후보 광고 캠페인 조회
- 입찰 여부 판단
- BidResponse validation
- 낙찰/낙찰 없음 결정
- 핵심 지표 기록

성능 핵심 경로(hot path)에서 제외하는 작업:

- campaign 원본 관리
- 광고 심사와 운영 백오피스
- reporting 집계
- billing
- impression/click/conversion tracking
- 외부 DSP/SSP 네트워크 연동

이 프로젝트는 원본 광고 캠페인 데이터를 매 요청마다 조회하지 않는다. 실시간 입찰 판단에 필요한 캠페인과 타겟팅 데이터는 사전에 준비되어 있다고 보고, BidRequest 이후에는 해당 데이터를 기반으로 후보 탐색과 낙찰 판단을 수행한다.

입찰 판단에 필요한 데이터를 어디에서 가져오고 어떤 형태로 준비할지는 이 문서에서 확정하지 않는다. 초기 구현에서는 테스트 데이터를 사전에 적재하는 방식으로 시작하고, 구체적인 데이터 구조와 갱신 전략은 Tech Spec 또는 ADR에서 결정한다.

### 4.3 Failure Boundaries

RTB 경매에서는 일부 실패가 전체 장애를 의미하지 않는다. 시스템은 실패 유형을 분리해 처리한다.

| 상황 | 처리 방식 |
|---|---|
| BidRequest 형식이 잘못됨 | 요청 실패로 처리한다. |
| 입찰 참여자가 입찰하지 않음(no-bid) | 정상 응답으로 처리하되 낙찰 후보에서 제외한다. |
| 입찰 참여자가 제한 시간 안에 응답하지 않음 | 응답 시간 초과(timeout)로 분류하고 낙찰 후보에서 제외한다. |
| BidResponse가 제한 시간 이후 도착 | 늦게 도착한 입찰 응답(late bid)으로 분류하고 낙찰 후보에서 제외한다. |
| BidResponse가 원 요청과 맞지 않음 | 잘못된 입찰 응답(invalid bid)으로 분류하고 낙찰 후보에서 제외한다. |
| 유효한 입찰 응답이 없음 | 낙찰 없음(no-winner)을 정상 결과로 반환한다. |

핵심 원칙은 제한 시간 안에 검증된 입찰 응답만 낙찰자 결정에 사용한다는 것이다.

## 5. Runtime Measurement Strategy

이 섹션은 RTB 핵심 실행 경로의 지연과 실패 원인을 설명하기 위해 무엇을 측정할지 정의한다.

### 5.1 Key Metrics

| 지표 | 의미 |
|---|---|
| p95/p99 latency | 대부분의 요청과 느린 요청의 응답 시간 |
| deadline compliance | 제한 시간 안에 낙찰/낙찰 없음이 결정된 비율 |
| observed throughput | 테스트 환경에서 관찰된 처리량 |
| timeout count | 제한 시간 안에 응답하지 못한 입찰 참여자 수 |
| late bid count | 제한 시간 이후 도착해 제외된 입찰 응답 수 |
| invalid bid count | 검증 실패로 제외된 입찰 응답 수 |
| no-winner rate | 유효한 입찰 응답이 없어 낙찰자가 없는 비율 |

### 5.2 Measurement Points

- 전체 경매 응답 시간
- BidRequest parsing/validation
- 후보 광고 캠페인 조회
- 입찰 여부 판단
- BidResponse validation
- 낙찰자 결정

### 5.3 Interpretation Principles

- 절대 성능 수치를 과장하지 않고, 실행 환경을 함께 기록한다.
- 처리량보다 p95/p99 latency와 deadline compliance를 우선 해석한다.
- 성능 평가는 baseline과 개선 후 결과를 비교한다.
- timeout, late bid, invalid bid, no-bid, no-winner를 하나의 실패로 묶지 않는다.

## 6. Deferred Decisions

이 섹션은 현재 아키텍처 단계에서 확정하지 않고, 이후 Tech Spec 또는 ADR에서 결정할 항목을 정리한다.

| 항목 | 지금 확정하지 않는 이유 | 결정 시점 |
|---|---|---|
| 경매에 참여시킬 광고 요청 범위 | 배너, 동영상, 네이티브처럼 어떤 광고 형식을 먼저 지원할지 아직 정하지 않는다. 이 결정은 요청 검증 규칙, 예시 payload, 테스트 케이스와 함께 다뤄야 한다. | Tech Spec |
| 입찰 판단 데이터 출처 | 원본 campaign 데이터를 매 요청마다 조회하지 않는다는 원칙만 정의한다. 입찰 판단에 필요한 데이터를 어디에서 가져오고 어떤 형태로 준비할지는 타겟팅 조건, 캠페인 수, 갱신 방식, 인스턴스 구성에 따라 달라진다. | Tech Spec / ADR |
| 레이턴시 목표와 테스트 환경 | 배너, 동영상처럼 광고 형식마다 허용 가능한 지연 시간이 다를 수 있다. 따라서 단일 목표 수치를 먼저 정하지 않고, 테스트 시나리오와 실행 환경을 정한 뒤 확정한다. | Performance Test Plan |
