import { afterEach, describe, expect, it, vi } from 'vitest';
import { createRoster, deleteRoster, getRoster, listRosterFactions, listRosters, updateRoster } from './rosterApi.js';
const reply = (status, body) => new Response(status === 204 ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const token = value => reply(200, { headerName: 'X-CSRF-TOKEN', token: value });
afterEach(() => vi.unstubAllGlobals());
describe('Roster API client', () => {
  it('reads private lists, factions and details without caching', async () => {
    const fetch = vi.fn().mockImplementation(async () => reply(200, []));
    vi.stubGlobal('fetch', fetch);
    await listRosters(); await listRosterFactions(); await getRoster(12);
    expect(fetch.mock.calls.map(([path]) => path)).toEqual(['/api/rosters', '/api/rosters/factions', '/api/rosters/12']);
    for (const [, options] of fetch.mock.calls) expect(options).toMatchObject({ method: 'GET', credentials: 'same-origin', cache: 'no-store' });
  });
  it('uses POST, PUT and DELETE with a fresh CSRF token for each action', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(token('a')).mockResolvedValueOnce(reply(201, { id: 12 }))
      .mockResolvedValueOnce(token('b')).mockResolvedValueOnce(reply(200, { id: 12 }))
      .mockResolvedValueOnce(token('c')).mockResolvedValueOnce(reply(204));
    vi.stubGlobal('fetch', fetch);
    const fields = { rosterName: 'Company', factionId: 2, ducatLimit: 700 };
    await createRoster(fields); await updateRoster(12, fields); await deleteRoster(12);
    const writes = fetch.mock.calls.filter(([, options]) => ['POST', 'PUT', 'DELETE'].includes(options.method));
    expect(writes.map(([, options]) => options.method)).toEqual(['POST', 'PUT', 'DELETE']);
    expect(writes.map(([, options]) => options.headers['X-CSRF-TOKEN'])).toEqual(['a', 'b', 'c']);
    expect(JSON.parse(writes[0][1].body)).toEqual(fields);
    expect(writes[2][1].body).toBeUndefined();
  });
  it('reports validation and expired sessions without automatically repeating writes', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(token('a')).mockResolvedValueOnce(reply(400, { message: 'Invalid settings.', errors: { rosterName: 'Required.' } })));
    await expect(createRoster({})).rejects.toMatchObject({ status: 400, fields: { rosterName: 'Required.' } });
    const fetch = vi.fn().mockResolvedValueOnce(token('b')).mockResolvedValueOnce(reply(403, {}));
    vi.stubGlobal('fetch', fetch);
    await expect(deleteRoster(12)).rejects.toMatchObject({ status: 403 });
    expect(fetch).toHaveBeenCalledTimes(2);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply(401, {})));
    await expect(listRosters()).rejects.toMatchObject({ status: 401 });
  });
  it('hides server details and handles network failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply(503, { message: 'private SQL details' })));
    await expect(listRosters()).rejects.toThrow('Rosters are temporarily unavailable.');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('fetch failed')));
    await expect(listRosters()).rejects.toThrow('Unable to reach the roster service.');
  });
});
