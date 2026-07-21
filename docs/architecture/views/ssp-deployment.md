# SSP 다중 리전 배포

상태: 실행·데이터 배치 확정·기술 미정

SSP 컨테이너 인스턴스와 기반 시설의 배치만 보여준다. 기술 제품과 실제 인스턴스 수는 정하지 않는다.

```mermaid
C4Deployment
    title SSP — 두 리전·다중 AZ 배포

    Deployment_Node(ssp_environment, "SSP 운영 환경", "독립 배포·운영 경계") {
        Deployment_Node(ssp_edge, "전역 진입", "분산 트래픽 기반 시설", "두 능동 리전으로 새 요청을 전달한다.")

        Deployment_Node(region_1, "리전 1", "능동") {
            Deployment_Node(az_1a, "AZ A", "독립 장애 영역") {
                Container(app_1a, "SSP 애플리케이션 인스턴스", "기술 미정", "경매·렌더링·통지를 처리한다.")
            }
            Deployment_Node(az_1b, "AZ B", "독립 장애 영역") {
                Container(app_1b, "SSP 애플리케이션 인스턴스", "기술 미정", "경매·렌더링·통지를 처리한다.")
            }
            ContainerDb(store_1, "SSP 리전 1 금액 사건 기록", "RDS PostgreSQL Multi-AZ", "리전 1의 청구·확정·미수집 사건을 추가 전용으로 보존한다.")
        }

        Deployment_Node(region_2, "리전 2", "능동") {
            Deployment_Node(az_2a, "AZ A", "독립 장애 영역") {
                Container(app_2a, "SSP 애플리케이션 인스턴스", "기술 미정", "경매·렌더링·통지를 처리한다.")
            }
            Deployment_Node(az_2b, "AZ B", "독립 장애 영역") {
                Container(app_2b, "SSP 애플리케이션 인스턴스", "기술 미정", "경매·렌더링·통지를 처리한다.")
            }
            ContainerDb(store_2, "SSP 리전 2 금액 사건 기록", "RDS PostgreSQL Multi-AZ", "리전 2의 청구·확정·미수집 사건을 추가 전용으로 보존한다.")
        }
    }

    Rel(ssp_edge, app_1a, "새 요청 전달")
    Rel(ssp_edge, app_1b, "새 요청 전달")
    Rel(ssp_edge, app_2a, "새 요청 전달")
    Rel(ssp_edge, app_2b, "새 요청 전달")
    Rel(app_1a, store_1, "성공 전 기록")
    Rel(app_1b, store_1, "성공 전 기록")
    Rel(app_2a, store_2, "성공 전 기록")
    Rel(app_2b, store_2, "성공 전 기록")
    Rel(store_1, store_2, "비동기 병합·대조")
    Rel(store_2, store_1, "비동기 병합·대조")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

- 전역 진입은 컨테이너가 아니라 배포 기반 시설이다.
- 두 리전은 평상시 모두 요청을 처리한다.
- 각 리전은 최소 두 AZ에 SSP 애플리케이션 인스턴스를 둔다.
- 경매와 통지의 실행 자원은 같은 프로세스 안에서 격리한다.
- 낙찰 결과는 인증된 렌더링 증표로 클라이언트가 운반하므로 경매 경로에서 저장하지 않는다. 각 리전은 자기 금액 사건을 여러 AZ에 내구 기록하고 다른 리전과 비동기로 병합한다.
