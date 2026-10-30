import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import RosterUnits from './RosterUnits.jsx';
import RosterDashboard from './RosterDashboard.jsx';
import * as api from './rosterApi.js';

vi.mock('./rosterApi.js', () => ({ getRosterUnits: vi.fn(), addRosterUnit: vi.fn(), removeRosterUnit: vi.fn(),
  createRoster: vi.fn(), deleteRoster: vi.fn(), getRoster: vi.fn(), listRosterFactions: vi.fn(), listRosters: vi.fn(), updateRoster: vi.fn() }));
const roster = { id: 12, rosterName: 'Company', factionId: 2, factionName: 'New Antioch', ducatLimit: 700,
  unitCount: 0, createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-10-01T10:00:00Z' };
const yeoman = { id: 5, name: 'Yeoman', type: 'Infantry', cost: 30, alternateCost: 0, notes: 'Rifle costs extra.' };
const heavy = { id: 6, name: 'Mechanized Heavy Infantry', type: 'Heavy infantry', cost: 85, alternateCost: 95,
  notes: '85 with Reinforced Armour; 95 with Machine Armour.' };
const first = { ...yeoman, id: 40, unitId: 5 };
const second = { ...yeoman, id: 41, unitId: 5 };
const result = (units = []) => ({ roster: { ...roster, unitCount: units.length }, units, availableUnits: [yeoman, heavy] });
let updated, expired, busy;
beforeEach(() => {
  vi.resetAllMocks();
  updated = vi.fn(); expired = vi.fn(); busy = vi.fn();
  api.getRosterUnits.mockResolvedValue(result());
  api.listRosters.mockResolvedValue([roster]);
  api.listRosterFactions.mockResolvedValue([{ id: 2, name: 'New Antioch' }]);
  api.getRoster.mockResolvedValue(roster);
});
async function ready() {
  const view = render(<RosterUnits rosterId={12} onRosterUpdated={updated} onBusyChange={busy} onSessionExpired={expired} />);
  await screen.findByRole('button', { name: 'Add Yeoman' });
  await waitFor(() => expect(screen.getByRole('button', { name: 'Add Yeoman' }).disabled).toBe(false));
  return view;
}

describe('Roster unit selection', () => {
  it('loads saved units and searches only the available catalogue', async () => {
    api.getRosterUnits.mockResolvedValue(result([first]));
    await ready();
    expect(screen.getByText('1. Yeoman')).toBeTruthy();
    expect(screen.getByText('Heavy infantry · 85 / 95 ducats')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Search available units'), { target: { value: 'heavy' } });
    expect(screen.queryByRole('button', { name: 'Add Yeoman' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Add Mechanized Heavy Infantry' })).toBeTruthy();
    expect(screen.getByText('1. Yeoman')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Search available units'), { target: { value: 'missing' } });
    expect(screen.getByText('No units match your search.')).toBeTruthy();
  });

  it('adds the same type twice and removes only the requested individual', async () => {
    api.addRosterUnit.mockResolvedValueOnce(result([first])).mockResolvedValueOnce(result([first, second]));
    api.removeRosterUnit.mockResolvedValue(result([second]));
    await ready();
    expect(screen.getByText('No units added.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Add Yeoman' }));
    await screen.findByRole('heading', { name: 'Roster units (1)' });
    fireEvent.click(screen.getByRole('button', { name: 'Add Yeoman' }));
    await screen.findByRole('heading', { name: 'Roster units (2)' });
    expect(api.addRosterUnit).toHaveBeenCalledTimes(2);
    expect(api.addRosterUnit).toHaveBeenLastCalledWith(12, 5, expect.any(AbortSignal));
    fireEvent.click(screen.getByRole('button', { name: 'Remove Yeoman 1' }));
    await screen.findByRole('heading', { name: 'Roster units (1)' });
    expect(api.removeRosterUnit).toHaveBeenCalledWith(12, 40, expect.any(AbortSignal));
    expect(screen.queryByRole('button', { name: 'Remove Yeoman 2' })).toBeNull();
    expect(updated).toHaveBeenLastCalledWith(expect.objectContaining({ id: 12, unitCount: 1 }));
  });

  it('prevents repeat add clicks while saving', async () => {
    let finish;
    api.addRosterUnit.mockReturnValue(new Promise(resolve => { finish = resolve; }));
    await ready();
    const button = screen.getByRole('button', { name: 'Add Yeoman' });
    fireEvent.click(button); fireEvent.click(button);
    expect(api.addRosterUnit).toHaveBeenCalledTimes(1);
    expect(button.disabled).toBe(true);
    expect(screen.getByRole('button', { name: 'Refresh units' }).disabled).toBe(true);
    expect(busy).toHaveBeenLastCalledWith(true);
    finish(result([first]));
    await screen.findByRole('heading', { name: 'Roster units (1)' });
    expect(busy).toHaveBeenLastCalledWith(false);
  });

  it('retains saved data after a failed removal and lets the user retry', async () => {
    api.getRosterUnits.mockResolvedValue(result([first]));
    api.removeRosterUnit.mockRejectedValueOnce(new Error('Unable to reach the roster service.')).mockResolvedValue(result());
    await ready();
    fireEvent.click(screen.getByRole('button', { name: 'Remove Yeoman 1' }));
    await screen.findByRole('alert');
    expect(screen.getByText('1. Yeoman')).toBeTruthy();
    expect(screen.queryByText('Yeoman removed.')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Remove Yeoman 1' }));
    await screen.findByText('Yeoman removed.');
    expect(screen.queryByText('1. Yeoman')).toBeNull();
  });

  it('reports failed additions without inventing a saved unit', async () => {
    api.addRosterUnit.mockRejectedValue(Object.assign(new Error('Choose a unit from this roster\'s faction.'), { status: 400 }));
    await ready();
    fireEvent.click(screen.getByRole('button', { name: 'Add Yeoman' }));
    await screen.findByRole('alert');
    expect(screen.getByRole('heading', { name: 'Roster units (0)' })).toBeTruthy();
    expect(screen.queryByText('Yeoman added.')).toBeNull();
  });

  it('supports refreshing after a load failure or stale unit response', async () => {
    api.getRosterUnits.mockRejectedValueOnce(new Error('Unable to load units.')).mockResolvedValue(result([first]));
    render(<RosterUnits rosterId={12} onRosterUpdated={updated} onBusyChange={busy} onSessionExpired={expired} />);
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: 'Refresh units' }));
    await screen.findByText('1. Yeoman');
    api.removeRosterUnit.mockRejectedValue(Object.assign(new Error('Roster unit not found. Refresh the unit list.'), { status: 404 }));
    fireEvent.click(screen.getByRole('button', { name: 'Remove Yeoman 1' }));
    await screen.findByRole('alert');
    api.getRosterUnits.mockResolvedValue(result());
    fireEvent.click(screen.getByRole('button', { name: 'Refresh units' }));
    await screen.findByRole('heading', { name: 'Roster units (0)' });
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('handles session expiry and aborts work when the roster closes', async () => {
    api.addRosterUnit.mockRejectedValue(Object.assign(new Error('Sign in again.'), { status: 401 }));
    const view = await ready();
    fireEvent.click(screen.getByRole('button', { name: 'Add Yeoman' }));
    await waitFor(() => expect(expired).toHaveBeenCalledTimes(1));
    api.getRosterUnits.mockReturnValue(new Promise(() => {}));
    fireEvent.click(screen.getByRole('button', { name: 'Refresh units' }));
    const signal = api.getRosterUnits.mock.calls.at(-1)[1];
    view.unmount();
    expect(signal.aborted).toBe(true);
    expect(busy).toHaveBeenLastCalledWith(false);
  });

  it('updates the dashboard count after saving and restores units when reopened', async () => {
    api.addRosterUnit.mockResolvedValue(result([first]));
    render(<RosterDashboard onSessionExpired={expired} />);
    fireEvent.click(await screen.findByRole('button', { name: /Company New Antioch/ }));
    await screen.findByRole('button', { name: 'Add Yeoman' });
    fireEvent.click(screen.getByRole('button', { name: 'Add Yeoman' }));
    await waitFor(() => expect(screen.getByRole('button', { name: /Company New Antioch/ }).textContent).toContain('1 unit'));
    expect(screen.getByText('1. Yeoman')).toBeTruthy();
    api.listRosters.mockResolvedValue([{ ...roster, unitCount: 1 }]);
    api.getRoster.mockResolvedValue({ ...roster, unitCount: 1 });
    api.getRosterUnits.mockResolvedValue(result([first]));
    fireEvent.click(screen.getByRole('button', { name: 'Refresh rosters' }));
    await waitFor(() => expect(screen.queryByText('1. Yeoman')).toBeNull());
    fireEvent.click(screen.getByRole('button', { name: /Company New Antioch/ }));
    await screen.findByText('1. Yeoman');
    expect(api.getRosterUnits).toHaveBeenCalledTimes(2);
  });

  it('disables roster navigation until the selected unit mutation finishes', async () => {
    let finish;
    api.addRosterUnit.mockReturnValue(new Promise(resolve => { finish = resolve; }));
    render(<RosterDashboard onSessionExpired={expired} />);
    fireEvent.click(await screen.findByRole('button', { name: /Company New Antioch/ }));
    await screen.findByRole('button', { name: 'Add Yeoman' });
    fireEvent.click(screen.getByRole('button', { name: 'Add Yeoman' }));
    expect(screen.getByRole('button', { name: 'Edit settings' }).disabled).toBe(true);
    expect(screen.getByRole('button', { name: 'Delete roster' }).disabled).toBe(true);
    expect(screen.getByRole('button', { name: 'Refresh rosters' }).disabled).toBe(true);
    finish(result([first]));
    await screen.findByText('1. Yeoman');
    expect(screen.getByRole('button', { name: 'Edit settings' }).disabled).toBe(false);
  });
});
