import assert from 'node:assert/strict';
import { getCurrentUser, registerAccount, loginAccount, logoutAccount, loadProject } from '../../../frontend/src/api.js';

// Run by AuthenticationWorkflowTest against its isolated application/database.
// This uses the real frontend API functions, not mocked success responses.
const [base, username] = process.argv.slice(2);
assert.match(base || '', /^http:\/\/127\.0\.0\.1:\d+$/);
assert.match(username || '', /^workflow_[a-f0-9]+$/);
const nativeFetch = globalThis.fetch;
const cookieJar = new Map();
const password = 'Workflow test passphrase 123!';

// Node has no browser cookie jar. Keep cookies in memory for this one test only.
// HTTP-only/SameSite flags and session isolation are tested by the Java suite.
globalThis.fetch = async (path, options = {}) => {
  const url = new URL(path, base);
  assert.equal(url.origin, base, 'The frontend must use the same backend origin.');
  assert.equal(options.credentials ?? 'same-origin', 'same-origin');
  const headers = new Headers(options.headers);
  if (cookieJar.size) headers.set('Cookie', [...cookieJar].map(([key, value]) => `${key}=${value}`).join('; '));
  const response = await nativeFetch(url, {
    ...options, headers, redirect: 'manual',
    signal: options.signal || AbortSignal.timeout(10000),
  });
  for (const value of response.headers.getSetCookie()) {
    const [pair, ...attributes] = value.split(';');
    const separator = pair.indexOf('=');
    const name = pair.slice(0, separator).trim();
    const contents = pair.slice(separator + 1).trim();
    const expires = attributes.find(attribute => /^\s*expires=/i.test(attribute));
    const deleted = !contents || attributes.some(attribute => /^\s*max-age=0\s*$/i.test(attribute)) ||
      (expires && Date.parse(expires.slice(expires.indexOf('=') + 1)) <= Date.now());
    if (deleted) cookieJar.delete(name);
    else cookieJar.set(name, contents);
  }
  return response;
};

try {
  const project = await loadProject();
  assert.equal(project.health.database, 'UP');
  assert.ok(project.catalog.units.some(unit => unit.name === 'Lieutenant'));
  assert.equal(await getCurrentUser(), null);

  // Server validation must still apply when browser validation is bypassed.
  await assert.rejects(registerAccount({ username, email: `${username}@example.invalid`,
    password, confirmPassword: 'does not match' }), error => error.status === 400 && Boolean(error.fields.confirmPassword));

  const account = await registerAccount({ username, email: `${username}@example.invalid`,
    password, confirmPassword: password });
  assert.ok(account.id > 0);
  assert.deepEqual(Object.keys(account).sort(), ['email', 'id', 'username']);
  assert.equal(await getCurrentUser(), null, 'Registration must not silently log in.');

  await assert.rejects(registerAccount({ username: username.toUpperCase(), email: `${username}@example.invalid`,
    password, confirmPassword: password }), error => error.status === 409);
  await assert.rejects(loginAccount(username, 'wrong password'), error => error.status === 401);
  assert.equal(await getCurrentUser(), null);

  assert.deepEqual(await loginAccount(username.toUpperCase(), password), account);
  assert.deepEqual(await getCurrentUser(), account);
  const authenticatedCookie = cookieJar.get('JSESSIONID');
  assert.ok(authenticatedCookie);

  // A request with the session cookie but no CSRF token must not log the user out.
  const rejected = await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
  assert.equal(rejected.status, 403);
  assert.deepEqual(await getCurrentUser(), account);

  assert.equal(await logoutAccount(), null);
  assert.equal(await getCurrentUser(), null);
  const replay = await nativeFetch(new URL('/api/auth/me', base), {
    headers: { Cookie: `JSESSIONID=${authenticatedCookie}` }, signal: AbortSignal.timeout(10000),
  });
  assert.equal(replay.status, 401, 'A logged-out session must not be reusable.');

  // A second complete login verifies that the client refreshes CSRF state after logout.
  assert.deepEqual(await loginAccount(username, password), account);
  await logoutAccount();
  assert.equal(await getCurrentUser(), null);
  console.log('PASS: real frontend API client registration, validation, login, CSRF, profile and logout.');
} finally {
  globalThis.fetch = nativeFetch;
  cookieJar.clear();
}
