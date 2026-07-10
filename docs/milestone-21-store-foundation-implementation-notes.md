# Milestone 21 Store Foundation Implementation Notes

## 대규모 테이블 인덱스 생성

V1은 기존 소규모 서비스 데이터의 스키마 전환을 위한 단일 트랜잭션 Flyway 마이그레이션으로 유지합니다. PostgreSQL의 `CREATE INDEX CONCURRENTLY`는 트랜잭션 안에서 실행할 수 없으므로, 이를 지금 추가하면 비트랜잭션 마이그레이션 분리와 운영 배포 순서가 필요합니다. 대용량 운영 데이터에 대한 온라인 인덱스 생성은 실제 테이블 크기와 잠금 허용 시간을 확인한 별도 운영 마이그레이션으로 계획합니다.
