import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ADMIN_BORIS, APPLICATIONS, BORIS, jsonResponse, noContent, renderApp } from './helpers';

describe('app shell', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the main navigation and hides Admin for a plain user', async () => {
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
    });

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    expect(nav).toHaveTextContent('Dashboard');
    expect(nav).toHaveTextContent('Applications');
    expect(nav).toHaveTextContent('Contacts');
    expect(nav).toHaveTextContent('Profile');
    expect(nav).not.toHaveTextContent('Admin');
  });

  it('shows Admin when the server says the account is an admin', async () => {
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(ADMIN_BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
    });

    expect(await screen.findByRole('link', { name: 'Admin' })).toBeInTheDocument();
  });

  it('logs out through the API and lands on the login page', async () => {
    const user = userEvent.setup();
    let loggedOut = false;
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
      'POST /api/auth/logout': () => {
        loggedOut = true;
        return noContent();
      },
    });

    await screen.findByText('Acme Corp');
    await user.click(screen.getByRole('button', { name: 'Log out' }));

    await waitFor(() => expect(loggedOut).toBe(true));
    expect(await screen.findByLabelText(/email/i)).toBeInTheDocument();
  });
});
