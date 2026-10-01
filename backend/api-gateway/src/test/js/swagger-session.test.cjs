const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const script = fs.readFileSync(path.join(__dirname, '../../main/resources/swagger/session-lifecycle.js'), 'utf8');

async function browser(savedInstance = 'run-a', currentInstance = 'run-a') {
  const storage = new Map([['authorized', 'saved-test-token']]);
  if (savedInstance) storage.set('railway.swagger.instance', savedInstance);
  const state = { currentInstance, unavailable: false, options: null, logouts: 0 };
  const window = {
    location: { origin: 'http://localhost:8080' },
    SwaggerUIBundle(options) {
      state.options = options;
      return { authActions: { logout(names) { assert.equal(names[0], 'bearerAuth'); state.logouts++; } } };
    },
    onload() { window.ui = window.SwaggerUIBundle({}); }
  };
  const sandbox = {
    window, URL,
    localStorage: { getItem: k => storage.get(k) ?? null, setItem: (k, v) => storage.set(k, v), removeItem: k => storage.delete(k) },
    fetch: async () => {
      if (state.unavailable) throw new Error('offline');
      return { ok: true, json: async () => ({ instanceId: state.currentInstance }) };
    }
  };
  vm.runInNewContext(script, sandbox);
  await window.onload();
  return { storage, state, window };
}

test('a refresh during the same run retains the saved token', async () => {
  const b = await browser();
  assert.equal(b.storage.get('authorized'), 'saved-test-token');
  const request = { url: '/api/users/me', headers: { Authorization: 'Bearer saved-test-token' } };
  assert.equal((await b.state.options.requestInterceptor(request)).headers.Authorization, request.headers.Authorization);
});

test('a gateway restart clears authorization before UI initialization', async () => {
  const b = await browser('run-a', 'run-b');
  assert.equal(b.storage.has('authorized'), false);
  assert.equal(b.storage.get('railway.swagger.instance'), 'run-b');
});

test('first use after upgrade clears legacy tokens with no instance identifier', async () => {
  assert.equal((await browser(null)).storage.has('authorized'), false);
});

test('an open tab strips its old token from a request after gateway restart', async () => {
  const b = await browser(); b.state.currentInstance = 'run-b';
  const request = await b.state.options.requestInterceptor({ url: '/api/users/me', headers: { authorization: 'Bearer old' } });
  assert.equal(request.headers.authorization, undefined);
  assert.equal(b.storage.has('authorized'), false);
  assert.equal(b.state.logouts, 1);
});

for (const endpoint of ['logout', 'logout-all']) {
  test(`successful ${endpoint} clears current and persisted authorization`, async () => {
    const b = await browser();
    await b.state.options.responseInterceptor({ url: `/api/auth/${endpoint}`, status: 204 });
    assert.equal(b.storage.has('authorized'), false);
    assert.equal(b.state.logouts, 1);
  });
}

test('a failed logout leaves credentials available to retry', async () => {
  const b = await browser();
  await b.state.options.responseInterceptor({ url: '/api/auth/logout', status: 503 });
  assert.equal(b.storage.get('authorized'), 'saved-test-token');
});

test('successful non-logout API responses retain authorization', async () => {
  const b = await browser();
  await b.state.options.responseInterceptor({ url: '/api/users/me', status: 200 });
  assert.equal(b.storage.get('authorized'), 'saved-test-token');
});

test('an unavailable gateway never receives the saved bearer token', async () => {
  const b = await browser(); b.state.unavailable = true;
  const request = await b.state.options.requestInterceptor({ url: '/api/users/me', headers: { Authorization: 'Bearer old' } });
  assert.equal(request.headers.Authorization, undefined);
  assert.equal(b.storage.has('authorized'), false);
});
