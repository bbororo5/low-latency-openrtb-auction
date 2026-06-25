# Tech Spec: OpenRTB 기반 저지연 RTB 입찰 시스템

이 문서는 PRD와 Architecture에서 정의한 RTB 입찰 시스템을 실제 구현으로 옮기기 위한 세부 명세를 다룬다.

PRD가 무엇을 해결할지 정의하고, Architecture가 어떤 구조로 바라볼지 정의한다면, Tech Spec은 개발자가 같은 기준으로 구현할 수 있도록 API 계약, 지원 필드, 실행 흐름, 데이터 모델, 실패 처리, 테스트 기준을 구체화한다.

## 1. Purpose & Scope

### 1.1 Purpose

이 문서의 목적은 경량 SSP와 경량 DSP로 구성된 RTB 입찰 시스템을 실제 구현으로 옮기기 위한 세부 기준을 정의하는 것이다.

경량 SSP는 입찰 요청(`BidRequest`)을 받아 여러 경량 DSP로 전달하고, 제한 시간 안에 도착한 입찰 응답(`BidResponse`)을 수집해 낙찰자와 낙찰가를 결정한다.

경량 DSP는 전달받은 BidRequest를 해석하고, 자신의 캠페인 데이터와 비교해 입찰 여부와 입찰가를 결정한 뒤 BidResponse 또는 no-bid를 반환한다.

특히 다음 질문에 답한다.

- 어떤 OpenRTB 요청/응답 필드를 이 시스템의 지원 범위로 삼을 것인가?
- 경량 SSP는 요청 검증, DSP 전달, 응답 수집, 낙찰 결정을 어떤 순서로 실행하는가?
- 경량 DSP는 어떤 설정과 캠페인 데이터를 바탕으로 bid 또는 no-bid를 결정하는가?
- 응답 시간 초과(timeout), 늦게 도착한 입찰 응답(late bid), 잘못된 입찰 응답(invalid bid), 입찰하지 않음(no-bid)을 어느 책임에서 어떻게 구분하는가?
- 구현이 요구사항을 만족하는지 어떤 테스트와 지표로 확인할 것인가?

### 1.2 Scope

이 문서는 현재 시스템 범위에서 필요한 구현 명세를 다룬다. 범위의 중심은 운영 수준의 광고 플랫폼 전체가 아니라 `게시자 -> 경량 SSP <-> 경량 DSP <- 광고주` 흐름의 성능 핵심 경로다.

포함하는 범위:

- OpenRTB BidRequest/BidResponse의 지원 필드
- 경량 SSP의 BidRequest 검증, DSP fan-out, 응답 수집
- 경량 SSP의 timeout, late bid, invalid bid, no-winner 처리
- 경량 SSP의 낙찰자와 낙찰가 결정 규칙
- 경량 DSP의 캠페인 데이터 모델
- 경량 DSP의 광고 타입별 요청 해석
- 경량 DSP의 bid/no-bid 결정과 입찰가 산정
- 경량 DSP의 BidResponse 생성
- 경매 결과 응답 형식
- timeout, late bid, invalid bid, no-bid, no-winner 처리
- 기능 테스트와 부하 테스트 기준

제외하는 범위:

- 전체 OpenRTB 2.6 스펙 구현
- 실제 외부 SSP/DSP 연동
- 광고 렌더링
- 노출/클릭/전환 추적
- 과금, 정산, 리포팅
- 광고 운영 백오피스
- Kubernetes 기반 운영 검증
- 클라우드 배포 환경에서의 절대 성능 보장

### 1.3 Relationship to Other Documents

이 문서는 다른 문서의 책임을 반복하지 않는다.

- PRD는 문제, 사용자 시나리오, 기능 요구사항, 성공 기준을 정의한다.
- Architecture는 시스템 경계, 품질 기준, C1/C2 관점, 큰 실행 흐름을 정의한다.
- Tech Spec은 구현자가 따라야 할 API 계약, 내부 컴포넌트, 데이터 모델, 처리 규칙, 테스트 기준을 정의한다.
- ADR은 여러 선택지가 있는 중요한 기술 결정을 별도로 기록한다.

### 1.4 Responsibility Boundary

