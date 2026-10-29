/** Fetch same-origin JSON so the same build works locally and behind nginx. */
export async function getJson(path, signal) {
  const response = await fetch(path, { signal, headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error('The service is temporarily unavailable. Please try again.');
  return response.json();
}
export async function loadProject(signal) {
  const [health, catalog] = await Promise.all([
    getJson('/api/health', signal), getJson('/api/catalog', signal),
  ]);
  if (health.status !== 'UP' || health.database !== 'UP') throw new Error('The database is not ready. Please try again.');
  return { health, catalog };
}

/** Authentication errors expose a status and safe field messages to the forms. */
export class AuthError extends Error {
  constructor(message, status = 0, fields = {}) {
    super(message);
    this.name = 'AuthError';
    this.status = status;
    this.fields = fields;
  }
}

async function authRequest(path, options = {}, anonymousAllowed = false) {
  let response;
  try {
    response = await fetch(path, {
      ...options, credentials: 'same-origin', cache: 'no-store',
      headers: { Accept: 'application/json', ...options.headers },
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new AuthError('Unable to reach the account service. Please try again.');
  }
  if (anonymousAllowed && response.status === 401) return null;
  if (response.status === 204) return null;
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 403) throw new AuthError('Your session changed. Please try again.', 403);
    if (response.status >= 500) throw new AuthError('The account service is temporarily unavailable. Please try again.', response.status);
    throw new AuthError(data?.message || 'The request failed. Please try again.', response.status, data?.errors || {});
  }
  if (!data) throw new AuthError('The account service returned an unexpected response. Please try again.');
  return data;
}

export function getCurrentUser(signal) {
  return authRequest('/api/auth/me', { signal }, true);
}

export function getCsrfToken(signal) {
  return authRequest('/api/auth/csrf', { signal });
}

async function accountWrite(path, body, contentType, signal) {
  // Fetch for each action: login/logout invalidate the previous session's CSRF token.
  const csrf = await getCsrfToken(signal);
  return authRequest(path, {
    method: 'POST', signal, body,
    headers: { 'Content-Type': contentType, [csrf.headerName]: csrf.token },
  });
}

export function registerAccount(fields, signal) {
  return accountWrite('/api/auth/register', JSON.stringify(fields), 'application/json', signal);
}

export async function loginAccount(username, password, signal) {
  await accountWrite('/api/auth/login', new URLSearchParams({ username, password }).toString(),
    'application/x-www-form-urlencoded', signal);
  const user = await getCurrentUser(signal);
  if (!user) throw new AuthError('Your session ended. Please sign in again.', 401);
  return user;
}

export function logoutAccount(signal) {
  return accountWrite('/api/auth/logout', '', 'application/x-www-form-urlencoded', signal);
}
