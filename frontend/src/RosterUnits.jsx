import React, { useEffect, useRef, useState } from 'react';
import { addRosterUnit, getRosterUnits, removeRosterUnit } from './rosterApi.js';
import RosterUnitDetails, { recruitmentPrice } from './RosterUnitDetails.jsx';

export default function RosterUnits({ rosterId, onRosterUpdated, onBusyChange, onSessionExpired }) {
  const [view, setView] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const requestRef = useRef(null);
  const busyRef = useRef(false);
  const callbacks = useRef({});
  callbacks.current = { onRosterUpdated, onBusyChange, onSessionExpired };

  async function perform(action, message = '', selectAdded = false) {
    if (busyRef.current) return;
    const controller = new AbortController();
    requestRef.current = controller;
    busyRef.current = true;
    setBusy(true);
    callbacks.current.onBusyChange(true);
    setError('');
    setNotice('');
    try {
      const result = await action(controller.signal);
      if (controller.signal.aborted) return;
      setView(result);
      setSelectedId(current => {
        if (selectAdded) {
          const previousIds = new Set((view?.units ?? []).map(unit => unit.id));
          const added = result.units.find(unit => !previousIds.has(unit.id));
          if (added) return added.id;
        }
        if (result.units.some(unit => unit.id === current)) return current;
        const previousIndex = view?.units.findIndex(unit => unit.id === current) ?? 0;
        return result.units[Math.max(0, Math.min(previousIndex, result.units.length - 1))]?.id ?? null;
      });
      callbacks.current.onRosterUpdated(result.roster);
      setNotice(message);
    } catch (reason) {
      if (controller.signal.aborted) return;
      if (reason.status === 401) { callbacks.current.onSessionExpired(); return; }
      setError(reason.message);
    } finally {
      if (!controller.signal.aborted && requestRef.current === controller) {
        busyRef.current = false;
        setBusy(false);
        callbacks.current.onBusyChange(false);
      }
    }
  }

  function load() { perform(signal => getRosterUnits(rosterId, signal)); }

  useEffect(() => {
    setView(null);
    setSelectedId(null);
    setQuery('');
    load();
    return () => {
      requestRef.current?.abort();
      busyRef.current = false;
      callbacks.current.onBusyChange(false);
    };
  }, [rosterId]);

  const needle = query.trim().toLowerCase();
  const available = (view?.availableUnits ?? []).filter(unit => `${unit.name} ${unit.type}`.toLowerCase().includes(needle));
  const selectedIndex = view?.units.findIndex(unit => unit.id === selectedId) ?? -1;
  const selectedUnit = view?.units[selectedIndex];

  return <section className="roster-units" aria-labelledby="roster-units-heading">
    <div className="unit-section-heading"><h4 id="roster-units-heading">Units</h4>
      <button type="button" disabled={busy} onClick={load}>Refresh units</button></div>
    {busy && <p className="unit-feedback" role="status">Updating units…</p>}
    {notice && <p className="unit-feedback account-notice" role="status">{notice}</p>}
    {error && <p className="unit-feedback account-error" role="alert">{error}</p>}
    {view && <>
      <div className="unit-columns">
        <section className="saved-unit-panel" aria-labelledby="saved-units-heading">
          <h5 id="saved-units-heading">Roster units ({view.units.length})</h5>
          {!view.units.length && <p className="unit-empty">No units added.</p>}
          <ul className="unit-list saved-unit-list">{view.units.map((unit, index) => <li key={unit.id}>
            <button type="button" className="unit-select" aria-pressed={unit.id === selectedId}
              aria-label={`Select ${unit.name} ${index + 1}`} disabled={busy} onClick={() => setSelectedId(unit.id)}>
              <strong>{unit.name}</strong><span>{unit.type}</span>
            </button>
            <button type="button" disabled={busy} aria-label={`Remove ${unit.name} ${index + 1}`}
              onClick={() => perform(signal => removeRosterUnit(rosterId, unit.id, signal), `${unit.name} removed.`)}>Remove</button>
          </li>)}</ul>
        </section>
        <RosterUnitDetails unit={selectedUnit} />
      </div>
        <section className="unit-recruitment" aria-labelledby="available-units-heading">
          <h5 id="available-units-heading">Available units</h5>
          <label className="unit-search">Search available units<input type="search" value={query}
            placeholder="Unit name or role" onChange={event => setQuery(event.target.value)} /></label>
          {!available.length && <p className="unit-empty">{view.availableUnits.length ? 'No units match your search.' : 'No units available for this faction.'}</p>}
          <ul className="unit-list recruitment-list">{available.map(unit => <li key={unit.id}>
            <div><strong>{unit.name}</strong><span>{unit.type} · {recruitmentPrice(unit)} ducats</span></div>
            <button type="button" disabled={busy} aria-label={`Add ${unit.name}`}
              onClick={() => perform(signal => addRosterUnit(rosterId, unit.id, signal), `${unit.name} added.`, true)}>Add unit</button>
          </li>)}</ul>
        </section>
    </>}
  </section>;
}
