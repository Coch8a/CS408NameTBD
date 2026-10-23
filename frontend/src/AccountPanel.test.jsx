import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import AccountPanel from './AccountPanel.jsx';
import { getCurrentUser, loginAccount, registerAccount, logoutAccount } from './api.js';

vi.mock('./api.js', () => ({ getCurrentUser: vi.fn(), loginAccount: vi.fn(), registerAccount: vi.fn(), logoutAccount: vi.fn() }));
const user = { id: 1, username: 'local_tester', email: 'tester@example.invalid' };
const password = 'Local test passphrase 123!';
beforeEach(() => {
  vi.resetAllMocks();
  getCurrentUser.mockResolvedValue(null);
  loginAccount.mockResolvedValue(user);
  registerAccount.mockResolvedValue(user);
  logoutAccount.mockResolvedValue(null);
  localStorage.clear(); sessionStorage.clear();
});
const fill = (label, value) => fireEvent.change(screen.getByLabelText(label, { exact: true }), { target: { value } });
async function openLogin() {
  render(<AccountPanel />);
  await screen.findByRole('form', { name: 'Sign in' });
}
async function openRegistration() {
  await openLogin();
  fireEvent.click(screen.getByRole('button', { name: 'Register', exact: true }));
}
function fillRegistration() {
  fill('Username', 'local_tester'); fill('Email', user.email);
  fill('Password', password); fill('Confirm password', password);
}

