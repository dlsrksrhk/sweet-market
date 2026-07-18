# Milestone 32 External Integration Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add independently runnable Mock Payment Gateway and Mock Delivery Provider Spring Boot applications, versioned signed-HTTP contracts, Sweet Market webhook probe boundaries, and a Docker Compose verification path without sharing runtime code or databases.

**Architecture:** Keep `backend`, `mock-payment-gateway`, and `mock-delivery-provider` as three standalone Gradle builds. Each application owns its PostgreSQL database and replay ledger; the only shared artifacts are OpenAPI JSON documents and HMAC test vectors under `contracts`. M32 proves authenticated commands and webhook-shaped inbound probes, request-size and replay defense, correlation propagation, container isolation, and restart-safe persistence before M33 adds payment behavior.

**Tech Stack:** Java 21, Spring Boot 3.5.15-SNAPSHOT, Gradle 8.14.5 wrapper from `backend`, Spring MVC, Spring Security, JDBC, Flyway, PostgreSQL 17, Testcontainers, Docker Compose, Node.js built-in test runner, PowerShell.

## Global Constraints

- New JUnit `@Test` method names use Korean words separated by underscores.
- `backend` continues to run on JDK 21 with a configured `JWT_SECRET`.
- The two simulators are standalone builds invoked with `backend\gradlew.bat -p <project>`; they do not depend on the `backend` Gradle project.
- Every application owns a distinct PostgreSQL database, Flyway history, configuration namespace, process, and port.
- No simulator imports `com.sweet.market.*`; Sweet Market does not import simulator packages.
- No shared Java DTO, entity, repository, service, or HMAC implementation module is introduced.
- OpenAPI JSON and protocol test vectors are the only cross-application source artifacts.
- Local ports are Sweet Market `8080`, Payment Gateway `8081`, Delivery Provider `8082`, and PostgreSQL hosts `15432`, `15433`, `15434`.
- Signed requests use `X-Api-Key`, `X-Key-Id`, `X-Request-Id`, `X-Timestamp`, `X-Signature`, `X-Correlation-Id`, and optional W3C `traceparent`.
- `X-Timestamp` is an epoch-second value and is accepted only within plus or minus 300 seconds.
- `X-Request-Id` is a UUID and is claimed once per authenticated source.
- Successful replay claims expire after 10 minutes; an hourly cleanup deletes at most 1,000 expired rows per transaction.
- The raw body limit is 1,048,576 bytes and is enforced before Jackson materialization, including chunked requests.
- The canonical HMAC payload is `v1`, key ID, epoch seconds, request ID, uppercase method, raw request target, and lowercase body SHA-256 joined by `\n`; the signature is lowercase HMAC-SHA256 hex.
- Verify HMAC before claiming the replay ID so invalid requests cannot fill the replay table.
- Health endpoints are unsigned; `/api/v1/**` simulator probes and `/api/integrations/**` Sweet Market probes are always signed.
- Secrets are environment-driven and must never appear in committed contracts, logs, traces, responses, or verification artifacts.
- M32 does not implement payment, cancellation, refund, shipment, return, durable business webhooks, Kafka, or the observability stack.

---

## Planned File Structure

### Shared protocol artifacts

- `contracts/payment-gateway-v1.openapi.json` — Payment Gateway M32 probe contract and security headers.
- `contracts/payment-gateway-webhooks-v1.openapi.json` — Sweet Market payment webhook probe contract.
- `contracts/delivery-provider-v1.openapi.json` — Delivery Provider M32 probe contract and security headers.
- `contracts/delivery-provider-webhooks-v1.openapi.json` — Sweet Market delivery webhook probe contract.
- `contracts/hmac-v1-test-vectors.json` — canonicalization, digest, and signature fixtures used by all three applications.
- `contracts/contracts.test.mjs` — dependency-free schema and protocol consistency checks.

### Mock Payment Gateway

- `mock-payment-gateway/settings.gradle`, `build.gradle` — standalone Java 21 Spring Boot build.
- `mock-payment-gateway/src/main/java/com/sweet/market/gateway/MockPaymentGatewayApplication.java` — application entry point.
- `mock-payment-gateway/src/main/java/com/sweet/market/gateway/probe/ProbeController.java` — signed M32 probe endpoint.
- `mock-payment-gateway/src/main/java/com/sweet/market/gateway/security/*` — independent cached-body, HMAC, replay, filter, properties, and security configuration.
- `mock-payment-gateway/src/main/java/com/sweet/market/gateway/web/CorrelationIdFilter.java` — correlation and trace-context preservation.
- `mock-payment-gateway/src/main/resources/db/migration/V1__create_integration_request_replays.sql` — replay ledger.
- `mock-payment-gateway/src/main/resources/application.yaml` — port, datasource, Flyway, management, and credential settings.
- `mock-payment-gateway/src/test/**` — unit, MockMvc, migration, security, and Testcontainers coverage.

### Mock Delivery Provider

- `mock-delivery-provider/settings.gradle`, `build.gradle` — standalone Java 21 Spring Boot build.
- `mock-delivery-provider/src/main/java/com/sweet/market/provider/MockDeliveryProviderApplication.java` — application entry point.
- `mock-delivery-provider/src/main/java/com/sweet/market/provider/probe/ProbeController.java` — signed M32 probe endpoint.
- `mock-delivery-provider/src/main/java/com/sweet/market/provider/security/*` — independent protocol implementation.
- `mock-delivery-provider/src/main/java/com/sweet/market/provider/web/CorrelationIdFilter.java` — correlation and trace-context preservation.
- `mock-delivery-provider/src/main/resources/db/migration/V1__create_integration_request_replays.sql` — replay ledger.
- `mock-delivery-provider/src/main/resources/application.yaml` — port, datasource, Flyway, management, and credential settings.
- `mock-delivery-provider/src/test/**` — unit, MockMvc, migration, security, and Testcontainers coverage.

### Sweet Market

- `backend/src/main/java/com/sweet/market/integration/security/*` — independent outbound signer, inbound verifier, replay guard, properties, and filter.
- `backend/src/main/java/com/sweet/market/integration/probe/ExternalProbeWebhookController.java` — payment and delivery signed probe intake.
- `backend/src/main/java/com/sweet/market/integration/web/CorrelationIdFilter.java` — correlation ID response and MDC lifecycle.
- `backend/src/main/resources/db/migration/V16__add_external_integration_security.sql` — source-scoped replay ledger.
- `backend/src/main/resources/application.yaml` — source and outbound credential configuration.
- `backend/src/test/java/com/sweet/market/integration/**` — vector, security, migration, and endpoint tests.

### Runtime verification

- `backend/Dockerfile.integration`, `mock-payment-gateway/Dockerfile`, `mock-delivery-provider/Dockerfile` — independent images.
- `docker-compose.integration.yml` — three applications, three PostgreSQL instances, Redis, readiness, and isolated volumes.
- `scripts/verify-m32-integration.ps1` — contract, build, container, signed-probe, isolation, and restart verification.
- `scripts/verify-m32-boundaries.ps1` — import and database-boundary audit.
- `docs/superpowers/handoffs/2026-07-18-milestone-32-external-integration-foundation-handoff.md` — verified M32 boundary and M33 starting point.

---

### Task 1: Version The External HTTP Contracts And HMAC Vectors

**Files:**
- Create: `contracts/payment-gateway-v1.openapi.json`
- Create: `contracts/payment-gateway-webhooks-v1.openapi.json`
- Create: `contracts/delivery-provider-v1.openapi.json`
- Create: `contracts/delivery-provider-webhooks-v1.openapi.json`
- Create: `contracts/hmac-v1-test-vectors.json`
- Create: `contracts/contracts.test.mjs`

