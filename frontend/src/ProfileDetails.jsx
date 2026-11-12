import React, { useEffect, useState } from 'react';
import { getJson } from './api.js';

const dice = value => `${value >= 0 ? '+' : ''}${value} DICE`;

export function ProfileStats({ profile }) {
  if (!profile) return null;
  const unit = profile.movementInches != null;
  const stats = unit ? [
    ['Move', `${profile.movementInches}″ ${profile.movementType}`],
    ['Ranged', dice(profile.rangedDice)], ['Melee', dice(profile.meleeDice)],
    ['Armour', profile.armourModifier], ['Base', `${profile.baseMm} mm`],
    ['Charge bonus', profile.chargeBonus === '0' ? 'None' : `${profile.chargeBonus}″`]
  ] : [
    ...(profile.rangeInches != null || profile.melee ? [
      ['Range', [profile.rangeInches != null ? `${profile.rangeInches}″` : '', profile.melee ? 'Melee' : ''].filter(Boolean).join(' / ')],
      ['Hands', profile.hands],
      ...(profile.rangeInches != null ? [['Ranged modifier', dice(profile.rangedDice)]] : []),
      ...(profile.melee ? [['Melee modifier', dice(profile.meleeDice)]] : [])
    ] : []),
    ...(profile.armourModifier ? [['Injury modifier', profile.armourModifier]] : [])
  ];
  return stats.length > 0 && <dl className="profile-stats">{stats.map(([label, value]) =>
    <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>;
}

export default function ProfileDetails({ kind, id, name, title = 'Rules', showStats = true }) {
  const [open, setOpen] = useState(false);
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!open || profile) return;
    const controller = new AbortController();
    setError(false);
    getJson(`/api/catalog/${kind}/${id}/profile`, controller.signal).then(data => {
      if (!controller.signal.aborted) setProfile(data);
    }).catch(() => {
      if (!controller.signal.aborted) setError(true);
    });
    return () => controller.abort();
  }, [open, profile, kind, id, attempt]);

  return <details className="profile-details" onToggle={event => setOpen(event.currentTarget.open)}>
    <summary aria-label={`${name} rules`}>{title}</summary>
    {open && !profile && (error ? <p role="alert">Could not load rules.{' '}
      <button type="button" onClick={() => setAttempt(value => value + 1)}>Retry</button>
    </p> : <p role="status">Loading rules…</p>)}
    {profile && <>
      {showStats && <ProfileStats profile={profile.stats} />}
      {profile.rules.length > 0 && <dl className="profile-rules">{profile.rules.map(rule => <div key={rule.code}>
        <dt>{rule.name}{rule.kind === 'upgrade' ? ' (upgrade)' : ''}</dt>
        <dd>{rule.summary} <a href={rule.source.url} target="_blank" rel="noreferrer"
          title={`${rule.source.title} ${rule.source.version}, p. ${rule.source.page}`}
          aria-label={`${rule.name} source, page ${rule.source.page}`}>p. {rule.source.page}</a></dd>
      </div>)}</dl>}
      <a className="profile-source" href={profile.source.url} target="_blank" rel="noreferrer">
        {profile.source.title} {profile.source.version} · p. {profile.source.page}
      </a>
    </>}
  </details>;
}
