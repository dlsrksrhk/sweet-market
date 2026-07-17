import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

import {normalizeMeasurement} from './normalize-m30-measurement.mjs';

const fixture = JSON.parse(await readFile(new URL('./fixtures/normalizer-input.json', import.meta.url), 'utf8'));
const collectionScript = await readFile(new URL('./collect-m30-measurement.ps1', import.meta.url), 'utf8');
const captureScript = await readFile(new URL('./capture-m30-query-evidence.ps1', import.meta.url), 'utf8')
        .catch(() => '');
const experimentConfig = await readFile(
        new URL('../backend/src/main/resources/application-local-experiment.yaml', import.meta.url), 'utf8',
);

test('Task 10 등록 계약으로 정규화하고 밀리초와 비율을 보존한다', () => {
    const measurement = normalizeMeasurement(structuredClone(fixture));

    assert.deepEqual(Object.keys(measurement), ['measurementId', 'artifactDirectory', 'off', 'on']);
    assert.equal(measurement.off.cacheMode, 'OFF');
    assert.equal(measurement.on.cacheMode, 'ON');
    assert.deepEqual(measurement.off.endpointMetrics.map(({endpoint}) => endpoint), [
        'catalog', 'events', 'popularity', 'detail',
    ]);
    assert.equal(measurement.off.endpointMetrics[0].p50Millis, 25);
    assert.equal(measurement.off.endpointMetrics[0].p95Millis, 38.5);
    assert.equal(measurement.off.endpointMetrics[0].throughputPerSecond, 0.013);
    assert.equal(measurement.off.endpointMetrics[0].errorRate, 0.25);
    assert.deepEqual(measurement.on.queryEvidence.map(({queryShape}) => queryShape), [
        'GLOBAL_CATALOG', 'FIXED_STORE_CATALOG', 'ACTIVE_EVENTS', 'POPULARITY',
    ]);
    assert.equal(measurement.on.queryEvidence[0].fullPlan, undefined);
    assert.equal(measurement.on.queryEvidence[0].capturedAt, undefined);
});

test('필수 endpoint가 누락되면 거부한다', () => {
    const input = structuredClone(fixture);
    input.offMetrics.endpointMetrics.pop();

    assert.throws(() => normalizeMeasurement(input), /OFF endpoint metrics must contain exactly/);
});

test('필수 mode가 누락되거나 바뀌면 거부한다', () => {
    const missing = structuredClone(fixture);
    delete missing.metadata.on;
    assert.throws(() => normalizeMeasurement(missing), /metadata\.on is required/);

    const changed = structuredClone(fixture);
    changed.onMetrics.cacheMode = 'OFF';
    assert.throws(() => normalizeMeasurement(changed), /on metrics cacheMode must be ON/);
});

test('필수 query shape가 누락되면 거부한다', () => {
    const input = structuredClone(fixture);
    input.queryEvidence.on.evidence.splice(1, 1);

    assert.throws(() => normalizeMeasurement(input), /ON query evidence must contain exactly/);
});

test('OFF와 ON의 비교 조건이 다르면 거부한다', () => {
    const input = structuredClone(fixture);
    input.metadata.on.hardwareDescription = 'different hardware';

    assert.throws(() => normalizeMeasurement(input), /OFF and ON metadata differ: hardwareDescription/);
});

test('k6 summary의 필수 측정값이 누락되면 거부한다', () => {
    const input = structuredClone(fixture);
    delete input.offSummary.metrics.http_req_duration.values['p(95)'];

    assert.throws(() => normalizeMeasurement(input), /off k6 summary is missing http_req_duration p\(95\)/);
});

test('k6 2 summary export의 직접 metric 값 형식을 허용한다', () => {
    const input = structuredClone(fixture);
    input.offSummary.metrics = {
        http_req_duration: {med: 272.7463, 'p(95)': 423.538155},
        http_reqs: {rate: 160.130416},
        http_req_failed: {value: 0},
    };

    assert.doesNotThrow(() => normalizeMeasurement(input));
});

