# M29 inventory reservation locking comparison

## Reproduction

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.purchase.InventoryLockingComparisonTest' --rerun-tasks
```

The comparison runs against the `postgres:17-alpine` Testcontainers fixture from
`IntegrationTestSupport`. Each scenario creates one stock-managed product with
three units, releases ten buyers through the same `CountDownLatch` barrier, and
measures elapsed time with `System.nanoTime`. No separate warm-up run was used;
the reported rows are the first run after the Spring test context started.

## Results

| Strategy | Successes | Conflicts | Retries | Elapsed |
| --- | ---: | ---: | ---: | ---: |
| Conditional update | 3 | 7 | 0 | 6 ms |
| Bounded optimistic retry (5) | 3 | 7 | 5 | 56 ms |
| Pessimistic lock | 3 | 7 | 0 | 10 ms |

The elapsed values are test-case wall-clock measurements from the focused run,
so they are useful for repeated local comparison rather than as a benchmark
claim. Every scenario asserts that available quantity is zero and therefore
never negative.

## Observations and production choice

The conditional SQL update rejects losers at the write boundary without a
read-then-write race and keeps the reservation path to one statement. The
optimistic variant can retry after a version conflict, while the pessimistic
variant serializes buyers through `FOR UPDATE`; both preserve stock but add
retry or lock-wait coordination. Production remains on the conditional update
because it has the smallest shared critical section and requires neither
application retry policy nor an explicit lock hold.

Focused recovery coverage also verifies that a failed payment followed by a
repeat cancel produces exactly one `RELEASE` adjustment, and that a product-race
loser leaves no coupon reservation behind.
