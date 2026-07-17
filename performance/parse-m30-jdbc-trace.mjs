import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

const REQUIRED_SHAPES = ['GLOBAL_CATALOG', 'FIXED_STORE_CATALOG', 'ACTIVE_EVENTS', 'POPULARITY'];
const REQUEST_PATHS = {
    GLOBAL_CATALOG: '/api/catalog/products?size=20',
    FIXED_STORE_CATALOG: '/api/stores/1/catalog/products?size=20',
    ACTIVE_EVENTS: '/api/discovery/events',
    POPULARITY: '/api/discovery/popular-products',
};

export function parseTraceBuffer(buffer, startOffset, endOffset) {
    if (!Buffer.isBuffer(buffer)) {
        throw new Error('trace input must be a Buffer');
    }
    if (!Number.isSafeInteger(startOffset) || !Number.isSafeInteger(endOffset)
            || startOffset < 0 || endOffset <= startOffset || endOffset > buffer.length) {
        throw new Error('trace byte offsets are invalid');
    }
    const segment = buffer.subarray(startOffset, endOffset);
    const records = parseRecords(segment.toString('utf8'));
    const classified = [];
    for (const record of records) {
        const queryShape = classify(record.preparedSql, record.binds.length);
        if (queryShape === null) {
            continue;
        }
        if (classified.some((statement) => statement.queryShape === queryShape)) {
            throw new Error(`live trace contains duplicate ${queryShape}`);
        }
        classified.push(normalizeStatement(queryShape, record));
    }
    if (classified.length !== REQUIRED_SHAPES.length
            || REQUIRED_SHAPES.some((shape) => !classified.some(({queryShape}) => queryShape === shape))) {
        throw new Error(`live trace must contain exactly ${REQUIRED_SHAPES.join(', ')}`);
    }
    classified.sort((left, right) => REQUIRED_SHAPES.indexOf(left.queryShape)
        - REQUIRED_SHAPES.indexOf(right.queryShape));
    const sanitized = JSON.stringify(classified);
    return {
        traceSegmentSha256: sha256(segment),
        sanitizedTraceSha256: sha256(Buffer.from(sanitized)),
        statements: classified,
    };
}

function parseRecords(text) {
    const lines = text.split(/\r?\n/);
    const records = [];
    const latestByThread = new Map();
    let pendingSql = null;
    for (const line of lines) {
        if (pendingSql !== null) {
            const closes = line.endsWith(']');
            pendingSql.parts.push(closes ? line.slice(0, -1) : line);
            if (closes) {
                const record = {
                    threadName: pendingSql.threadName,
                    preparedSql: pendingSql.parts.join('\n').trim(),
                    binds: [],
                };
                records.push(record);
                latestByThread.set(record.threadName, record);
                pendingSql = null;
            }
            continue;
        }
        const statement = line.match(/^\S+\s+DEBUG\s+\d+\s+---\s+\[[^\]]+\]\s+\[([^\]]+)\].*JdbcTemplate\s+:\s+Executing prepared SQL statement \[(.*)$/);
        if (statement) {
            const threadName = statement[1];
            if (!threadName.startsWith('http-nio-')) {
                latestByThread.delete(threadName);
                continue;
            }
            const closes = statement[2].endsWith(']');
            if (closes) {
                const record = {
                    threadName,
                    preparedSql: statement[2].slice(0, -1).trim(),
                    binds: [],
                };
                records.push(record);
                latestByThread.set(threadName, record);
            } else {
                pendingSql = {threadName, parts: [statement[2]]};
            }
            continue;
        }
        const bind = line.match(/^\S+\s+TRACE\s+\d+\s+---\s+\[[^\]]+\]\s+\[([^\]]+)\].*StatementCreatorUtils\s+:\s+Setting SQL statement parameter value: column index (\d+), parameter value \[(.*?)\], value class \[(.*?)\], SQL type/);
        if (bind) {
            const record = latestByThread.get(bind[1]);
            if (record) {
                record.binds.push({
                    index: Number.parseInt(bind[2], 10),
                    value: bind[3],
                    valueClass: bind[4],
                });
            }
        }
    }
    if (pendingSql !== null) {
        throw new Error('live trace ended inside a prepared SQL statement');
    }
    return records;
}

function classify(sql, bindCount) {
    if (sql.startsWith('SELECT p.id AS product_id,')) {
        return bindCount === 4 || /\bWHERE\s+s\.id\s*=\s*\?/i.test(sql)
            ? 'FIXED_STORE_CATALOG' : 'GLOBAL_CATALOG';
    }
    if (sql.startsWith("SELECT * FROM (SELECT 'PROMOTION' AS event_type")) {
        return 'ACTIVE_EVENTS';
    }
    if (sql.startsWith('WITH wishlist_scores AS (')) {
        return 'POPULARITY';
    }
    return null;
}