**Interfaces:**
- Consumes: The header names, canonical payload, port assignments, 300-second skew, and 1 MiB raw-body limit from Global Constraints.
- Produces: `POST /api/v1/probes`, Sweet Market payment/delivery `POST /api/integrations/.../v1/probes`, `ProbeRequest`, `ProbeResponse`, `ProbeWebhook`, and fixed HMAC vector values consumed by Tasks 4-7.

- [ ] **Step 1: Write the failing dependency-free contract test**

Create `contracts/contracts.test.mjs` with Node built-ins only:

```js
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createHash, createHmac } from 'node:crypto'
import test from 'node:test'

const here = new URL('.', import.meta.url)
const load = async (name) => JSON.parse(await readFile(new URL(name, here), 'utf8'))

const requiredHeaders = [
  'X-Api-Key', 'X-Key-Id', 'X-Request-Id', 'X-Timestamp',
  'X-Signature', 'X-Correlation-Id'
]

test('네_계약은_서명된_probe_경로와_공통_헤더를_정의한다', async () => {
  const cases = [
    ['payment-gateway-v1.openapi.json', '/api/v1/probes'],
    ['payment-gateway-webhooks-v1.openapi.json', '/api/integrations/payment-gateway/v1/probes'],
    ['delivery-provider-v1.openapi.json', '/api/v1/probes'],
    ['delivery-provider-webhooks-v1.openapi.json', '/api/integrations/delivery-provider/v1/probes']
  ]

  for (const [name, path] of cases) {
    const document = await load(name)
    assert.equal(document.openapi, '3.1.0')
    const operation = document.paths[path].post
    assert.equal(operation.requestBody.required, true)
    assert.equal(operation['x-raw-body-limit-bytes'], 1_048_576)
    const names = operation.parameters.map((parameter) => parameter.name)
    for (const header of requiredHeaders) assert.ok(names.includes(header), `${name}: ${header}`)
  }
})

test('HMAC_vector는_정의된_canonical_payload와_일치한다', async () => {
  const vector = await load('hmac-v1-test-vectors.json')
  const body = Buffer.from(vector.bodyUtf8, 'utf8')
  const bodyHash = createHash('sha256').update(body).digest('hex')
  const canonical = [
    'v1', vector.keyId, String(vector.timestamp), vector.requestId,
    vector.method.toUpperCase(), vector.rawTarget, bodyHash
  ].join('\n')
  assert.equal(canonical, vector.canonical)
  assert.equal(bodyHash, vector.bodySha256)
  assert.equal(createHmac('sha256', vector.secret).update(canonical).digest('hex'), vector.signature)
})
```

- [ ] **Step 2: Run the test and verify the contracts are absent**

Run:

```powershell
node --test contracts/contracts.test.mjs
```

Expected: FAIL with `ENOENT` for `payment-gateway-v1.openapi.json`.

- [ ] **Step 3: Add the four OpenAPI documents with exact shared shapes**

Each provider document defines `POST /api/v1/probes`; each webhook document defines its exact Sweet Market path. Use OpenAPI `3.1.0`, JSON Schema objects with `additionalProperties: false`, and these schemas:

```json
{
  "ProbeRequest": {
    "type": "object",
    "additionalProperties": false,
    "required": ["message"],
    "properties": {
      "message": { "type": "string", "minLength": 1, "maxLength": 100 }
    }
  },
  "ProbeResponse": {
    "type": "object",
    "additionalProperties": false,
    "required": ["service", "message", "requestId", "correlationId"],
    "properties": {
      "service": { "type": "string" },
      "message": { "type": "string" },
      "requestId": { "type": "string", "format": "uuid" },
      "correlationId": { "type": "string", "format": "uuid" }
    }
  },
  "ProbeWebhook": {
    "type": "object",
    "additionalProperties": false,
    "required": ["source", "message"],
    "properties": {
      "source": { "enum": ["PAYMENT_GATEWAY", "DELIVERY_PROVIDER"] },
      "message": { "type": "string", "minLength": 1, "maxLength": 100 }
    }
  }
}
```

Every operation declares the six required headers, optional `traceparent`, `Content-Type: application/json`, `x-raw-body-limit-bytes: 1048576`, `400`, `401`, `409`, `413`, and a success response. Provider probes return `200` with `ProbeResponse`; Sweet Market webhook probes return `204`.

All error responses reference this exact schema:

```json
{
  "IntegrationError": {
    "type": "object",
    "additionalProperties": false,
    "required": ["code", "message", "requestId"],
    "properties": {
      "code": { "type": "string" },
      "message": { "type": "string" },
      "requestId": { "type": ["string", "null"], "format": "uuid" }
    }
  }
}
```

- [ ] **Step 4: Add a fixed HMAC vector**

Create `contracts/hmac-v1-test-vectors.json` with a non-secret test credential and values generated once by the test algorithm:

```json
{
  "apiKey": "m32-test-api-key",
  "keyId": "m32-test-key-1",
  "secret": "m32-test-hmac-secret-32bytes-minimum",
  "timestamp": 1784386800,
  "requestId": "3b2f8c6a-2f88-4f75-8c7b-4ad40b519a41",
  "method": "POST",
  "rawTarget": "/api/v1/probes",
  "bodyUtf8": "{\"message\":\"m32-contract-probe\"}",
  "bodySha256": "1858662b42bc7be12235617df1cc1938fb5a3488e7132fc3a035bf5e8a4327d4",
  "canonical": "v1\nm32-test-key-1\n1784386800\n3b2f8c6a-2f88-4f75-8c7b-4ad40b519a41\nPOST\n/api/v1/probes\n1858662b42bc7be12235617df1cc1938fb5a3488e7132fc3a035bf5e8a4327d4",
  "signature": "00c0d3767207c817239881c692fb37de931444242e18186c08a95c51114cd2ac"
}
```

- [ ] **Step 5: Run contract verification**

Run:

```powershell
node --test contracts/contracts.test.mjs
```

Expected: `2` tests pass and no dependency installation occurs.

- [ ] **Step 6: Commit the protocol artifacts**

```powershell
git add contracts
git commit -m "docs: define milestone 32 integration contracts"
```

---

### Task 2: Bootstrap The Independent Mock Payment Gateway

**Files:**
- Create: `mock-payment-gateway/settings.gradle`
- Create: `mock-payment-gateway/build.gradle`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/MockPaymentGatewayApplication.java`
- Create: `mock-payment-gateway/src/main/resources/application.yaml`
- Create: `mock-payment-gateway/src/main/resources/db/migration/V1__create_integration_request_replays.sql`
- Create: `mock-payment-gateway/src/test/java/com/sweet/market/gateway/MockPaymentGatewayApplicationTest.java`
- Create: `mock-payment-gateway/src/test/java/com/sweet/market/gateway/support/GatewayIntegrationTestSupport.java`
- Create: `mock-payment-gateway/src/test/java/com/sweet/market/gateway/migration/GatewayFreshDatabaseStartupTest.java`

**Interfaces:**
- Consumes: JDK 21 and Gradle wrapper `backend\gradlew.bat`.
- Produces: Standalone application `com.sweet.market.gateway.MockPaymentGatewayApplication`, port `8081`, datasource environment prefix `PAYMENT_GATEWAY_DB_*`, and table `integration_request_replays` consumed by Task 4.

- [ ] **Step 1: Create the standalone build and a failing context test**

Use `rootProject.name = 'mock-payment-gateway'`. The build applies the same Spring Boot and dependency-management plugin versions as `backend` and contains only:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.flywaydb:flyway-core'
    runtimeOnly 'org.postgresql:postgresql'
    runtimeOnly 'org.flywaydb:flyway-database-postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

Configure Java toolchain 21 and `useJUnitPlatform()`. Add:

```java
@SpringBootTest
class MockPaymentGatewayApplicationTest {

