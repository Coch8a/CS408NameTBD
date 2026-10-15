import React, { useEffect, useState } from 'react';
import { loadProject } from './api.js';

/** Compact, read-only catalogue. Search and category filters run locally. */
export default function App() {
  const [project, setProject] = useState(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('All categories');
  const [alphabetical, setAlphabetical] = useState(false);

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

  const catalog = project?.catalog;
  const needle = query.trim().toLowerCase();
  const matches = item => `${item.name} ${item.type} ${item.notes}`.toLowerCase().includes(needle);
  const sorted = items => alphabetical ? [...items].sort((a, b) => a.name.localeCompare(b.name)) : items;
  const units = sorted((catalog?.units ?? []).filter(matches));
  const equipment = sorted((catalog?.equipment ?? []).filter(item => matches(item) &&
    (category === 'All categories' || item.type === category)));
  const categories = [...new Set((catalog?.equipment ?? []).map(item => item.type))];

  return <>
    <header className="app-header">
      <span className="app-title">Trench Crusade Roster Builder</span>
      <nav aria-label="Main navigation"><a href="#units">Units</a><a href="#equipment">Weapons &amp; equipment</a><a href="#project-status">Project status</a></nav>
    </header>
    <div className="workspace">
      <aside className="sidebar" aria-label="Faction">
        <h2>Factions</h2>
        <a className="selected-faction" href="#catalog" aria-current="page">New Antioch</a>
        <p className="sidebar-note">More factions planned.</p>
        <div className="rules-reference">
          <h2>Rules reference</h2>
          <p>Warbands of Trench Crusade</p>
          <p>Version {catalog?.rulesVersion ?? '1.0.2'}</p>
          <a href={catalog?.sourceUrl ?? 'https://www.trenchcrusade.com/rules/'} target="_blank" rel="noreferrer">View published rules</a>
        </div>
      </aside>
      <main id="catalog">
        <div className="catalog-heading"><h1>New Antioch</h1><span>Catalogue</span></div>
        <p className="catalog-intro">Recruitment costs and selected armoury items. All prices are in ducats.</p>
        <div className="toolbar" role="search" aria-label="Catalogue filters">
          <label className="search-field">Search catalogue<input type="search" value={query} placeholder="Search units, weapons or equipment" onChange={event => setQuery(event.target.value)}/></label>
          <label>Equipment category<select value={category} onChange={event => setCategory(event.target.value)}><option>All categories</option>{categories.map(type => <option key={type}>{type}</option>)}</select></label>
          <label className="sort-control"><input type="checkbox" checked={alphabetical} onChange={event => setAlphabetical(event.target.checked)}/>Sort alphabetically</label>
          <button type="button" onClick={() => { setQuery(''); setCategory('All categories'); setAlphabetical(false); }}>Clear filters</button>
        </div>
        {!project && !error && <p role="status" className="message">Loading catalogue…</p>}
        {error && <div role="alert" className="error-message"><span>{error}</span><button type="button" onClick={() => setAttempt(value => value + 1)}>Try again</button></div>}
        {project && <>
          <section className="catalog-panel" id="units" aria-labelledby="units-heading">
            <div className="panel-heading"><h2 id="units-heading">Units</h2><span>{units.length} of {catalog.units.length}</span></div>
            <table><caption className="visually-hidden">New Antioch unit recruitment costs</caption>
              <thead><tr><th scope="col">Unit</th><th scope="col">Role</th><th scope="col">Limit</th><th scope="col" className="numeric">Ducats</th><th scope="col">Loadout / cost notes</th></tr></thead>
              <tbody>{units.map(unit => <tr key={unit.id}><th scope="row">{unit.name}</th><td>{unit.type}</td><td>{unit.limit || 'Unlimited'}</td><td className="numeric cost">{unit.cost}{unit.alternateCost > 0 ? ` / ${unit.alternateCost}` : ''}</td><td className="notes">{unit.notes}</td></tr>)}</tbody>
            </table>
            {units.length === 0 && <p className="empty-message">No units match your search.</p>}
          </section>
          <section className="catalog-panel" id="equipment" aria-labelledby="equipment-heading">
            <div className="panel-heading"><h2 id="equipment-heading">Weapons &amp; equipment</h2><span>{equipment.length} of {catalog.equipment.length}</span></div>
            <table><caption className="visually-hidden">Selected New Antioch armoury prices</caption>
              <thead><tr><th scope="col">Item</th><th scope="col">Category</th><th scope="col" className="numeric">Ducats</th><th scope="col">Restrictions / cost notes</th></tr></thead>
              <tbody>{equipment.map(item => <tr key={item.id}><th scope="row">{item.name}</th><td>{item.type}</td><td className="numeric cost">{item.cost}</td><td className="notes">{item.notes || '—'}</td></tr>)}</tbody>
            </table>
            {equipment.length === 0 && <p className="empty-message">No equipment matches your filters.</p>}
          </section>
          <p className="catalog-footnote">Selected entries, not the full faction roster. Unit costs exclude additional gear unless stated. See the published rules for complete restrictions.</p>
        </>}
        <section className="project-status" id="project-status" aria-labelledby="status-heading">
          <h2 id="status-heading">Project status</h2>
          <ul>
            <li aria-live="polite">{project ? 'Catalogue available; database connected.' : error ? 'Catalogue unavailable; connection check failed.' : 'Checking catalogue and database connection…'}</li>
            <li>Registration and login: planned.</li>
            <li>Roster creation, editing and saving: planned.</li>
          </ul>
        </section>
      </main>
    </div>
  </>;
}
