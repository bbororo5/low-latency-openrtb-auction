# PRD: OpenRTB 기반 저지연 RTB 입찰 시스템

## 1. Overview

이 프로젝트는 OpenRTB 2.6의 입찰 요청(`BidRequest`) / 입찰 응답(`BidResponse`) 흐름을 기반으로, 실시간 광고 입찰 요청을 처리하고 제한 시간 안에 낙찰자와 낙찰가를 결정하는 저지연 RTB 입찰 시스템을 정의한다.

프로젝트의 범위는 광고 플랫폼 전체가 아니라 **입찰 요청 처리부터 입찰 여부와 입찰가 판단, 입찰 응답 생성, 낙찰자/낙찰가 결정까지의 성능 핵심 경로(hot path)**다.

실제 광고 렌더링, 노출/클릭 추적, 과금, 리포팅, 광고 운영 백오피스는 포함하지 않는다.

이 문서는 시스템이 해결해야 할 문제, 기능 요구사항, 실패 케이스, 성능 측정 기준을 정의한다. 기술 스택, 세부 아키텍처, 배포 방식, 최적화 구현은 Tech Spec과 ADR에서 다룬다.

## 2. Problem Statement

RTB 환경에서는 하나의 광고 요청이 제한된 시간 안에 처리되어야 한다. 요청을 받은 시스템은 광고 슬롯, 지면, 디바이스, 최소 입찰가 등의 정보를 해석하고, 여러 경량 DSP의 응답을 수집한 뒤 유효한 입찰 중 낙찰자와 낙찰가를 결정해야 한다.

하지만 입찰 참여자는 항상 정상 응답을 반환하지 않는다. 어떤 참여자는 입찰하지 않음(`no-bid`)을 반환할 수 있고, 어떤 참여자는 제한 시간을 초과하거나 잘못된 응답을 반환할 수 있다. 제한 시간 이후 도착한 입찰 응답은 가격이 높더라도 낙찰 후보에 포함되면 안 된다.

이 프로젝트의 핵심 문제는 다음과 같다.

> 대용량 입찰 요청 상황을 가정하고, 제한 시간 안에서 입찰 여부와 입찰가 판단, 여러 입찰 응답(`BidResponse`) 수집을 수행한 뒤, 응답 시간 초과(timeout), 잘못된 입찰 응답(invalid bid), 늦게 도착한 입찰 응답(late bid)을 제외하고 유효한 입찰 응답 중 낙찰자와 낙찰가를 결정하는 것.

## 3. Scope in OpenRTB Ecosystem

OpenRTB는 광고 지면을 판매하는 쪽과 광고를 구매하는 쪽이 실시간 입찰 요청과 응답을 주고받기 위한 표준 규약이다.

이 프로젝트는 운영 수준의 SSP와 DSP 전체를 구현하지 않는다. 대신 게시자(Publisher), 광고 판매 측인 경량 SSP, 광고 구매 측인 경량 DSP, 광고주(Advertiser)의 관계를 단순화해 구현한다.

기본 흐름은 `Publisher -> 경량 SSP <-> 경량 DSP <- Advertiser`로 본다. 본 프로젝트는 이 중 광고 판매 측의 경매 흐름과 광고 구매 측의 입찰 판단 흐름을 작게 구현해, OpenRTB 요청/응답 기반 경매 핵심 경로를 검증한다.

포함하는 논리 역할:

- OpenRTB 요청 수신 및 검증
- 경량 DSP의 입찰 여부 판단
- 경량 DSP의 입찰가 결정
- 간단한 광고 타겟팅 및 후보 광고 캠페인 매칭
- 등록된 여러 경량 DSP로 BidRequest 전달
- BidResponse 생성
- 여러 BidResponse 수집
- 낙찰자와 낙찰가 결정

제외하는 역할:

- 실제 매체사 연동
- 실제 외부 DSP 연동
- 어떤 DSP에 요청을 보낼지 고르는 라우팅 최적화
- 광고 운영 백오피스
- 광고 노출/클릭/전환 측정
- 정산 및 과금
- 리포팅 시스템

