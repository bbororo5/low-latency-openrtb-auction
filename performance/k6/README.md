# k6 성능 실험 도구

상태: 초기 구현 진단용

현재 스크립트는 연결과 기초 병목을 확인하기 위한 도구다. 아직 합의된 ASR과 후속 아키텍처를 반영하지 않았으므로 실행 결과를 합격 증거로 사용하지 않는다.

| 스크립트 | 용도 |
|---|---|
| `smoke.js` | 요청, SSP–DSP 연결, 낙찰 결과, 지표 생성 확인 |
| `http-ok-baseline.js` | 비즈니스 로직 없는 HTTP 기준점 |
| `openrtb-json-baseline.js` | OpenRTB JSON 해석·생성 비용 확인 |
| `load-capacity.js` | 시간 초과 DSP를 뺈 기초 처리량 진단 |
| `load-baseline.js` | 시간 초과 DSP의 격리 확인 |
| `overload-recovery.js` | 과부하 후 회복 확인 |

## 실행

```bash
docker compose -f docker-compose.perf.yml up --build -d ssp prometheus grafana
docker compose -f docker-compose.perf.yml --profile test run --rm k6-smoke
```

부하 실험 예:

```bash
RPS=100 DURATION=1m \
  docker compose -f docker-compose.perf.yml --profile test run --rm k6-load-capacity
```

정리:

```bash
docker compose -f docker-compose.perf.yml down
```

## 해석 원칙

- 같은 호스트에서 k6, SSP, DSP, 관측 도구를 함께 실행한 결과는 로컬 진단이다.
- 스크립트에 남은 이전 요청률·응답 시간 기준은 현재 합격 기준이 아니다.
- 성능만 보지 말고 낙찰 정확성, DSP 결과 수, 예산 불변식을 함께 확인한다.
- 합격 시험은 대상과 분리된 부하 발생기, 전체 데이터, 장애 주입, 시험 전후 예산 대조를 포함해야 한다.

최종 합격 기준은 [부하·데이터·검증 기준](../../docs/requirements/workload-data-verification.md)을 따른다.