    @Test
    void 결제_게이트웨이_애플리케이션이_기동한다() {
    }
}
```

- [ ] **Step 2: Run the context test and verify the entry point is absent**

Run:

```powershell
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\backend\gradlew.bat -p mock-payment-gateway test --tests '*MockPaymentGatewayApplicationTest'
```

Expected: FAIL because no `@SpringBootConfiguration` is present.

- [ ] **Step 3: Add the application entry point and configuration**

```java
package com.sweet.market.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MockPaymentGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockPaymentGatewayApplication.class, args);
    }
}
```

Set `server.port: ${PAYMENT_GATEWAY_PORT:8081}`, application name `mock-payment-gateway`, PostgreSQL URL `jdbc:postgresql://${PAYMENT_GATEWAY_DB_HOST:localhost}:${PAYMENT_GATEWAY_DB_PORT:15433}/${PAYMENT_GATEWAY_DB_NAME:payment_gateway}`, username/password environment fallbacks for local development, Flyway enabled, SQL init disabled, and actuator exposure limited to `health,info`.

- [ ] **Step 4: Add the replay migration and fresh-database test**

Migration:

```sql
CREATE TABLE integration_request_replays (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(80) NOT NULL,
    request_id UUID NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_gateway_replay_client_request UNIQUE (client_id, request_id)
);

CREATE INDEX idx_gateway_replay_expiry
    ON integration_request_replays (expires_at, id);
```

`GatewayIntegrationTestSupport` owns a static PostgreSQL 17 Testcontainer and a `@DynamicPropertySource` for datasource credentials. `GatewayFreshDatabaseStartupTest` extends it and asserts the table and unique constraint exist through `JdbcTemplate`.

- [ ] **Step 5: Run the standalone application tests**

Run:

```powershell
.\backend\gradlew.bat -p mock-payment-gateway test
```

Expected: context and migration tests pass; no Sweet Market application classes are on the classpath.

- [ ] **Step 6: Commit the Payment Gateway skeleton**

```powershell
git add mock-payment-gateway
git commit -m "build: bootstrap mock payment gateway"
```

---

### Task 3: Bootstrap The Independent Mock Delivery Provider

**Files:**
- Create: `mock-delivery-provider/settings.gradle`
- Create: `mock-delivery-provider/build.gradle`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/MockDeliveryProviderApplication.java`
- Create: `mock-delivery-provider/src/main/resources/application.yaml`
- Create: `mock-delivery-provider/src/main/resources/db/migration/V1__create_integration_request_replays.sql`
- Create: `mock-delivery-provider/src/test/java/com/sweet/market/provider/MockDeliveryProviderApplicationTest.java`
- Create: `mock-delivery-provider/src/test/java/com/sweet/market/provider/support/ProviderIntegrationTestSupport.java`
- Create: `mock-delivery-provider/src/test/java/com/sweet/market/provider/migration/ProviderFreshDatabaseStartupTest.java`

**Interfaces:**
- Consumes: JDK 21 and Gradle wrapper `backend\gradlew.bat`.
- Produces: Standalone application `com.sweet.market.provider.MockDeliveryProviderApplication`, port `8082`, datasource environment prefix `DELIVERY_PROVIDER_DB_*`, and independent `integration_request_replays` table consumed by Task 5.

- [ ] **Step 1: Create the standalone build and failing context test**

Use `rootProject.name = 'mock-delivery-provider'`, Java 21, `useJUnitPlatform()`, and this complete dependency set:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.flywaydb:flyway-core'
    runtimeOnly 'org.postgresql:postgresql'
    runtimeOnly 'org.flywaydb:flyway-database-postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

Add:

```java
@SpringBootTest
class MockDeliveryProviderApplicationTest {

    @Test
    void 배송_제공자_애플리케이션이_기동한다() {
    }
}
```

- [ ] **Step 2: Run the test and verify the entry point is absent**

```powershell
.\backend\gradlew.bat -p mock-delivery-provider test --tests '*MockDeliveryProviderApplicationTest'
```

Expected: FAIL because no `@SpringBootConfiguration` is present.

- [ ] **Step 3: Add the application entry point and port/database configuration**

Create `MockDeliveryProviderApplication` with `@SpringBootApplication`, `@ConfigurationPropertiesScan`, `@EnableScheduling`, and the standard `main` method. Configure application name `mock-delivery-provider`, port `${DELIVERY_PROVIDER_PORT:8082}`, and database `jdbc:postgresql://${DELIVERY_PROVIDER_DB_HOST:localhost}:${DELIVERY_PROVIDER_DB_PORT:15434}/${DELIVERY_PROVIDER_DB_NAME:delivery_provider}` with its own credentials. Expose only actuator `health,info`.

- [ ] **Step 4: Add the independent replay migration and startup test**

Create the provider-owned migration in full:

```sql
CREATE TABLE integration_request_replays (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(80) NOT NULL,
    request_id UUID NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_provider_replay_client_request UNIQUE (client_id, request_id)
);

CREATE INDEX idx_provider_replay_expiry
    ON integration_request_replays (expires_at, id);
```

`ProviderIntegrationTestSupport` starts its own PostgreSQL 17 container. The startup test asserts the provider table and constraint without importing gateway test code.

- [ ] **Step 5: Run the standalone provider tests**

```powershell
.\backend\gradlew.bat -p mock-delivery-provider test
```

Expected: context and migration tests pass independently.

- [ ] **Step 6: Commit the Delivery Provider skeleton**

```powershell
git add mock-delivery-provider
git commit -m "build: bootstrap mock delivery provider"
```

---

### Task 4: Protect The Payment Gateway Probe With Independent HMAC And Replay Defense

**Files:**
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/security/IntegrationSecurityProperties.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/security/SignedRequest.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/security/HmacCanonicalizer.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/security/ReplayGuard.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/security/JdbcReplayGuard.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/security/ReplayCleanupScheduler.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/security/CachedBodyHttpServletRequest.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/security/SignedRequestFilter.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/security/GatewaySecurityConfig.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/web/IntegrationErrorResponse.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/web/IntegrationExceptionHandler.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/probe/ProbeRequest.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/probe/ProbeResponse.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/probe/ProbeController.java`
- Create: `mock-payment-gateway/src/test/java/com/sweet/market/gateway/security/HmacCanonicalizerTest.java`
- Create: `mock-payment-gateway/src/test/java/com/sweet/market/gateway/security/GatewaySignedProbeApiTest.java`
- Create: `mock-payment-gateway/src/test/java/com/sweet/market/gateway/security/ReplayCleanupSchedulerTest.java`
- Modify: `mock-payment-gateway/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: HMAC vector from Task 1 and replay table from Task 2.
- Produces: `HmacCanonicalizer.canonicalize(...)`, `ReplayGuard.tryClaim(...)`, signed `POST /api/v1/probes`, and stable request attributes `integration.requestId`/`integration.clientId`.

- [ ] **Step 1: Write failing vector and API security tests**

The canonicalizer test loads `../contracts/hmac-v1-test-vectors.json` and asserts body digest, canonical string, and signature. The MockMvc API tests cover:

```java
@Test void 올바른_서명은_probe를_허용한다()
@Test void 현재키와_다음키는_rotation_중_모두_허용한다()
@Test void 잘못된_서명은_저장하지_않고_거부한다()
@Test void 만료된_timestamp를_거부한다()
@Test void 같은_requestId의_replay를_거부한다()
@Test void 같은_requestId라도_다른_client는_독립적으로_처리한다()
@Test void 1MiB를_넘는_chunked_body를_JSON_parse전에_거부한다()
@Test void actuator_health는_서명없이_허용한다()
@Test void 만료된_replay를_한번에_1000개까지만_정리한다()
```

