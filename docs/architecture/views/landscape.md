# 시스템 전경

상태: 구조 확정·기술 미정

두 개의 구현 대상 소프트웨어 시스템과 외부 시스템의 전체 관계를 보여준다. 특정 시스템 하나를 중심으로 한 Level 1 도식은 별도 문서에 있다.

```mermaid
C4Context
    title 초저지연 RTB 입찰 시스템 — 시스템 전경

    System_Ext(supplier, "공급자 시스템", "광고 슬롯 경매를 요청하며 k6가 모의한다.")
    System_Ext(ad_client, "광고 표시 클라이언트", "렌더링 성공을 알리며 통합 시험이 모의한다.")

    Enterprise_Boundary(ssp_company, "SSP 회사") {
        System(ssp, "SSP", "경매·낙찰·렌더링 검증과 DSP 통지를 책임진다.")
    }

    Enterprise_Boundary(project_dsp_company, "프로젝트 DSP 회사") {
        System(project_dsp, "프로젝트 DSP", "캠페인 선택·입찰·예산 예약과 과금을 책임진다.")
    }

    System_Ext(external_dsp_1, "외부 DSP 1", "독립 회사를 나타내는 모의 입찰 시스템이다.")
    System_Ext(external_dsp_2, "외부 DSP 2", "독립 회사를 나타내는 모의 입찰 시스템이다.")

    Rel(supplier, ssp, "경매 요청")
    Rel(ad_client, ssp, "렌더링 성공 통지")
    Rel(ssp, project_dsp, "입찰 요청·응답과 nurl·lurl·burl")
    Rel(ssp, external_dsp_1, "입찰 요청·응답과 통지")
    Rel(ssp, external_dsp_2, "입찰 요청·응답과 통지")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="2")
```

- SSP와 프로젝트 DSP는 모두 구현 범위지만 서로 다른 회사·소프트웨어 시스템이다.
- 외부 DSP는 프로젝트 DSP 게이트웨이를 사용하지 않는다.
- 공급자 애플리케이션, 광고 표시 클라이언트와 외부 DSP 내부는 구현하지 않는다.