## 4. Goals

이 프로젝트는 다음 목표를 가진다.

- OpenRTB 2.6의 핵심 입찰 요청/응답 흐름을 기반으로 한다.
- 입찰 요청(`BidRequest`)을 수신하고 필수 필드를 검증한다.
- 광고 슬롯, 광고 타입, 최소 입찰가 등 입찰 판단에 필요한 정보를 해석한다.
- 경량 DSP가 요청을 평가해 입찰 여부와 입찰가를 결정한다.
- 여러 경량 DSP 응답을 제한 시간 안에 수집한다.
- 정상 입찰 응답, 입찰하지 않음(no-bid), 응답 시간 초과(timeout), 잘못된 응답(invalid response), 늦게 도착한 입찰 응답(late bid)을 구분한다.
- 유효한 입찰 응답 중 낙찰자와 낙찰가를 결정한다.
- 유효한 입찰 응답이 없는 경우 no-winner 결과를 반환한다.
- 동시 요청 수와 경량 DSP 수 증가가 응답 시간(latency)에 미치는 영향을 측정한다.
- p95/p99 latency와 제한 시간 내 응답률을 주요 성능 척도로 사용한다.
- 성능 개선은 추측이 아니라 측정 결과를 기반으로 설명한다.

## 5. Non-Goals

이 프로젝트는 다음을 목표로 하지 않는다.

- 전체 광고 서버 구현
- 실제 광고 렌더링
- 노출 추적(impression tracking)
- 클릭 추적(click tracking)
- 전환 추적(conversion tracking)
- billing
- reporting
- 광고 운영 백오피스
- OpenRTB 2.6 전체 스펙 구현
- 실제 외부 DSP/SSP 연동
- 운영 수준의 광고 거래소 구현
- Kubernetes 기반 운영 검증
- 클라우드/무료 배포 환경에서의 절대 성능 증명

## 6. Actors and Core Concepts

`입찰 요청 생성기(Auction Client)`

입찰 요청을 보내는 주체다. 실제 매체사나 SSP를 구현하지 않고, 이 프로젝트에서는 테스트 가능한 요청 생성 주체로 간주한다.

`Publisher`

광고 지면을 가진 쪽이다. 이 프로젝트에서는 실제 게시자 서비스를 구현하지 않고, 입찰 요청 생성기(Auction Client)가 광고 요청을 발생시키는 역할로 대신한다.

`경량 SSP`

게시자의 광고 지면을 판매하는 쪽을 작게 구현한 역할이다. BidRequest를 여러 경량 DSP에게 전달하고, 제한 시간 안에 도착한 BidResponse를 수집해 낙찰자와 낙찰가를 결정한다. OpenRTB 문서에서는 이 역할을 SSP 또는 Exchange로 표현한다.

`경량 DSP`

광고주를 대신해 광고 지면을 구매하는 쪽을 작게 구현한 역할이다. BidRequest를 평가해 입찰 여부와 입찰가를 결정하고 BidResponse 또는 no-bid를 반환한다. OpenRTB 문서에서는 이 역할을 DSP 또는 Bidder로 표현한다.

`Advertiser`

광고비를 내고 캠페인을 운영하는 쪽이다. 이 프로젝트에서는 실제 광고주 시스템을 구현하지 않고, 테스트용 캠페인 데이터로만 모델링한다.

`RTB 입찰 시스템`

BidRequest 전달, 경량 DSP의 입찰 여부 판단, BidResponse 수집, 낙찰자/낙찰가 결정을 수행하는 시스템이다.

`입찰 참여자(Bidder)`

BidRequest를 평가하고 입찰 응답 또는 no-bid를 반환하는 주체다. 이 프로젝트에서는 이 역할을 경량 DSP로 구현한다.

`BidRequest`

