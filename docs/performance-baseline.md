# Performance Baseline

이 문서는 성능 실험에서 얻은 기준선과 해석 원칙을 요약한다. 날짜별 상세 실험 기록은 `performance-reports/*`에 둔다.

이 결과는 production benchmark가 아니다. 측정값은 부하 발생기, 네트워크 경로, target system 배포 조건 안에서만 해석한다.

## 1. Measurement Goal

성능 측정의 목표는 최대 RPS를 과장하는 것이 아니다.

목표는 다음을 분리해서 보는 것이다.

| Concern | Question |
|---|---|
| Correctness | 부하 중에도 winner/no-winner가 맞는가? |
| Deadline | 제한 시간 안에 결과가 결정되는가? |
| Tail latency | p95/p99가 어떻게 변하는가? |
| Failure distribution | timeout, late bid, invalid bid, no-bid가 어떻게 변하는가? |
| Bottleneck | 병목이 auction logic, DSP fan-out, runtime, load path 중 어디에 있는가? |

## 2. Baseline Scenario

기준 시나리오는 provider-facing 경매 요청 한 건이 SSP와 여러 DSP를 거쳐 AuctionResult로 끝나는 흐름이다.

기본 조건:

- SSP 1개
- DSP 여러 개
- banner 요청
- first-price auction
- 정상 bid, no-bid, timeout DSP를 포함한 혼합 결과

기존 direct OpenRTB 경로의 측정은 historical baseline으로만 본다. 현재 기본 입력은 `POST /publisher/auction`이다.

## 3. Success Criteria

부하를 감당한다고 판단하려면 다음을 함께 만족해야 한다.

| Category | Criterion |
|---|---|
| Transport stability | 요청 실패가 없어야 한다. |
| Auction correctness | 예상 winner 또는 no-winner가 유지되어야 한다. |
| Result distribution | bid/no-bid/timeout/invalid/error 분류가 기대와 맞아야 한다. |
| Latency | p95/p99를 기록하고 이전 baseline과 비교한다. |
| Deadline compliance | 제한 시간 초과 비율을 확인한다. |

처리량만으로 성공을 판단하지 않는다.

## 4. Measurement Layers

성능은 한 번에 해석하지 않고 계층별로 분리한다.

| Layer | Purpose |
|---|---|
| Host baseline | VM/OS의 기본 자원 한계를 확인한다. |
| HTTP baseline | application logic 없는 HTTP 처리 비용을 본다. |
| JSON/OpenRTB baseline | encoding/decoding 비용을 본다. |
| RTB auction baseline | SSP-DSP fan-out과 winner decision을 포함한 hot path를 본다. |
| Timeout resilience | 느린 DSP가 있을 때 deadline isolation을 확인한다. |

## 5. Current Interpretation

현재까지의 핵심 해석:

- 로컬 단일 Compose 환경은 부하 발생기와 target system이 자원을 공유해 latency 해석이 어렵다.
- target system과 load generator를 분리하면 병목 위치를 더 잘 설명할 수 있다.
- 일부 구간에서는 경매 logic보다 HTTP/runtime/load path가 먼저 병목으로 관찰됐다.
- DSP 수와 timeout DSP 존재 여부는 p95/p99와 worker 점유에 직접 영향을 준다.
- 높은 RPS보다 correctness와 deadline compliance가 먼저다.

## 6. Detailed Reports

상세 수치, 명령어, 실험 환경은 아래 기록을 기준으로 한다.

- [AWS Single VM JSON Baseline - 2026-07-08](performance-reports/2026-07-08-aws-single-vm-json-baseline.md)
- [Local Compose Load Investigation - 2026-07-06](performance-reports/2026-07-06-local-compose-load-investigation.md)
- [AWS HttpServer Executor Investigation - 2026-07-06](performance-reports/2026-07-06-aws-httpserver-executor-investigation.md)
- [Capacity Scenario Stress - 2026-07-06](performance-reports/2026-07-06-capacity-scenario-stress.md)
- [Grafana Cloud k6 Baseline - 2026-07-06](performance-reports/2026-07-06-grafana-cloud-k6-baseline.md)
- [AWS Load Generator Capacity - 2026-07-07](performance-reports/2026-07-07-aws-loadgen-capacity.md)
- [AWS VM Baseline - 2026-07-08](performance-reports/2026-07-08-aws-vm-baseline.md)

## 7. Next Measurement Direction

다음 성능 작업은 다음 순서로 진행한다.

1. 같은 조건에서 provider-facing path baseline을 고정한다.
2. timeout DSP 유무에 따른 p95/p99 차이를 비교한다.
3. DSP instance 수 증가에 따른 fan-out 비용을 측정한다.
4. runtime saturation 신호와 latency 변화를 함께 본다.
5. 최적화 전후에 correctness와 deadline compliance가 유지되는지 검증한다.
