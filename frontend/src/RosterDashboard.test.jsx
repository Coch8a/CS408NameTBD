import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import RosterDashboard from './RosterDashboard.jsx';
import * as api from './rosterApi.js';

vi.mock('./rosterApi.js', () => ({ createRoster: vi.fn(), deleteRoster: vi.fn(), getRoster: vi.fn(),
  listRosterFactions: vi.fn(), listRosters: vi.fn(), updateRoster: vi.fn() }));
const roster = { id: 12, rosterName: 'First Company', factionId: 2, factionName: 'New Antioch',
  ducatLimit: 700, unitCount: 0, createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-10-01T10:00:00Z' };
beforeEach(() => {
  vi.resetAllMocks();
  api.listRosters.mockResolvedValue([roster]);
  api.listRosterFactions.mockResolvedValue([{ id: 2, name: 'New Antioch' }]);
  api.getRoster.mockResolvedValue(roster);
});
async function ready(onSessionExpired = vi.fn()) {
  const view = render(<RosterDashboard onSessionExpired={onSessionExpired} />);
  await waitFor(() => expect(screen.getByRole('button', { name: 'New roster' }).disabled).toBe(false));
  return view;
}
const change = (label, value) => fireEvent.change(screen.getByLabelText(label), { target: { value } });
async function open() {
  fireEvent.click(screen.getByRole('button', { name: /First Company New Antioch/ }));
  await screen.findByRole('heading', { name: 'First Company' });
}

describe('Saved roster dashboard', () => {
  it('shows empty state, creates a roster, and opens its saved settings', async () => {
    api.listRosters.mockResolvedValue([]);
    api.createRoster.mockResolvedValue(roster);
    await ready();
    expect(screen.getByText(/No saved rosters/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'New roster' }));
    expect(document.activeElement).toBe(screen.getByLabelText('Roster name'));
    change('Roster name', '  First Company  ');
    fireEvent.submit(screen.getByRole('form', { name: 'Create roster' }));
    await screen.findByRole('heading', { name: 'First Company' });
    expect(api.createRoster).toHaveBeenCalledWith({ rosterName: 'First Company', factionId: 2, ducatLimit: 700 }, expect.any(AbortSignal));
    expect(screen.getByText('Roster created.')).toBeTruthy();
    expect(screen.queryByRole('form')).toBeNull();
  });
  it('validates settings before sending requests', async () => {
    await ready();
    fireEvent.click(screen.getByRole('button', { name: 'New roster' }));
    change('Roster name', '   ');
    change('Ducat limit', '0');
    fireEvent.submit(screen.getByRole('form'));
    expect(screen.getByText('Enter a roster name.')).toBeTruthy();
    expect(screen.getByText('Enter a whole number from 1 to 1,000,000.')).toBeTruthy();
    change('Ducat limit', '700.5');
    fireEvent.submit(screen.getByRole('form'));
    expect(api.createRoster).not.toHaveBeenCalled();
  });
  it('opens a saved roster and persists edited settings', async () => {
    api.updateRoster.mockResolvedValue({ ...roster, rosterName: 'Second Company', ducatLimit: 900 });
    await ready(); await open();
    fireEvent.click(screen.getByRole('button', { name: 'Edit settings' }));
    expect(screen.getByLabelText('Roster name').value).toBe('First Company');
    change('Roster name', 'Second Company'); change('Ducat limit', '900');
    fireEvent.submit(screen.getByRole('form', { name: 'Edit roster settings' }));
    await screen.findByRole('heading', { name: 'Second Company' });
    expect(api.updateRoster).toHaveBeenCalledWith(12, { rosterName: 'Second Company', factionId: 2, ducatLimit: 900 }, expect.any(AbortSignal));
    expect(screen.getByText('Roster settings saved.')).toBeTruthy();
  });
  it('cancels edits without changing the saved roster', async () => {
    await ready(); await open();
    fireEvent.click(screen.getByRole('button', { name: 'Edit settings' }));
    change('Roster name', 'Discard this');
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByRole('heading', { name: 'First Company' })).toBeTruthy();
    expect(api.updateRoster).not.toHaveBeenCalled();
  });
  it('requires confirmation, permits cancellation, and removes only after successful deletion', async () => {
    api.deleteRoster.mockResolvedValue(null);
    await ready(); await open();
    fireEvent.click(screen.getByRole('button', { name: 'Delete roster' }));
    expect(api.deleteRoster).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Keep roster' }));
    fireEvent.click(screen.getByRole('button', { name: 'Keep roster' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete roster' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }));
    await screen.findByText('Roster deleted.');
    expect(api.deleteRoster).toHaveBeenCalledWith(12, expect.any(AbortSignal));
    expect(screen.queryByRole('heading', { name: 'First Company' })).toBeNull();
    expect(screen.getByText(/No saved rosters/)).toBeTruthy();
  });
  it('keeps the roster when deletion fails and supports retry', async () => {
    api.deleteRoster.mockRejectedValueOnce(new Error('Network unavailable.')).mockResolvedValue(null);
    await ready(); await open();
    fireEvent.click(screen.getByRole('button', { name: 'Delete roster' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }));
    await screen.findByRole('alert');
    expect(screen.getByRole('heading', { name: 'First Company' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }));
    await screen.findByText('Roster deleted.');
  });
  it('blocks duplicate submissions and retains fields after a rejected save', async () => {
    let reject;
    api.createRoster.mockReturnValue(new Promise((_, no) => { reject = no; }));
    await ready();
    fireEvent.click(screen.getByRole('button', { name: 'New roster' }));
    change('Roster name', 'Test');
    const form = screen.getByRole('form');
    fireEvent.submit(form); fireEvent.submit(form);
    expect(api.createRoster).toHaveBeenCalledTimes(1);
    expect(form.querySelector('fieldset').disabled).toBe(true);
    reject(Object.assign(new Error('Choose an available faction.'), { fields: { factionId: 'Unavailable faction.' } }));
    await screen.findByRole('alert');
    expect(screen.getByLabelText('Roster name').value).toBe('Test');
    expect(document.activeElement).toBe(screen.getByLabelText('Faction'));
  });
  it('filters saved lists and refreshes after a load failure', async () => {
    api.listRosters.mockRejectedValueOnce(new Error('Unable to load rosters.')).mockResolvedValue([roster]);
    render(<RosterDashboard onSessionExpired={vi.fn()} />);
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: 'Refresh rosters' }));
    await screen.findByRole('button', { name: /First Company New Antioch/ });
    change('Search saved rosters', 'missing');
    expect(screen.getByText('No rosters match your search.')).toBeTruthy();
    change('Search saved rosters', 'Antioch');
    expect(screen.getByRole('button', { name: /First Company New Antioch/ })).toBeTruthy();
  });
  it('hands session expiry back to the account interface', async () => {
    const expired = vi.fn();
    api.getRoster.mockRejectedValue(Object.assign(new Error('Sign in again.'), { status: 401 }));
    await ready(expired);
    fireEvent.click(screen.getByRole('button', { name: /First Company New Antioch/ }));
    await waitFor(() => expect(expired).toHaveBeenCalledTimes(1));
  });
  it('aborts an in-flight request when the account workspace unmounts', async () => {
    api.listRosters.mockReturnValue(new Promise(() => {}));
    const view = render(<RosterDashboard onSessionExpired={vi.fn()} />);
    const signal = api.listRosters.mock.calls[0][0];
    view.unmount();
    expect(signal.aborted).toBe(true);
  });
  it('removes a stale selected entry after the server reports it no longer exists', async () => {
    api.getRoster.mockRejectedValue(Object.assign(new Error('Roster not found.'), { status: 404 }));
    await ready();
    fireEvent.click(screen.getByRole('button', { name: /First Company New Antioch/ }));
    await screen.findByRole('alert');
    expect(screen.queryByRole('button', { name: /First Company New Antioch/ })).toBeNull();
    expect(screen.getByText(/No saved rosters/)).toBeTruthy();
  });

});