Use a mutable test `Clock` fixed to the vector timestamp and test properties with two key IDs. Assert invalid signatures leave `integration_request_replays` empty; replay returns `409`; oversized body returns `413`; authentication failures return `401`.
Every non-2xx response must match `IntegrationError(code, message, requestId)` from the OpenAPI document; validation errors use code `INTEGRATION_REQUEST_INVALID`, authentication errors `INTEGRATION_AUTHENTICATION_FAILED`, replay `INTEGRATION_REPLAY_DETECTED`, and oversized input `INTEGRATION_BODY_TOO_LARGE`.

- [ ] **Step 2: Run the focused tests and verify RED**

```powershell
.\backend\gradlew.bat -p mock-payment-gateway test --tests '*HmacCanonicalizerTest' --tests '*GatewaySignedProbeApiTest'
```

Expected: FAIL because the security and probe types do not exist.

- [ ] **Step 3: Implement the canonicalizer and replay interface**

Use exact signatures:

```java
public record SignedRequest(
        String apiKey, String keyId, UUID requestId, Instant timestamp,
        String method, String rawTarget, byte[] body, String signature
) {}

public final class HmacCanonicalizer {
    public String bodySha256(byte[] body) {
        return HexFormat.of().formatHex(digest("SHA-256", body));
    }

    public String canonicalize(
            String keyId, Instant timestamp, UUID requestId,
            String method, String rawTarget, byte[] body
    ) {
        return String.join("\n",
                "v1", keyId, Long.toString(timestamp.getEpochSecond()),
                requestId.toString(), method.toUpperCase(Locale.ROOT),
                rawTarget, bodySha256(body));
    }

    public String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public boolean matches(String expectedHex, String suppliedHex) {
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.US_ASCII),
                suppliedHex.getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] digest(String algorithm, byte[] value) {
        try {
            return MessageDigest.getInstance(algorithm).digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is unavailable", exception);
        }
    }
}

public interface ReplayGuard {
    boolean tryClaim(String clientId, UUID requestId, Instant receivedAt, Instant expiresAt);
    int deleteExpired(Instant now, int limit);
}
```

`JdbcReplayGuard.tryClaim` uses `INSERT ... ON CONFLICT DO NOTHING`; cleanup deletes a bounded ID subquery ordered by `expires_at, id`.

- [ ] **Step 4: Implement bounded raw-body authentication**

`SignedRequestFilter` applies only to `/api/v1/**` and performs this exact order:

```text
read at most 1,048,577 bytes -> 413 if oversized
parse required headers -> 400 if malformed
resolve API key and key ID -> 401 if unknown
check timestamp against Clock -> 401 if outside 300 seconds
canonicalize raw request target and body
constant-time signature comparison -> 401 if invalid
tryClaim replay ID with `expiresAt = receivedAt + replayRetention` -> 409 if already claimed
wrap cached body and continue
```

The cached wrapper must return a new `ServletInputStream` over the stored bytes and the original character encoding. Configure a testable `Clock` bean and do not log headers or canonical strings.

- [ ] **Step 5: Add strict properties and the probe endpoint**

```java
@ConfigurationProperties("gateway.integration-security")
@Validated
public record IntegrationSecurityProperties(
        @NotNull Duration allowedClockSkew,
        @Min(1) int maxBodyBytes,
        @NotNull Duration replayRetention,
        @Min(1) int cleanupBatchSize,
        @NotEmpty List<Client> clients
) {
    public record Client(@NotBlank String clientId, @NotBlank String apiKey, @NotEmpty List<Key> keys) {}
    public record Key(@NotBlank String keyId, @NotBlank String secret) {}
}
```

`ProbeController` returns service `mock-payment-gateway`, the echoed message, authenticated request ID, and correlation ID. `GatewaySecurityConfig` permits `/actuator/health` and requires the signed filter for `/api/v1/**`; all other routes are denied until later milestones define them.

Bind credentials without source defaults:

```yaml
gateway:
  integration-security:
    allowed-clock-skew: PT5M
    max-body-bytes: 1048576
    replay-retention: PT10M
    cleanup-batch-size: 1000
    clients:
      - client-id: sweet-market
        api-key: ${PAYMENT_GATEWAY_MERCHANT_API_KEY}
        keys:
          - key-id: ${PAYMENT_GATEWAY_HMAC_CURRENT_KEY_ID}
            secret: ${PAYMENT_GATEWAY_HMAC_CURRENT_SECRET}
          - key-id: ${PAYMENT_GATEWAY_HMAC_NEXT_KEY_ID}
            secret: ${PAYMENT_GATEWAY_HMAC_NEXT_SECRET}
```

Integration tests override all six values; no secret fallback is committed.

The filter serializes `IntegrationErrorResponse` directly for pre-controller failures. `IntegrationExceptionHandler` maps probe validation failures to the same schema, preserving the authenticated request ID when available.
`ReplayCleanupScheduler` runs hourly, calls `deleteExpired(clock.instant(), cleanupBatchSize)`, and never issues an unbounded delete.

- [ ] **Step 6: Run all Gateway tests**

```powershell
.\backend\gradlew.bat -p mock-payment-gateway test
```

Expected: all context, migration, vector, rotation, replay, body-limit, and API tests pass.

- [ ] **Step 7: Commit the signed Gateway boundary**

```powershell
git add mock-payment-gateway
git commit -m "feat: secure mock payment gateway probe"
```

---

### Task 5: Protect The Delivery Provider Probe With An Independent Implementation

**Files:**
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/security/IntegrationSecurityProperties.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/security/SignedRequest.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/security/HmacCanonicalizer.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/security/ReplayGuard.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/security/JdbcReplayGuard.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/security/ReplayCleanupScheduler.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/security/CachedBodyHttpServletRequest.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/security/SignedRequestFilter.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/security/ProviderSecurityConfig.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/web/IntegrationErrorResponse.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/web/IntegrationExceptionHandler.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/probe/ProbeRequest.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/probe/ProbeResponse.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/probe/ProbeController.java`
- Create: `mock-delivery-provider/src/test/java/com/sweet/market/provider/security/HmacCanonicalizerTest.java`
- Create: `mock-delivery-provider/src/test/java/com/sweet/market/provider/security/ProviderSignedProbeApiTest.java`
- Create: `mock-delivery-provider/src/test/java/com/sweet/market/provider/security/ReplayCleanupSchedulerTest.java`
- Modify: `mock-delivery-provider/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: `contracts/hmac-v1-test-vectors.json` from Task 1 but no Gateway Java code.
- Produces: An independently implemented signed `POST /api/v1/probes` returning service `mock-delivery-provider`.

- [ ] **Step 1: Write provider-owned failing tests**

Create provider tests with these Korean names and no imports from `com.sweet.market.gateway`:

```java
@Test void 공개_vector와_동일한_HMAC을_계산한다()
@Test void 올바른_서명은_배송_probe를_허용한다()
@Test void rotation_중_두_keyId를_허용한다()
@Test void 잘못된_signature와_만료_timestamp를_거부한다()
@Test void replay는_한번만_처리한다()
@Test void 서명실패는_replay_table을_오염시키지_않는다()
@Test void raw_body_limit를_JSON_parse전에_적용한다()
@Test void 만료된_replay를_한번에_1000개까지만_정리한다()
```

Assert the provider's `400/401/409/413` bodies use the exact error codes and fields defined for Task 4 rather than Spring's default error document.

- [ ] **Step 2: Run the provider security tests and verify RED**

```powershell
.\backend\gradlew.bat -p mock-delivery-provider test --tests '*HmacCanonicalizerTest' --tests '*ProviderSignedProbeApiTest'
```

Expected: FAIL because provider security types do not exist.

- [ ] **Step 3: Implement the provider-owned protocol classes**

