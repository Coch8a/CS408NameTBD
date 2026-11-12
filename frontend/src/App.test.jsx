import React from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App.jsx';

const catalog = { rulesVersion: '1.0.2', sourceUrl: 'https://www.trenchcrusade.com/rules/',
  factions: [{ id: 2, name: 'New Antioch' }],
  units: [
    { id: 4, name: 'Lieutenant', cost: 70, type: 'Leader', limit: 1, alternateCost: 0, notes: 'Required leader.' },
    { id: 5, name: 'Yeoman', cost: 30, type: 'Infantry', limit: 0, alternateCost: 0, notes: 'Bolt-Action Rifle: +5 ducats at recruitment (35 total).' },
    { id: 6, name: 'Mechanized Heavy Infantry', cost: 85, type: 'Heavy infantry', limit: 3, alternateCost: 95, notes: '85 with Reinforced Armour; 95 with Machine Armour. Weapons cost extra.' }
  ],
  equipment: [
    { id: 4, name: 'Bolt-Action Rifle', cost: 10, type: 'Ranged weapon', notes: 'Yeoman recruitment price: 5 ducats.' },
    { id: 5, name: 'Pistol', cost: 6, type: 'Ranged weapon', notes: '' },
    { id: 6, name: 'Combat Helmet', cost: 5, type: 'Equipment', notes: 'Headgear.' }
  ] };
