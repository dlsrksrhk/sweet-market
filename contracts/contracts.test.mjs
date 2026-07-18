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

const expectedSchemas = {
  ProbeRequest: {
    type: 'object', additionalProperties: false, required: ['message'],
    properties: { message: { type: 'string', minLength: 1, maxLength: 100 } }
  },
  ProbeResponse: {
    type: 'object', additionalProperties: false,
    required: ['service', 'message', 'requestId', 'correlationId'],
    properties: {
      service: { type: 'string' }, message: { type: 'string' },
      requestId: { type: 'string', format: 'uuid' },
      correlationId: { type: 'string', format: 'uuid' }
    }
  },
  ProbeWebhook: {
    type: 'object', additionalProperties: false, required: ['source', 'message'],
    properties: {
      source: { enum: ['PAYMENT_GATEWAY', 'DELIVERY_PROVIDER'] },
      message: { type: 'string', minLength: 1, maxLength: 100 }
    }
  },
  IntegrationError: {
    type: 'object', additionalProperties: false, required: ['code', 'message', 'requestId'],
    properties: {
      code: { type: 'string' }, message: { type: 'string' },
      requestId: { type: ['string', 'null'], format: 'uuid' }
    }
  }
}

const expectedVector = {
  apiKey: 'm32-test-api-key',
  keyId: 'm32-test-key-1',
  secret: 'm32-test-hmac-secret-32bytes-minimum',
  timestamp: 1784386800,
  requestId: '3b2f8c6a-2f88-4f75-8c7b-4ad40b519a41',
  method: 'POST',
  rawTarget: '/api/v1/probes',
  bodyUtf8: '{"message":"m32-contract-probe"}',
  bodySha256: '1858662b42bc7be12235617df1cc1938fb5a3488e7132fc3a035bf5e8a4327d4',
  canonical: 'v1\nm32-test-key-1\n1784386800\n3b2f8c6a-2f88-4f75-8c7b-4ad40b519a41\nPOST\n/api/v1/probes\n1858662b42bc7be12235617df1cc1938fb5a3488e7132fc3a035bf5e8a4327d4',
  signature: '00c0d3767207c817239881c692fb37de931444242e18186c08a95c51114cd2ac'
}

const expectedParameters = [
  { name: 'X-Api-Key', in: 'header', required: true, schema: { type: 'string' } },
  { name: 'X-Key-Id', in: 'header', required: true, schema: { type: 'string' } },
  { name: 'X-Request-Id', in: 'header', required: true, schema: { type: 'string', format: 'uuid' } },
  { name: 'X-Timestamp', in: 'header', required: true, description: 'Epoch seconds accepted within plus or minus 300 seconds.', schema: { type: 'string', pattern: '^[0-9]+$' } },
  { name: 'X-Signature', in: 'header', required: true, description: 'Lowercase HMAC-SHA256 hex of the v1 canonical payload.', schema: { type: 'string', pattern: '^[0-9a-f]{64}$' } },
  { name: 'X-Correlation-Id', in: 'header', required: true, schema: { type: 'string', format: 'uuid' } },
  { name: 'traceparent', in: 'header', required: false, schema: { type: 'string' } },
  { name: 'Content-Type', in: 'header', required: true, schema: { const: 'application/json' } }
]

test('네_계약은_서명된_probe_바인딩을_완전하게_정의한다', async () => {
  const cases = [
    ['payment-gateway-v1.openapi.json', '/api/v1/probes', 'http://localhost:8081', 'ProbeRequest', '200', 'ProbeResponse'],
    ['payment-gateway-webhooks-v1.openapi.json', '/api/integrations/payment-gateway/v1/probes', 'http://localhost:8080', 'ProbeWebhook', '204'],
    ['delivery-provider-v1.openapi.json', '/api/v1/probes', 'http://localhost:8082', 'ProbeRequest', '200', 'ProbeResponse'],
    ['delivery-provider-webhooks-v1.openapi.json', '/api/integrations/delivery-provider/v1/probes', 'http://localhost:8080', 'ProbeWebhook', '204']
  ]

  for (const [name, path, serverUrl, requestSchema, successStatus, successSchema] of cases) {
    const document = await load(name)
    assert.equal(document.openapi, '3.1.0')
    assert.deepEqual(document.servers, [{ url: serverUrl }])
    const operation = document.paths[path].post
    assert.equal(operation.requestBody.required, true)
    assert.deepEqual(Object.keys(operation.requestBody.content), ['application/json'])
    assert.equal(operation.requestBody.content['application/json'].schema.$ref, `#/components/schemas/${requestSchema}`)
    assert.equal(operation['x-raw-body-limit-bytes'], 1_048_576)
    assert.deepEqual(operation.parameters, expectedParameters)
    for (const header of requiredHeaders) {
      const parameter = operation.parameters.find((candidate) => candidate.name === header)
      assert.equal(parameter.in, 'header', `${name}: ${header} location`)
      assert.equal(parameter.required, true, `${name}: ${header} required`)
    }
    for (const status of ['400', '401', '409', '413']) {
      assert.equal(operation.responses[status].$ref, `#/components/responses/${({ 400: 'BadRequest', 401: 'Unauthorized', 409: 'Conflict', 413: 'PayloadTooLarge' })[status]}`)
    }
    assert.ok(operation.responses[successStatus], `${name}: ${successStatus}`)
    if (successSchema) {
      assert.equal(operation.responses[successStatus].content['application/json'].schema.$ref, `#/components/schemas/${successSchema}`)
    } else {
      assert.equal(operation.responses[successStatus].content, undefined)
    }
    for (const [schemaName, schema] of Object.entries(expectedSchemas)) {
      assert.deepEqual(document.components.schemas[schemaName], schema, `${name}: ${schemaName}`)
    }
  }
})

test('HMAC_vector는_고정된_canonical_payload와_일치한다', async () => {
  const vector = await load('hmac-v1-test-vectors.json')
  assert.deepEqual(vector, expectedVector)
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
