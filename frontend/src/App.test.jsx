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
function healthyFetch(data = catalog) {
  return vi.fn(async path => path === '/api/auth/me' ? { ok: false, status: 401 } : ({ ok: true, json: async () => path === '/api/health'
    ? { status: 'UP', database: 'UP' } : data }));
}
afterEach(() => vi.unstubAllGlobals());
async function renderCatalogue(data = catalog) {
  vi.stubGlobal('fetch', healthyFetch(data));
  const result = render(<App />);
  await screen.findByText('Catalogue available; database connected.');
  return result;
}
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
  it('searches units and equipment and clears filters', async () => {
    await renderCatalogue();
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'pistol' } });
    expect(screen.getByText('Pistol')).toBeTruthy();
    expect(screen.queryByText('Combat Helmet')).toBeNull();
    expect(screen.getByText('No units match your search.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(screen.getByText('Combat Helmet')).toBeTruthy();
    expect(screen.getByText('Lieutenant')).toBeTruthy();
  });
  it('filters equipment by category without hiding units', async () => {
    await renderCatalogue();
    fireEvent.change(screen.getByRole('combobox', { name: 'Equipment category' }), { target: { value: 'Equipment' } });
    expect(screen.getByText('Combat Helmet')).toBeTruthy();
    expect(screen.queryByText('Pistol')).toBeNull();
    expect(screen.getByText('Lieutenant')).toBeTruthy();
  });
  it('sorts rows alphabetically when selected', async () => {
    await renderCatalogue();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Sort alphabetically' }));
    const rows = within(screen.getByRole('table', { name: /armoury prices/ })).getAllByRole('row');
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
