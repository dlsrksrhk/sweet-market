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