Implement these provider-owned interfaces under `com.sweet.market.provider.security`; do not depend on or copy compiled Gateway artifacts:

```java
public record SignedRequest(
        String apiKey, String keyId, UUID requestId, Instant timestamp,
        String method, String rawTarget, byte[] body, String signature
) {}

public interface ReplayGuard {
    boolean tryClaim(String clientId, UUID requestId, Instant receivedAt, Instant expiresAt);
    int deleteExpired(Instant now, int limit);
}

public final class HmacCanonicalizer {
    public String bodySha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String canonicalize(
            String keyId, Instant timestamp, UUID requestId,
            String method, String rawTarget, byte[] body
    ) {
        return String.join("\n", "v1", keyId,
                Long.toString(timestamp.getEpochSecond()), requestId.toString(),
                method.toUpperCase(Locale.ROOT), rawTarget, bodySha256(body));
    }

    public String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public boolean matches(String expectedHex, String suppliedHex) {
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.US_ASCII),
                suppliedHex.getBytes(StandardCharsets.US_ASCII));
    }
}
```

The provider filter performs this order without calling Jackson first:

```text
read at most 1,048,577 bytes -> 413 if oversized
parse required headers -> 400 if malformed
resolve API key and key ID -> 401 if unknown
check timestamp against Clock -> 401 if outside 300 seconds
canonicalize raw request target and body
constant-time signature comparison -> 401 if invalid
tryClaim replay ID with `expiresAt = receivedAt + replayRetention` -> 409 if already claimed
wrap cached body and continue
```

Use provider-owned SQL and `provider.integration-security` configuration.
The provider-owned cleanup scheduler runs hourly, calls `deleteExpired(clock.instant(), 1000)`, and never performs an unbounded delete.
`IntegrationSecurityProperties` contains `Duration allowedClockSkew`, `int maxBodyBytes`, `Duration replayRetention`, `int cleanupBatchSize`, and `List<Client> clients`; `Client` contains non-blank `clientId`, non-blank `apiKey`, and non-empty `List<Key> keys`; `Key` contains non-blank `keyId` and `secret`.

- [ ] **Step 4: Implement the Delivery Provider probe and security chain**

The controller returns:

```java
new ProbeResponse(
        "mock-delivery-provider",
        request.message(),
        authenticatedRequestId,
        correlationId
)
```

Permit only health and signed `/api/v1/**`; deny undefined routes. Enforce the same `400/401/409/413` semantics declared in the provider OpenAPI document.

Bind independent provider credentials:

```yaml
provider:
  integration-security:
    allowed-clock-skew: PT5M
    max-body-bytes: 1048576
    replay-retention: PT10M
    cleanup-batch-size: 1000
    clients:
      - client-id: sweet-market
        api-key: ${DELIVERY_PROVIDER_MERCHANT_API_KEY}
        keys:
          - key-id: ${DELIVERY_PROVIDER_HMAC_CURRENT_KEY_ID}
            secret: ${DELIVERY_PROVIDER_HMAC_CURRENT_SECRET}
          - key-id: ${DELIVERY_PROVIDER_HMAC_NEXT_KEY_ID}
            secret: ${DELIVERY_PROVIDER_HMAC_NEXT_SECRET}
```

- [ ] **Step 5: Run all Delivery Provider tests**

```powershell
.\backend\gradlew.bat -p mock-delivery-provider test
```

Expected: all independent provider tests pass, including the public vector.

- [ ] **Step 6: Commit the provider boundary**

```powershell
git add mock-delivery-provider
git commit -m "feat: secure mock delivery provider probe"
```

---

### Task 6: Add Sweet Market Signing And Signed Webhook Probe Intake

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__add_external_integration_security.sql`
- Create: `backend/src/main/java/com/sweet/market/integration/security/ExternalIntegrationProperties.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/ExternalIntegrationSecurityConfiguration.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/ExternalSystem.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/SignedHeaders.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/HmacCanonicalizer.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/ExternalRequestSigner.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/HmacExternalRequestSigner.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/ReplayGuard.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/JdbcReplayGuard.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/ReplayCleanupScheduler.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/CachedBodyHttpServletRequest.java`
- Create: `backend/src/main/java/com/sweet/market/integration/security/SignedWebhookFilter.java`
- Create: `backend/src/main/java/com/sweet/market/integration/probe/ProbeWebhookRequest.java`
- Create: `backend/src/main/java/com/sweet/market/integration/probe/ExternalProbeWebhookController.java`
- Create: `backend/src/main/java/com/sweet/market/integration/web/IntegrationErrorResponse.java`
- Create: `backend/src/main/java/com/sweet/market/integration/web/IntegrationProbeExceptionHandler.java`
- Create: `backend/src/test/java/com/sweet/market/integration/security/ExternalRequestSignerTest.java`
- Create: `backend/src/test/java/com/sweet/market/integration/security/SignedWebhookProbeApiTest.java`
- Create: `backend/src/test/java/com/sweet/market/integration/security/ReplayCleanupSchedulerTest.java`
- Create: `backend/src/test/java/com/sweet/market/store/migration/ExternalIntegrationSecurityMigrationTest.java`
- Modify: `backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.yaml`
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`

**Interfaces:**
- Consumes: Gateway/provider webhook OpenAPI documents and HMAC vector from Task 1.
- Produces: `ExternalRequestSigner.sign(...)` for M34/M36, source-scoped signed probe webhook endpoints, and table `external_integration_request_replays`.

- [ ] **Step 1: Write migration, vector, and endpoint RED tests**

Add tests for:

```java
@Test void 공개_vector와_동일한_외부요청_signature를_생성한다()
@Test void 결제_gateway의_서명된_probe_webhook을_수신한다()
@Test void 배송_provider의_서명된_probe_webhook을_수신한다()
@Test void path의_source와_body의_source가_다르면_거부한다()
@Test void source별_API_key와_keyId를_분리한다()
@Test void webhook_replay와_변조와_만료를_거부한다()
@Test void 서명실패는_replay를_claim하지_않는다()
@Test void 1MiB초과_webhook을_parse전에_거부한다()
@Test void 만료된_source_replay를_한번에_1000개까지만_정리한다()
```

Migration test asserts source/request uniqueness and expiry index use on representative rows.

- [ ] **Step 2: Run focused backend tests and verify RED**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.integration.*' --tests '*ExternalIntegrationSecurityMigrationTest'
```

Expected: FAIL because the migration and integration boundary are absent.

- [ ] **Step 3: Add source-scoped replay persistence**

Migration:

```sql
CREATE TABLE external_integration_request_replays (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(40) NOT NULL,
    request_id UUID NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_external_replay_source_request UNIQUE (source, request_id)
);

CREATE INDEX idx_external_replay_expiry
    ON external_integration_request_replays (expires_at, id);
```

Add this table to `IntegrationTestSupport.cleanUp()` only if individual integration tests do not already truncate it in setup; never truncate another service's database.

- [ ] **Step 4: Implement Sweet Market properties and outbound signer**

Use distinct inbound sources and outbound destinations:

```java
public enum ExternalSystem { PAYMENT_GATEWAY, DELIVERY_PROVIDER }

public record SignedHeaders(
        String apiKey, String keyId, UUID requestId,
        long timestamp, String signature, String correlationId
) {}