OpenRTB 기반 입찰 요청이다. 광고 노출 기회, 광고 타입, 지면, 디바이스, 최소 입찰가, 응답 제한 시간 등의 정보를 포함한다.

`BidResponse`

경량 DSP가 특정 광고 노출 기회에 대해 입찰 의사와 입찰가를 표현하는 응답이다.

`No-Bid`

경량 DSP가 해당 요청에 입찰하지 않는 결과다.

`Auction Deadline`

경량 DSP 응답 수집과 낙찰자/낙찰가 결정을 완료해야 하는 제한 시간이다.

`Late Bid`

제한 시간 이후 도착한 입찰 응답이다. 가격이 높더라도 낙찰 후보에 포함하지 않는다.

`Invalid Bid`

필수 정보 누락, 잘못된 광고 노출 기회 ID, 최소 입찰가 미만 가격 등으로 인해 낙찰 후보에 포함할 수 없는 입찰 응답이다.

`Winner`

유효한 입찰 응답 중 경매 규칙에 따라 선택된 최종 낙찰 응답이다.

`Auction Price`

경매 규칙에 따라 결정된 낙찰가다. 이 프로젝트는 Tech Spec에서 정의한 First Price Auction을 기본 경매 규칙으로 사용한다.

`No-Winner`

유효한 입찰 응답이 없어 낙찰자를 결정하지 못한 결과다. 이 프로젝트에서는 장애가 아니라 정상 결과로 다룬다.

## 7. Main Scenario

1. 입찰 요청 생성기(Auction Client)가 OpenRTB BidRequest를 보낸다.
2. 시스템은 BidRequest의 필수 필드를 검증한다.
3. 시스템은 광고 노출 기회, 광고 타입, 최소 입찰가, 지면/디바이스 정보를 해석한다.
4. 경량 SSP는 등록된 여러 경량 DSP에게 동일한 BidRequest를 전달한다.
5. 각 경량 DSP는 요청과 자신의 캠페인 데이터를 평가한다.
6. 각 경량 DSP는 입찰가가 담긴 BidResponse 또는 no-bid를 반환한다.
7. 시스템은 제한 시간 안에 도착한 응답만 수집한다.
8. 시스템은 잘못된 입찰 응답(invalid bid)과 늦게 도착한 입찰 응답(late bid)을 제외한다.
9. 시스템은 유효한 입찰 응답 중 낙찰자와 낙찰가를 결정한다.
10. 낙찰자가 있으면 낙찰 결과(winner result)를 반환한다.
11. 유효한 입찰 응답이 없으면 낙찰 없음 결과(no-winner result)를 반환한다.

## 8. Functional Requirements

`FR-001: BidRequest 수신`

시스템은 OpenRTB 2.6 기반의 제한된 BidRequest를 받을 수 있어야 한다.

`FR-002: BidRequest 검증`

시스템은 필수 필드가 누락되거나 처리할 수 없는 요청을 invalid request로 분류해야 한다.

`FR-003: 광고 요청 정보 해석`

시스템은 광고 노출 기회, 광고 타입, 광고 크기, 최소 입찰가, 지면 또는 앱 정보를 입찰 판단에 사용할 수 있어야 한다.

`FR-004: 경량 DSP 입찰 여부 판단`

경량 DSP는 요청 정보를 기반으로 입찰 여부와 입찰가를 결정할 수 있어야 한다.

`FR-005: BidResponse 생성`

경량 DSP는 입찰 가능한 경우 지원 범위에 맞는 BidResponse를 생성해야 한다.

`FR-006: 여러 경량 DSP 요청 전달 및 응답 수집`

경량 SSP는 등록된 모든 경량 DSP에게 동일한 BidRequest를 전달하고, 여러 경량 DSP 응답을 수집할 수 있어야 한다.

`FR-007: 응답 제한 시간 적용`

시스템은 응답 제한 시간 안에 도착한 응답만 낙찰 후보에 포함해야 한다.

`FR-008: 경량 DSP 결과 분류`

