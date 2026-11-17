import React, { useEffect, useState } from 'react';
import { rosterSnapshot } from './rosterTransfer.js';
import { sortRosterUnits } from './rosterOrder.js';

export default function PrintableRoster({ id }) {
  const [roster, setRoster] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!/^[1-9][0-9]*$/.test(id)) {
      setError('Choose a valid roster.');
      return;
    }
    const controller = new AbortController();
    rosterSnapshot(id, controller.signal).then(data => {
      if (!controller.signal.aborted) setRoster(data);
    }).catch(problem => {
      if (!controller.signal.aborted) setError(problem.message);
    });
    return () => controller.abort();
  }, [id]);

  const units = roster ? sortRosterUnits(roster.file.units.map((unit, index) => ({
    unit, index, detail: roster.unitDetails[index],
  })), row => row.detail.type) : [];

  return <main className="print-roster">
    <div className="print-controls">
      <a href="/">Back to roster builder</a>
      <button type="button" disabled={!roster} onClick={() => window.print()}>Print / Save as PDF</button>
    </div>
    {error && <p role="alert">{error}</p>}
    {!error && !roster && <p role="status">Loading roster…</p>}
    {roster && <>
      <h1>{roster.file.name}</h1>
      <p>{roster.factionName} · {roster.totalCost} / {roster.file.ducatLimit} ducats · {roster.glorySpent ?? 0} / {roster.file.gloryLimit ?? 0} Glory</p>
      {roster.overBudget > 0 && <p>{roster.overBudget} ducats over budget.</p>}
      {roster.overGloryBudget > 0 && <p>{roster.overGloryBudget} Glory over budget.</p>}
      {roster.unitIssues.map(issue => <p key={issue}>{issue}</p>)}
      {units.length === 0 && <p>No units added.</p>}
      {units.map(({ unit, index, detail }) => <section key={index} className="print-unit">
        <h2>{unit.trenchMole ? "Trench Mole (Yeoman)" : unit.name} · {detail.totalCost} ducats{detail.glorySpent > 0 && ` · ${detail.glorySpent} Glory`}</h2>
        {unit.equipment.length > 0 && <ul>{unit.equipment.map(item =>
          <li key={item.name}>{item.quantity} × {item.name}</li>)}</ul>}
      </section>)}
    </>}
  </main>;
}