public interface ExternalRequestSigner {
    SignedHeaders sign(
            ExternalSystem destination, String method, String rawTarget,
            UUID requestId, Instant timestamp, byte[] body, String correlationId
    );
}
```

`ExternalIntegrationProperties` has `enabled`, `allowedClockSkew`, `maxBodyBytes`, `replayRetention`, `cleanupBatchSize`, inbound credentials by source, and outbound credentials by destination. Resolve credentials by explicit enum rather than URL substring.

Use separate credentials by direction. Sweet Market outbound payment values (`MARKET_PAYMENT_GATEWAY_*`) match the Gateway's inbound `PAYMENT_GATEWAY_*` values; outbound delivery values match `DELIVERY_PROVIDER_*`. Sweet Market inbound webhook values are separate:

```yaml
market:
  external-integrations:
    allowed-clock-skew: PT5M
    max-body-bytes: 1048576
    replay-retention: PT10M
    cleanup-batch-size: 1000
    inbound:
      payment-gateway:
        api-key: ${MARKET_PAYMENT_WEBHOOK_API_KEY}
        current-key-id: ${MARKET_PAYMENT_WEBHOOK_CURRENT_KEY_ID}
        current-secret: ${MARKET_PAYMENT_WEBHOOK_CURRENT_SECRET}
        next-key-id: ${MARKET_PAYMENT_WEBHOOK_NEXT_KEY_ID}
        next-secret: ${MARKET_PAYMENT_WEBHOOK_NEXT_SECRET}
      delivery-provider:
        api-key: ${MARKET_DELIVERY_WEBHOOK_API_KEY}
        current-key-id: ${MARKET_DELIVERY_WEBHOOK_CURRENT_KEY_ID}
        current-secret: ${MARKET_DELIVERY_WEBHOOK_CURRENT_SECRET}
        next-key-id: ${MARKET_DELIVERY_WEBHOOK_NEXT_KEY_ID}
        next-secret: ${MARKET_DELIVERY_WEBHOOK_NEXT_SECRET}
    outbound:
      payment-gateway:
        api-key: ${MARKET_PAYMENT_GATEWAY_API_KEY}
        current-key-id: ${MARKET_PAYMENT_GATEWAY_HMAC_KEY_ID}
        current-secret: ${MARKET_PAYMENT_GATEWAY_HMAC_SECRET}
      delivery-provider:
        api-key: ${MARKET_DELIVERY_PROVIDER_API_KEY}
        current-key-id: ${MARKET_DELIVERY_PROVIDER_HMAC_KEY_ID}
        current-secret: ${MARKET_DELIVERY_PROVIDER_HMAC_SECRET}
```

Tests and Compose supply every value. Normal application startup must fail validation when an enabled integration profile omits a required credential; focused tests use explicit dynamic properties.

Set `market.external-integrations.enabled: ${EXTERNAL_INTEGRATIONS_ENABLED:false}`. `ExternalIntegrationSecurityConfiguration` creates the filter, source credential resolver, signer, and a qualified `@Bean("externalIntegrationClock") Clock.systemUTC()` only when enabled. Endpoint tests set the property to `true`; existing M1-M31 test contexts remain unchanged when disabled.

- [ ] **Step 5: Implement signed webhook filtering and probe endpoints**

Resolve the source only from the two exact path prefixes and attach it as a request attribute. Before Jackson runs, the filter must read at most 1,048,577 bytes, reject oversized input with `413`, parse required headers or return `400`, resolve the source-specific API key/key ID or return `401`, enforce the 300-second clock window, canonicalize the raw target/body, compare HMAC in constant time, and only then claim `(source, requestId)` with `expiresAt = receivedAt + replayRetention` or return `409`. Add:

```java
@PostMapping("/api/integrations/payment-gateway/v1/probes")
@ResponseStatus(HttpStatus.NO_CONTENT)
void paymentProbe(@Valid @RequestBody ProbeWebhookRequest request) {}

@PostMapping("/api/integrations/delivery-provider/v1/probes")
@ResponseStatus(HttpStatus.NO_CONTENT)
void deliveryProbe(@Valid @RequestBody ProbeWebhookRequest request) {}
```

The payment path accepts only body source `PAYMENT_GATEWAY`; the delivery path accepts only `DELIVERY_PROVIDER`. A mismatch returns the contract's `400` error and produces no domain side effect.

`SignedWebhookFilter` and `IntegrationProbeExceptionHandler` both emit `IntegrationErrorResponse(code, message, requestId)`. Assert exact `INTEGRATION_REQUEST_INVALID`, `INTEGRATION_AUTHENTICATION_FAILED`, `INTEGRATION_REPLAY_DETECTED`, and `INTEGRATION_BODY_TOO_LARGE` codes in the endpoint tests.
`ReplayCleanupScheduler` is conditional on the integration feature, runs hourly, and delegates a maximum of 1,000 expired rows to the bounded repository delete.

Permit these exact paths in `SecurityConfig`; the signed webhook filter remains their authentication boundary and runs before `JwtAuthenticationFilter`. Do not permit a wildcard that exposes future integration paths without HMAC.
Guard the controller and integration configuration with `@ConditionalOnProperty(name = "market.external-integrations.enabled", havingValue = "true")`. Inject the filter into `SecurityConfig` through `ObjectProvider<SignedWebhookFilter>` and add it only when present, so the existing default-disabled application and tests do not require integration secrets.

- [ ] **Step 6: Run focused and full backend tests**

```powershell
.\gradlew.bat test --tests 'com.sweet.market.integration.*' --tests '*ExternalIntegrationSecurityMigrationTest'
.\gradlew.bat --no-daemon test
```

Expected: focused tests pass, then all existing backend tests pass with the new replay table included in cleanup.

- [ ] **Step 7: Commit the Sweet Market integration boundary**

```powershell
git add backend/src/main/java/com/sweet/market/integration backend/src/main/resources/db/migration/V16__add_external_integration_security.sql backend/src/main/resources/application.yaml backend/src/main/java/com/sweet/market/auth/security/SecurityConfig.java backend/src/test/java/com/sweet/market/integration backend/src/test/java/com/sweet/market/store/migration/ExternalIntegrationSecurityMigrationTest.java backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
git commit -m "feat: add signed external integration boundary"
```

---

### Task 7: Propagate Correlation And Trace Context In All Three Applications

**Files:**
- Create: `backend/src/main/java/com/sweet/market/integration/web/CorrelationIdFilter.java`
- Create: `backend/src/test/java/com/sweet/market/integration/web/CorrelationIdFilterTest.java`
- Create: `mock-payment-gateway/src/main/java/com/sweet/market/gateway/web/CorrelationIdFilter.java`
- Create: `mock-payment-gateway/src/test/java/com/sweet/market/gateway/web/CorrelationIdFilterTest.java`
- Create: `mock-delivery-provider/src/main/java/com/sweet/market/provider/web/CorrelationIdFilter.java`
- Create: `mock-delivery-provider/src/test/java/com/sweet/market/provider/web/CorrelationIdFilterTest.java`
- Modify: `backend/src/main/resources/application.yaml`
- Modify: `mock-payment-gateway/src/main/resources/application.yaml`
- Modify: `mock-delivery-provider/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: `X-Correlation-Id` and optional `traceparent` from Task 1 contracts.
- Produces: validated UUID correlation request attribute, response header, MDC key `correlationId`, and preserved `traceparent` request attribute for M34/M36 HTTP clients.

- [ ] **Step 1: Write failing filter lifecycle tests in every application**

Each test suite verifies:

```java
@Test void 유효한_correlationId를_응답과_MDC에_전파한다()
@Test void correlationId가_없으면_UUID를_생성한다()
@Test void 잘못된_correlationId는_새_UUID로_대체한다()
@Test void 요청완료후_MDC를_정리한다()
@Test void W3C_traceparent를_변경하지_않고_request_attribute에_보존한다()
```

- [ ] **Step 2: Run the three focused suites and verify RED**

```powershell
.\backend\gradlew.bat -p backend test --tests '*CorrelationIdFilterTest'
.\backend\gradlew.bat -p mock-payment-gateway test --tests '*CorrelationIdFilterTest'
.\backend\gradlew.bat -p mock-delivery-provider test --tests '*CorrelationIdFilterTest'
```

