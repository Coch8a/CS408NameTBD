import React from 'react';
import { expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import PrintableRoster from './PrintableRoster.jsx';
import { rosterSnapshot } from './rosterTransfer.js';

vi.mock('./rosterTransfer.js', () => ({ rosterSnapshot: vi.fn() }));

it('prints units in role order with their original costs and equipment', async () => {
  rosterSnapshot.mockResolvedValue({
    file: { name: 'Mixed patrol', ducatLimit: 700, gloryLimit: 2, units: [
      { name: 'Yeoman', equipment: [{ name: 'Shovel', quantity: 1 }] },
      { name: 'Hired Scout', equipment: [] },
      { name: 'Trench Cleric', equipment: [] },
      { name: 'Lieutenant', equipment: [{ name: 'Pistol', quantity: 1 }, { name: 'Field Shrine', quantity: 1 }] },
    ] },
    unitDetails: [
      { type: 'Infantry', totalCost: 35 }, { type: 'Mercenaries', totalCost: 50 },
      { type: 'Elite', totalCost: 60 }, { type: 'Leader', totalCost: 76, glorySpent: 2 },
    ],
    factionName: 'New Antioch', totalCost: 221, overBudget: 0, glorySpent: 2, unitIssues: [],
  });

  render(<PrintableRoster id="1" />);
  await screen.findByRole('heading', { name: 'Mixed patrol' });
  const headings = screen.getAllByRole('heading', { level: 2 });
  expect(headings.map(heading => heading.textContent)).toEqual([
    'Lieutenant · 76 ducats · 2 Glory', 'Trench Cleric · 60 ducats', 'Yeoman · 35 ducats', 'Hired Scout · 50 ducats',
  ]);
  expect(screen.getByText(/221 \/ 700 ducats · 2 \/ 2 Glory/)).toBeTruthy();
  expect(headings[0].parentElement.textContent).toContain('1 × Field Shrine');
  expect(headings[0].parentElement.textContent).toContain('1 × Pistol');
  expect(headings[2].parentElement.textContent).toContain('1 × Shovel');
});
