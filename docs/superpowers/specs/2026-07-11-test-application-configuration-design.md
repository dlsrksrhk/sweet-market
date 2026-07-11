# 테스트 전용 애플리케이션 설정 설계

## 목적

Codex와 개발자가 별도 Spring 프로파일을 지정하지 않고 `backend`에서 `./gradlew test`를 실행해도 동일한 테스트 설정을 사용하게 한다. 로컬 또는 운영용 `src/main/resources/application.yaml`의 값과 상태에 테스트 결과가 좌우되지 않도록 한다.

## 구성

`backend/src/test/resources/application.yaml`을 추가한다. Gradle의 테스트 클래스패스는 `src/test/resources`를 우선하므로 테스트 실행 시 이 파일이 자동으로 선택된다. `application-test.yaml`, `@ActiveProfiles`, Gradle 시스템 프로퍼티는 추가하지 않는다.

테스트 설정에는 다음 값만 둔다.

- Flyway 활성화, 기존 스키마 인계를 위한 baseline-on-migrate 활성화 및 baseline version 0
- 기본 JPA schema 동작을 위한 ddl-auto update
- 테스트 중 불필요한 Batch job과 scheduler 비활성화. scheduler는 애플리케이션 소유 속성인 `market.scheduling.enabled=false`로 비활성화한다.
- 32바이트 이상인 고정 테스트 JWT secret과 토큰 유효시간
- 이미지 파일을 저장소의 `backend/build/test-product-images` 아래로 격리
- 현재 테스트가 사용하는 multipart 제한과 로컬 테스트 CORS origin
- 기존 Batch schema 초기화 방식

데이터베이스 URL, 사용자명, 비밀번호는 YAML에 고정하지 않는다. Spring context를 시작하는 테스트가 기존 `@DynamicPropertySource`를 통해 Testcontainers 접속 정보를 주입한다.

## 스케줄링 활성화 경계

`MarketApplication`에서 무조건 `@EnableScheduling`을 제거하고, `OrderAutoConfirmSchedulingConfig`가 `market.scheduling.enabled`에 따라 스케줄링 인프라를 활성화하는 단일 경계가 되게 한다. 이 속성은 `matchIfMissing=true`로 기본 활성화되어 로컬·개발·운영의 기존 동작을 유지하고, 테스트 YAML에서만 `false`로 설정한다. 기존 local/dev 프로파일 설정은 더 이상 별도의 `@EnableScheduling`을 제공하지 않으므로 전역 비활성화를 우회할 수 없다.

## 우선순위와 예외

공통 테스트 설정보다 개별 테스트의 요구가 더 구체적이면 기존 재정의를 유지한다.

- 통합 테스트는 Hibernate `create`와 Flyway 비활성화를 동적으로 지정한다.
- 기존 스키마 마이그레이션 테스트는 Hibernate `none`을 지정한다.
- 빈 DB 시작 테스트는 공통 Flyway 및 Hibernate 설정을 사용한다.

운영용 설정을 검증하는 테스트를 새로 만들지는 않는다. 이 변경의 범위는 테스트 실행 환경의 재현성과 로컬 운영 설정으로부터의 격리다.

## 검증

프로파일이나 추가 JVM 옵션 없이 다음 명령을 실행한다.

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test --rerun-tasks
```

성공 조건은 전체 테스트 통과, 테스트 리포트의 failure 및 error 0건, 비활성 시 스케줄링 인프라 미생성, 속성 미지정 시 스케줄링 인프라 생성이다. `git diff --check`도 통과해야 한다.