Expected: FAIL because the filters are absent.

- [ ] **Step 3: Implement the three independent filters**

Each `OncePerRequestFilter` uses these constants and behavior:

```java
static final String CORRELATION_HEADER = "X-Correlation-Id";
static final String CORRELATION_ATTRIBUTE = "integration.correlationId";
static final String TRACEPARENT_ATTRIBUTE = "integration.traceparent";
static final String MDC_KEY = "correlationId";
```

Parse correlation ID as UUID, generate when absent/invalid, set request attributes before the chain, set the response header, and remove the MDC key in `finally`. Accept `traceparent` only when it matches the W3C version-00 shape `00-[32 lowercase hex]-[16 lowercase hex]-[2 lowercase hex]`; otherwise omit the attribute rather than inventing a trace.

- [ ] **Step 4: Put correlation before signed authentication**

Register order so correlation runs before HMAC/JWT filters. Add `%X{correlationId:-}` to console log patterns without adding API key, request signature, or authorization headers.

- [ ] **Step 5: Run all three application test suites**

```powershell
.\backend\gradlew.bat -p backend test --tests 'com.sweet.market.integration.*'
.\backend\gradlew.bat -p mock-payment-gateway test
.\backend\gradlew.bat -p mock-delivery-provider test
```

Expected: all correlation, security, context, and migration tests pass.

- [ ] **Step 6: Commit correlation propagation**

```powershell
git add backend/src/main/java/com/sweet/market/integration/web/CorrelationIdFilter.java backend/src/test/java/com/sweet/market/integration/web/CorrelationIdFilterTest.java backend/src/main/resources/application.yaml mock-payment-gateway/src/main/java/com/sweet/market/gateway/web/CorrelationIdFilter.java mock-payment-gateway/src/test/java/com/sweet/market/gateway/web/CorrelationIdFilterTest.java mock-payment-gateway/src/main/resources/application.yaml mock-delivery-provider/src/main/java/com/sweet/market/provider/web/CorrelationIdFilter.java mock-delivery-provider/src/test/java/com/sweet/market/provider/web/CorrelationIdFilterTest.java mock-delivery-provider/src/main/resources/application.yaml
git commit -m "feat: propagate integration correlation context"
```

---

### Task 8: Run Three Isolated Applications With Docker Compose

**Files:**
- Create: `backend/Dockerfile.integration`
- Create: `mock-payment-gateway/Dockerfile`
- Create: `mock-delivery-provider/Dockerfile`
- Create: `docker-compose.integration.yml`
- Create: `.dockerignore`
- Create: `.env.integration.example`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: Ports and datasource environment variables from Tasks 2-6.
- Produces: Compose services `market`, `mock-payment-gateway`, `mock-delivery-provider`, `market-postgres`, `payment-postgres`, `delivery-postgres`, and `redis` with health-gated startup.

- [ ] **Step 1: Add a failing Compose configuration check**

Create the initial Compose file referencing the seven required services but before Dockerfiles exist. Run:

```powershell
docker compose --env-file .env.integration -f docker-compose.integration.yml config --quiet
docker compose --env-file .env.integration -f docker-compose.integration.yml build
```

Expected: config parses; build fails because the Dockerfiles are absent.

- [ ] **Step 2: Add independent multi-stage images**

Each Dockerfile uses the repository's Gradle 8.14.5 wrapper and Java 21. The simulator pattern is:

```dockerfile
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY backend/gradlew ./gradlew
COPY backend/gradle ./gradle
COPY mock-payment-gateway ./app
RUN chmod +x gradlew && ./gradlew -p app bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/app/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Use the provider directory/port `8082` in its image. `backend/Dockerfile.integration` builds `backend`, exposes `8080`, and uses the same runtime hardening. Do not copy `.git`, `.worktrees`, local uploads, or `web/node_modules`; add a root `.dockerignore` if build context proves they are sent.

- [ ] **Step 3: Define isolated databases and application environment**

Use PostgreSQL 17-alpine with distinct database/user/password/volume triples. Healthcheck each database with its own database name. Application services depend on their own database health only; Sweet Market additionally depends on Redis. Inject development-only API keys and HMAC secrets through Compose environment variables, not application source.

`docker-compose.integration.yml` uses required interpolation such as `${MARKET_PAYMENT_GATEWAY_HMAC_SECRET:?set in .env.integration}`. Commit `.env.integration.example` with variable names and empty values, ignore `.env.integration`, and pass it with `--env-file .env.integration`. The example file is documentation, not a usable credential set.

Before the manual Compose commands, copy the example to ignored `.env.integration` and fill every required value with local development-only credentials. Add these exact ignore rules:

```gitignore
.env.integration
mock-payment-gateway/build/
mock-delivery-provider/build/
```

Use four distinct directional test credential sets in Compose:

```text
Sweet -> Payment: MARKET_PAYMENT_GATEWAY_* = PAYMENT_GATEWAY_MERCHANT_*
Payment -> Sweet: PAYMENT_GATEWAY_WEBHOOK_* = MARKET_PAYMENT_WEBHOOK_*
Sweet -> Delivery: MARKET_DELIVERY_PROVIDER_* = DELIVERY_PROVIDER_MERCHANT_*
Delivery -> Sweet: DELIVERY_PROVIDER_WEBHOOK_* = MARKET_DELIVERY_WEBHOOK_*
```

Set `EXTERNAL_INTEGRATIONS_ENABLED=true` only on the Compose Sweet Market service. Use separate current/next key IDs and secrets for each direction; do not reuse one secret across payment and delivery.

Add application healthchecks using `curl --fail http://localhost:<port>/actuator/health`. Do not mount source trees into runtime containers.

- [ ] **Step 4: Validate configuration and boot the topology**

```powershell
docker compose --env-file .env.integration -f docker-compose.integration.yml config --quiet
docker compose --env-file .env.integration -f docker-compose.integration.yml up -d --build
docker compose --env-file .env.integration -f docker-compose.integration.yml ps
```

Expected: all seven services become healthy/running; ports `8080/8081/8082` and `15432/15433/15434` are distinct.

- [ ] **Step 5: Prove persistence and database isolation**

Restart each simulator and assert its replay table remains. Connect with `psql` inside each PostgreSQL container and assert:

```text
payment-postgres: integration_request_replays exists; Sweet tables absent
delivery-postgres: integration_request_replays exists; Sweet tables absent
market-postgres: external_integration_request_replays exists; simulator domain tables absent
```

- [ ] **Step 6: Commit the integration topology**

```powershell
docker compose --env-file .env.integration -f docker-compose.integration.yml down
git add backend/Dockerfile.integration mock-payment-gateway/Dockerfile mock-delivery-provider/Dockerfile docker-compose.integration.yml .gitignore .dockerignore .env.integration.example
git commit -m "build: run milestone 32 integration topology"
```

---

### Task 9: Automate Signed Probes And Boundary Audits

**Files:**
- Create: `scripts/verify-m32-boundaries.ps1`
- Create: `scripts/verify-m32-integration.ps1`
- Create: `scripts/verify-m32-scripts.test.ps1`

**Interfaces:**
- Consumes: Four OpenAPI paths, Compose services, exact HMAC algorithm, and environment credentials.
- Produces: One reproducible M32 verification command and machine-readable exit status for CI/local use.

- [ ] **Step 1: Write failing static tests for the scripts**

`verify-m32-scripts.test.ps1` parses both scripts with the PowerShell parser and asserts the integration script contains all four paths, `docker compose ... config`, all three Gradle builds, Node contract tests, unique request IDs, HMAC-SHA256, replay-negative checks, and a `finally` cleanup path. It must also assert no literal configured secret is written to output.

Run:

```powershell
pwsh -NoProfile -File scripts/verify-m32-scripts.test.ps1
```

