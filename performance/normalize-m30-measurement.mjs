import {readFile, writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

const REQUIRED_ENDPOINTS = ['catalog', 'events', 'popularity', 'detail'];
const REQUIRED_QUERY_SHAPES = ['GLOBAL_CATALOG', 'FIXED_STORE_CATALOG', 'ACTIVE_EVENTS', 'POPULARITY'];
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
    '--query-evidence',
    '--out',
];

export function normalizeMeasurement({
    metadata,
    offSummary,
    onSummary,
    offMetrics,
    onMetrics,
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

    return {
        measurementId: metadata.measurementId,
        artifactDirectory: metadata.artifactDirectory,
        off: {
            ...off,
            endpointMetrics: normalizeEndpointMetrics(offMetrics, 'OFF', 'off'),
            queryEvidence: normalizeQueryEvidence(queryEvidence?.off, 'OFF'),
        },
        on: {
            ...on,
            endpointMetrics: normalizeEndpointMetrics(onMetrics, 'ON', 'on'),
            queryEvidence: normalizeQueryEvidence(queryEvidence?.on, 'ON'),
        },
    };
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
