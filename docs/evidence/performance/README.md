# Performance Evidence Register

상태: Active
현재 acceptance evidence: 없음

이 디렉터리의 기존 기록은 아키텍처가 정립되기 전의 병목 탐색과 측정 환경 조사다. 현재 `VP-002~004`를 통과했다는 증거로 사용하지 않는다.

## Evidence Classes

| Class | Meaning | May verify QR/ADR? |
|---|---|---:|
| Current acceptance | 현재 commit, provider path, reference environment, full pass criteria | yes |
| Environment baseline | VM/network/runtime의 측정 가능성 확인 | no |
| Methodology evidence | load generator, monitoring, topology 문제 조사 | no |
| Historical diagnostic | 과거 구현의 병목과 실패 관찰 | no |

## Existing Reports

| Report | Class | Reusable conclusion |
|---|---|---|
| `2026-07-08-aws-vm-baseline.md` | Environment baseline | AWS VM 측정 환경과 기본 경로 |
| `2026-07-06-load-generator-path-investigation.md` | Methodology | load generator 배치 영향 |
| `2026-07-06-grafana-cloud-k6-baseline.md` | Methodology | remote monitoring 연결 |
| `2026-07-06-capacity-scenario-stress.md` | Methodology | capacity/stress 시나리오 형태 |
| `2026-07-06-local-compose-load-investigation.md` | Historical diagnostic | co-located compose 병목 |
| `2026-07-06-aws-httpserver-executor-investigation.md` | Historical diagnostic | server/executor 병목 후보 |
| `2026-07-07-aws-loadgen-capacity.md` | Historical diagnostic | 과거 capacity 관찰 |
| `2026-07-08-aws-single-vm-json-baseline.md` | Historical diagnostic | single-VM/direct JSON baseline |

## Current Acceptance Entry Requirements

새 보고서는 다음을 모두 기록해야 Current acceptance가 될 수 있다.

- UTC/KST date와 exact Git commit
- build artifact 또는 container image digest
- target CPU/memory/OS/JDK와 process resource limit
- SSP/DSP/load-generator/monitoring topology
- provider request payload와 DSP behavior
- RPS, duration, VU allocation, warm-up 조건
- p95/p99, HTTP failure, dropped iteration, domain checks
- terminal result 분포, executor/in-flight/rejection 신호
- `VP-002`, `VP-003`, `VP-004` 각각의 pass/fail
- raw k6 summary 또는 저장 위치

파일명은 `YYYY-MM-DD-vp-NNN-short-description.md`를 사용한다. 측정 중 구현 또는 topology가 바뀌면 같은 보고서에 섞지 않는다.

## Next Evidence

1. 별도 load-generator host에서 `VP-002`를 실행한다.
2. timeout DSP를 추가해 `VP-003`을 실행한다.
3. 최초 실패 RPS를 찾고 `overload-recovery.js`에 2배 RPS를 넣어 `VP-004`를 실행한다.
4. observability on/off를 같은 commit/topology에서 비교해 `QR-008`을 판정한다.
