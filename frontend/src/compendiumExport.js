const legacyNames = {
  'Mechanized Heavy Infantry': 'Mechanised Heavy Infantry',
  'Bolt-Action Rifle': 'Bolt Action Rifle',
  'Medi-kit': 'Medi-Kit',
  'Shock Trooper': 'Shocktrooper',
};

const legacyName = name => legacyNames[name] ?? name;
const fence = '\x60\x60\x60';

export function compendiumText({ file, totalCost, glorySpent = 0, unitDetails }) {
  if (file.faction !== 'new-antioch') throw new Error('Compendium export supports New Antioch.');
  if (!file.units.length) throw new Error('Add a unit before exporting to the Compendium Generator.');
  const title = file.name.replace(/[|\x60\r\n\u2028\u2029]/g, ' ').trim() || 'Warband';
  const gloryLimit = file.gloryLimit ?? 0;
  const totalWidth = Math.max(String(file.ducatLimit).length, String(gloryLimit).length);
  const spentLabel = amount => `Spent : ${amount} (0 Lost)`;
  const spentWidth = Math.max(spentLabel(totalCost).length, spentLabel(glorySpent).length);
  const totals = (total, spent) =>
    `Total : ${String(total).padEnd(totalWidth)} | ${spentLabel(spent).padEnd(spentWidth)} | Available : ${total - spent}`;
  const lines = [
    fence, ` ${title} | The Principality of New Antioch `,
    `[ DUCATS ] ${totals(file.ducatLimit, totalCost)}`,
    `[ GLORY ] ${totals(gloryLimit, glorySpent)}`,
  ];
  for (const [elite, heading] of [[true, '[ ELITE MEMBERS ]'], [false, '[ INFANTRY ]']]) {
    const group = file.units.flatMap((unit, i) => (['Leader', 'Elite'].includes(unitDetails[i].type)) === elite ? [[unit, unitDetails[i]]] : []);
    if (!group.length) continue;
    lines.push(heading);
    for (const [unit, detail] of group) {
      const name = legacyName(unit.name);
      const header = `${name} | ${name} | ${detail.totalCost} ducats ${detail.glorySpent ?? 0} glory`;
      const gear = unit.equipment.flatMap(item => {
        const { amount, currency } = detail.prices[item.name];
        return Array.from({ length: item.quantity }, () => `-${legacyName(item.name)} (${amount} ${currency})`);
      });
      const upgrades = unit.trenchMole ? ['-Infiltrator (10 ducats)'] : [];
      const width = Math.max(header.length + 8, ...gear.map(row => row.length - 1),
        ...upgrades.map(row => row.length - 1));
      lines.push(` ${header} ${'-'.repeat(Math.floor((width + width % 2 - header.length - 2) / 2))}`);
      if (upgrades.length) lines.push('[ UPGRADES ]', ...upgrades);
      if (gear.length) lines.push('[ EQUIPMENT ]', ...gear);
      lines.push('');
    }
  }
  return lines.join('\n') + '\n\n\n' + fence;
}
