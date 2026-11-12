import React, { useEffect, useRef, useState } from 'react';
import { getEquipment, setEquipment } from './equipmentApi.js';
import ProfileDetails from './ProfileDetails.jsx';

const label = type => ({
  'Ranged weapon': 'Ranged weapons', 'Melee weapon': 'Melee weapons', Shield: 'Shields', Equipment: 'Gear',
}[type] || type);

function EquipmentPicker({ type, items, busy, change }) {
  const [choice, setChoice] = useState('');
  const item = items.find(option => String(option.id) === choice);
  return <div className="equipment-picker">
    <div className="equipment-controls">
      <label className="equipment-choice">{label(type)}
        <select value={item ? choice : ''} disabled={busy} onChange={event => setChoice(event.target.value)}>
          <option value="">Choose an item</option>
          {items.map(option => <option key={option.id} value={option.id}>
            {option.name} · {option.purchaseCost} ducats
          </option>)}
        </select>
      </label>
      <button type="button" disabled={busy || !item?.compatible}
        aria-label={item ? `Add one ${item.name}` : `Add ${label(type).toLowerCase()}`}
        onClick={() => change(item.id, item.quantity + 1)}>Add</button>
    </div>
    {item && <>
      {!item.compatible && <p className="equipment-reason">{item.reason}</p>}
      <ProfileDetails key={item.id} kind="equipment" id={item.id} name={`Preview ${item.name}`} />
    </>}
  </div>;
}

function EquippedLoadout({ equipment, options, busy, change }) {
  const byId = new Map(options.map(item => [item.id, item]));
  return <section className="equipped-loadout" aria-label="Equipped loadout">
    <h5>Equipped loadout</h5>
    {equipment.length === 0 ? <p className="unit-empty">No equipment equipped.</p> : <ul>
      {equipment.map(item => {
        const option = byId.get(item.id);
        return <li key={item.id}>
          <div className="equipped-item">
            <strong>{item.name}</strong> <output aria-label={`${item.name} quantity`}>×{item.quantity}</output>
            <ProfileDetails kind="equipment" id={item.id} name={item.name} />
            {item.type !== 'Armour' && option?.decreaseReason && !option.canDecrease &&
              <p className="equipment-reason">{option.decreaseReason}</p>}
          </div>
          {item.type !== 'Armour' && <button type="button" disabled={busy || !option?.canDecrease}
            aria-label={`Remove one ${item.name}`} onClick={() => change(item.id, item.quantity - 1)}>Remove</button>}
        </li>;
      })}
    </ul>}
  </section>;
}

export default function EquipmentPanel({ rosterId, unitId, onUpdated, onBusyChange, onSessionExpired }) {
  const [view, setView] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const requestRef = useRef(null);
  const busyRef = useRef(false);
  const callbacks = useRef({});
  callbacks.current = { onUpdated, onBusyChange, onSessionExpired };

  async function perform(action) {
    if (busyRef.current) return;
    const controller = new AbortController();
    requestRef.current = controller;
    busyRef.current = true;
    setBusy(true);
    setError('');
    callbacks.current.onBusyChange(true);
    try {
      const result = await action(controller.signal);
      if (controller.signal.aborted) return;
      setView(result);
      callbacks.current.onUpdated(result);
    } catch (reason) {
      if (controller.signal.aborted) return;
      if (reason.status === 401) callbacks.current.onSessionExpired();
      else setError(reason.message);
    } finally {
      if (!controller.signal.aborted && requestRef.current === controller) {
        busyRef.current = false;
        setBusy(false);
        callbacks.current.onBusyChange(false);
      }
    }
  }

  const load = () => perform(signal => getEquipment(rosterId, unitId, signal));
  const change = (id, quantity) => perform(signal => setEquipment(rosterId, unitId, id, quantity, signal));
  useEffect(() => {
    setView(null);
    load();
    return () => {
      requestRef.current?.abort();
      busyRef.current = false;
      callbacks.current.onBusyChange(false);
    };
  }, [rosterId, unitId]);

  const options = view?.availableEquipment ?? [];
  const armour = options.filter(item => item.type === 'Armour');
  const groups = new Map();
  for (const item of options) {
    if (item.type === 'Armour') continue;
    if (!groups.has(item.type)) groups.set(item.type, []);
    groups.get(item.type).push(item);
  }
  const worn = armour.find(item => item.quantity > 0);
  return <section className="equipment-panel" aria-label="Unit equipment">
    {view && <EquippedLoadout equipment={view.equipment} options={options} busy={busy} change={change} />}
    <div className="equipment-heading"><h5>Equipment</h5>
      <button type="button" disabled={busy} onClick={load}>Refresh equipment</button></div>
    {busy && <p role="status" className="unit-feedback">Updating equipment…</p>}
    {error && <p role="alert" className="account-error">{error}</p>}
    {view && <>
      {armour.length > 0 && <>
        <label className="equipment-choice">Armour
          <select value={worn?.id ?? ''} disabled={busy} onChange={event => {
            if (event.target.value) change(Number(event.target.value), 1);
            else if (worn) change(worn.id, 0);
          }}>
            <option value="" disabled={view.requiresArmour}>None</option>
            {armour.map(item => <option key={item.id} value={item.id} disabled={!item.compatible && !item.quantity}>
              {item.name} · {item.purchaseCost} ducats{!item.compatible && !item.quantity ? ` — ${item.reason}` : ''}
            </option>)}
          </select>
        </label>
        <details className="profile-details armour-rules">
          <summary>Armour rules</summary>
          {armour.map(item => <ProfileDetails key={item.id} kind="equipment" id={item.id}
            name={`Preview ${item.name}`} title={item.name} />)}
        </details>
      </>}
      {[...groups].map(([type, items]) => <EquipmentPicker key={type} type={type} items={items} busy={busy} change={change} />)}
    </>}
  </section>;
}
