import React, { useEffect, useRef, useState } from 'react';
import { getCurrentUser, loginAccount, logoutAccount, registerAccount } from './api.js';

const emptyFields = { username: '', email: '', password: '', confirmPassword: '' };

/** Plain account forms. Credentials stay in memory; the browser owns the session cookie. */
export default function AccountPanel({ onUserChange, sessionRevision = 0 }) {
  const [user, setUser] = useState(null);
  const userRef = useRef(null);
  const [checking, setChecking] = useState(true);
  const [checkError, setCheckError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [mode, setMode] = useState('login');
  const [fields, setFields] = useState(emptyFields);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const profileRequest = useRef(null);
  const actionRequest = useRef(null);
  const formRef = useRef(null);

  function rememberUser(account) {
    userRef.current = account;
    setUser(account);
    onUserChange?.(account);
  }

  useEffect(() => {
    async function refresh() {
      if (busyRef.current) return;
      profileRequest.current?.abort();
      const controller = new AbortController();
      profileRequest.current = controller;
      try {
        const account = await getCurrentUser(controller.signal);
        if (controller.signal.aborted) return;
        if (userRef.current && !account) setNotice('Your session ended. Please sign in again.');
        rememberUser(account);
        setCheckError('');
      } catch (reason) {
        if (!controller.signal.aborted) setCheckError(reason.message);
      } finally {
        if (!controller.signal.aborted) setChecking(false);
      }
    }
    refresh();
    window.addEventListener('focus', refresh);
    return () => {
      profileRequest.current?.abort();
      window.removeEventListener('focus', refresh);
    };
  }, [attempt, sessionRevision]);

  useEffect(() => () => actionRequest.current?.abort(), []);

  function changeMode(nextMode) {
    if (busyRef.current) return;
    setMode(nextMode);
    setFields(current => ({ ...current, password: '', confirmPassword: '' }));
    setErrors({});
    setError('');
    setNotice('');
  }

  function showErrors(nextErrors) {
    setErrors(nextErrors);
    const first = Object.keys(nextErrors)[0];
    if (first) formRef.current?.elements.namedItem(first)?.focus();
  }

  function validate() {
    const next = {};
    const username = fields.username.trim();
    if (!username) next.username = 'Enter your username.';
    else if (mode === 'register' && !/^[A-Za-z0-9_]{3,50}$/.test(username)) {
      next.username = 'Use 3–50 letters, numbers, or underscores.';
    }
    if (mode === 'register') {
      if (!fields.email.trim() || !/^[^\s@]+@[^\s@]+$/.test(fields.email.trim())) next.email = 'Enter a valid email address.';
      if (fields.email.trim().length > 254) next.email = 'Email must be at most 254 characters.';
    }
    if (!fields.password) next.password = 'Enter your password.';
    else if (mode === 'register') {
      if (fields.password.length < 12) next.password = 'Use at least 12 characters.';
      else if (new TextEncoder().encode(fields.password).length > 72) next.password = 'Password is too long. Try fewer characters.';
    }
    if (mode === 'register' && fields.password !== fields.confirmPassword) next.confirmPassword = 'Passwords must match.';
    showErrors(next);
    return Object.keys(next).length === 0;
  }

  async function perform(action) {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError('');
    setNotice('');
    profileRequest.current?.abort();
    const controller = new AbortController();
    actionRequest.current = controller;
    try {
      await action(controller.signal);
    } catch (reason) {
      if (controller.signal.aborted) return;
      setError(reason.message);
      showErrors(reason.fields || {});
      setFields(current => ({ ...current, password: '', confirmPassword: '' }));
    } finally {
      busyRef.current = false;
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  function submit(event) {
    event.preventDefault();
    if (busyRef.current || !validate()) return;
    perform(async signal => {
      if (mode === 'register') {
        const account = await registerAccount({ ...fields, username: fields.username.trim(), email: fields.email.trim() }, signal);
        if (signal.aborted) return;
        setFields({ ...emptyFields, username: account.username });
        setMode('login');
        setNotice('Account created. Sign in with your new password.');
      } else {
        const account = await loginAccount(fields.username.trim(), fields.password, signal);
        if (signal.aborted) return;
        rememberUser(account);
        setFields(emptyFields);
        setCheckError('');
        setNotice('Signed in.');
      }
      setErrors({});
    });
  }

  function signOut() {
    perform(async signal => {
      await logoutAccount(signal);
      if (signal.aborted) return;
      rememberUser(null);
      setFields(emptyFields);
      setErrors({});
      setMode('login');
      setCheckError('');
      setNotice('Signed out.');
    });
  }

  function field(name, label, type, autoComplete, hint) {
    return <div className="account-field">
      <label htmlFor={`account-${name}`}>{label}</label>
      <input id={`account-${name}`} name={name} type={type} autoComplete={autoComplete}
        value={fields[name]} required aria-invalid={Boolean(errors[name])}
        aria-describedby={[hint && `hint-${name}`, errors[name] && `error-${name}`].filter(Boolean).join(' ') || undefined}
        onChange={event => setFields(current => ({ ...current, [name]: event.target.value }))}/>
      {hint && <small id={`hint-${name}`} className="account-hint">{hint}</small>}
      {errors[name] && <span id={`error-${name}`} className="field-error">{errors[name]}</span>}
    </div>;
  }

  return <section className="catalog-panel account-panel" id="account" aria-labelledby="account-heading">
    <div className="panel-heading"><h2 id="account-heading">Account</h2><span>{checking ? 'Checking session' : user ? `Signed in as ${user.username}` : 'Signed out'}</span></div>
    <div className="account-body">
      {checking && <p role="status">Checking your session…</p>}
      {checkError && <div role="alert" className="error-message"><span>{checkError}</span>
        <button type="button" disabled={busy} onClick={() => { setChecking(true); setAttempt(value => value + 1); }}>Retry account check</button></div>}
      {notice && <p className="account-notice" role="status">{notice}</p>}
      {error && <p className="account-error" role="alert">{error}</p>}
      {!checking && user && <div className="account-summary">
        <dl><div><dt>Username</dt><dd>{user.username}</dd></div><div><dt>Email</dt><dd>{user.email}</dd></div></dl>
        <button type="button" disabled={busy} onClick={signOut}>{busy ? 'Signing out…' : 'Sign out'}</button>
      </div>}
      {!checking && !user && !checkError && <>
        <div className="account-modes" aria-label="Account options">
          <button type="button" aria-pressed={mode === 'login'} disabled={busy} onClick={() => changeMode('login')}>Log in</button>
          <button type="button" aria-pressed={mode === 'register'} disabled={busy} onClick={() => changeMode('register')}>Register</button>
        </div>
        <form ref={formRef} aria-label={mode === 'register' ? 'Create account' : 'Sign in'} onSubmit={submit} noValidate>
          <fieldset disabled={busy}>
            <legend className="visually-hidden">{mode === 'register' ? 'Registration details' : 'Login details'}</legend>
            <div className="account-fields">
              {field('username', 'Username', 'text', 'username', mode === 'register' ? '3–50 letters, numbers, or underscores.' : undefined)}
              {mode === 'register' && field('email', 'Email', 'email', 'email')}
              {field('password', 'Password', 'password', mode === 'register' ? 'new-password' : 'current-password', mode === 'register' ? 'Use at least 12 characters.' : undefined)}
              {mode === 'register' && field('confirmPassword', 'Confirm password', 'password', 'new-password')}
            </div>
            <button className="account-submit" type="submit">{busy ? 'Please wait…' : mode === 'register' ? 'Create account' : 'Sign in'}</button>
          </fieldset>
        </form>
      </>}
    </div>
  </section>;
}
