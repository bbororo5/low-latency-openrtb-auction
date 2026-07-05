# k6 Performance Tests

이 디렉토리는 경량 SSP와 경량 DSP의 hot path를 측정하기 위한 k6 스크립트를 둔다.

## Test Types

| Type | Purpose | Current Status |
|---|---|---|
| Smoke | 메인 시나리오와 관찰 지표 파이프라인이 정상인지 확인 | `smoke.js` |
| Load | 일정한 정상 부하에서 p95/p99와 결과 분포 확인 | 예정 |
| Stress | 부하를 올리며 시스템 한계와 병목 지점 확인 | 예정 |
| Spike | 짧은 시간의 급격한 트래픽 증가 영향 확인 | 예정 |
| Soak | 장시간 부하에서 누수와 누적 악화 확인 | 후순위 |

## Smoke Test

전제:

- SSP: `localhost:8080`
- DSP-A: `localhost:8081`, `normal-medium`
- DSP-B: `localhost:8082`, `normal-high`
- DSP-C: `localhost:8083`, `no-bid`
- DSP-D: `localhost:8084`, `timeout`

실행:

```bash
k6 run performance/k6/smoke.js
```

SSP 주소를 바꾸려면:

```bash
BASE_URL=http://localhost:8080 k6 run performance/k6/smoke.js
```

검증 내용:

- HTTP status가 `200`인지
- AuctionResult가 `WINNER`인지
- 기본 topology에서 `dsp-b`가 낙찰되는지
- DSP 결과 분포가 `bid=2`, `no-bid=1`, `timeout=1`인지

이 smoke test는 성능 한계를 측정하지 않는다. k6 요청, SSP/DSP 연결, AuctionResult, Prometheus metric 생성이 함께 동작하는지 확인하는 첫 단계다.