이 문서는 경량 SSP와 경량 DSP의 책임을 분리해서 정의한다.

| 영역 | 책임 |
|---|---|
| 경량 SSP | BidRequest 수신/검증, 경량 DSP 호출, 응답 제한 시간 적용, BidResponse 수집/검증, 낙찰자/낙찰가 결정 |
| 경량 DSP | BidRequest 해석, 캠페인 후보 평가, 광고 타입별 입찰 가능 여부 판단, 입찰가 산정, BidResponse 또는 no-bid 생성 |
| 공통 | OpenRTB 객체 모델, 실패 분류, 성능 지표, 테스트 기준 |

외부 실제 SSP/DSP와의 네트워크 연동은 다루지 않는다. 이 프로젝트에서 경량 SSP와 경량 DSP는 OpenRTB 요청/응답 기반 경매 핵심 경로를 검증하기 위한 내부 구현 단위다.

## 2. OpenRTB 입출력 계약

이 장은 이 시스템이 받을 OpenRTB 요청과 내부 경량 DSP가 반환할 OpenRTB 응답의 범위를 정의한다.

OpenRTB 2.6에서 `BidRequest`는 최상위 입찰 요청 객체이며, `id`와 최소 1개의 `Imp` 객체가 필요하다. `Imp`는 경매에 붙일 광고 노출 기회이며, `banner`, `video`, `audio`, `native` 중 어떤 광고 형식을 제공하는지 표현한다.

이 시스템은 전체 OpenRTB 2.6을 구현하지 않는다. 경매 흐름, timeout 처리, 여러 경량 DSP 응답 수집, 낙찰자/낙찰가 결정을 검증하기 위해 **배너, 동영상, 네이티브 광고 요청**을 지원한다.

세 광고 타입을 지원하는 이유는 타입별로 입찰 판단 조건이 달라지기 때문이다. 배너는 크기, 동영상은 재생 시간과 프로토콜, 네이티브는 별도 native request payload를 중심으로 검증한다. 이를 통해 단순 if-else가 아니라 광고 타입별 요청 해석과 검증 책임을 코드 구조로 분리한다.

`BidResponse`는 경량 DSP가 입찰 의사를 표현하는 내부 응답으로 사용한다. 최종 API 응답은 OpenRTB 표준 객체가 아니라, 테스트와 검증을 위한 프로젝트 전용 `AuctionResult`로 반환한다.

### 2.1 지원할 BidRequest 필드

지원하는 요청 형태는 다음과 같다.

- 하나의 `BidRequest`는 정확히 하나의 `Imp`를 가진다.
- `Imp`는 `banner`, `video`, `native` 중 정확히 하나의 광고 타입 객체를 가져야 한다.
- `audio`, `pmp`는 지원하지 않는다.
- `site`는 지원하고, `app`은 지원하지 않는다.
- 사용자 식별과 추적 목적의 복잡한 필드는 사용하지 않는다.