시스템은 경량 DSP 결과를 정상 입찰 응답, 입찰하지 않음(no-bid), 응답 시간 초과(timeout), 잘못된 응답(invalid response), 늦게 도착한 입찰 응답(late bid)으로 구분해야 한다.

`FR-009: BidResponse 검증`

시스템은 BidResponse가 원 요청의 광고 노출 기회와 일치하고, 최소 입찰가 등 기본 제약을 만족하는지 검증해야 한다.

`FR-010: 낙찰자와 낙찰가 결정`

시스템은 유효한 입찰 응답 중 경매 규칙에 따라 낙찰자(winner)와 낙찰가(auction price)를 결정해야 한다.

`FR-011: No-Winner 결과 반환`

유효한 입찰 응답이 없으면 시스템은 낙찰 없음(no-winner) 결과를 반환해야 한다.

`FR-012: 결과 설명 가능성`

시스템은 테스트와 디버깅을 위해 winner, no-winner, timeout, invalid bid 등 주요 판단 결과를 확인할 수 있어야 한다.

## 9. Failure and Edge Cases

다음 케이스를 요구사항 수준에서 다룬다.

- BidRequest에 광고 노출 기회가 없음
- 지원하지 않는 광고 타입
- 최소 입찰가가 누락되거나 비정상 값임
- 모든 경량 DSP가 no-bid
- 모든 경량 DSP가 timeout
- 일부 경량 DSP만 timeout
- 경량 DSP가 malformed response 반환
- BidResponse의 광고 노출 기회 ID가 원 요청과 다름
- 입찰가가 최소 입찰가보다 낮음
- 입찰가가 0 이하
- 제한 시간 이후 가장 높은 입찰 응답이 도착함
- 유효한 입찰 응답이 여러 개 존재함
- 동일 가격 입찰 응답이 여러 개 존재함
- 경량 DSP 수가 증가함
- 동시 요청 수가 증가함

각 케이스의 구체적인 구현 방식은 Tech Spec에서 정의한다. PRD에서는 시스템이 어떤 결과를 내야 하는지만 정의한다.

## 10. Performance and Measurement Requirements

이 프로젝트의 성능 요구사항은 특정 배포 환경에서 절대 RPS를 달성하는 것이 아니다. 실행 환경에 따라 처리량은 크게 달라질 수 있으므로, 처리량은 고정 목표가 아니라 통제된 환경에서 관찰되는 지표로 다룬다.

성능 검증은 문서화된 로컬 환경에서 수행한다. 목표는 보편적인 처리량 수치를 증명하는 것이 아니라, 같은 조건에서 동시 요청 수, 경량 DSP 수, 응답 지연이 latency와 제한 시간 내 응답률에 어떤 영향을 주는지 측정하는 것이다.

### Primary Metrics

- 전체 경매 응답 시간 p95/p99
- 제한 시간 내 응답률

### Secondary Metrics

- 관찰된 처리량
- 경량 DSP timeout 비율
- invalid bid 비율
- no-winner 비율

### Diagnostic Metrics

- CPU usage
- memory usage
- active threads
- connection usage
- GC pause

### Performance Requirements

`PERF-001: Latency 측정`

시스템은 전체 경매 응답 시간을 p50, p95, p99로 측정할 수 있어야 한다.

`PERF-002: Deadline 내 응답률 측정`

시스템은 설정된 응답 제한 시간 안에 결과를 반환한 비율을 측정할 수 있어야 한다.

`PERF-003: Observed Throughput 기록`

처리량은 고정 목표가 아니라, 특정 응답 시간과 제한 시간 조건에서 관찰된 결과로 기록한다.

`PERF-004: 동시 요청 수 변화 측정`

성능 테스트는 동시 요청 수 증가에 따른 응답 시간과 제한 시간 내 응답률 변화를 측정해야 한다.

`PERF-005: 경량 DSP 수 변화 측정`

