import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

import {normalizeMeasurement} from './normalize-m30-measurement.mjs';

const fixture = JSON.parse(await readFile(new URL('./fixtures/normalizer-input.json', import.meta.url), 'utf8'));
const collectionScript = await readFile(new URL('./collect-m30-measurement.ps1', import.meta.url), 'utf8');

test('Task 10 등록 계약으로 정규화하고 밀리초와 비율을 보존한다', () => {
    const measurement = normalizeMeasurement(structuredClone(fixture));

    assert.deepEqual(Object.keys(measurement), ['measurementId', 'artifactDirectory', 'off', 'on']);
    assert.equal(measurement.off.cacheMode, 'OFF');
    assert.equal(measurement.on.cacheMode, 'ON');
    assert.deepEqual(measurement.off.endpointMetrics.map(({endpoint}) => endpoint), [
        'catalog', 'events', 'popularity', 'detail',
    ]);
    assert.equal(measurement.off.endpointMetrics[0].p50Millis, 12.345);
    assert.equal(measurement.off.endpointMetrics[0].p95Millis, 45.678);
    assert.equal(measurement.off.endpointMetrics[0].throughputPerSecond, 67.891);
    assert.equal(measurement.off.endpointMetrics[0].errorRate, 0.0001234);
    assert.deepEqual(measurement.on.queryEvidence.map(({queryShape}) => queryShape), [
        'GLOBAL_CATALOG', 'FIXED_STORE_CATALOG', 'ACTIVE_EVENTS', 'POPULARITY',
    ]);
    assert.equal(measurement.on.queryEvidence[0].fullPlan, undefined);
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
    input.queryEvidence.on.splice(1, 1);

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
