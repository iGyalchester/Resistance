import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { APPLICATIONS, BORIS, jsonResponse, renderApp } from './helpers';

describe('login flow', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('walks email -> code -> dashboard', async () => {
    const user = userEvent.setup();
    const { fetchMock } = renderApp('/login', {
      'POST /api/auth/code': () => jsonResponse({ message: 'ok' }),
      'POST /api/auth/login': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
    });

    await user.type(await screen.findByLabelText('Email address'), 'boris@gmail.com');
    await user.click(screen.getByRole('button', { name: /send me a login code/i }));

    // step 2 shows where the code went
    expect(await screen.findByText('Check your email')).toBeInTheDocument();
    expect(screen.getByText('boris@gmail.com')).toBeInTheDocument();
    const codeCall = fetchMock.mock.calls.find(([url]) => String(url) === '/api/auth/code');
    expect(codeCall?.[1]?.body).toBe(JSON.stringify({ email: 'boris@gmail.com' }));

    await user.type(screen.getByLabelText('Login code'), '123456');
    await user.click(screen.getByRole('button', { name: /log in/i }));

    // landed on the dashboard as Boris
    expect(await screen.findByText('Acme Corp')).toBeInTheDocument();
    expect(screen.getByText('Boris Gerard')).toBeInTheDocument();
  });

  it('shows an error for a bad code and stays on the code page', async () => {
    const user = userEvent.setup();
    renderApp('/login', {
      'POST /api/auth/code': () => jsonResponse({ message: 'ok' }),
      'POST /api/auth/login': () => jsonResponse({ error: 'invalid_code' }, 400),
    });

    await user.type(await screen.findByLabelText('Email address'), 'boris@gmail.com');
    await user.click(screen.getByRole('button', { name: /send me a login code/i }));
    await user.type(await screen.findByLabelText('Login code'), '000000');
    await user.click(screen.getByRole('button', { name: /log in/i }));

    expect(await screen.findByText(/that code didn't work/i)).toBeInTheDocument();
    expect(screen.getByLabelText('Login code')).toBeInTheDocument();
  });

  it('redirects anonymous visitors from the dashboard to the login page', async () => {
    renderApp('/dashboard');

    expect(await screen.findByLabelText('Email address')).toBeInTheDocument();
  });
});