function healthyFetch(data = catalog, filtered = data) {
  return vi.fn(async path => path === '/api/auth/me' ? { ok: false, status: 401 } : ({ ok: true, json: async () => path === '/api/health'
    ? { status: 'UP', database: 'UP' } : path.startsWith('/api/catalog?') ? filtered : data }));
}
afterEach(() => vi.unstubAllGlobals());
async function renderCatalogue(data = catalog, filtered = data) {
  vi.stubGlobal('fetch', healthyFetch(data, filtered));
  const result = render(<App />);
  await screen.findByText('Catalogue available; database connected.');
  return result;
}
const unitTable = () => within(screen.getByRole('table', { name: /unit recruitment costs/ }));
const equipmentTable = () => within(screen.getByRole('table', { name: /armoury prices/ }));
const params = () => {
  const request = fetch.mock.calls.find(([path]) => path.startsWith('/api/catalog?'));
  return Object.fromEntries(new URLSearchParams(request[0].split('?')[1]));
};
describe('New Antioch catalogue', () => {
  it('shows unit costs, both armour options, and status as a plain list', async () => {
    const { container } = await renderCatalogue();
    const units = screen.getByRole('table', { name: /unit recruitment costs/ });
    expect(within(units).getByRole('row', { name: /Lieutenant/ }).textContent).toContain('70');
    expect(within(units).getByRole('row', { name: /Yeoman/ }).textContent).toContain('30');
    expect(within(units).getByRole('row', { name: /Mechanized Heavy Infantry/ }).textContent).toContain('85 / 95');
    expect(container.querySelector('#project-status ul').textContent).toContain('Roster creation, settings, saving and deletion: available.');
    expect(container.querySelector('img, svg, canvas')).toBeNull();
    expect(container.textContent).not.toMatch(/checkpoint|sprint|student|educational|sample|CS408/i);
  });
  it('searches on the server and clears filters', async () => {
    await renderCatalogue(catalog, { ...catalog, units: [], equipment: [catalog.equipment[1]] });
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'PISTOL' } });
    await screen.findByText('No units match your search.');
    expect(params()).toEqual({ faction: 'new-antioch', q: 'PISTOL' });
    expect(equipmentTable().getByRole('row', { name: /Pistol/ })).toBeTruthy();
    expect(equipmentTable().queryByRole('row', { name: /Combat Helmet/ })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(screen.getByRole('searchbox').value).toBe('');
    expect(equipmentTable().getByRole('row', { name: /Combat Helmet/ })).toBeTruthy();
    expect(unitTable().getByRole('row', { name: /Lieutenant/ })).toBeTruthy();
  });
  it('combines equipment filters without hiding units', async () => {
    await renderCatalogue(catalog, { ...catalog, equipment: [catalog.equipment[2]] });
    fireEvent.change(screen.getByRole('combobox', { name: 'Equipment category' }), { target: { value: 'Equipment' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Max equipment cost' }), { target: { value: '5' } });
    await screen.findByRole('row', { name: /Combat Helmet/ });
    expect(params()).toEqual({ faction: 'new-antioch', equipmentType: 'Equipment', equipmentMaxCost: '5' });
    expect(equipmentTable().queryByRole('row', { name: /Pistol/ })).toBeNull();
    expect(unitTable().getByRole('row', { name: /Lieutenant/ })).toBeTruthy();
  });
  it('combines unit type, cost and equipment eligibility and keeps the options after filtering', async () => {
    await renderCatalogue(catalog, { ...catalog, units: [catalog.units[2]] });
    fireEvent.change(screen.getByRole('combobox', { name: 'Unit type' }), { target: { value: 'Heavy infantry' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Max unit cost' }), { target: { value: '85' } });
    fireEvent.change(screen.getByRole('combobox', { name: 'Unit can equip' }), { target: { value: '6' } });
    await screen.findByRole('row', { name: /Mechanized Heavy Infantry/ });
    expect(params()).toEqual({ faction: 'new-antioch', unitType: 'Heavy infantry', unitMaxCost: '85', equipmentId: '6' });
    expect(unitTable().queryByRole('row', { name: /Yeoman/ })).toBeNull();
    expect(screen.getByRole('option', { name: 'Infantry', exact: true })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(screen.getByRole('spinbutton', { name: 'Max unit cost' }).value).toBe('');
    expect(screen.getByRole('combobox', { name: 'Unit can equip' }).value).toBe('');
    expect(unitTable().getByRole('row', { name: /Yeoman/ })).toBeTruthy();
  });
  it('requests rows in alphabetical order when selected', async () => {
    await renderCatalogue(catalog, { ...catalog, equipment: [catalog.equipment[0], catalog.equipment[2], catalog.equipment[1]] });
    fireEvent.click(screen.getByRole('checkbox', { name: 'Sort alphabetically' }));
    await screen.findByRole('row', { name: /Pistol/ });
    expect(params()).toEqual({ faction: 'new-antioch', alphabetical: 'true' });
    const rows = equipmentTable().getAllByRole('row');
    expect(rows[1].textContent).toContain('Bolt-Action Rifle');
    expect(rows[2].textContent).toContain('Combat Helmet');
    expect(rows[3].textContent).toContain('Pistol');
  });
  it('reports connection failure and supports retry', async () => {
    vi.stubGlobal('fetch', vi.fn(async path => ({ ok: false, status: path === '/api/auth/me' ? 401 : 503 })));
    render(<App />);
    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.queryByText('Catalogue available; database connected.')).toBeNull();
    vi.stubGlobal('fetch', healthyFetch());
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Lieutenant')).toBeTruthy();
  });
  it('handles an empty catalogue without inventing data', async () => {
    await renderCatalogue({ ...catalog, units: [], equipment: [] });
    expect(screen.getByText('No units match your search.')).toBeTruthy();
    expect(screen.getByText('No equipment matches your filters.')).toBeTruthy();
  });
  it('loads the signed-in account dashboard and clears private rosters after logout', async () => {
    const user = { id: 1, username: 'tester', email: 'tester@example.invalid' };
    let signedIn = true;
    const roster = { id: 12, rosterName: 'Private company', factionId: 2, factionName: 'New Antioch',
      ducatLimit: 700, unitCount: 0, createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-10-01T10:00:00Z' };
    vi.stubGlobal('fetch', vi.fn(async path => {
      let status = 200;
      let body;
      if (path === '/api/auth/me') { status = signedIn ? 200 : 401; body = signedIn ? user : {}; }
      else if (path === '/api/auth/csrf') body = { token: 'test', headerName: 'X-CSRF-TOKEN' };
      else if (path === '/api/auth/logout') { signedIn = false; return new Response(null, { status: 204 }); }
      else if (path === '/api/rosters') body = [roster];
      else if (path === '/api/rosters/factions') body = catalog.factions;
      else if (path === '/api/health') body = { status: 'UP', database: 'UP' };
      else body = catalog;
      return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
    }));
    render(<App />);
    await screen.findByRole('button', { name: /Private company New Antioch/ });
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    await screen.findByText('Signed out.');
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /Private company New Antioch/ })).toBeNull();
      expect(screen.queryByRole('button', { name: 'New roster' })).toBeNull();
    });
    expect(screen.getByText('Lieutenant')).toBeTruthy();
  });

});
