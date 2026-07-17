import {readFile, writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

const REQUIRED_ENDPOINTS = ['catalog', 'events', 'popularity', 'detail'];
const REQUIRED_QUERY_SHAPES = ['GLOBAL_CATALOG', 'FIXED_STORE_CATALOG', 'ACTIVE_EVENTS', 'POPULARITY'];
const QUERY_REQUEST_PATHS = {
    GLOBAL_CATALOG: '/api/catalog/products?size=20',
    FIXED_STORE_CATALOG: '/api/stores/1/catalog/products?size=20',
    ACTIVE_EVENTS: '/api/discovery/events',
    POPULARITY: '/api/discovery/popular-products',
};
const QUERY_BIND_COUNTS = {
    GLOBAL_CATALOG: 3,
    FIXED_STORE_CATALOG: 4,
    ACTIVE_EVENTS: 4,
    POPULARITY: 4,
};
const COMPARABLE_FIELDS = [
    'gitCommit',
    'dirtyWorktree',
    'fixtureVersion',
    'scenarioVersion',
    'environmentName',
    'hardwareDescription',
    'warmupSeconds',
    'measuredSeconds',
];
const REQUIRED_OPTIONS = [
    '--metadata',
    '--off-summary',
    '--on-summary',
    '--off-metrics',
    '--on-metrics',
    '--off-samples',
    '--on-samples',
    '--query-evidence',
    '--out',
];

export function normalizeMeasurement({
    metadata,
    offSummary,
    onSummary,
    offMetrics,
    onMetrics,
    offSamples,
    onSamples,
    queryEvidence,
}) {
    requireObject(metadata, 'metadata');
    requireText(metadata.measurementId, 'metadata.measurementId');
    requireText(metadata.artifactDirectory, 'metadata.artifactDirectory');
    if (!/^performance\/results\/[A-Za-z0-9._/-]+$/.test(metadata.artifactDirectory)
            || metadata.artifactDirectory.includes('..')) {
        throw new Error('metadata.artifactDirectory must be a normalized path below performance/results');
    }

    const off = normalizeCondition(metadata.off, 'OFF', 'metadata.off');
    const on = normalizeCondition(metadata.on, 'ON', 'metadata.on');
    for (const field of COMPARABLE_FIELDS) {
        if (off[field] !== on[field]) {
            throw new Error(`OFF and ON metadata differ: ${field}`);
        }
    }

    validateK6Summary(offSummary, 'off');
    validateK6Summary(onSummary, 'on');

    const offCollectorServerInfo = normalizeCollectorServerInfo(offMetrics, 'OFF');
    const onCollectorServerInfo = normalizeCollectorServerInfo(onMetrics, 'ON');
    const offEndpointMetrics = normalizeEndpointMetrics(offMetrics, 'OFF', 'off');
    const onEndpointMetrics = normalizeEndpointMetrics(onMetrics, 'ON', 'on');
    validateRouteSamples(offSamples, 'OFF', offEndpointMetrics, off.measuredSeconds);
    validateRouteSamples(onSamples, 'ON', onEndpointMetrics, on.measuredSeconds);
    const offQueryEvidence = normalizeQueryEvidenceBundle(
            queryEvidence?.off, 'OFF', off, 1, offCollectorServerInfo,
    );
    const onQueryEvidence = normalizeQueryEvidenceBundle(
            queryEvidence?.on, 'ON', on, 2, onCollectorServerInfo,
    );
    if (offQueryEvidence.fixedClock !== onQueryEvidence.fixedClock) {
        throw new Error('OFF and ON plan provenance fixedClock must match');
    }
    if (offQueryEvidence.capturedAt >= onQueryEvidence.capturedAt) {
        throw new Error('OFF plan capture must precede ON plan capture');
    }

    return {
        measurementId: metadata.measurementId,
        artifactDirectory: metadata.artifactDirectory,
        off: {
            ...off,
            endpointMetrics: offEndpointMetrics,
            queryEvidence: offQueryEvidence.evidence,
        },
        on: {
            ...on,
            endpointMetrics: onEndpointMetrics,
            queryEvidence: onQueryEvidence.evidence,
        },
    };
}

function normalizeCollectorServerInfo(metrics, expectedMode) {
    requireObject(metrics, `${expectedMode} metrics`);
    requireObject(metrics.serverInfo, `${expectedMode} collector serverInfo`);
    const before = normalizeServerInfo(metrics.serverInfo.before, expectedMode, `${expectedMode} collector before`);
    const after = normalizeServerInfo(metrics.serverInfo.after, expectedMode, `${expectedMode} collector after`);
    if (!sameServerInfo(before, after)) {
        throw new Error(`${expectedMode} collector server identity changed during k6`);
    }
    return before;
}

function normalizeServerInfo(info, expectedMode, label) {
    requireObject(info, label);
    requirePositiveInteger(info.serverProcessId, `${label} serverProcessId`);
    const expectedProfiles = expectedMode === 'OFF'
        ? ['local', 'performance-fixture', 'local-experiment', 'cache-off']
        : ['local', 'performance-fixture', 'local-experiment'];
    if (!Array.isArray(info.activeProfiles)
            || info.activeProfiles.length !== expectedProfiles.length
            || new Set(info.activeProfiles).size !== expectedProfiles.length
            || expectedProfiles.some((profile) => !info.activeProfiles.includes(profile))) {
        throw new Error(`${label} activeProfiles must contain exactly ${expectedProfiles.join(', ')}`);
    }
    requireTimestamp(info.fixedClock, `${label} fixedClock`);
    if (info.cacheMode !== expectedMode) {
        throw new Error(`${label} cacheMode must be ${expectedMode}`);
    }
    return {
        serverProcessId: info.serverProcessId,
        activeProfiles: [...info.activeProfiles],
        fixedClock: info.fixedClock,
        cacheMode: info.cacheMode,
    };
}

function sameServerInfo(left, right) {
    return left.serverProcessId === right.serverProcessId
        && left.fixedClock === right.fixedClock
        && left.cacheMode === right.cacheMode
        && [...left.activeProfiles].sort().join('\0') === [...right.activeProfiles].sort().join('\0');
}

function normalizeCondition(condition, expectedMode, label) {
    requireObject(condition, label);
    if (condition.cacheMode !== expectedMode) {
        throw new Error(`${label}.cacheMode must be ${expectedMode}`);
    }
    for (const field of ['gitCommit', 'fixtureVersion', 'scenarioVersion', 'environmentName', 'hardwareDescription']) {
        requireText(condition[field], `${label}.${field}`);
    }
    if (typeof condition.dirtyWorktree !== 'boolean') {
        throw new Error(`${label}.dirtyWorktree must be boolean`);
    }
    requirePositiveInteger(condition.warmupSeconds, `${label}.warmupSeconds`);
    requirePositiveInteger(condition.measuredSeconds, `${label}.measuredSeconds`);
    const startedAt = requireTimestamp(condition.startedAt, `${label}.startedAt`);
    const completedAt = requireTimestamp(condition.completedAt, `${label}.completedAt`);
    if (completedAt <= startedAt) {
        throw new Error(`${label}.completedAt must be after startedAt`);
    }
    return {
        cacheMode: expectedMode,
        gitCommit: condition.gitCommit,
        dirtyWorktree: condition.dirtyWorktree,
        fixtureVersion: condition.fixtureVersion,
        scenarioVersion: condition.scenarioVersion,
        environmentName: condition.environmentName,
        hardwareDescription: condition.hardwareDescription,
        warmupSeconds: condition.warmupSeconds,
        measuredSeconds: condition.measuredSeconds,
        startedAt: condition.startedAt,
        completedAt: condition.completedAt,
    };
}

function validateK6Summary(summary, label) {
    requireObject(summary, `${label} k6 summary`);
    const required = [
        ['http_req_duration', 'med'],
        ['http_req_duration', 'p(95)'],
        ['http_reqs', 'rate'],
        ['http_req_failed', 'rate'],
    ];
    for (const [metric, value] of required) {
        const exportedMetric = summary.metrics?.[metric];
        const exportedValue = exportedMetric?.values?.[value]
                ?? exportedMetric?.[value]
                ?? (metric === 'http_req_failed' && value === 'rate' ? exportedMetric?.value : undefined);
        if (!Number.isFinite(exportedValue)) {
            throw new Error(`${label} k6 summary is missing ${metric} ${value}`);
        }
    }
}

function normalizeEndpointMetrics(metrics, expectedMode, label) {
    requireObject(metrics, `${label} metrics`);
    if (metrics.cacheMode !== expectedMode) {
        throw new Error(`${label} metrics cacheMode must be ${expectedMode}`);
    }
    const values = metrics.endpointMetrics;
    requireExactNames(
            values,
            REQUIRED_ENDPOINTS,
            ({endpoint}) => endpoint,
            `${expectedMode} endpoint metrics`,
    );
    return values.map((metric, index) => {
        requireObject(metric, `${label} metrics endpoint ${index}`);
        for (const field of ['p50Millis', 'p95Millis', 'throughputPerSecond', 'errorRate']) {
            requireNonNegativeNumber(metric[field], `${label}.${metric.endpoint}.${field}`);
        }
        if (metric.p50Millis > metric.p95Millis) {
            throw new Error(`${label}.${metric.endpoint}.p50Millis must not exceed p95Millis`);
        }
        if (metric.errorRate > 1) {
            throw new Error(`${label}.${metric.endpoint}.errorRate must not exceed 1`);
        }
        requireNonNegativeInteger(metric.jdbcStatementCount, `${label}.${metric.endpoint}.jdbcStatementCount`);
        for (const field of ['cacheHitCount', 'cacheMissCount', 'cacheEvictionCount']) {
            if (metric[field] !== null) {
                requireNonNegativeInteger(metric[field], `${label}.${metric.endpoint}.${field}`);
            }
        }
        return {
            cacheMode: expectedMode,
            endpoint: metric.endpoint,
            p50Millis: metric.p50Millis,
            p95Millis: metric.p95Millis,
            throughputPerSecond: metric.throughputPerSecond,
            errorRate: metric.errorRate,
            jdbcStatementCount: metric.jdbcStatementCount,
            cacheHitCount: metric.cacheHitCount,
            cacheMissCount: metric.cacheMissCount,
            cacheEvictionCount: metric.cacheEvictionCount,
        };
    });
}

function validateRouteSamples(samples, expectedMode, endpointMetrics, measuredSeconds) {
    requireObject(samples, `${expectedMode} route samples`);
    if (samples.cacheMode !== expectedMode) {
        throw new Error(`${expectedMode} route samples cacheMode must be ${expectedMode}`);
    }
    requireText(samples.measuredScenario, `${expectedMode} route samples measuredScenario`);
    if (samples.measuredSeconds !== measuredSeconds) {
        throw new Error(`${expectedMode} route samples measuredSeconds must match metadata`);
    }
    requireExactNames(
            samples.endpoints,
            REQUIRED_ENDPOINTS,
            ({endpoint}) => endpoint,
            `${expectedMode} route samples`,
    );
    const metricsByEndpoint = new Map(endpointMetrics.map((metric) => [metric.endpoint, metric]));
    for (const route of samples.endpoints) {
        requireObject(route, `${expectedMode} ${route?.endpoint} route samples`);
        const durations = route.durationsMillis;
        const failures = route.failureFlags;
        if (!Array.isArray(durations) || !Array.isArray(failures) || durations.length !== failures.length) {
            throw new Error(`${expectedMode} ${route.endpoint} route sample counts must match`);
        }
        if (durations.length === 0) {
            throw new Error(`${expectedMode} ${route.endpoint} route samples must not be empty`);
        }
        durations.forEach((duration, index) => requireNonNegativeNumber(
                duration, `${expectedMode}.${route.endpoint}.durationsMillis[${index}]`,
        ));
        failures.forEach((failure, index) => {
            if (failure !== 0 && failure !== 1) {
                throw new Error(`${expectedMode}.${route.endpoint}.failureFlags[${index}] must be 0 or 1`);
            }
        });
        const metric = metricsByEndpoint.get(route.endpoint);
        const recomputed = {
            p50Millis: round(percentile(durations, 0.50), 3),
            p95Millis: round(percentile(durations, 0.95), 3),
            throughputPerSecond: round(durations.length / measuredSeconds, 3),
            errorRate: round(failures.reduce((sum, value) => sum + value, 0) / durations.length, 7),
        };
        for (const field of Object.keys(recomputed)) {
            if (recomputed[field] !== metric[field]) {
                throw new Error(`${expectedMode} ${route.endpoint} route samples do not reproduce ${field}`);
            }
        }
    }
}

function percentile(values, percentileValue) {
    const sorted = [...values].sort((left, right) => left - right);
    const position = (sorted.length - 1) * percentileValue;
    const lower = Math.floor(position);
    const upper = Math.ceil(position);
    if (lower === upper) {
        return sorted[lower];
    }
    return sorted[lower] + ((sorted[upper] - sorted[lower]) * (position - lower));
}

function round(value, digits) {
    const scale = 10 ** digits;
    const scaled = value * scale;
    const lower = Math.floor(scaled);
    const fraction = scaled - lower;
    const midpointTolerance = Number.EPSILON * Math.max(1, Math.abs(scaled)) * 4;
    if (Math.abs(fraction - 0.5) <= midpointTolerance) {
        return (lower % 2 === 0 ? lower : lower + 1) / scale;
    }
    return Math.round(scaled) / scale;
}

function normalizeQueryEvidenceBundle(bundle, expectedMode, condition, expectedSequence, collectorServerInfo) {
    requireObject(bundle, `${expectedMode} query evidence bundle`);
    const capturedAt = requireTimestamp(bundle.capturedAt, `${expectedMode} query evidence capturedAt`);
    if (capturedAt < Date.parse(condition.completedAt)) {
        throw new Error(`${expectedMode} plan capture must occur after the measured run`);
    }
    const provenance = bundle.provenance;
    requireObject(provenance, `${expectedMode} plan provenance`);
    const planServerInfo = normalizeServerInfo(
            provenance.serverInfo, expectedMode, `${expectedMode} plan provenance`,
    );
    if (!sameServerInfo(collectorServerInfo, planServerInfo)) {
        throw new Error(`${expectedMode} collector and plan server identity must match`);
    }
    if (provenance.healthStatus !== 'UP') {
        throw new Error(`${expectedMode} plan provenance healthStatus must be UP`);
    }
    if (provenance.captureSequence !== expectedSequence) {
        throw new Error(`${expectedMode} plan provenance captureSequence must be ${expectedSequence}`);
    }
    normalizeTraceProvenance(provenance.trace, expectedMode);
    return {
        capturedAt,
        fixedClock: planServerInfo.fixedClock,
        evidence: normalizeQueryEvidence(bundle.evidence, expectedMode),
    };
}

function normalizeTraceProvenance(trace, expectedMode) {
    requireObject(trace, `${expectedMode} plan trace provenance`);
    requireText(trace.sourceLog, `${expectedMode} plan trace provenance sourceLog`);
    if (trace.sourceLog.includes('/') || trace.sourceLog.includes('\\') || !trace.sourceLog.endsWith('.log')) {
        throw new Error(`${expectedMode} plan trace provenance sourceLog must be a log basename`);
    }
    if (trace.logger !== 'org.springframework.jdbc.core=TRACE') {
        throw new Error(`${expectedMode} plan trace provenance logger must be org.springframework.jdbc.core=TRACE`);
    }
    requireNonNegativeInteger(trace.startOffset, `${expectedMode} plan trace provenance startOffset`);
    requirePositiveInteger(trace.endOffset, `${expectedMode} plan trace provenance endOffset`);
    if (trace.endOffset <= trace.startOffset) {
        throw new Error(`${expectedMode} plan trace provenance endOffset must exceed startOffset`);
    }
    for (const field of ['traceSegmentSha256', 'sanitizedTraceSha256']) {
        if (typeof trace[field] !== 'string' || !/^[a-f0-9]{64}$/.test(trace[field])) {
            throw new Error(`${expectedMode} plan trace provenance ${field} must be SHA-256`);
        }
    }
    if (trace.statementCount !== 4) {
        throw new Error(`${expectedMode} plan trace provenance statementCount must be 4`);
    }
}

function normalizeQueryEvidence(evidence, expectedMode) {
    requireExactNames(
            evidence,
            REQUIRED_QUERY_SHAPES,
            ({queryShape}) => queryShape,
            `${expectedMode} query evidence`,
    );
    return evidence.map((query, index) => {
        requireObject(query, `${expectedMode} query evidence ${index}`);
        if (query.cacheMode !== expectedMode) {
            throw new Error(`${expectedMode} query evidence cacheMode must be ${expectedMode}`);
        }
        if (query.requestPath !== QUERY_REQUEST_PATHS[query.queryShape]) {
            throw new Error(`${expectedMode}.${query.queryShape}.requestPath must match the live HTTP shape`);
        }
        requireText(query.traceThread, `${expectedMode}.${query.queryShape}.traceThread`);
        if (!query.traceThread.startsWith('http-nio-')) {
            throw new Error(`${expectedMode}.${query.queryShape}.traceThread must be an HTTP worker`);
        }
        requireText(query.preparedSql, `${expectedMode}.${query.queryShape}.preparedSql`);
        requireText(query.exactSql, `${expectedMode}.${query.queryShape}.exactSql`);
        if (!Array.isArray(query.capturedBinds)
                || query.capturedBinds.length !== QUERY_BIND_COUNTS[query.queryShape]) {
            throw new Error(`${expectedMode}.${query.queryShape}.capturedBinds must contain live positional binds`);
        }
        query.capturedBinds.forEach((bind, bindIndex) => {
            requireObject(bind, `${expectedMode}.${query.queryShape}.capturedBinds[${bindIndex}]`);
            if (bind.index !== bindIndex + 1) {
                throw new Error(`${expectedMode}.${query.queryShape}.capturedBinds indexes must be contiguous`);
            }
            requireText(bind.value, `${expectedMode}.${query.queryShape}.capturedBinds[${bindIndex}].value`);
            requireText(bind.valueClass, `${expectedMode}.${query.queryShape}.capturedBinds[${bindIndex}].valueClass`);
        });
        requireText(query.bindSummary, `${expectedMode}.${query.queryShape}.bindSummary`);
        requireText(query.planSummary, `${expectedMode}.${query.queryShape}.planSummary`);
        requireNonNegativeNumber(query.executionMillis, `${expectedMode}.${query.queryShape}.executionMillis`);
        for (const field of ['actualRows', 'sharedHitBlocks', 'sharedReadBlocks']) {
            requireNonNegativeInteger(query[field], `${expectedMode}.${query.queryShape}.${field}`);
        }
        return {
            cacheMode: expectedMode,
            queryShape: query.queryShape,
            bindSummary: query.bindSummary,
            planSummary: query.planSummary,
            executionMillis: query.executionMillis,
            actualRows: query.actualRows,
            sharedHitBlocks: query.sharedHitBlocks,
            sharedReadBlocks: query.sharedReadBlocks,
        };
    });
}

function requireExactNames(values, required, name, label) {
    if (!Array.isArray(values)) {
        throw new Error(`${label} must contain exactly ${required.join(', ')}`);
    }
    const actual = values.map(name);
    if (actual.length !== required.length
            || new Set(actual).size !== required.length
            || required.some((value) => !actual.includes(value))) {
        throw new Error(`${label} must contain exactly ${required.join(', ')}`);
    }
}

function requireObject(value, label) {
    if (value === null || typeof value !== 'object' || Array.isArray(value)) {
        throw new Error(`${label} is required`);
    }
}

function requireText(value, label) {
    if (typeof value !== 'string' || value.trim() !== value || value.length === 0) {
        throw new Error(`${label} is required`);
    }
}

function requireTimestamp(value, label) {
    requireText(value, label);
    const timestamp = Date.parse(value);
    if (!Number.isFinite(timestamp)) {
        throw new Error(`${label} must be an ISO-8601 timestamp`);
    }
    return timestamp;
}

function requirePositiveInteger(value, label) {
    if (!Number.isInteger(value) || value <= 0) {
        throw new Error(`${label} must be a positive integer`);
    }
}

function requireNonNegativeInteger(value, label) {
    if (!Number.isSafeInteger(value) || value < 0) {
        throw new Error(`${label} must be a non-negative integer`);
    }
}

function requireNonNegativeNumber(value, label) {
    if (!Number.isFinite(value) || value < 0) {
        throw new Error(`${label} must be a non-negative number`);
    }
}

function parseArguments(args) {
    if (args.length % 2 !== 0) {
        throw new Error(`usage: node performance/normalize-m30-measurement.mjs ${REQUIRED_OPTIONS.join(' <json> ')} <json>`);
    }
    const options = new Map();
    for (let index = 0; index < args.length; index += 2) {
        const option = args[index];
        if (!REQUIRED_OPTIONS.includes(option) || options.has(option)) {
            throw new Error(`unknown or duplicate option: ${option}`);
        }
        options.set(option, args[index + 1]);
    }
    for (const option of REQUIRED_OPTIONS) {
        if (!options.has(option)) {
            throw new Error(`missing required option: ${option}`);
        }
    }
    return options;
}

async function readJson(path) {
    return JSON.parse(await readFile(path, 'utf8'));
}

async function main() {
    const options = parseArguments(process.argv.slice(2));
    const measurement = normalizeMeasurement({
        metadata: await readJson(options.get('--metadata')),
        offSummary: await readJson(options.get('--off-summary')),
        onSummary: await readJson(options.get('--on-summary')),
        offMetrics: await readJson(options.get('--off-metrics')),
        onMetrics: await readJson(options.get('--on-metrics')),
        offSamples: await readJson(options.get('--off-samples')),
        onSamples: await readJson(options.get('--on-samples')),
        queryEvidence: await readJson(options.get('--query-evidence')),
    });
    await writeFile(options.get('--out'), `${JSON.stringify(measurement, null, 2)}\n`, 'utf8');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    main().catch((error) => {
        console.error(error.message);
        process.exitCode = 1;
    });
}
