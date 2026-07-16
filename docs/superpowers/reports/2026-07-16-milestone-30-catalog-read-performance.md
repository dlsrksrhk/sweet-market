# M30 catalog-read performance experiment

## Scope and reproducibility

The workload is [`performance/m30-catalog-reads.js`](../../../performance/m30-catalog-reads.js). It uses a one-minute 20-VU warm-up, followed by a five-minute 100-VU measured interval. Each iteration chooses home/catalog reads 70% of the time and product-detail reads 30% of the time. The home path calls active events, popularity, and the global catalog together.

Use the `local` fixture profile together with the statement-counter profile. `local` runs `DemoDataInitializer` (120 catalog products) on an empty database. The PowerShell lookup below selects the first returned buyer-visible catalog card, so `PRODUCT_ID` is valid for the detail portion of the workload.

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat bootRun --args='--spring.profiles.active=local,local-experiment'

# separate terminal at repository root
$env:BASE_URL='http://localhost:8080'
$catalog = Invoke-RestMethod "$env:BASE_URL/api/catalog/products?size=1"
$env:PRODUCT_ID = [string]$catalog.data.content[0].id
if ([string]::IsNullOrWhiteSpace($env:PRODUCT_ID)) { throw 'Local fixture did not provide a buyer-visible product.' }
k6 run performance/m30-catalog-reads.js
```

`local-experiment` wraps the application `DataSource`, so the `discovery.jdbc.statements` counter includes statements issued by both Hibernate and `NamedParameterJdbcTemplate`. Read it at `/actuator/metrics/discovery.jdbc.statements`; the timers are `discovery.read.duration` tagged with `endpoint=catalog|events|popularity|detail`. Caffeine statistics are exported with cache name `discovery.active-events`.

## Cache-off / cache-on comparison

The active-event cache supports `discovery.active-event-cache.enabled`; the `cache-off` profile sets it to `false`. Run the exact same fixture and `PRODUCT_ID` lookup for each server mode, restarting the server between runs:

```powershell
# cache off
.\gradlew.bat bootRun --args='--spring.profiles.active=local,local-experiment,cache-off'

# cache on (default)
.\gradlew.bat bootRun --args='--spring.profiles.active=local,local-experiment'
```

For each mode, snapshot `/actuator/metrics/discovery.jdbc.statements` before and after `k6 run performance/m30-catalog-reads.js`. In cache-on mode also capture `/actuator/metrics/cache.gets?tag=cache:discovery.active-events` for hit/miss totals. Cache-off invokes the repository loader for every event request, so it has no cache-hit assertion to collect.

## Fixture volume

| Fixture | Volume | Source |
| --- | ---: | --- |
| Buyer-visible catalog products | 120 | `DemoDataInitializer.CATALOG_PRODUCT_COUNT` |
| Buyer-visible detail product | 1 required | `PRODUCT_ID` supplied to k6 |
| Product-view events | Record before run | `select count(*) from product_view_events;` |
| Active promotion/coupon events | Record before run | `GET /api/discovery/events` |

## Measured result record

No latency, error, SQL, cache, or query-plan value is fabricated here. This work creates the reproducible workload and instrumentation; fill the following rows from the one five-minute run above before making a performance claim.

| Mode | p50 | p95 | HTTP errors | JDBC statements | Cache gets/hits/misses |
| --- | ---: | ---: | ---: | ---: | --- |
| Cache disabled (`cache-off` Spring profile) | Not measured | Not measured | Not measured | Not measured | Not applicable |
| Cache enabled (normal 30-second entry) | Not measured | Not measured | Not measured | Not measured | Not measured |

Capture the k6 summary for p50/p95 and error rate, `discovery.jdbc.statements` before and after the run for statement count, and the Caffeine `cache.gets` metrics with `result=hit|miss` for cache statistics.

## Query-plan record

Run each captured SQL statement from the local SQL log through PostgreSQL with its real bind values, using `EXPLAIN (ANALYZE, BUFFERS)`. Preserve the full output with this report when recording a run.

| Query | Required request | Result |
| --- | --- | --- |
| Global catalog | `GET /api/catalog/products?size=20` | Not measured |
| Fixed-store catalog | `GET /api/stores/{storeId}/catalog/products?size=20` | Not measured |
| Active events | `GET /api/discovery/events` | Not measured |
| Popular products | `GET /api/discovery/popular-products` | Not measured |

```sql
EXPLAIN (ANALYZE, BUFFERS)
-- paste the logged global/fixed-store catalog, active-events, or popularity SQL here with its values.
SELECT 1;
```
