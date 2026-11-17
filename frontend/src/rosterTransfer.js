import { request } from './rosterApi.js';
import { getCsrfToken } from './api.js';

export const rosterSnapshot = (id, signal) => request(`/api/rosters/${id}/export`, 'GET', undefined, signal);

export async function importRoster(file, signal) {
  if (!file || file.size > 1024 * 1024) throw new Error('Choose a JSON file no larger than 1 MiB.');
  const csrf = await getCsrfToken(signal);
  const response = await fetch('/api/rosters/import', {
    method: 'POST', signal, credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: file,
  });
  const data = await response.json().catch(() => null);
  if (!response.ok || !data) {
    throw Object.assign(new Error(data?.message || 'Unable to import this roster.'), { status: response.status });
  }
  return data;
}

export function downloadRoster(name, content, extension = 'json') {
  const title = name.replace(/[^a-z0-9_-]+/gi, '-').replace(/^-+|-+$/g, '') || 'roster';
  const url = URL.createObjectURL(new Blob([content], {
    type: extension === 'json' ? 'application/json' : 'text/plain;charset=utf-8',
  }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `${title}.${extension}`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
