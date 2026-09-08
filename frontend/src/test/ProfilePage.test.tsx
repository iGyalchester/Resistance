import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { BORIS, PROFILE, bodyOf, jsonResponse, renderApp } from './helpers';

describe('profile page', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('loads the profile with the email read-only and saves name and phone', async () => {
    const user = userEvent.setup();
    let sent: unknown = null;
    renderApp('/profile', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/profile': () => jsonResponse(PROFILE),
      'PUT /api/profile': (init) => {
        sent = bodyOf(init);
        return jsonResponse({ ...PROFILE, fullName: 'Boris G.', phone: null });
      },
    });

    const email = await screen.findByLabelText('Email');
    expect(email).toHaveValue('boris@gmail.com');
    expect(email).toHaveAttribute('readonly');
    await waitFor(() => expect(screen.getByLabelText('Phone')).toHaveValue('+1 555 0100'));

    await user.clear(screen.getByLabelText('Full name'));
    await user.type(screen.getByLabelText('Full name'), 'Boris G.');
    await user.clear(screen.getByLabelText('Phone'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent).toEqual({ fullName: 'Boris G.', phone: null }));
    expect(await screen.findByText('Profile saved')).toBeInTheDocument();
    // the shell picks up the new name without a reload
    expect(screen.getAllByText('Boris G.').length).toBeGreaterThan(0);
  });

  it('shows a field error from the server', async () => {
    const user = userEvent.setup();
    renderApp('/profile', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/profile': () => jsonResponse(PROFILE),
      'PUT /api/profile': () => jsonResponse({ error: 'validation', fields: { phone: 'at most 40 characters' } }, 400),
    });

    await screen.findByLabelText('Email');
    await user.type(screen.getByLabelText('Phone'), '9');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('at most 40 characters')).toBeInTheDocument();
  });
});
