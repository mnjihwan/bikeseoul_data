# bikeseoul_data

> 현재 로컬 실행은 [통합 저장소 README](https://github.com/mnjihwan/Ddareungi-rider/blob/develop/README.md)와 통합 `../compose.yaml`을 기준으로 합니다. MySQL은 라이더와 공유하며, `.env`는 최상위 파일을 사용합니다. Compose가 환경변수를 주입하며, `gradle bootRun`은 `.env`를 자동으로 읽지 않습니다. 아래 내용은 원천 데이터와 초기 설계 설명입니다.

서울시 공공자전거의 마스터·상세·실시간·이용통계 API와 OD/이용통계 CSV를 MySQL에 적재하는 프로젝트입니다.
누적 데이터는 `Ddareungi-rider`의 검색 기준 데이터와 수요 예측 학습 데이터로 사용합니다.

## 현재 구현 범위 (2026-10-12)

- Java 21 / Spring Boot 4.1 / Gradle
- OA-21235 `bikeStationMaster` 마스터 API 일일 동기화
- OA-15493 `bikeList` 실시간 API 5분 스냅샷 수집 및 기존 시간별 이력 유지
- OA-13252 `tbCycleStationInfo` 상세 API로 공식 번호·ST ID 연결
- OA-21229 공식 일별 ZIP 자동 다운로드와 OD CSV의 트랜잭션 교체
- 시간별·일별·월별 통계 API 및 보유 이용통계 CSV를 대여소 기준으로 집계
- 원본의 빈 값·목적지 `X` 보존, 파일 체크섬과 수집 완료/실패 기록
- 1,000건 제한을 고려한 API 페이지 분할 호출
- Flyway 스키마 관리
- Docker Compose 기반 로컬 MySQL

## 실행 준비

1. `.env.example`을 참고해 `SEOUL_API_KEY`를 설정합니다.
2. 최상위 `scripts/start-local.ps1 -Mode Real`로 실행하거나 아래의 단발 수집 명령을 사용합니다.
3. OA-21229 ZIP을 내려받아 CSV를 `data/oa-21229/`에 압축 해제합니다.
4. 이용통계 CSV는 `data/usage/`에 둡니다.
5. 추가 수집은 `EXTENDED_COLLECTION_ENABLED`, 다운로드는 `OD_DOWNLOAD_ENABLED`로 제어합니다. 기본값은 true입니다.

프로젝트 최상위에서 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\collect-data-once.ps1`을 실행하면 다운로드된 원본을 복사하고, 웹을 켜지 않고 수집·DB 저장·상태 조회를 한 번 수행한 뒤 처음에 꺼져 있던 DB/Docker를 다시 종료합니다. [전체 데이터셋·주기·실행 및 검증 안내](../docs/data-collection.md)를 참고하세요.

OA-21229는 Open API가 아니라 서울 열린데이터광장에서 제공하는 ZIP/CSV 파일 데이터입니다.
공식 페이지에 게시된 일별 파일을 자동으로 내려받으며 보유 ZIP/CSV도 입력 폴더에서 읽습니다.
같은 파일은 SHA-256으로 건너뛰고 변경 파일은 기존 파일의 행 삭제·새 행 적재·완료 기록을 하나의 트랜잭션으로 교체합니다.

## 데이터 흐름

```text
OA-21235 -> bikeStationMaster -> station
OA-15493 -> bikeList -> station + station_history + station_inventory_5m
OA-13252 -> tbCycleStationInfo -> station_number / ST-ID mapping
OA-21229 -> MS949 / UTF-8 CSV -> station_flow_5m
OA-15245/15246/15248/15249 -> API / CSV -> station_usage
```

`station_history`는 기존 시간별 이력, `station_inventory_5m`은 새 5분 재고 이력입니다.
과거 이용 수요는 `station_flow_5m`과 `station_usage`에 구분해 저장합니다. 과거에 폐쇄된 대여소도 보존하기 위해 OD 테이블에는
마스터 테이블 외래키를 강제하지 않습니다.

## 공식 데이터셋

| ID | 용도 | 서비스/형식 |
|---|---|---|
| OA-21229 | AI 학습용 대여소 간 이용량 | `tpss_bcycl_od_statnhm_*.csv`, MS949 / UTF-8 자동 구분 |
| OA-15493 | 현재 자전거 수와 거치율 | `bikeList`, JSON |
| OA-21235 | 대여소 ID·주소·좌표 | `bikeStationMaster`, JSON |

## 다음 단계

- 누락된 과거 기간의 명시적 추가 수집 및 데이터 품질 분석
- API·CSV 수집 실패의 운영 알림과 재시도 정책 고도화
- 기상·공휴일 데이터 결합
- 별도 [Python AI 패키지](../ai/README.md)의 수요 기준 모델·LightGBM을 평가하고 실제 재고 예측으로 확장

## Git 개발 규칙

[Git Flow·Conventional Commits](GIT_WORKFLOW.md)를 따릅니다. 통합 실행과 AI 소스는 [Ddareungi-rider 개발 브랜치](https://github.com/mnjihwan/Ddareungi-rider/tree/develop)에서 관리하며, 이 저장소는 그 프로젝트의 submodule로 연결됩니다.
