import test from 'node:test';
import assert from 'node:assert/strict';
import { callRiskApi } from './api.js';

test('sends Bearer token to correct endpoint and returns expected fields only', async () => {
  const result = await callRiskApi({ getToken: async () => 'test-only', apiUrl: 'http://localhost:8080/', fetchImpl: async (url, options) => {
    assert.equal(url, 'http://localhost:8080/api/risk-status');
    assert.equal(options.headers.Authorization, 'Bearer test-only');
    return new Response(JSON.stringify({ status: 'protected', subject: 'test-user', control: 'JWT', unexpected: 'ignored' }));
  }});
  assert.deepEqual(result, { status: 'protected', subject: 'test-user', control: 'JWT' });
});
test('empty 401 reports status rather than JSON parsing error', async () => {
  await assert.rejects(callRiskApi({ getToken: async () => 'test-only', fetchImpl: async () => new Response(null, { status: 401 }) }), /API HTTP 401/);
});
test('never exposes arbitrary backend error body', async () => {
  await assert.rejects(callRiskApi({ getToken: async () => 'test-only', fetchImpl: async () => new Response('sensitive-detail', { status: 500 }) }), ex => ex.message.includes('500') && !ex.message.includes('sensitive-detail'));
});
test('invalid successful response is handled', async () => {
  await assert.rejects(callRiskApi({ getToken: async () => 'test-only', fetchImpl: async () => new Response('') }), /JSON válido/);
});
test('token failure prevents HTTP call', async () => {
  await assert.rejects(callRiskApi({ getToken: async () => { throw new Error('login-required'); }, fetchImpl: async () => { assert.fail('must not fetch'); } }), /login-required/);
});
