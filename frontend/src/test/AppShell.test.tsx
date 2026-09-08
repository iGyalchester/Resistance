import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ADMIN_BORIS, ANALYTICS, ASSISTANT_BORIS, BORIS, jsonResponse, noContent, renderApp, sse, sseResponse } from './helpers';

describe('app shell', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the main navigation and hides Admin for a plain user', async () => {
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/analytics/summary': () => jsonResponse(ANALYTICS),
    });

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    expect(nav).toHaveTextContent('Dashboard');
    expect(nav).toHaveTextContent('Applications');
    expect(nav).toHaveTextContent('Contacts');
    expect(nav).toHaveTextContent('Assistant');
    expect(nav).toHaveTextContent('Help');
    expect(nav).toHaveTextContent('Profile');
    expect(nav).not.toHaveTextContent('Admin');
    // no quick "Ask" button and no drawer while the assistant is off
    expect(screen.queryByRole('button', { name: 'Ask' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ask the assistant' })).not.toBeInTheDocument();
  });

  it('opens the assistant drawer from the dashboard and keeps the chat while closed', async () => {
    const user = userEvent.setup();
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'GET /api/analytics/summary': () => jsonResponse(ANALYTICS),
      'POST /api/assistant/messages': () =>
        sseResponse([sse('delta', { text: 'Chase Acme.' }) + sse('done', { usage: { inputTokens: 1, outputTokens: 1 } })]),
    });

    expect(screen.queryByRole('complementary', { name: 'Assistant' })).not.toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Ask the assistant' }));
    const drawer = await screen.findByRole('complementary', { name: 'Assistant' });
    await user.click(within(drawer).getByRole('button', { name: 'Which applications should I follow up on?' }));
    expect(await within(drawer).findByText('Chase Acme.')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('complementary', { name: 'Assistant' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ask' }));
    expect(within(await screen.findByRole('complementary', { name: 'Assistant' })).getByText('Chase Acme.')).toBeInTheDocument();
  });

  it('shows Admin when the server says the account is an admin', async () => {
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(ADMIN_BORIS),
      'GET /api/analytics/summary': () => jsonResponse(ANALYTICS),
    });

    expect(await screen.findByRole('link', { name: 'Admin' })).toBeInTheDocument();
  });

  it('logs out through the API and lands on the login page', async () => {
    const user = userEvent.setup();
    let loggedOut = false;
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/analytics/summary': () => jsonResponse(ANALYTICS),
      'POST /api/auth/logout': () => {
        loggedOut = true;
        return noContent();
      },
    });

    await screen.findByText('Active');
    await user.click(screen.getByRole('button', { name: 'Log out' }));

    await waitFor(() => expect(loggedOut).toBe(true));
    expect(await screen.findByLabelText(/email/i)).toBeInTheDocument();
  });
});