성능 테스트는 경량 DSP 수 증가에 따른 응답 시간과 제한 시간 내 응답률 변화를 측정해야 한다.

`PERF-006: 테스트 환경 기록`

성능 테스트 결과에는 실행 환경, 테스트 조건, 요청 수, 동시성 수준, 경량 DSP 수가 함께 기록되어야 한다.

## 11. Assumptions

PRD는 다음 가정을 둔다.

- OpenRTB 2.6 전체가 아니라 제한된 요청/응답 범위를 사용한다.
- 하나의 BidRequest는 최소 하나의 광고 노출 기회를 가진다.
- 광고 타입은 제한적으로 다룬다.
- 실제 광고 렌더링과 과금은 다루지 않는다.
- 실제 외부 DSP/SSP와 통신하지 않는다.
- 어떤 DSP에 요청을 보낼지 고르는 라우팅 최적화는 다루지 않는다.
- 등록된 모든 경량 DSP에게 동일한 BidRequest를 전달한다.
- 여러 경량 DSP는 동일한 구현체를 서로 다른 설정과 캠페인 데이터로 실행해 모델링할 수 있다.
- 각 경량 DSP는 지원 광고 타입, 응답 지연, 입찰가, 실패 모드가 다를 수 있다.
- 성능 측정은 로컬 통제 환경에서 수행한다.
- 구현 세부 기술은 초기 구현 과정에서 최소 범위로 선택한다.
- 경량 SSP와 경량 DSP는 별도 애플리케이션으로 분리한다.

## 12. Milestones

`M1: Project Goal Finalization`

프로젝트 목표와 범위를 확정한다.

`M2: PRD Finalization`

기능 요구사항, 실패 케이스, 성능 측정 기준을 확정한다.

`M3: Architecture`

시스템 경계, 품질 기준, 실행 흐름을 정의한다.

`M4: Tech Spec`

구현 구조, 기술 스택, API 계약, 테스트 전략을 결정한다.

`M5: Contract and Test Fixture Setup`

OpenRTB 요청/응답 DTO, 지원 필드 검증 규칙, 예시 BidRequest, 예시 BidResponse, 테스트용 캠페인 데이터를 준비한다.

`M6: Lightweight DSP Flow`

경량 DSP의 광고 타입별 요청 해석, 캠페인 매칭, bid/no-bid 결정, 입찰가 산정, BidResponse 생성을 구현한다.

`M7: Lightweight SSP Auction Flow`

경량 SSP의 BidRequest 수신, 여러 경량 DSP 호출, 응답 수집, timeout 처리, 낙찰자와 낙찰가 결정을 구현한다.

`M8: Failure Case Handling`

no-bid, timeout, invalid bid, late bid, no-winner 시나리오를 구현한다.

`M9: Performance Measurement`

로컬 통제 환경에서 응답 시간(latency), 제한 시간 내 응답률(deadline compliance), 관찰된 처리량(observed throughput)을 측정한다.

`M10: ADR`

구현과 성능 측정 중 실제 갈림길이 생긴 결정만 선택지, trade-off, 근거와 함께 기록한다.

`M11: Optimization`

측정 결과를 바탕으로 병목을 개선한다.

`M12: Retrospective`

구현 결과, 성능 결과, 설계 trade-off, 배운 점을 정리한다.

## 13. Open Questions

아직 PRD 단계에서 확정하지 않을 질문들이다.

- OpenRTB 2.6 기반 요청/응답 중 어떤 필드까지 지원할 것인가?
- 광고 타입과 타입별 지원 필드는 어디까지 둘 것인가?
- DSP 라우팅 최적화를 다루지 않는 결정은 타당한가?
- 후보 광고 캠페인 매칭은 어느 수준까지 구현할 것인가?
- 성능 테스트의 로컬 기준 환경은 어떻게 문서화할 것인가?
- 성능 최적화 전후 비교 기준은 무엇으로 둘 것인가?
- Kubernetes나 외부 배포를 범위에서 제외하는 결정은 타당한가?
