import { request } from './rosterApi.js';

const path = (rosterId, unitId) => `/api/rosters/${rosterId}/units/${unitId}/equipment`;
export const getEquipment = (rosterId, unitId, signal) => request(path(rosterId, unitId), 'GET', undefined, signal);
export const setEquipment = (rosterId, unitId, equipmentId, quantity, signal) =>
  request(`${path(rosterId, unitId)}/${equipmentId}`, 'PUT', { quantity }, signal);
