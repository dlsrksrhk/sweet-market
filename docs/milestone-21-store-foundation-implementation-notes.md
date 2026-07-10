# Milestone 21 Store Foundation Implementation Notes

## Fresh database startup boundary

현재 프로젝트에는 M21 이전 엔티티 전체를 만드는 Flyway baseline migration이 없습니다. 따라서 기본 Hibernate 설정은 `update`를 사용합니다. 기존 스키마에서는 Flyway V1이 상점 데이터 백필과 M21 스키마 변경을 수행하고, Hibernate는 누락된 엔티티 테이블만 비파괴적으로 보완합니다. 빈 데이터베이스에서는 Flyway가 M21 테이블을 먼저 만들고 Hibernate가 기존 JPA 테이블을 생성하므로 애플리케이션을 시작할 수 있습니다. 전체 baseline migration을 도입한 뒤에는 `validate`로 되돌려 Flyway를 유일한 DDL 소유자로 전환합니다.

## 대규모 테이블 인덱스 생성

V1은 기존 소규모 서비스 데이터의 스키마 전환을 위한 단일 트랜잭션 Flyway 마이그레이션으로 유지합니다. PostgreSQL의 `CREATE INDEX CONCURRENTLY`는 트랜잭션 안에서 실행할 수 없으므로, 이를 지금 추가하면 비트랜잭션 마이그레이션 분리와 운영 배포 순서가 필요합니다. 대용량 운영 데이터에 대한 온라인 인덱스 생성은 실제 테이블 크기와 잠금 허용 시간을 확인한 별도 운영 마이그레이션으로 계획합니다.
