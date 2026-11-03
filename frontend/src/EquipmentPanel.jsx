import React, { useEffect, useRef, useState } from 'react';
import { getEquipment, setEquipment } from './equipmentApi.js';

const label = type => ({ 'Ranged weapon': 'Ranged weapons', 'Melee weapon': 'Melee weapons', Shield: 'Shields' }[type] || type);

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
  const groups = [...new Set(options.filter(item => item.type !== 'Armour').map(item => item.type))];
  const worn = armour.find(item => item.quantity > 0);
  return <section className="equipment-panel" aria-label="Unit equipment">
    <div className="equipment-heading"><h5>Equipment</h5>
      <button type="button" disabled={busy} onClick={load}>Refresh equipment</button></div>
    {busy && <p role="status" className="unit-feedback">Updating equipment…</p>}
    {error && <p role="alert" className="account-error">{error}</p>}
    {view && <div className="equipment-options">
      <label className="armour-choice">Armour
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
      {groups.map(type => {
        const items = options.filter(item => item.type === type);
        return items.length > 0 && <div className="equipment-group" key={type}>
          <h6>{label(type)}</h6>
          <ul>{items.map(item => <li key={item.id}>
            <div className="equipment-name"><strong>{item.name}</strong><span>{item.purchaseCost} ducats each</span>
              {!item.compatible && item.quantity === 0 && <small id={`equipment-reason-${unitId}-${item.id}`}>{item.reason}</small>}
            </div>
            <div className="equipment-quantity">
              <button type="button" disabled={busy || !item.canDecrease} title={item.decreaseReason || 'Remove one'}
                aria-label={`Remove one ${item.name}`} onClick={() => change(item.id, item.quantity - 1)}>−</button>
              <output aria-label={`${item.name} quantity`}>{item.quantity}</output>
              <button type="button" disabled={busy || !item.compatible} title={item.reason || 'Add one'}
                aria-describedby={!item.compatible && !item.quantity ? `equipment-reason-${unitId}-${item.id}` : undefined}
                aria-label={`Add one ${item.name}`} onClick={() => change(item.id, item.quantity + 1)}>+</button>
            </div>
          </li>)}</ul>
        </div>;
      })}
    </div>}
  </section>;
}
