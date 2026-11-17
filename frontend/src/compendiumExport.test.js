import { expect, it } from 'vitest';
import { compendiumText } from './compendiumExport.js';

it('matches the archived Compendium export with elite and infantry entries, aliases and repeated gear', () => {
  const file = { name: 'Forward Patrol', faction: 'new-antioch', ducatLimit: 700, units: [
    { name: 'Yeoman', equipment: [{ name: 'Bolt-Action Rifle', quantity: 1 }] },
    { name: 'Lieutenant', equipment: [{ name: 'Pistol', quantity: 2 }] },
    { name: 'Mechanized Heavy Infantry', equipment: [{ name: 'Machine Armour', quantity: 1 }] },
  ] };
  const unitDetails = [
    { type: 'Infantry', totalCost: 35, prices: { 'Bolt-Action Rifle': 5 } },
    { type: 'Leader', totalCost: 82, prices: { Pistol: 6 } },
    { type: 'Heavy infantry', totalCost: 95, prices: { 'Machine Armour': 10 } },
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
