import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

import {parseTraceBuffer} from './parse-m30-jdbc-trace.mjs';

const fixture = await readFile(new URL('./fixtures/m30-jdbc-trace.log', import.meta.url));

test('live_trace에서_네_HTTP_SQL과_bind를_정확히_복원한다', () => {
    const parsed = parseTraceBuffer(fixture, 0, fixture.length);

    assert.deepEqual(parsed.statements.map(({queryShape}) => queryShape), [
        'GLOBAL_CATALOG', 'FIXED_STORE_CATALOG', 'ACTIVE_EVENTS', 'POPULARITY',
    ]);
    assert.equal(parsed.statements.some(({preparedSql}) => preparedSql.includes('operational_events')), false);
    assert.equal(parsed.statements[0].capturedBinds.length, 3);
    assert.match(parsed.statements[0].exactSql, /promotion\.start_at <= '2026-07-17T00:00:00Z'::timestamptz/);
    assert.match(parsed.statements[0].exactSql, /LIMIT 21$/);
    assert.equal(parsed.statements[1].bindSummary,
            'now=2026-07-17T00:00:00Z, storeId=1, limitPlusOne=21');
    assert.equal(parsed.statements[3].bindSummary,
            'since=2026-07-10T00:00:00Z, now=2026-07-17T00:00:00Z');
    assert.match(parsed.traceSegmentSha256, /^[a-f0-9]{64}$/);
    assert.match(parsed.sanitizedTraceSha256, /^[a-f0-9]{64}$/);
});

test('live_trace에_필수_shape가_없거나_중복되면_거부한다', () => {
    const text = fixture.toString('utf8');
    const withoutPopularity = Buffer.from(text.slice(0, text.indexOf('2026-07-17T22:00:04.000')));
    assert.throws(() => parseTraceBuffer(withoutPopularity, 0, withoutPopularity.length),
            /must contain exactly GLOBAL_CATALOG, FIXED_STORE_CATALOG, ACTIVE_EVENTS, POPULARITY/);

    const globalStart = text.indexOf('2026-07-17T22:00:01.000');
    const fixedStart = text.indexOf('2026-07-17T22:00:02.000');
    const duplicate = Buffer.from(`${text}\n${text.slice(globalStart, fixedStart)}`);
    assert.throws(() => parseTraceBuffer(duplicate, 0, duplicate.length), /duplicate GLOBAL_CATALOG/);
});

test('live_trace의_bind가_shape계약과_다르면_거부한다', () => {
    const inconsistent = Buffer.from(fixture.toString('utf8').replace(
            'column index 4, parameter value [2026-07-17T00:00Z], value class [java.time.OffsetDateTime]',
            'column index 4, parameter value [2026-07-18T00:00Z], value class [java.time.OffsetDateTime]',
    ));

    assert.throws(() => parseTraceBuffer(inconsistent, 0, inconsistent.length), /bind contract/);
});