function normalizeStatement(queryShape, record) {
    const binds = [...record.binds].sort((left, right) => left.index - right.index);
    if (binds.some((bind, index) => bind.index !== index + 1)) {
        throw new Error(`${queryShape} bind indexes are not contiguous`);
    }
    const normalizedBinds = binds.map(normalizeBind);
    validateBindContract(queryShape, normalizedBinds);
    return {
        queryShape,
        requestPath: REQUEST_PATHS[queryShape],
        threadName: record.threadName,
        preparedSql: record.preparedSql,
        capturedBinds: normalizedBinds.map(({index, value, valueClass}) => ({index, value, valueClass})),
        exactSql: replacePlaceholders(record.preparedSql, normalizedBinds),
        bindSummary: bindSummary(queryShape, normalizedBinds),
    };
}

function normalizeBind(bind) {
    if (bind.valueClass === 'java.time.OffsetDateTime') {
        const timestamp = Date.parse(bind.value);
        if (!Number.isFinite(timestamp)) {
            throw new Error(`invalid OffsetDateTime bind at index ${bind.index}`);
        }
        const value = new Date(timestamp).toISOString().replace('.000Z', 'Z');
        return {...bind, value, sqlLiteral: `'${value}'::timestamptz`};
    }
    if (bind.valueClass === 'java.lang.Integer' || bind.valueClass === 'java.lang.Long') {
        if (!/^-?\d+$/.test(bind.value)) {
            throw new Error(`invalid integral bind at index ${bind.index}`);
        }
        return {...bind, value: String(Number.parseInt(bind.value, 10)), sqlLiteral: bind.value};
    }
    throw new Error(`unsupported live trace bind class ${bind.valueClass}`);
}

function validateBindContract(shape, binds) {
    const same = (left, right) => binds[left]?.value === binds[right]?.value;
    let valid = false;
    if (shape === 'GLOBAL_CATALOG') {
        valid = binds.length === 3 && same(0, 1) && binds[2].value === '21';
    } else if (shape === 'FIXED_STORE_CATALOG') {
        valid = binds.length === 4 && same(0, 1) && binds[2].value === '1' && binds[3].value === '21';
    } else if (shape === 'ACTIVE_EVENTS') {
        valid = binds.length === 4 && same(0, 1) && same(1, 2) && same(2, 3);
    } else if (shape === 'POPULARITY') {
        valid = binds.length === 4 && same(0, 1) && same(2, 3)
            && Date.parse(binds[2].value) - Date.parse(binds[0].value) === 7 * 24 * 60 * 60 * 1000;
    }
    if (!valid) {
        throw new Error(`${shape} live trace bind contract does not match`);
    }
}

function replacePlaceholders(sql, binds) {
    let result = '';
    let bindIndex = 0;
    let quoted = false;
    for (let index = 0; index < sql.length; index += 1) {
        const character = sql[index];
        if (character === "'") {
            result += character;
            if (quoted && sql[index + 1] === "'") {
                result += sql[index + 1];
                index += 1;
            } else {
                quoted = !quoted;
            }
        } else if (character === '?' && !quoted) {
            if (bindIndex >= binds.length) {
                throw new Error('prepared SQL contains more placeholders than captured binds');
            }
            result += binds[bindIndex].sqlLiteral;
            bindIndex += 1;
        } else {
            result += character;
        }
    }
    if (quoted || bindIndex !== binds.length) {
        throw new Error('prepared SQL placeholder count does not match captured binds');
    }
    return result;
}

function bindSummary(shape, binds) {
    if (shape === 'GLOBAL_CATALOG') {
        return `now=${binds[0].value}, limitPlusOne=${binds[2].value}`;
    }
    if (shape === 'FIXED_STORE_CATALOG') {
        return `now=${binds[0].value}, storeId=${binds[2].value}, limitPlusOne=${binds[3].value}`;
    }
    if (shape === 'ACTIVE_EVENTS') {
        return `now=${binds[0].value}`;
    }
    return `since=${binds[0].value}, now=${binds[2].value}`;
}

function sha256(buffer) {
    return createHash('sha256').update(buffer).digest('hex');
}

async function main() {
    const options = new Map();
    for (let index = 2; index < process.argv.length; index += 2) {
        options.set(process.argv[index], process.argv[index + 1]);
    }
    for (const name of ['--trace-log', '--start-offset', '--end-offset']) {
        if (!options.has(name)) {
            throw new Error(`missing ${name}`);
        }
    }
    const buffer = await readFile(options.get('--trace-log'));
    const parsed = parseTraceBuffer(
            buffer,
            Number.parseInt(options.get('--start-offset'), 10),
            Number.parseInt(options.get('--end-offset'), 10),
    );
    process.stdout.write(`${JSON.stringify(parsed)}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    main().catch((error) => {
        process.stderr.write(`${error.message}\n`);
        process.exitCode = 1;
    });
}
