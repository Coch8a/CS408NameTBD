import React from 'react';
import EquipmentPanel from './EquipmentPanel.jsx';
import ProfileDetails, { ProfileStats } from './ProfileDetails.jsx';

export const recruitmentPrice = unit => unit.alternateCost > 0
  ? `${unit.cost} / ${unit.alternateCost}` : String(unit.cost);

export default function RosterUnitDetails({ unit, rosterId, onUpdated, onBusyChange, onSessionExpired, onTrenchMole, busy }) {
  return <section className="unit-detail-panel" aria-labelledby="unit-detail-heading">
    <h5 id="unit-detail-heading">Unit details</h5>
    {unit ? <>
      <h6>{unit.trenchMole ? 'Trench Mole' : unit.name}</h6>
      {unit.name === 'Yeoman' && <label className="unit-feedback">
        <input type="checkbox" checked={Boolean(unit.trenchMole)} disabled={busy}
          onChange={event => onTrenchMole(event.target.checked)} /> Trench Mole (+10 ducats)
      </label>}
      {unit.trenchMole && <p className="unit-feedback">INFILTRATOR</p>}
      <dl className="unit-metadata">
        <div><dt>Role</dt><dd>{unit.type}</dd></div>
        <div><dt>Recruitment</dt><dd>{unit.costs?.recruitmentCost ?? recruitmentPrice(unit)} ducats</dd></div>
        {unit.costs && <>
          <div><dt>Equipment</dt><dd>{unit.costs.equipmentCost} ducats{unit.costs.glorySpent > 0 && ` · ${unit.costs.glorySpent} Glory`}</dd></div>
          <div><dt>Total</dt><dd>{unit.costs.totalCost} ducats{unit.costs.glorySpent > 0 && ` · ${unit.costs.glorySpent} Glory`}</dd></div>
        </>}
      </dl>
      <ProfileStats profile={unit.profile} />
      {unit.profile && <ProfileDetails kind="units" id={unit.unitId} name={unit.name} showStats={false} />}
      {unit.costs && <EquipmentPanel rosterId={rosterId} unitId={unit.id} onUpdated={onUpdated}
        onBusyChange={onBusyChange} onSessionExpired={onSessionExpired} />}
    </> : <p className="unit-empty">Select a unit to view its details.</p>}
  </section>;
}
