import React, { useEffect, useRef, useState } from 'react';
import { addRosterUnit, getRosterUnits, removeRosterUnit } from './rosterApi.js';

const price = unit => unit.alternateCost > 0 ? `${unit.cost} / ${unit.alternateCost}` : String(unit.cost);

export default function RosterUnits({ rosterId, onRosterUpdated, onBusyChange, onSessionExpired }) {
  const [view, setView] = useState(null);
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const requestRef = useRef(null);
  const busyRef = useRef(false);
  const callbacks = useRef({});
  callbacks.current = { onRosterUpdated, onBusyChange, onSessionExpired };

  async function perform(action, message = '') {
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

  return <section className="roster-units" aria-labelledby="roster-units-heading">
    <div className="unit-section-heading"><h4 id="roster-units-heading">Units</h4>
      <button type="button" disabled={busy} onClick={load}>Refresh units</button></div>
    {busy && <p className="unit-feedback" role="status">Updating units…</p>}
    {notice && <p className="unit-feedback account-notice" role="status">{notice}</p>}
    {error && <p className="unit-feedback account-error" role="alert">{error}</p>}
    {view && <>
      <div className="unit-columns">
        <section aria-labelledby="saved-units-heading">
          <h5 id="saved-units-heading">Roster units ({view.units.length})</h5>
          {!view.units.length && <p className="unit-empty">No units added.</p>}
          <ul className="unit-list">{view.units.map((unit, index) => <li key={unit.id}>
            <div><strong>{index + 1}. {unit.name}</strong><span>{unit.type}</span></div>
            <button type="button" disabled={busy} aria-label={`Remove ${unit.name} ${index + 1}`}
              onClick={() => perform(signal => removeRosterUnit(rosterId, unit.id, signal), `${unit.name} removed.`)}>Remove</button>
          </li>)}</ul>
        </section>
        <section aria-labelledby="available-units-heading">
          <h5 id="available-units-heading">Available units</h5>
          <label className="unit-search">Search available units<input type="search" value={query}
            placeholder="Unit name or role" onChange={event => setQuery(event.target.value)} /></label>
          {!available.length && <p className="unit-empty">{view.availableUnits.length ? 'No units match your search.' : 'No units available for this faction.'}</p>}
          <ul className="unit-list">{available.map(unit => <li key={unit.id}>
            <div><strong>{unit.name}</strong><span>{unit.type} · {price(unit)} ducats</span>
              {unit.notes && <small>{unit.notes}</small>}</div>
            <button type="button" disabled={busy} aria-label={`Add ${unit.name}`}
              onClick={() => perform(signal => addRosterUnit(rosterId, unit.id, signal), `${unit.name} added.`)}>Add unit</button>
          </li>)}</ul>
        </section>
      </div>
      <p className="roster-note">Changes save automatically. Prices are catalogue costs.</p>
    </>}
  </section>;
}
