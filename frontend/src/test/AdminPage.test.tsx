import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ADMIN_ACCOUNTS, ADMIN_BORIS, ADMIN_OVERVIEW, BORIS, jsonResponse, renderApp } from './helpers';

describe('admin page', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows tiles, the activity chart, counters with their caveat, and the accounts', async () => {
    renderApp('/admin', {
      'GET /api/auth/me': () => jsonResponse(ADMIN_BORIS),
      'GET /api/admin/overview': () => jsonResponse(ADMIN_OVERVIEW),
      'GET /api/admin/accounts': () => jsonResponse(ADMIN_ACCOUNTS),
    });

    expect((await screen.findByText('Accounts', { selector: '.stat-label' })).parentElement).toHaveTextContent(/^3Accounts/);
    expect(screen.getByText('Applications', { selector: '.stat-label' }).parentElement).toHaveTextContent(/^12Applications/);
    expect(screen.getByText('25%')).toBeInTheDocument();
    expect(screen.getByText('Active').parentElement).toHaveTextContent(/^11Active/);

    expect(screen.getByRole('img', { name: /activity per day over 30 days: 4 changes from email, 1 by hand/i })).toBeInTheDocument();

    // counters, with the "since restart" caveat and the cost estimate at the stated rates
    expect(screen.getAllByText(/since this process started/i).length).toBeGreaterThan(0);
    expect(screen.getByText('Codes requested').nextElementSibling).toHaveTextContent('9');
    expect(screen.getByText('Failed codes').nextElementSibling).toHaveTextContent('2');
    expect(screen.getByText('Estimated cost').nextElementSibling).toHaveTextContent('$7.50');
    expect(screen.getByText(/per million tokens/)).toBeInTheDocument();

    const accounts = screen.getByText('boris@gmail.com').closest('table')!;
    expect(within(accounts).getAllByRole('row')).toHaveLength(3);
    expect(within(accounts).getByText('Boris Gerard')).toBeInTheDocument();
    expect(screen.getByText('alias assigned')).toBeInTheDocument();
    expect(screen.getByText('no alias yet')).toBeInTheDocument();
    expect(screen.queryByText('null')).not.toBeInTheDocument();
  });

  it('filters the accounts by email or name', async () => {
    const user = userEvent.setup();
    renderApp('/admin', {
      'GET /api/auth/me': () => jsonResponse(ADMIN_BORIS),
      'GET /api/admin/overview': () => jsonResponse(ADMIN_OVERVIEW),
      'GET /api/admin/accounts': () => jsonResponse(ADMIN_ACCOUNTS),
    });

    await screen.findByText('quiet@example.com');
    await user.type(screen.getByLabelText('Search accounts'), 'gerard');

    expect(screen.getByText('boris@gmail.com')).toBeInTheDocument();
    expect(screen.queryByText('quiet@example.com')).not.toBeInTheDocument();
    await user.clear(screen.getByLabelText('Search accounts'));
    await user.type(screen.getByLabelText('Search accounts'), 'nobody');
    expect(screen.getByText('No accounts match.')).toBeInTheDocument();
  });

  it('tells a non-admin they are not authorized without calling the admin API', async () => {
    const { fetchMock } = renderApp('/admin', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
    });

    expect(await screen.findByText('You are not authorized to see this page.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/admin'))).toBe(false);
  });

  it('shows not authorized when the server answers 403 despite the role claim', async () => {
    renderApp('/admin', {
      'GET /api/auth/me': () => jsonResponse(ADMIN_BORIS),
      'GET /api/admin/overview': () => jsonResponse({ error: 'forbidden' }, 403),
      'GET /api/admin/accounts': () => jsonResponse({ error: 'forbidden' }, 403),
    });

    expect(await screen.findByText('You are not authorized to see this page.')).toBeInTheDocument();
  });
});
