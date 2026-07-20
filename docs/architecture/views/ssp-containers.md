# SSP 컨테이너

상태: 실행 경계 검토안·기술 미정

범위는 SSP 소프트웨어 시스템 하나다. 리전·AZ·부하 분산과 복제는 [SSP 배포 관점](ssp-deployment.md)에서 다룬다.

```mermaid
C4Container
    title SSP — 컨테이너

    System_Ext(supplier, "공급자 시스템", "경매 요청을 보낸다.")
    System_Ext(ad_client, "광고 표시 클라이언트", "렌더링 성공을 통지한다.")
    System_Ext(project_dsp, "프로젝트 DSP", "입찰하고 금액 통지를 접수한다.")
    System_Ext(external_dsp_1, "외부 DSP 1", "독립 모의 DSP다.")
    System_Ext(external_dsp_2, "외부 DSP 2", "독립 모의 DSP다.")

    Container_Boundary(ssp, "SSP") {
        Container(ssp_application, "SSP 애플리케이션", "기술 미정", "경매·렌더링 검증과 내구성 있는 통지 전달을 실행한다.")
        ContainerDb(ssp_store, "SSP 성공 사실 저장소", "저장 기술 미정", "낙찰·렌더링·통지 발신함과 전달 상태를 보존한다.")
    }

    Rel(supplier, ssp_application, "경매 요청")
    Rel(ad_client, ssp_application, "렌더링 성공 통지")
    Rel(ssp_application, project_dsp, "기한 있는 입찰 요청·응답과 통지")
    Rel(ssp_application, external_dsp_1, "기한 있는 입찰 요청·응답과 통지")
    Rel(ssp_application, external_dsp_2, "기한 있는 입찰 요청·응답과 통지")
    Rel(ssp_application, ssp_store, "성공 사실 기록·발신함 처리")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

## 컨테이너 책임

| 컨테이너 | 책임 | 확정하지 않은 내부 경계 |
|---|---|---|
| SSP 애플리케이션 | 요청 검증, DSP별 격리·병렬 호출, 1가격 낙찰, 렌더링 검증, 발신함 처리와 통지 재전달 | 경매와 렌더링·통지의 프로세스 분리 여부, 내부 실행 자원 격리 방식 |
| SSP 성공 사실 저장소 | 공개한 낙찰·렌더링 사실, 통지 발신함과 전달 상태 보존 | 저장 제품, 스키마, 분할·복제 구현 |

경매와 렌더링·통지는 우선 같은 프로세스 안에서 실행 자원을 격리한다. 별도 프로세스는 현재 구조에 포함하지 않으며 통지 적체가 경매의 50ms 시간 예산을 실제로 침범할 때 재검토한다.
