# 프로젝트 DSP 다중 리전 배포

상태: 실행·예산 원장 배치 확정·캠페인 저장 배치 검토안·기술 미정

프로젝트 DSP 컨테이너 인스턴스와 기반 시설의 배치만 보여준다. 전역 책임 원장과 각 리전 예산 원장은 독립된 물리 저장소 배포 경계다. 기술 제품과 실제 인스턴스 수는 정하지 않는다.

```mermaid
C4Deployment
    title 프로젝트 DSP — 두 리전·다중 AZ 배포

    Deployment_Node(dsp_environment, "프로젝트 DSP 운영 환경", "독립 배포·운영 경계") {
        Deployment_Node(dsp_edge, "전역 진입", "분산 트래픽 기반 시설", "건강한 능동 리전으로 새 연결을 전달한다.")

        Deployment_Node(region_1, "리전 1", "능동") {
            Deployment_Node(az_1a, "AZ A", "독립 장애 영역") {
                Container(gateway_1a, "DSP 게이트웨이 인스턴스", "기술 미정", "요청을 분산하고 과부하를 차단한다.")
                Container(app_1a, "DSP 애플리케이션 인스턴스", "기술 미정", "로컬 입찰과 예산 배경 처리를 실행한다.")
            }
            Deployment_Node(az_1b, "AZ B", "독립 장애 영역") {
                Container(gateway_1b, "DSP 게이트웨이 인스턴스", "기술 미정", "요청을 분산하고 과부하를 차단한다.")
                Container(app_1b, "DSP 애플리케이션 인스턴스", "기술 미정", "로컬 입찰과 예산 배경 처리를 실행한다.")
            }
            ContainerDb(campaign_1, "캠페인 실행 원본 복제본", "저장 기술 미정", "리전 1 배포 원본이다.")
            ContainerDb(regional_1, "리전 1 예산 원장", "저장 기술 미정", "리전 1 책임액의 유일한 작성자다.")
            ContainerDb(money_1, "금액 사건 저장소 복제본", "저장 기술 미정", "리전 1 접근 복제본이다.")
            ContainerDb(global_1, "전역 책임 원장 복제본", "저장 기술 미정", "분할·복제된 전역 권위다.")
        }

        Deployment_Node(region_2, "리전 2", "능동") {
            Deployment_Node(az_2a, "AZ A", "독립 장애 영역") {
                Container(gateway_2a, "DSP 게이트웨이 인스턴스", "기술 미정", "요청을 분산하고 과부하를 차단한다.")
                Container(app_2a, "DSP 애플리케이션 인스턴스", "기술 미정", "로컬 입찰과 예산 배경 처리를 실행한다.")
            }
            Deployment_Node(az_2b, "AZ B", "독립 장애 영역") {
                Container(gateway_2b, "DSP 게이트웨이 인스턴스", "기술 미정", "요청을 분산하고 과부하를 차단한다.")
                Container(app_2b, "DSP 애플리케이션 인스턴스", "기술 미정", "로컬 입찰과 예산 배경 처리를 실행한다.")
            }
            ContainerDb(campaign_2, "캠페인 실행 원본 복제본", "저장 기술 미정", "리전 2 배포 원본이다.")
            ContainerDb(regional_2, "리전 2 예산 원장", "저장 기술 미정", "리전 2 책임액의 유일한 작성자다.")
            ContainerDb(money_2, "금액 사건 저장소 복제본", "저장 기술 미정", "리전 2 접근 복제본이다.")
            ContainerDb(global_2, "전역 책임 원장 복제본", "저장 기술 미정", "분할·복제된 전역 권위다.")
        }
    }

    Rel(dsp_edge, gateway_1a, "새 연결 전달")
    Rel(dsp_edge, gateway_1b, "새 연결 전달")
    Rel(dsp_edge, gateway_2a, "새 연결 전달")
    Rel(dsp_edge, gateway_2b, "새 연결 전달")
    Rel(gateway_1a, app_1a, "요청 분산")
    Rel(gateway_1b, app_1b, "요청 분산")
    Rel(gateway_2a, app_2a, "요청 분산")
    Rel(gateway_2b, app_2b, "요청 분산")
    Rel(app_1a, campaign_1, "배경 자료 갱신")
    Rel(app_1a, regional_1, "권한·격리·회수")
    Rel(app_1a, global_1, "책임액·확정 지출 집계")
    Rel(app_1a, money_1, "금액 사건 접수")
    Rel(app_2a, campaign_2, "배경 자료 갱신")
    Rel(app_2a, regional_2, "권한·격리·회수")
    Rel(app_2a, global_2, "책임액·확정 지출 집계")
    Rel(app_2a, money_2, "금액 사건 접수")
    BiRel(campaign_1, campaign_2, "버전 자료 복제")
    Rel(regional_1, regional_2, "세부 기록 복구 사본", "비동기")
    Rel(regional_2, regional_1, "세부 기록 복구 사본", "비동기")
    BiRel(money_1, money_2, "금액 사실 RPO 0")
    BiRel(global_1, global_2, "책임 이전 강한 보존")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

- 전역 진입은 컨테이너가 아니라 배포 기반 시설이다.
- 두 리전은 자기 책임액과 로컬 권한으로 독립 입찰한다.
- 전역 책임 원장과 각 리전 예산 원장은 클러스터·장애·합의 범위를 공유하지 않는다.
- 게이트웨이와 DSP 애플리케이션 인스턴스는 각각 리전 풀을 이루며 도식의 선은 인스턴스 간 고정 결합을 뜻하지 않는다.
- 입찰과 예산 처리는 같은 애플리케이션 프로세스 안에서 실행 자원을 격리한다.
- AZ B의 DSP 애플리케이션도 같은 지역 저장소 관계를 가지며 중복 선은 생략했다.
- 리전 예산 원장의 점선 의미는 비동기 복구이며 상대 리전의 책임액을 수정하지 않는다.