| 객체 | 필드 | 필수 여부 | 사용 목적 |
|---|---|---:|---|
| `BidRequest` | `id` | 필수 | 경매 요청 식별자 |
| `BidRequest` | `imp` | 필수 | 경매에 붙일 광고 노출 기회 목록. 이 시스템에서는 1개만 허용 |
| `BidRequest` | `at` | 선택 | 경매 방식. 명시값이 없으면 시스템 기본값을 사용 |
| `BidRequest` | `tmax` | 선택 | 경량 DSP 응답 수집 제한 시간. 없으면 시스템 기본값 사용 |
| `BidRequest` | `site` | 선택 | 지면 정보. 캠페인 매칭 조건으로 사용 |
| `BidRequest` | `device` | 선택 | 디바이스/지역 정보. 캠페인 매칭 조건으로 사용 |
| `BidRequest` | `test` | 선택 | 테스트 요청 여부. 기록용으로만 사용 |
| `Imp` | `id` | 필수 | 광고 노출 기회 식별자. BidResponse의 `impid`와 매칭 |
| `Imp` | `banner` | 조건부 필수 | 배너 광고 요청 정보. 배너 요청일 때 필수 |
| `Imp` | `video` | 조건부 필수 | 동영상 광고 요청 정보. 동영상 요청일 때 필수 |
| `Imp` | `native` | 조건부 필수 | 네이티브 광고 요청 정보. 네이티브 요청일 때 필수 |
| `Imp` | `bidfloor` | 선택 | 최소 입찰가. 없으면 0으로 처리 |
| `Imp` | `bidfloorcur` | 선택 | 최소 입찰가 통화. 이 시스템에서는 `USD`만 허용 |
| `Banner` | `w` | 배너 필수 | 배너 너비. `format`을 사용하지 않으므로 필수로 본다 |
| `Banner` | `h` | 배너 필수 | 배너 높이. `format`을 사용하지 않으므로 필수로 본다 |
| `Video` | `mimes` | 동영상 필수 | 지원 가능한 동영상 MIME 타입 |
| `Video` | `minduration` | 동영상 필수 | 허용 가능한 최소 재생 시간 |
| `Video` | `maxduration` | 동영상 필수 | 허용 가능한 최대 재생 시간 |
| `Video` | `protocols` | 동영상 필수 | 지원 가능한 동영상 응답 프로토콜 |
| `Video` | `w` | 동영상 선택 | 동영상 광고 너비 |
| `Video` | `h` | 동영상 선택 | 동영상 광고 높이 |
| `Native` | `request` | 네이티브 필수 | Native Ad Specification을 따르는 JSON 문자열 |
| `Native` | `ver` | 네이티브 선택 | Native API 버전 |
| `Site` | `id` | 선택 | 지면 식별자 |
| `Site` | `domain` | 선택 | 지면 도메인 |
| `Site` | `cat` | 선택 | 지면 카테고리 |
| `Device` | `geo.country` | 선택 | 국가 타겟팅 조건 |
| `Device` | `ua` | 선택 | 사용자 에이전트. 기록/테스트 조건으로만 사용 |

요청은 다음과 같이 해석한다.

- `BidRequest.id`가 없으면 잘못된 요청(invalid request)이다.
- `imp`가 없거나 1개가 아니면 지원하지 않는 요청(unsupported request)이다.
- `Imp.id`가 없으면 잘못된 요청이다.
- `Imp`가 `banner`, `video`, `native` 중 정확히 하나를 갖지 않으면 지원하지 않는 요청이다.
- 배너 요청에서 `Banner.w`, `Banner.h`가 없으면 잘못된 요청이다.
- 동영상 요청에서 `Video.mimes`, `Video.minduration`, `Video.maxduration`, `Video.protocols` 중 하나라도 없으면 잘못된 요청이다.
- 네이티브 요청에서 `Native.request`가 없거나 JSON 문자열로 파싱할 수 없으면 잘못된 요청이다.
- `bidfloorcur`가 `USD`가 아니면 지원하지 않는 요청이다.

### 2.2 지원할 BidResponse 필드

경량 DSP는 입찰 가능한 경우 다음 형태의 `BidResponse`를 반환한다.

| 객체 | 필드 | 필수 여부 | 사용 목적 |
|---|---|---:|---|
| `BidResponse` | `id` | 필수 | 원본 `BidRequest.id` |
| `BidResponse` | `seatbid` | 필수 | 입찰 묶음 |
| `BidResponse` | `cur` | 선택 | 입찰 통화. 이 시스템에서는 `USD`만 허용 |
| `SeatBid` | `seat` | 선택 | 경량 DSP 또는 광고주 seat 식별자 |
| `SeatBid` | `bid` | 필수 | 실제 입찰 목록. 이 시스템에서는 1개만 사용 |
| `Bid` | `id` | 필수 | 경량 DSP가 생성한 입찰 식별자 |
| `Bid` | `impid` | 필수 | 원본 `Imp.id`와 매칭되는 값 |
| `Bid` | `price` | 필수 | CPM 기준 입찰가 |
| `Bid` | `cid` | 선택 | 캠페인 식별자 |
| `Bid` | `crid` | 선택 | 광고 소재 식별자 |
| `Bid` | `adomain` | 선택 | 광고주 도메인 |
| `Bid` | `w` | 선택 | 응답 광고 너비 |
| `Bid` | `h` | 선택 | 응답 광고 높이 |
| `Bid` | `mtype` | 필수 | 광고 타입. 배너 `1`, 동영상 `2`, 네이티브 `4` |
| `Bid` | `adm` | 조건부 필수 | 동영상/네이티브 응답의 광고 마크업. 실제 렌더링하지 않고 존재 여부만 검증 |

