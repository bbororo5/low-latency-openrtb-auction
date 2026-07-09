# Observability Strategy

이 문서는 RTB hot path가 느리거나 결과가 달라졌을 때 원인을 설명하기 위한 최소 관측 기준을 정의한다. 도구별 설정이나 cloud 연결 절차는 운영 노트나 ADR에서 다룬다.

## 1. Observability Goals

관측성은 다음 질문에 답해야 한다.

| Question | Why it matters |
|---|---|
| 전체 경매가 느린가? | provider-facing latency와 deadline compliance를 판단한다. |
| DSP 호출이 느린가? | fan-out과 외부 호출 비용을 분리한다. |
| DSP 내부 판단이 느린가? | campaign lookup, matching, pricing 비용을 분리한다. |
| 왜 winner가 없었는가? | no-bid, timeout, invalid bid, late bid를 구분한다. |
| 최적화가 결과 품질을 깨뜨렸는가? | 성능 개선 전후의 correctness를 비교한다. |

## 2. Signals

| Signal | Project meaning |
|---|---|
| Latency | auction duration, DSP call duration, DSP bid handling duration |
| Traffic | auction request count, DSP call count, bid handling count |
| Result quality | winner/no-winner, bid/no-bid, timeout, late bid, invalid bid |
| Saturation | thread, connection, CPU, GC 같은 runtime 한계 신호 |

`NO_BID`와 `NO_WINNER`는 무조건 장애가 아니다. business result와 system error를 분리해서 본다.

## 3. Primary Metrics

| Metric group | Purpose |
|---|---|
| SSP auction latency | Provider Slot Request부터 AuctionResult까지의 p95/p99를 본다. |
| SSP auction result count | winner, no-winner, invalid request, unsupported request 분포를 본다. |
| SSP DSP call latency | DSP별 호출 지연과 timeout 원인을 분리한다. |
| SSP DSP call result count | bid, no-bid, timeout, late bid, error 분포를 본다. |
| DSP bid handling latency | DSP 내부 판단 비용을 본다. |
| DSP no-bid reason count | DSP가 왜 입찰하지 않았는지 설명한다. |

## 4. Tag Policy

Metric tag는 낮은 cardinality 값만 허용한다.

허용:

- `app`
- `media_type`
- `result`
- `reason`
- `dsp_id`

금지:

- `request_id`
- `imp_id`
- `bid_id`
- `campaign_id`
- `creative_id`
- `user_id`
- `ip`

높은 cardinality 값은 metric tag가 아니라 log나 trace에 남긴다.

## 5. Metrics, Logs, Traces

| Data type | Role |
|---|---|
| Metrics | 집계와 추세 확인. p95/p99, timeout rate, no-bid reason에 사용한다. |
| Logs | 개별 요청의 분류 이유를 확인한다. request id, DSP id, reason을 남긴다. |
| Traces | SSP에서 DSP로 이어지는 호출 흐름과 병목 위치를 확인한다. |

Metrics는 business event나 ledger의 대체물이 아니다.

## 6. Monitoring Paths

현재 관측 경로는 세 단계로 본다.

| Path | Purpose |
|---|---|
| Local metrics | 개발 중 빠르게 latency와 result distribution을 확인한다. |
| Performance test metrics | 부하 테스트 중 target system의 p95/p99와 failure distribution을 본다. |
| Cloud metrics / traces | 외부 경로에서 관찰되는 latency와 service call flow를 확인한다. |

구체적인 도구 선택은 ADR과 실행 환경 문서에서 다룬다. 이 문서의 기준은 “어떤 도구를 쓰는가”가 아니라 “어떤 질문에 답해야 하는가”다.

## 7. Alerting Boundary

현재 프로젝트는 운영 SLO와 alert policy를 확정하지 않는다. 다만 제품급 확장 시 다음 조건은 alert 후보가 된다.

- deadline compliance 급락
- timeout rate 급증
- invalid bid rate 급증
- no-winner rate의 비정상적 변화
- DSP별 latency 편차 급증
- runtime saturation 지속
