import React from 'react';
import { beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import EquipmentPanel from './EquipmentPanel.jsx';
import RosterUnits from './RosterUnits.jsx';
import * as api from './equipmentApi.js';
import * as units from './rosterApi.js';
vi.mock('./equipmentApi.js', () => ({ getEquipment: vi.fn(), setEquipment: vi.fn() }));
vi.mock('./rosterApi.js', () => ({ getRosterUnits: vi.fn(), addRosterUnit: vi.fn(), removeRosterUnit: vi.fn() }));
const item = (id, name, type, price, quantity=0, extra={}) => ({id,name,type,purchaseCost:price,quantity,maxQuantity:1,compatible:!quantity,reason:quantity?'Maximum 1.':'',canDecrease:!!quantity,decreaseReason:'',...extra});
const state = (quantity=0) => ({roster:{id:1,totalCost:30+5*quantity,ducatLimit:700,remainingDucats:670-5*quantity,overBudget:0},rosterUnitId:10,
 unitCost:{rosterUnitId:10,recruitmentCost:30,equipmentCost:5*quantity,totalCost:30+5*quantity},requiresArmour:false,equipment:[],availableEquipment:[
 item(1,'Bolt-Action Rifle','Ranged weapon',5,quantity),item(2,'Bayonet','Melee weapon',2,0,{compatible:!!quantity,reason:quantity?'':'Requires a bayonet lug.'}),
 item(3,'Standard Armour','Armour',15),item(4,'Machine Armour','Armour',50,0,{compatible:false,reason:'Unavailable for this unit.'})]});
let updated,busy,expired;
beforeEach(()=>{vi.resetAllMocks();updated=vi.fn();busy=vi.fn();expired=vi.fn();api.getEquipment.mockResolvedValue(state());api.setEquipment.mockResolvedValue(state(1));});
const mount=()=>render(<EquipmentPanel rosterId={1} unitId={10} onUpdated={updated} onBusyChange={busy} onSessionExpired={expired}/>);
it('loads grouped options and disables incompatible gear',async()=>{
 mount();await screen.findByText('Bolt-Action Rifle');
 expect(screen.getByRole('button',{name:'Add one Bayonet'}).disabled).toBe(true);
 expect(screen.getByText('Requires a bayonet lug.')).toBeTruthy();
 expect(screen.getByRole('option',{name:/Machine Armour/}).disabled).toBe(true);
 expect(updated).toHaveBeenCalledWith(state());
});
it('saves quantities without changing the display before the response',async()=>{
 let finish;api.setEquipment.mockReturnValue(new Promise(resolve=>finish=resolve));mount();await screen.findByText('Bolt-Action Rifle');
 fireEvent.click(screen.getByRole('button',{name:'Add one Bolt-Action Rifle'}));
 expect(screen.getByLabelText('Bolt-Action Rifle quantity').textContent).toBe('0');
 expect(screen.getByRole('button',{name:'Add one Bolt-Action Rifle'}).disabled).toBe(true);
 finish(state(1));await waitFor(()=>expect(screen.getByLabelText('Bolt-Action Rifle quantity').textContent).toBe('1'));
 expect(api.setEquipment).toHaveBeenCalledWith(1,10,1,1,expect.any(AbortSignal));
 expect(updated).toHaveBeenLastCalledWith(state(1));
 expect(screen.getByRole('button',{name:'Add one Bayonet'}).disabled).toBe(false);
});
it('keeps quantities and totals after a failed save and allows retry',async()=>{
 api.setEquipment.mockRejectedValueOnce(new Error('Save failed.')).mockResolvedValue(state(1));mount();await screen.findByText('Bolt-Action Rifle');
 fireEvent.click(screen.getByRole('button',{name:'Add one Bolt-Action Rifle'}));await screen.findByRole('alert');
 expect(screen.getByLabelText('Bolt-Action Rifle quantity').textContent).toBe('0');expect(updated).toHaveBeenCalledTimes(1);
 fireEvent.click(screen.getByRole('button',{name:'Add one Bolt-Action Rifle'}));await waitFor(()=>expect(updated).toHaveBeenCalledTimes(2));
 expect(screen.queryByRole('alert')).toBeNull();
});
it('uses one armour selection and preserves required armour',async()=>{
 const s={...state(),requiresArmour:true,availableEquipment:[item(7,'Reinforced Armour','Armour',0,1),item(8,'Machine Armour','Armour',10)]};
 api.getEquipment.mockResolvedValue(s);api.setEquipment.mockResolvedValue({...s,availableEquipment:[item(7,'Reinforced Armour','Armour',0),item(8,'Machine Armour','Armour',10,1)]});
 mount();await screen.findByRole('combobox',{name:'Armour'});expect(screen.getByRole('option',{name:'None'}).disabled).toBe(true);
 fireEvent.change(screen.getByRole('combobox',{name:'Armour'}),{target:{value:'8'}});
 await waitFor(()=>expect(screen.getByRole('combobox',{name:'Armour'}).value).toBe('8'));
 expect(api.setEquipment).toHaveBeenCalledWith(1,10,8,1,expect.any(AbortSignal));
});
it('expires the account on 401 and supports retrying failed loads',async()=>{
 api.getEquipment.mockRejectedValueOnce(new Error('Load failed.')).mockRejectedValueOnce(Object.assign(new Error('Expired.'),{status:401}));
 mount();await screen.findByRole('alert');fireEvent.click(screen.getByRole('button',{name:'Refresh equipment'}));
 await waitFor(()=>expect(expired).toHaveBeenCalledTimes(1));
});
it('ignores a response after switching units',async()=>{
 let finish;api.getEquipment.mockReturnValueOnce(new Promise(resolve=>finish=resolve)).mockResolvedValue({...state(),rosterUnitId:11});
 const view=mount();const oldSignal=api.getEquipment.mock.calls[0][2];
 view.rerender(<EquipmentPanel rosterId={1} unitId={11} onUpdated={updated} onBusyChange={busy} onSessionExpired={expired}/>);
 await screen.findByText('Bolt-Action Rifle');finish({...state(),rosterUnitId:10});await Promise.resolve();
 expect(oldSignal.aborted).toBe(true);expect(updated).toHaveBeenCalledTimes(1);expect(updated.mock.calls[0][0].rosterUnitId).toBe(11);
});
it('updates the selected unit and sidebar from server costs while blocking navigation during writes',async()=>{
 const unit={id:10,unitId:3,name:'Yeoman',type:'Infantry',cost:30,costs:state().unitCost};
 units.getRosterUnits.mockResolvedValue({roster:state().roster,units:[unit],availableUnits:[]});
 let finish;api.setEquipment.mockReturnValue(new Promise(resolve=>finish=resolve));
 render(<RosterUnits rosterId={1} onRosterUpdated={updated} onBusyChange={busy} onSessionExpired={expired}/>);
 await screen.findByText('Bolt-Action Rifle');
 fireEvent.click(screen.getByRole('button',{name:'Add one Bolt-Action Rifle'}));
 expect(screen.getByRole('button',{name:'Select Yeoman 1'}).disabled).toBe(true);
 expect(screen.getByRole('button',{name:'Remove Yeoman 1'}).disabled).toBe(true);
 finish(state(1));await waitFor(()=>expect(screen.getByRole('button',{name:'Select Yeoman 1'}).textContent).toContain('35 ducats'));
 expect(within(screen.getByRole('region',{name:'Unit details'})).getByText('5 ducats')).toBeTruthy();
 expect(updated).toHaveBeenLastCalledWith(state(1).roster);
});
