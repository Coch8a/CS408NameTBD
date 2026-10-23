import { afterEach, describe, expect, it, vi } from 'vitest';
import { getCurrentUser, registerAccount, loginAccount, logoutAccount } from './api.js';

const account = { id: 1, username: 'tester', email: 'test@example.invalid' };
const response = (status, body) => new Response(status === 204 ? null : JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});
const token = value => response(200, { headerName: 'X-CSRF-TOKEN', token: value });
afterEach(() => vi.unstubAllGlobals());

describe('Session API client', () => {
  it('treats anonymous profile responses as signed out', async () => {
    const fetch = vi.fn().mockResolvedValue(response(401, { message: 'Sign in to continue.' }));
    vi.stubGlobal('fetch', fetch);
    expect(await getCurrentUser()).toBeNull();
    expect(fetch).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({ credentials: 'same-origin', cache: 'no-store' }));
  });
  it('gets a fresh CSRF token for each write and uses the backend content types', async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce(token('register-token')).mockResolvedValueOnce(response(201, account))
      .mockResolvedValueOnce(token('login-token')).mockResolvedValueOnce(response(200, { message: 'Signed in.' }))
      .mockResolvedValueOnce(response(200, account))
      .mockResolvedValueOnce(token('logout-token')).mockResolvedValueOnce(response(204));
    vi.stubGlobal('fetch', fetch);
    const fields = { username: 'tester', email: account.email, password: 'A test & passphrase!', confirmPassword: 'A test & passphrase!' };
    await registerAccount(fields);
    expect(await loginAccount(fields.username, fields.password)).toEqual(account);
    expect(await logoutAccount()).toBeNull();
    const writes = fetch.mock.calls.filter(([, options]) => options.method === 'POST');
    expect(writes).toHaveLength(3);
    expect(writes.map(([, options]) => options.headers['X-CSRF-TOKEN'])).toEqual(['register-token', 'login-token', 'logout-token']);
    expect(JSON.parse(writes[0][1].body)).toEqual(fields);
    expect(writes[1][1].headers['Content-Type']).toBe('application/x-www-form-urlencoded');
    expect(new URLSearchParams(writes[1][1].body).get('password')).toBe(fields.password);
    expect(writes.every(([, options]) => options.credentials === 'same-origin')).toBe(true);
  });
  it('reports a stale CSRF token without automatically repeating a write', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(token('stale')).mockResolvedValueOnce(response(403, {}));
    vi.stubGlobal('fetch', fetch);
    await expect(logoutAccount()).rejects.toMatchObject({ status: 403, message: 'Your session changed. Please try again.' });
    expect(fetch).toHaveBeenCalledTimes(2);
  });
  it('preserves server field errors and duplicate account errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(token('a')).mockResolvedValueOnce(response(400, { message: 'Check the registration fields.', errors: { email: 'Invalid email.' } })));
    await expect(registerAccount({})).rejects.toMatchObject({ status: 400, fields: { email: 'Invalid email.' } });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(token('b')).mockResolvedValueOnce(response(409, { message: 'Username or email is already in use.' })));
    await expect(registerAccount({})).rejects.toMatchObject({ status: 409, message: 'Username or email is already in use.' });
  });
  it('shows safe network and server errors without exposing server details', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('fetch failed')));
    await expect(getCurrentUser()).rejects.toThrow('Unable to reach the account service.');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(503, { message: 'private database error' })));
    await expect(getCurrentUser()).rejects.toThrow('The account service is temporarily unavailable.');
  });
  it('does not report login success when the profile request is unauthorized', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(token('a')).mockResolvedValueOnce(response(200, {})).mockResolvedValueOnce(response(401, {})));
    await expect(loginAccount('tester', 'password')).rejects.toMatchObject({ status: 401 });
  });
});
