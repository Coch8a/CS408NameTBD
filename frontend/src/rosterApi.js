import { getCsrfToken } from './api.js';

/** Roster requests use the same session cookie and fresh CSRF tokens as account actions. */
async function request(path, method, fields, signal) {
  const headers = { Accept: 'application/json' };
  if (method !== 'GET') {
    const csrf = await getCsrfToken(signal);
    headers[csrf.headerName] = csrf.token;
    if (fields !== undefined) headers['Content-Type'] = 'application/json';
  }
  let response;
  try {
    response = await fetch(path, { method, signal, credentials: 'same-origin', cache: 'no-store',
      headers, body: fields === undefined ? undefined : JSON.stringify(fields) });
  } catch (reason) {
    if (reason.name === 'AbortError') throw reason;
    throw new Error('Unable to reach the roster service. Please try again.');
  }
  if (response.status === 204) return null;
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const message = response.status === 401 ? 'Your session ended. Please sign in again.'
      : response.status === 403 ? 'Your session changed. Please try again.'
      : response.status >= 500 ? 'Rosters are temporarily unavailable. Please try again.'
      : data?.message || 'The roster request failed. Please try again.';
    throw Object.assign(new Error(message), { status: response.status, fields: data?.errors || {} });
  }
  if (!data) throw new Error('The roster service returned an unexpected response. Please try again.');
  return data;
}

export const listRosters = signal => request('/api/rosters', 'GET', undefined, signal);
export const listRosterFactions = signal => request('/api/rosters/factions', 'GET', undefined, signal);
export const getRoster = (id, signal) => request(`/api/rosters/${id}`, 'GET', undefined, signal);
export const createRoster = (fields, signal) => request('/api/rosters', 'POST', fields, signal);
export const updateRoster = (id, fields, signal) => request(`/api/rosters/${id}`, 'PUT', fields, signal);
export const deleteRoster = (id, signal) => request(`/api/rosters/${id}`, 'DELETE', undefined, signal);
