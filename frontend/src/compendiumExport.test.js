import { expect, it } from 'vitest';
import { compendiumText } from './compendiumExport.js';

it('matches the archived Compendium export with elite and infantry entries, aliases and repeated gear', () => {
  const file = { name: 'Forward Patrol', faction: 'new-antioch', ducatLimit: 700, units: [
    { name: 'Yeoman', equipment: [{ name: 'Bolt-Action Rifle', quantity: 1 }] },
    { name: 'Lieutenant', equipment: [{ name: 'Pistol', quantity: 2 }] },
    { name: 'Mechanized Heavy Infantry', equipment: [{ name: 'Machine Armour', quantity: 1 }] },
  ] };
  const unitDetails = [
    { type: 'Infantry', totalCost: 35, prices: { 'Bolt-Action Rifle': { amount: 5, currency: 'ducats' } } },
    { type: 'Leader', totalCost: 82, prices: { Pistol: { amount: 6, currency: 'ducats' } } },
    { type: 'Heavy infantry', totalCost: 95, prices: { 'Machine Armour': { amount: 10, currency: 'ducats' } } },
  ];
  const expected = [
    "```",
    " Forward Patrol | The Principality of New Antioch ",
    "[ DUCATS ] Total : 700 | Spent : 212 (0 Lost) | Available : 488",
    "[ GLORY ] Total : 0   | Spent : 0 (0 Lost)   | Available : 0",
    "[ ELITE MEMBERS ]",
    " Lieutenant | Lieutenant | 82 ducats 0 glory ---",
    "[ EQUIPMENT ]",
    "-Pistol (6 ducats)",
    "-Pistol (6 ducats)",
    "",
    "[ INFANTRY ]",
    " Yeoman | Yeoman | 35 ducats 0 glory ---",
    "[ EQUIPMENT ]",
    "-Bolt Action Rifle (5 ducats)",
    "",
    " Mechanised Heavy Infantry | Mechanised Heavy Infantry | 95 ducats 0 glory ---",
    "[ EQUIPMENT ]",
    "-Machine Armour (10 ducats)",
    "",
    "",
    "",
    "```"
  ].join('\n');
  expect(compendiumText({ file, totalCost: 212, unitDetails })).toBe(expected);
});

it('exports the new elites and the legacy Shocktrooper and Infiltrator names', () => {
  const file = { name: 'Patrol', faction: 'new-antioch', ducatLimit: 700, units: [
    { name: 'Shock Trooper', equipment: [] },
    { name: 'Sniper Priest', equipment: [] },
    { name: 'Trench Cleric', equipment: [] },
    { name: 'Yeoman', trenchMole: true, equipment: [] },
  ] };
  const unitDetails = [
    { type: 'Infantry', totalCost: 45 },
    { type: 'Elite', totalCost: 50 },
    { type: 'Elite', totalCost: 60 },
    { type: 'Infantry', totalCost: 40 },
  ];
  const [elites, troops] = compendiumText({ file, totalCost: 195, unitDetails }).split('[ INFANTRY ]');
  expect(elites).toContain('Sniper Priest | Sniper Priest | 50 ducats');
  expect(elites).toContain('Trench Cleric | Trench Cleric | 60 ducats');
  expect(troops).toContain('Shocktrooper | Shocktrooper | 45 ducats');
  expect(troops).toMatch(/Yeoman \| Yeoman \| 40 ducats 0 glory -+\n\[ UPGRADES \]\n-Infiltrator \(10 ducats\)/);
});

it('preserves separate Ducat and Glory prices in Compendium text', () => {
  const file = { name: 'Patrol', faction: 'new-antioch', ducatLimit: 800, gloryLimit: 6, units: [
    { name: 'Lieutenant', equipment: [{ name: 'Pistol', quantity: 1 }, { name: 'Field Shrine', quantity: 1 }] },
  ] };
  const unitDetails = [{ type: 'Leader', totalCost: 76, glorySpent: 2, prices: {
    Pistol: { amount: 6, currency: 'ducats' }, 'Field Shrine': { amount: 2, currency: 'glory' },
  } }];
  const text = compendiumText({ file, totalCost: 76, glorySpent: 2, unitDetails });
  expect(text).toMatch(/\[ GLORY \] Total : 6 +\| Spent : 2 \(0 Lost\) +\| Available : 4/);
  expect(text).toContain('Lieutenant | Lieutenant | 76 ducats 2 glory');
  expect(text).toContain('-Pistol (6 ducats)');
  expect(text).toContain('-Field Shrine (2 glory)');
});