BidResponse는 다음 기준으로 검증한다.

- `BidResponse.id`는 원본 `BidRequest.id`와 같아야 한다.
- `Bid.impid`는 원본 `Imp.id`와 같아야 한다.
- `Bid.price`는 `Imp.bidfloor` 이상이어야 한다.
- `cur`가 있으면 `USD`여야 한다.
- `mtype`은 원본 요청의 광고 타입과 일치해야 한다.
- 배너 응답에서 `Bid.w`, `Bid.h`가 있으면 원본 `Banner.w`, `Banner.h`와 같아야 한다.
- 동영상 응답은 `adm`을 가져야 한다.
- 네이티브 응답은 `adm`을 가져야 한다.

입찰하지 않음(no-bid)은 다음 중 하나로 표현할 수 있다.

- 경량 DSP가 내부 결과로 `NO_BID`를 반환한다.
- OpenRTB 응답 형태가 필요한 테스트에서는 빈 `seatbid`를 가진 응답을 사용할 수 있다.

HTTP 204 방식의 no-bid는 실제 외부 DSP 연동을 하지 않으므로 다루지 않는다.

### 2.3 경매 결과 응답

이 시스템은 경량 DSP의 `BidResponse`를 그대로 외부 호출자에게 반환하지 않는다. 여러 경량 DSP 응답을 수집한 뒤, 프로젝트 전용 `AuctionResult`를 반환한다.

`AuctionResult`는 테스트와 성능 측정을 위해 다음 정보를 포함한다.

| 필드 | 설명 |
|---|---|
| `requestId` | 원본 `BidRequest.id` |
| `impId` | 경매 대상 `Imp.id` |
| `mediaType` | `BANNER`, `VIDEO`, `NATIVE` 중 하나 |
| `status` | `WINNER`, `NO_WINNER`, `INVALID_REQUEST`, `UNSUPPORTED_REQUEST` 중 하나 |
| `winnerDspId` | 낙찰된 경량 DSP 식별자. 낙찰자가 없으면 없음 |
| `winningBidId` | 낙찰된 `Bid.id`. 낙찰자가 없으면 없음 |
| `winningPrice` | 낙찰가. 낙찰자가 없으면 없음 |
| `auctionPrice` | 경매 규칙에 따라 결정된 최종 가격 |
| `currency` | 통화. 이 시스템에서는 `USD` |
| `elapsedMs` | 요청 처리 시작부터 결과 결정까지 걸린 시간 |
| `dspResultCounts` | bid, no-bid, timeout, late bid, invalid bid 개수 |

`AuctionResult`는 OpenRTB 표준 객체가 아니다. 이 프로젝트가 경량 SSP와 경량 DSP의 핵심 흐름을 함께 구현하기 때문에, 최종 결과를 검증하기 위한 테스트 응답 객체로 둔다.

## 3. Runtime Flow

### 3.1 전체 요청 처리 흐름

### 3.2 C3: 애플리케이션 내부 컴포넌트

## 4. 경량 SSP 설계

### 4.1 책임

### 4.2 BidRequest 수신과 검증

### 4.3 DSP Fan-out

### 4.4 BidResponse 수집

### 4.5 Timeout / Late Bid 처리

### 4.6 Invalid Bid 검증

### 4.7 낙찰자와 낙찰가 결정

## 5. 경량 DSP 설계

### 5.1 책임

### 5.2 DSP 설정

### 5.3 캠페인 데이터 모델

### 5.4 광고 타입별 요청 해석

### 5.5 입찰 여부 결정

### 5.6 입찰가 결정

### 5.7 BidResponse 생성

### 5.8 No-Bid 반환

## 6. 공통 실패 처리

### 6.1 실패 분류

### 6.2 요청 실패

### 6.3 DSP 응답 실패

### 6.4 No-Winner 처리

## 7. 성능 지표와 테스트 전략

### 7.1 측정 지표

### 7.2 기능 테스트 시나리오

### 7.3 부하 테스트 시나리오

## 8. Deferred Decisions & ADR Candidates
