const rank = type => {
  switch (type?.trim().toLowerCase()) {
    case 'leader': return 0;
    case 'elite': return 1;
    case 'mercenary':
    case 'mercenaries': return 3;
    default: return 2;
  }
};

export const sortRosterUnits = (units, typeOf = unit => unit.type) =>
  [...units].sort((a, b) => rank(typeOf(a)) - rank(typeOf(b)));
