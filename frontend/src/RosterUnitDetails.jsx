import React from 'react';
import EquipmentPanel from './EquipmentPanel.jsx';

export const recruitmentPrice = unit => unit.alternateCost > 0
  ? `${unit.cost} / ${unit.alternateCost}` : String(unit.cost);

export default function RosterUnitDetails({ unit, rosterId, onUpdated, onBusyChange, onSessionExpired }) {
  return <section className="unit-detail-panel" aria-labelledby="unit-detail-heading">
    <h5 id="unit-detail-heading">Unit details</h5>
    {unit ? <>
      <h6>{unit.name}</h6>
      <dl className="unit-metadata">
        <div><dt>Role</dt><dd>{unit.type}</dd></div>
        <div><dt>Recruitment</dt><dd>{unit.costs?.recruitmentCost ?? recruitmentPrice(unit)} ducats</dd></div>
        {unit.costs && <>
          <div><dt>Equipment</dt><dd>{unit.costs.equipmentCost} ducats</dd></div>
          <div><dt>Total</dt><dd>{unit.costs.totalCost} ducats</dd></div>
        </>}
      </dl>
      {unit.costs && <EquipmentPanel rosterId={rosterId} unitId={unit.id} onUpdated={onUpdated}
        onBusyChange={onBusyChange} onSessionExpired={onSessionExpired} />}
    </> : <p className="unit-empty">Select a unit to view its details.</p>}
  </section>;
}
