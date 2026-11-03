import React from 'react';

export const recruitmentPrice = unit => unit.alternateCost > 0
  ? `${unit.cost} / ${unit.alternateCost}` : String(unit.cost);

export default function RosterUnitDetails({ unit }) {
  return <section className="unit-detail-panel" aria-labelledby="unit-detail-heading">
    <h5 id="unit-detail-heading">Unit details</h5>
    {unit ? <>
      <h6>{unit.name}</h6>
      <dl className="unit-metadata">
        <div><dt>Role</dt><dd>{unit.type}</dd></div>
        <div><dt>Recruitment cost</dt><dd>{recruitmentPrice(unit)} ducats</dd></div>
      </dl>
    </> : <p className="unit-empty">Select a unit to view its details.</p>}
  </section>;
}