describe('Account forms', () => {
  it('restores an existing session on page load without displaying credentials', async () => {
    getCurrentUser.mockResolvedValue(user);
    const { container } = render(<AccountPanel />);
    expect(await screen.findByText('Signed in as local_tester')).toBeTruthy();
    expect(screen.getByText(user.email)).toBeTruthy();
    expect(screen.queryByLabelText('Password', { exact: true })).toBeNull();
    expect(container.querySelector('img, svg, canvas')).toBeNull();
  });
  it('creates an account and returns to login with username filled and passwords cleared', async () => {
    await openRegistration(); fillRegistration();
    fireEvent.submit(screen.getByRole('form', { name: 'Create account' }));
    expect(await screen.findByText('Account created. Sign in with your new password.')).toBeTruthy();
    expect(registerAccount).toHaveBeenCalledWith({ username: user.username, email: user.email, password, confirmPassword: password }, expect.any(AbortSignal));
    expect(screen.getByLabelText('Username', { exact: true }).value).toBe(user.username);
    expect(screen.getByLabelText('Password', { exact: true }).value).toBe('');
    expect(loginAccount).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Sign out' })).toBeNull();
  });
  it('validates fields and focuses the first invalid field without contacting the server', async () => {
    await openRegistration();
    fill('Username', 'bad name'); fill('Email', 'invalid'); fill('Password', 'short'); fill('Confirm password', 'different');
    fireEvent.submit(screen.getByRole('form', { name: 'Create account' }));
    expect(screen.getByText('Passwords must match.')).toBeTruthy();
    expect(screen.getByText('Enter a valid email address.')).toBeTruthy();
    expect(screen.getByLabelText('Username', { exact: true }).getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(screen.getByLabelText('Username', { exact: true }));
    expect(registerAccount).not.toHaveBeenCalled();
  });
  it('rejects passwords exceeding the BCrypt UTF-8 byte limit', async () => {
    await openRegistration(); fillRegistration();
    fill('Password', 'é'.repeat(37)); fill('Confirm password', 'é'.repeat(37));
    fireEvent.submit(screen.getByRole('form', { name: 'Create account' }));
    expect(screen.getByText('Password is too long. Try fewer characters.')).toBeTruthy();
    expect(registerAccount).not.toHaveBeenCalled();
  });
  it('shows duplicate-account failures and keeps non-secret fields for correction', async () => {
    registerAccount.mockRejectedValue(new Error('Username or email is already in use.'));
    await openRegistration(); fillRegistration();
    fireEvent.submit(screen.getByRole('form', { name: 'Create account' }));
    expect((await screen.findByRole('alert')).textContent).toContain('already in use');
    expect(screen.getByLabelText('Email', { exact: true }).value).toBe(user.email);
    expect(screen.getByLabelText('Password', { exact: true }).value).toBe('');
    expect(screen.getByLabelText('Confirm password', { exact: true }).value).toBe('');
  });
  it('shows backend field validation messages', async () => {
    registerAccount.mockRejectedValue(Object.assign(new Error('Check the registration fields.'), { fields: { email: 'Enter a valid email address.' } }));
    await openRegistration(); fillRegistration();
    fireEvent.submit(screen.getByRole('form', { name: 'Create account' }));
    expect(await screen.findByText('Enter a valid email address.')).toBeTruthy();
    expect(screen.getByLabelText('Email', { exact: true }).getAttribute('aria-describedby')).toContain('error-email');
  });
  it('logs in and logs out without writing credentials to browser storage', async () => {
    await openLogin(); fill('Username', ' local_tester '); fill('Password', password);
    fireEvent.submit(screen.getByRole('form', { name: 'Sign in' }));
    expect(await screen.findByText('Signed in as local_tester')).toBeTruthy();
    expect(loginAccount).toHaveBeenCalledWith('local_tester', password, expect.any(AbortSignal));
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(await screen.findByText('Signed out.')).toBeTruthy();
    expect(screen.getByRole('form', { name: 'Sign in' })).toBeTruthy();
    expect(localStorage.length).toBe(0); expect(sessionStorage.length).toBe(0);
  });
  it('keeps the form available after invalid credentials', async () => {
    loginAccount.mockRejectedValue(new Error('Invalid username or password.'));
    await openLogin(); fill('Username', user.username); fill('Password', password);
    fireEvent.submit(screen.getByRole('form', { name: 'Sign in' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Invalid username or password');
    expect(screen.getByLabelText('Password', { exact: true }).value).toBe('');
    expect(screen.queryByRole('button', { name: 'Sign out' })).toBeNull();
  });
  it('prevents duplicate submits while login is pending', async () => {
    let finish;
    loginAccount.mockReturnValue(new Promise(resolve => { finish = resolve; }));
    await openLogin(); fill('Username', user.username); fill('Password', password);
    const form = screen.getByRole('form', { name: 'Sign in' });
    fireEvent.submit(form); fireEvent.submit(form);
    expect(loginAccount).toHaveBeenCalledTimes(1);
    expect(form.querySelector('fieldset').disabled).toBe(true);
    expect(screen.getByRole('button', { name: 'Register', exact: true }).disabled).toBe(true);
    finish(user);
    await screen.findByText('Signed in as local_tester');
  });
  it('does not pretend logout succeeded if the server could not be reached', async () => {
    getCurrentUser.mockResolvedValue(user);
    logoutAccount.mockRejectedValue(new Error('Unable to reach the account service. Please try again.'));
    render(<AccountPanel />);
    fireEvent.click(await screen.findByRole('button', { name: 'Sign out' }));
    await screen.findByRole('alert');
    expect(screen.getByText('Signed in as local_tester')).toBeTruthy();
    logoutAccount.mockResolvedValue(null);
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    await screen.findByText('Signed out.');
  });
  it('detects an ended session when returning to the window', async () => {
    getCurrentUser.mockResolvedValueOnce(user).mockResolvedValue(null);
    render(<AccountPanel />);
    await screen.findByText('Signed in as local_tester');
    fireEvent(window, new Event('focus'));
    await screen.findByText('Your session ended. Please sign in again.');
    expect(screen.getByRole('form', { name: 'Sign in' })).toBeTruthy();
  });
  it('supports retrying an unsuccessful initial session check', async () => {
    getCurrentUser.mockRejectedValueOnce(new Error('Unable to reach the account service. Please try again.')).mockResolvedValue(null);
    render(<AccountPanel />);
    fireEvent.click(await screen.findByRole('button', { name: 'Retry account check' }));
    await screen.findByRole('form', { name: 'Sign in' });
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
