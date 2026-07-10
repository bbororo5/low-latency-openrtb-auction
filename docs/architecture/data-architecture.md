# Data Architecture and Consistency

상태: Active 1.0
관련 결정: `ADR-003`, `ADR-006`

데이터는 별도 설계 단계가 아니라 domain correctness, deadline, failure isolation을 검토하는 고정 관심사다. 현재 범위는 데이터 원본 플랫폼이 아니라 auction serving path이므로, source-of-truth 관리와 serving snapshot read를 분리한다.

## 1. Data Ownership and Lifetime

| Data | Owner in current scope | Authority | Lifetime | Consistency rule |
|---|---|---|---|---|
| Provider slot request | Provider | inbound request | one request | decode 후 immutable value로 취급 |
| Inventory placement | SSP bootstrap | 외부 원본의 준비된 fixture | SSP process | startup immutable copy, restart-only update |
| Campaign/creative/policy | DSP bootstrap | 외부 원본의 준비된 fixture | DSP process | startup immutable copy, restart-only update |
| Auction context | SSP | normalized request + inventory snapshot | one auction | `receivedAt`, cutoff, floor, media가 실행 중 불변 |
| OpenRTB request template | SSP | auction context projection | one auction | request/imp/media/floor/currency 동일 |
| Outbound OpenRTB request | SSP gateway | template + remaining deadline | one fan-out | 모든 선택 DSP에 같은 `outboundTmax`와 payload 의미 사용 |
| DSP call observation | SSP gateway | wire/clock observation | one DSP call | 아직 candidate가 아님 |
| Terminal result/candidate | SSP judge | validation policy | one auction | DSP당 terminal result 하나; valid candidate만 winner 입력 |
| Auction result | SSP | winner policy | response lifetime | 반환 후 변경 불가 |
| Metrics | 각 process | runtime event의 derived aggregate | retention backend에 따름 | source data를 대체하지 않으며 재수집 외 재구축 보장 없음 |

## 2. Source and Derived Data

- inventory/campaign의 authoritative source와 동기화 파이프라인은 현재 system boundary 밖이다.
- `InMemoryInventoryCatalog`와 `InMemoryCampaignLookup`은 startup 입력의 serving copy이며 원본 시스템이 아니다.
- auction context, OpenRTB payload, candidate, result, metrics는 요청 또는 snapshot에서 파생된 데이터다.
- 파생 데이터가 원본을 갱신하거나 billing/money event의 근거가 되지 않는다.

## 3. Snapshot Contract

1. constructor는 제공된 collection을 immutable collection으로 복사한다.
2. process가 시작된 뒤 snapshot을 교체하는 API를 제공하지 않는다.
3. 한 요청 중간에 inventory/campaign view가 바뀌지 않는다.
4. 변경 반영은 새 process 시작과 함께 원자적으로 일어난 것으로 취급한다.
5. freshness, version distribution, rollback, reload failure는 정의하지 않는다.

이 계약은 분산 일관성 문제가 없다는 뜻이 아니다. 현재 release가 그 문제를 runtime reload 없이 범위 밖으로 밀어 작은 상태 공간을 선택했다는 뜻이다.

## 4. Revisit Triggers

다음 중 하나가 요구되면 requirements를 먼저 변경하고 `ADR-006`을 supersede한다.

- 무중단 inventory/campaign reload
- snapshot freshness SLA 또는 version 노출
- 여러 SSP/DSP instance 사이의 동시 version 전환
- 원본 변경 event, rollback, cache rebuild
- budget/billing/ledger 또는 exactly-once money event