test('cold start에서 아직 없는 read duration metric을 허용한다', () => {
    assert.match(
            collectionScript,
            /readDuration = Invoke-ActuatorMetric 'discovery\.read\.duration' -AllowMissing/,
    );
});

test('OFF와_ON의_mode별_plan_provenance를_검증하고_등록계약에서는_제거한다', () => {
    const missingOffProfile = structuredClone(fixture);
    missingOffProfile.queryEvidence.off.provenance.serverInfo.activeProfiles = [
        'local', 'performance-fixture', 'local-experiment',
    ];
    assert.throws(() => normalizeMeasurement(missingOffProfile), /OFF plan provenance activeProfiles/);

    const unexpectedOnProfile = structuredClone(fixture);
    unexpectedOnProfile.queryEvidence.on.provenance.serverInfo.activeProfiles.push('cache-off');
    assert.throws(() => normalizeMeasurement(unexpectedOnProfile), /ON plan provenance activeProfiles/);

    const measurement = normalizeMeasurement(structuredClone(fixture));
    assert.equal(measurement.off.queryEvidence[0].provenance, undefined);
});

test('OFF와_ON의_plan_capture가_같은_fixture_clock을_쓰지않으면_거부한다', () => {
    const input = structuredClone(fixture);
    input.onMetrics.serverInfo.before.fixedClock = '2026-07-17T00:00:01Z';
    input.onMetrics.serverInfo.after.fixedClock = '2026-07-17T00:00:01Z';
    input.queryEvidence.on.provenance.serverInfo.fixedClock = '2026-07-17T00:00:01Z';

    assert.throws(() => normalizeMeasurement(input), /plan provenance fixedClock must match/);
});

test('collector_before와_after의_PID가_다르면_거부한다', () => {
    const input = structuredClone(fixture);
    input.offMetrics.serverInfo.after.serverProcessId = 999;

    assert.throws(() => normalizeMeasurement(input), /OFF collector server identity changed during k6/);
});

test('collector와_plan_capture의_PID가_다르면_거부한다', () => {
    const input = structuredClone(fixture);
    input.queryEvidence.on.provenance.serverInfo.serverProcessId = 999;

    assert.throws(() => normalizeMeasurement(input), /ON collector and plan server identity must match/);
});

test('plan_capture의_live_trace_provenance와_SQL_bind가_없으면_거부한다', () => {
    const missingTrace = structuredClone(fixture);
    delete missingTrace.queryEvidence.off.provenance.trace;
    assert.throws(() => normalizeMeasurement(missingTrace), /OFF plan trace provenance/);

    const missingBinds = structuredClone(fixture);
    delete missingBinds.queryEvidence.on.evidence[0].capturedBinds;
    assert.throws(() => normalizeMeasurement(missingBinds), /ON.GLOBAL_CATALOG.capturedBinds/);

    const measurement = normalizeMeasurement(structuredClone(fixture));
    assert.equal(measurement.off.queryEvidence[0].preparedSql, undefined);
    assert.equal(measurement.off.queryEvidence[0].exactSql, undefined);
});

test('collector의_profile_clock_cache_mode가_정확하지않으면_거부한다', () => {
    const wrongProfile = structuredClone(fixture);
    wrongProfile.offMetrics.serverInfo.before.activeProfiles.pop();
    assert.throws(() => normalizeMeasurement(wrongProfile), /OFF collector before activeProfiles/);

    const wrongClock = structuredClone(fixture);
    wrongClock.onMetrics.serverInfo.after.fixedClock = '2026-07-17T00:00:01Z';
    assert.throws(() => normalizeMeasurement(wrongClock), /ON collector server identity changed during k6/);

    const wrongMode = structuredClone(fixture);
    wrongMode.onMetrics.serverInfo.before.cacheMode = 'OFF';
    assert.throws(() => normalizeMeasurement(wrongMode), /ON collector before cacheMode must be ON/);
});

test('route_sample에서_재계산한_endpoint_metric이_다르면_거부한다', () => {
    const input = structuredClone(fixture);
    input.offSamples.endpoints[0].durationsMillis[3] = 400;

    assert.throws(() => normalizeMeasurement(input), /OFF catalog route samples do not reproduce p95Millis/);
});