Expected: FAIL because the scripts are absent.

- [ ] **Step 2: Implement the boundary audit**

The boundary script fails if:

```powershell
rg -n 'import com\.sweet\.market\.(?!gateway)' mock-payment-gateway/src
rg -n 'import com\.sweet\.market\.(?!provider)' mock-delivery-provider/src
rg -n 'com\.sweet\.market\.(gateway|provider)' backend/src
```

Use PowerShell/.NET regex if ripgrep lookahead support is unavailable. Also inspect each application's configured JDBC database name and fail if any two are equal. Print only filenames and rule names, never credentials.

- [ ] **Step 3: Implement a reusable HMAC request function**

`verify-m32-integration.ps1` accepts `-EnvFile .env.integration`, parses non-comment `NAME=VALUE` lines into an in-memory dictionary, rejects missing/duplicate/empty required values, and never writes values to output. It defines:

```powershell
function New-SignedHeaders {
    param(
        [string]$ApiKey, [string]$KeyId, [string]$Secret,
        [string]$Method, [string]$RawTarget, [byte[]]$Body,
        [Guid]$RequestId, [DateTimeOffset]$Timestamp, [Guid]$CorrelationId
    )
    # SHA-256 body hex, seven-line canonical value, HMAC-SHA256 lowercase hex.
    # Return a hashtable with the seven protocol headers and application/json.
}
```

The script prefers an existing process environment variable and otherwise reads the named env file. It fails with a redacted missing-variable message and never accepts secret values as command-line arguments.

- [ ] **Step 4: Implement the complete verification flow**

The script performs this exact sequence:

```text
Node contract tests
PowerShell parser/static tests
boundary audit
Payment Gateway complete tests
Delivery Provider complete tests
Sweet Market complete backend tests with JDK 21/JWT
Compose config and build
Compose up and wait for health with a bounded deadline
signed Payment Gateway probe -> 200 and exact echo/correlation
signed Delivery Provider probe -> 200 and exact echo/correlation
signed payment webhook probe to Sweet Market -> 204
signed delivery webhook probe to Sweet Market -> 204
repeat one request ID -> 409
mutate body after signing -> 401
send expired timestamp -> 401
send 1 MiB + 1 byte chunked body -> 413
restart each simulator and re-check health/replay persistence
database isolation audit
Compose logs secret scan
finally: docker compose down
```

Use bounded polling instead of unbounded sleep. Capture sanitized summaries only; do not commit runtime logs.

- [ ] **Step 5: Run static and live verification**

```powershell
pwsh -NoProfile -File scripts/verify-m32-scripts.test.ps1
pwsh -NoProfile -File scripts/verify-m32-integration.ps1 -EnvFile .env.integration
```

Expected: static tests pass; live script exits `0`, reports all four signed probes and four negative security cases, and removes running integration containers in `finally`.

- [ ] **Step 6: Commit verification automation**

```powershell
git add scripts
git commit -m "test: automate milestone 32 integration verification"
```

---

### Task 10: Reconcile M32, Run Complete Verification, And Hand Off M33

**Files:**
- Create: `docs/superpowers/reports/2026-07-18-milestone-32-external-integration-foundation-verification.md`
- Create: `docs/superpowers/handoffs/2026-07-18-milestone-32-external-integration-foundation-handoff.md`
- Modify: `docs/superpowers/roadmaps/2026-07-18-realistic-payment-delivery-integration-milestone-32-39-roadmap.md`

**Interfaces:**
- Consumes: All M32 application, protocol, security, topology, and script outputs.
- Produces: Reproducible verification evidence and the precise M33 starting boundary.

- [ ] **Step 1: Run forced application suites and count XML results**

```powershell
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\backend\gradlew.bat -p mock-payment-gateway cleanTest test --no-daemon
.\backend\gradlew.bat -p mock-delivery-provider cleanTest test --no-daemon
.\backend\gradlew.bat -p backend cleanTest test --no-daemon
```

Expected: all three builds succeed. Sum every `TEST-*.xml` and record suites, tests, failures, errors, skips, and duration separately; do not copy old counts.

- [ ] **Step 2: Run complete web and protocol verification**

```powershell
cd web
npm install
npm test
npm run build
cd ..
node --test contracts/contracts.test.mjs
pwsh -NoProfile -File scripts/verify-m32-scripts.test.ps1
pwsh -NoProfile -File scripts/verify-m32-integration.ps1 -EnvFile .env.integration
```

Expected: web tests/build, two contract tests, script tests, and live topology verification pass. Record actual Node/npm versions and build advisory rather than expected values.

- [ ] **Step 3: Run security, privacy, and repository hygiene audits**

```powershell
pwsh -NoProfile -File scripts/verify-m32-boundaries.ps1
git diff --check
git status --short
```

Scan committed contracts, configs, docs, and generated verification summaries for API-key values, HMAC secrets, authorization headers, private absolute paths, placeholders, and raw runtime logs. Expected: zero secret/placeholder/path findings and only known user-owned changes remain unstaged.

- [ ] **Step 4: Write the verification report**

Record:

- Exact application and tool versions.
- Exact test counts and durations per build.
- Contract/vector hashes and path versions.
- Four positive signed probe results and negative replay/mutation/expiry/body-limit results.
- Compose service/port/database matrix.
- Restart persistence and database-isolation observations.
- Correlation and `traceparent` propagation evidence.
- Secret/privacy scan and boundary-audit results.
- Known limitations: probes only, no payment/shipment behavior, no durable business webhook sender, no Kafka, and no observability backend yet.

- [ ] **Step 5: Write the M32 handoff and update roadmap status**

The handoff states that M33 may add Payment Gateway domain behavior only behind the signed `/api/v1` boundary, must extend the payment OpenAPI contract before implementation, must reuse the provider-owned replay/security behavior without importing Sweet code, and must add durable payment webhook outbox semantics rather than treating the M32 probe as a business callback engine.

Update the roadmap with a concise M32 completion link; do not mark M33 complete or change its approved scope.

- [ ] **Step 6: Request final code review and resolve all Critical/Important findings**

Use `superpowers:requesting-code-review` over the full M32 merge-base range. Re-run the focused affected suites after every fix, then re-run the complete verification script. Record non-blocking Minor findings explicitly in the report/handoff.

- [ ] **Step 7: Commit final verification and handoff**

```powershell
git add docs/superpowers/reports/2026-07-18-milestone-32-external-integration-foundation-verification.md docs/superpowers/handoffs/2026-07-18-milestone-32-external-integration-foundation-handoff.md docs/superpowers/roadmaps/2026-07-18-realistic-payment-delivery-integration-milestone-32-39-roadmap.md
git commit -m "docs: hand off milestone 32 integration foundation"
```

---

## Final Plan Verification

Before declaring M32 complete, the implementing agent must show fresh evidence for all of the following:

```powershell
# Standalone builds
.\backend\gradlew.bat -p mock-payment-gateway cleanTest test --no-daemon
.\backend\gradlew.bat -p mock-delivery-provider cleanTest test --no-daemon

# Existing Sweet Market regression
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\backend\gradlew.bat -p backend cleanTest test --no-daemon

# Contracts and scripts
node --test contracts/contracts.test.mjs
pwsh -NoProfile -File scripts/verify-m32-scripts.test.ps1
pwsh -NoProfile -File scripts/verify-m32-boundaries.ps1
pwsh -NoProfile -File scripts/verify-m32-integration.ps1 -EnvFile .env.integration

# Web regression
Push-Location web
npm test
npm run build
Pop-Location

# Hygiene
git diff --check
git status --short
```

Completion requires independently bootable applications, isolated databases, passing public-vector implementations in all three codebases, signed positive and negative probes over real ports, restart-safe replay protection, redacted evidence, and no regression in M1-M31 behavior.
