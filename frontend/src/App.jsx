import React, { useEffect, useState } from 'react';
import { loadCatalogue, loadProject } from './api.js';
import AccountPanel from './AccountPanel.jsx';
import RosterDashboard from './RosterDashboard.jsx';
import ProfileDetails from './ProfileDetails.jsx';

const emptyFilters = {
  q: '', unitType: '', unitMaxCost: '', equipmentId: '', equipmentType: '', equipmentMaxCost: '', alphabetical: false,
};
const validCost = value => value === '' || (/^\d+$/.test(value) && Number(value) <= 2147483647);

/** Account workspace and public catalogue. */
export default function App() {
  const [user, setUser] = useState(null);
  const [sessionRevision, setSessionRevision] = useState(0);
  const [project, setProject] = useState(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [filters, setFilters] = useState(emptyFilters);
  const [filtered, setFiltered] = useState(null);
  const [filterRetry, setFilterRetry] = useState(0);
  const entries = Object.entries({ ...filters, q: filters.q.trim() })
    .filter(([, value]) => value !== '' && value !== false);
  const search = entries.length ? new URLSearchParams([['faction', 'new-antioch'], ...entries]).toString() : '';
  const invalidCost = !validCost(filters.unitMaxCost) || !validCost(filters.equipmentMaxCost);

  useEffect(() => {
    const controller = new AbortController();
    setError('');
    setProject(null);
    loadProject(controller.signal).then(data => {
      if (!controller.signal.aborted) setProject(data);
    }).catch(reason => {
      if (!controller.signal.aborted) setError(reason.message);
    });
    return () => controller.abort();
  }, [attempt]);

  useEffect(() => {
    setFiltered(null);
    if (!project || !search || invalidCost) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      loadCatalogue(search, controller.signal).then(catalog => {
        if (!controller.signal.aborted) setFiltered({ search, catalog });
      }).catch(reason => {
        if (!controller.signal.aborted) setFiltered({ search, error: reason.message });
      });
    }, 200);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [project, search, invalidCost, filterRetry]);

  const catalog = project?.catalog;
  const result = filtered?.search === search ? filtered : null;
  const matches = invalidCost ? null : search ? result?.catalog : catalog;
  const units = matches?.units ?? [];
  const equipment = matches?.equipment ?? [];
  const unitTypes = [...new Set((catalog?.units ?? []).map(unit => unit.type))];
  const categories = [...new Set((catalog?.equipment ?? []).map(item => item.type))];
  const change = (key, value) => setFilters(current => ({ ...current, [key]: value }));

  return <>
    <header className="app-header">
      <span className="app-title">Trench Crusade Roster Builder</span>
      <nav aria-label="Main navigation"><a href="#account">Account</a><a href="#rosters">My rosters</a><a href="#units">Units</a><a href="#equipment">Weapons &amp; equipment</a><a href="#project-status">Project status</a></nav>
    </header>
    <div className="workspace">
      <aside className="sidebar" aria-label="Faction">
        <h2>Factions</h2>
        <a className="selected-faction" href="#catalog" aria-current="page">New Antioch</a>
        <div className="rules-reference">
          <h2>Rules reference</h2>
          <p>Warbands of Trench Crusade</p>
          <p>Version {catalog?.rulesVersion ?? '1.0.2'}</p>
          <a href={catalog?.sourceUrl ?? 'https://www.trenchcrusade.com/rules/'} target="_blank" rel="noreferrer">View published rules</a>
        </div>
      </aside>
      <main id="catalog">
        <AccountPanel onUserChange={setUser} sessionRevision={sessionRevision} />
        {user ? <RosterDashboard key={user.id} onSessionExpired={() => {
          setUser(null);
          setSessionRevision(value => value + 1);
        }} /> : <section id="rosters" className="catalog-panel" aria-labelledby="signed-out-rosters-heading">
          <div className="panel-heading"><h2 id="signed-out-rosters-heading">My rosters</h2></div>
          <p className="empty-message"><a href="#account">Sign in</a> to create and manage your saved rosters.</p>
        </section>}
        <div className="catalog-heading"><h1>New Antioch</h1><span>Catalogue</span></div>
        <div className="toolbar" role="search" aria-label="Catalogue filters">
          <label className="search-field">Search catalogue<input type="search" value={filters.q} placeholder="Search units, weapons or equipment" onChange={event => change('q', event.target.value)}/></label>
          <label>Unit type<select value={filters.unitType} onChange={event => change('unitType', event.target.value)}><option value="">All unit types</option>{unitTypes.map(type => <option key={type}>{type}</option>)}</select></label>
          <label>Max unit cost<input type="number" min="0" step="1" value={filters.unitMaxCost} onChange={event => change('unitMaxCost', event.target.value)}/></label>
          <label>Unit can equip<select value={filters.equipmentId} onChange={event => change('equipmentId', event.target.value)}><option value="">Any item</option>{(catalog?.equipment ?? []).map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label>Equipment category<select value={filters.equipmentType} onChange={event => change('equipmentType', event.target.value)}><option value="">All categories</option>{categories.map(type => <option key={type}>{type}</option>)}</select></label>
          <label>Max equipment cost<input type="number" min="0" step="1" value={filters.equipmentMaxCost} onChange={event => change('equipmentMaxCost', event.target.value)}/></label>
          <label className="sort-control"><input type="checkbox" checked={filters.alphabetical} onChange={event => change('alphabetical', event.target.checked)}/>Sort alphabetically</label>
          <button type="button" onClick={() => setFilters(emptyFilters)}>Clear filters</button>
        </div>
        {!project && !error && <p role="status" className="message">Loading catalogue…</p>}
        {error && <div role="alert" className="error-message"><span>{error}</span><button type="button" onClick={() => setAttempt(value => value + 1)}>Try again</button></div>}
        {invalidCost && <p role="alert" className="message">Use whole ducat amounts from 0 to 2,147,483,647.</p>}
        {project && search && !invalidCost && !result && <p role="status" className="message">Filtering catalogue…</p>}
        {result?.error && !invalidCost && <div role="alert" className="error-message"><span>{result.error}</span><button type="button" onClick={() => setFilterRetry(value => value + 1)}>Try again</button></div>}
        {matches && <>
          <section className="catalog-panel" id="units" aria-labelledby="units-heading">
            <div className="panel-heading"><h2 id="units-heading">Units</h2><span>{units.length} of {catalog.units.length}</span></div>
            <table><caption className="visually-hidden">New Antioch unit recruitment costs</caption>
              <thead><tr><th scope="col">Unit</th><th scope="col">Role</th><th scope="col">Limit</th><th scope="col" className="numeric">Ducats</th><th scope="col">Profile / rules</th></tr></thead>
              <tbody>{units.map(unit => <tr key={unit.id}><th scope="row">{unit.name}</th><td>{unit.type}</td><td>{unit.limit || 'Unlimited'}</td><td className="numeric cost">{unit.cost}{unit.alternateCost > 0 ? ` / ${unit.alternateCost}` : ''}</td><td className="catalog-profile"><ProfileDetails kind="units" id={unit.id} name={unit.name} /></td></tr>)}</tbody>
            </table>
            {units.length === 0 && <p className="empty-message">No units match your search.</p>}
          </section>
          <section className="catalog-panel" id="equipment" aria-labelledby="equipment-heading">
            <div className="panel-heading"><h2 id="equipment-heading">Weapons &amp; equipment</h2><span>{equipment.length} of {catalog.equipment.length}</span></div>
            <table><caption className="visually-hidden">Selected New Antioch armoury prices</caption>
              <thead><tr><th scope="col">Item</th><th scope="col">Category</th><th scope="col" className="numeric">Ducats</th><th scope="col">Profile / rules</th></tr></thead>
              <tbody>{equipment.map(item => <tr key={item.id}><th scope="row">{item.name}</th><td>{item.type}</td><td className="numeric cost">{item.cost}</td><td className="catalog-profile"><ProfileDetails kind="equipment" id={item.id} name={item.name} /></td></tr>)}</tbody>
            </table>
            {equipment.length === 0 && <p className="empty-message">No equipment matches your filters.</p>}
          </section>
        </>}
        <section className="project-status" id="project-status" aria-labelledby="status-heading">
          <h2 id="status-heading">Project status</h2>
          <ul>
            <li aria-live="polite">{project ? 'Catalogue available; database connected.' : error ? 'Catalogue unavailable; connection check failed.' : 'Checking catalogue and database connection…'}</li>
            <li>Registration, login and logout: available.</li>
            <li>Roster creation, settings, saving and deletion: available.</li>
            <li>Roster units, equipment and cost totals: available.</li>
          </ul>
        </section>
      </main>
    </div>
  </>;
}
