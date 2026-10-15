/** Fetch same-origin JSON so the same build works locally and behind nginx. */
export async function getJson(path, signal) {
  const response = await fetch(path, { signal, headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error('The service is temporarily unavailable. Please try again.');
  return response.json();
}
export async function loadProject(signal) {
  const [health, catalog] = await Promise.all([
    getJson('/api/health', signal), getJson('/api/catalog', signal),
  ]);
  if (health.status !== 'UP' || health.database !== 'UP') throw new Error('The database is not ready. Please try again.');
  return { health, catalog };
}
