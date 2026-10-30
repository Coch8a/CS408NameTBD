import React, { useEffect, useRef, useState } from 'react';
import RosterUnits from './RosterUnits.jsx';
import { createRoster, deleteRoster, getRoster, listRosterFactions, listRosters, updateRoster } from './rosterApi.js';

const emptyFields = { rosterName: '', factionId: '', ducatLimit: '700' };
const ordered = rows => [...rows].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt) || b.id - a.id);
const dateLabel = value => new Date(value).toLocaleString();

/** A private saved-list workspace. Mount with the account ID as its key to isolate sessions. */
export default function RosterDashboard({ onSessionExpired }) {
  const [rosters, setRosters] = useState([]);
  const [factions, setFactions] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [selected, setSelected] = useState(null);
  const [mode, setMode] = useState('view');
  const [fields, setFields] = useState(emptyFields);
  const [fieldErrors, setFieldErrors] = useState({});
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [unitsBusy, setUnitsBusy] = useState(false);
  const working = busy || unitsBusy;
  const busyRef = useRef(false);
  const requestRef = useRef(null);
  const formRef = useRef(null);
  const headingRef = useRef(null);

  async function perform(action, missingId = selected?.id) {
    if (busyRef.current || unitsBusy) return;
    const controller = new AbortController();
    requestRef.current = controller;
    busyRef.current = true;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action(controller.signal);
    } catch (reason) {
      if (controller.signal.aborted) return;
      if (reason.status === 401) { onSessionExpired(); return; }
      if (reason.status === 404) {
        if (missingId) setRosters(current => current.filter(row => row.id !== missingId));
        setSelected(null);
        setMode('view');
      }
      setError(reason.message);
      showErrors(reason.fields || {});
    } finally {
      if (requestRef.current === controller && !controller.signal.aborted) {
        busyRef.current = false;
        setBusy(false);
      }
    }
  }

  function load() {
    perform(async signal => {
      const [rows, available] = await Promise.all([listRosters(signal), listRosterFactions(signal)]);
      if (signal.aborted) return;
      setRosters(ordered(rows));
      setFactions(available);
      setSelected(null);
      setMode('view');
      setLoaded(true);
    });
  }

  useEffect(() => {
    load();
    return () => {
      requestRef.current?.abort();
      busyRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (mode === 'create' || mode === 'edit') formRef.current?.elements.namedItem('rosterName')?.focus();
    if (mode === 'view' && selected) headingRef.current?.focus();
  }, [mode, selected?.id]);

  function showErrors(errors) {
    setFieldErrors(errors);
    const first = Object.keys(errors)[0];
    if (first) formRef.current?.elements.namedItem(first)?.focus();
  }

  function begin(next) {
    if (busyRef.current || unitsBusy) return;
    setMode(next);
    setError('');
    setNotice('');
    setFieldErrors({});
    setFields(next === 'create'
      ? { ...emptyFields, factionId: String(factions[0]?.id ?? '') }
      : { rosterName: selected.rosterName, factionId: String(selected.factionId), ducatLimit: String(selected.ducatLimit) });
  }

  function open(id) {
    perform(async signal => {
      const roster = await getRoster(id, signal);
      if (signal.aborted) return;
      setSelected(roster);
      setMode('view');
      setFieldErrors({});
      setRosters(current => ordered(current.map(row => row.id === roster.id ? roster : row)));
    }, id);
  }

  function submit(event) {
    event.preventDefault();
    if (busyRef.current || unitsBusy) return;
    const errors = {};
    const name = fields.rosterName.trim();
    if (!name) errors.rosterName = 'Enter a roster name.';
    else if (name.length > 120) errors.rosterName = 'Use at most 120 characters.';
    if (!factions.some(faction => String(faction.id) === fields.factionId)) errors.factionId = 'Choose an available faction.';
    if (!/^\d+$/.test(fields.ducatLimit) || Number(fields.ducatLimit) < 1 || Number(fields.ducatLimit) > 1000000) {
      errors.ducatLimit = 'Enter a whole number from 1 to 1,000,000.';
    }
    showErrors(errors);
    if (Object.keys(errors).length) return;
    perform(async signal => {
      const payload = { rosterName: name, factionId: Number(fields.factionId), ducatLimit: Number(fields.ducatLimit) };
      const roster = mode === 'create' ? await createRoster(payload, signal) : await updateRoster(selected.id, payload, signal);
      if (signal.aborted) return;
      setRosters(current => ordered([roster, ...current.filter(row => row.id !== roster.id)]));
      setSelected(roster);
      setMode('view');
      setQuery('');
      setNotice(mode === 'create' ? 'Roster created.' : 'Roster settings saved.');
    });
  }

  function remove() {
    perform(async signal => {
      await deleteRoster(selected.id, signal);
      if (signal.aborted) return;
      setRosters(current => current.filter(row => row.id !== selected.id));
      setSelected(null);
      setMode('view');
      setNotice('Roster deleted.');
    });
  }

  const editing = mode === 'create' || mode === 'edit';
  const visible = rosters.filter(row => `${row.rosterName} ${row.factionName}`.toLowerCase().includes(query.trim().toLowerCase()));
  const change = event => setFields(current => ({ ...current, [event.target.name]: event.target.value }));
  const fieldError = name => fieldErrors[name] && <span className="field-error" id={`roster-error-${name}`}>{fieldErrors[name]}</span>;
  const accessibility = name => ({ 'aria-invalid': Boolean(fieldErrors[name]),
    'aria-describedby': fieldErrors[name] ? `roster-error-${name}` : undefined });

  return <section id="rosters" className="catalog-panel roster-dashboard" aria-labelledby="rosters-heading">
    <div className="panel-heading"><h2 id="rosters-heading">My rosters</h2></div>
    <div className="roster-toolbar">
      <label>Search saved rosters<input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Roster name or faction" /></label>
      <button type="button" disabled={working || !loaded || !factions.length || mode !== 'view'} onClick={() => begin('create')}>New roster</button>
      <button type="button" disabled={working || mode !== 'view'} onClick={load}>Refresh rosters</button>
    </div>
    {busy && <p className="roster-feedback" role="status">{editing ? 'Saving roster…' : mode === 'delete' ? 'Deleting roster…' : 'Loading rosters…'}</p>}
    {notice && <p className="roster-feedback account-notice" role="status">{notice}</p>}
    {error && <p className="roster-feedback account-error" role="alert">{error}</p>}
    <div className="roster-workspace">
      <aside className="roster-list" aria-label="Saved rosters">
        {loaded && !rosters.length && <p className="empty-message">No saved rosters.</p>}
        {loaded && rosters.length > 0 && !visible.length && <p className="empty-message">No rosters match your search.</p>}
        <ul>{visible.map(roster => <li key={roster.id}>
          <button type="button" className="roster-row" aria-pressed={selected?.id === roster.id}
            disabled={working || mode !== 'view'} onClick={() => open(roster.id)}>
            <strong>{roster.rosterName}</strong><span>{roster.factionName}</span>
            <span>{roster.ducatLimit.toLocaleString()} ducat limit · {roster.unitCount} {roster.unitCount === 1 ? 'unit' : 'units'}</span>
          </button>
        </li>)}</ul>
      </aside>
      <div className="roster-detail">
        {editing ? <form ref={formRef} aria-label={mode === 'create' ? 'Create roster' : 'Edit roster settings'} onSubmit={submit} noValidate>
          <h3>{mode === 'create' ? 'New roster' : 'Roster settings'}</h3>
          <fieldset disabled={working}>
            <legend className="visually-hidden">Roster settings</legend>
            <div className="account-field"><label htmlFor="roster-name">Roster name</label>
              <input id="roster-name" name="rosterName" value={fields.rosterName} onChange={change} maxLength={120} required {...accessibility('rosterName')} />{fieldError('rosterName')}</div>
            <div className="roster-settings-grid">
              <div className="account-field"><label htmlFor="roster-faction">Faction</label>
                <select id="roster-faction" name="factionId" value={fields.factionId} onChange={change} required {...accessibility('factionId')}>
                  <option value="">Choose a faction</option>{factions.map(faction => <option key={faction.id} value={faction.id}>{faction.name}</option>)}
                </select>{fieldError('factionId')}</div>
              <div className="account-field"><label htmlFor="roster-limit">Ducat limit</label>
                <input id="roster-limit" name="ducatLimit" type="number" min="1" max="1000000" step="1" value={fields.ducatLimit} onChange={change} required {...accessibility('ducatLimit')} />{fieldError('ducatLimit')}</div>
            </div>
            <div className="roster-actions"><button type="submit">{mode === 'create' ? 'Create roster' : 'Save changes'}</button>
              <button type="button" onClick={() => { setMode('view'); setError(''); setFieldErrors({}); }}>Cancel</button></div>
          </fieldset>
        </form> : selected ? <>
          <h3 ref={headingRef} tabIndex={-1}>{selected.rosterName}</h3>
          <dl className="roster-metadata">
            <div><dt>Faction</dt><dd>{selected.factionName}</dd></div>
            <div><dt>Ducat limit</dt><dd>{selected.ducatLimit.toLocaleString()}</dd></div>
            <div><dt>Units</dt><dd>{selected.unitCount}</dd></div>
            <div><dt>Last saved</dt><dd><time dateTime={selected.updatedAt}>{dateLabel(selected.updatedAt)}</time></dd></div>
          </dl>
          {mode === 'delete' ? <div className="delete-confirmation" role="group" aria-label="Confirm roster deletion">
            <p>Delete “{selected.rosterName}”? This permanently removes the roster and its saved units and equipment.</p>
            <div className="roster-actions"><button type="button" className="danger-button" disabled={working} onClick={remove}>Confirm delete</button>
              <button type="button" disabled={working} autoFocus onClick={() => { setMode('view'); setError(''); }}>Keep roster</button></div>
          </div> : <div className="roster-actions"><button type="button" disabled={working} onClick={() => begin('edit')}>Edit settings</button>
            <button type="button" className="danger-button" disabled={working} onClick={() => { setMode('delete'); setError(''); setNotice(''); }}>Delete roster</button></div>}
          {mode === 'view' && <RosterUnits key={`${selected.id}:${selected.factionId}`} rosterId={selected.id}
            onBusyChange={setUnitsBusy} onSessionExpired={onSessionExpired}
            onRosterUpdated={roster => {
              setSelected(current => current?.id === roster.id ? roster : current);
              setRosters(current => ordered(current.map(row => row.id === roster.id ? roster : row)));
            }} />}
        </> : <p className="empty-message">Select or create a roster.</p>}
      </div>
    </div>
  </section>;
}