test('PowerShell_Math_Round와_같은_midpoint_to_even을_사용한다', () => {
    const input = structuredClone(fixture);
    input.onSamples.endpoints[0].durationsMillis = [3.7325, 3.7325];
    input.onSamples.endpoints[0].failureFlags = [0, 0];
    Object.assign(input.onMetrics.endpointMetrics[0], {
        p50Millis: 3.732,
        p95Millis: 3.732,
        throughputPerSecond: 0.007,
        errorRate: 0,
    });

    assert.doesNotThrow(() => normalizeMeasurement(input));
});

test('duration과_failure_flag의_sample수가_다르면_거부한다', () => {
    const input = structuredClone(fixture);
    input.onSamples.endpoints[1].failureFlags.pop();

    assert.throws(() => normalizeMeasurement(input), /ON events route sample counts must match/);
});

test('collector는_sanitized_route_sample을_쓴뒤_raw만_삭제한다', () => {
    assert.match(collectionScript, /route-samples-\$modeName\.json/);
    assert.match(collectionScript, /durationsMillis/);
    assert.match(collectionScript, /failureFlags/);
    assert.match(collectionScript, /Remove-Item -LiteralPath \$rawPath -Force/);
});

test('collector는_k6_직전과직후의_authenticated_server_info를_보존하고_비교한다', () => {
    assert.match(collectionScript, /\/actuator\/info/);
    assert.match(collectionScript, /serverInfoBefore\s*=\s*Get-SanitizedServerInfo/);
    assert.match(collectionScript, /serverInfoAfter\s*=\s*Get-SanitizedServerInfo/);
    assert.match(collectionScript, /serverInfo\s*=\s*\[ordered\]@\{ before = \$serverInfoBefore; after = \$serverInfoAfter \}/);
    assert.match(collectionScript, /server identity changed during k6/);
    const beforeIndex = collectionScript.indexOf('$serverInfoBefore = Get-SanitizedServerInfo');
    const k6Index = collectionScript.indexOf('& k6 run');
    const afterIndex = collectionScript.indexOf('$serverInfoAfter = Get-SanitizedServerInfo');
    assert.ok(beforeIndex >= 0 && beforeIndex < k6Index && k6Index < afterIndex);
});

test('plan_capture는_실행중인_서버_provenance와_네_HTTP_shape와_full_explain을_사용한다', () => {
    assert.match(captureScript, /\/actuator\/info/);
    assert.match(captureScript, /TraceLogPath/);
    assert.match(captureScript, /\/api\/catalog\/products\?size=20/);
    assert.match(captureScript, /\/api\/stores\/1\/catalog\/products\?size=20/);
    assert.match(captureScript, /\/api\/discovery\/events/);
    assert.match(captureScript, /\/api\/discovery\/popular-products/);
    assert.match(captureScript, /EXPLAIN \(ANALYZE, BUFFERS, FORMAT JSON\)/);
    assert.match(captureScript, /traceStartOffset\s*=\s*\(Get-Item/);
    assert.match(captureScript, /parse-m30-jdbc-trace\.mjs/);
    assert.match(captureScript, /preparedSql/);
    assert.match(captureScript, /capturedBinds/);
    assert.match(captureScript, /traceSegmentSha256/);
    assert.match(captureScript, /sanitizedTraceSha256/);
    assert.match(captureScript, /serverInfo/);
    assert.match(captureScript, /\/actuator\/loggers\/org\.springframework\.jdbc\.core/);
    assert.match(experimentConfig, /include:\s*health,info,metrics,loggers/);
    assert.doesNotMatch(captureScript, /TemplatePath/);
    const offsetIndex = captureScript.indexOf('$traceStartOffset = (Get-Item');
    const requestIndex = captureScript.indexOf('Invoke-RestMethod "$resolvedBaseUrl/api/catalog/products?size=20"');
    assert.ok(offsetIndex >= 0 && offsetIndex < requestIndex);
});
