import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ASSISTANT_BORIS, BORIS, FAQ, jsonResponse, renderApp } from './helpers';

describe('help page', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lists every question with its answer and links to the assistant', async () => {
    const user = userEvent.setup();
    renderApp('/help', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'GET /api/help': () => jsonResponse(FAQ),
    });

    expect(await screen.findByText('What is my intake address?')).toBeInTheDocument();
    expect(screen.getByText('What do the statuses mean?')).toBeInTheDocument();
    await user.click(screen.getByText('What do the statuses mean?'));
    expect(screen.getByText('Applied, Screening, Interview, Offer, then the end states.')).toBeInTheDocument();

    const links = screen.getAllByRole('link', { name: 'Ask the assistant about this' });
    expect(links).toHaveLength(2);
    expect(links[1]).toHaveAttribute('href', '/assistant?q=What%20do%20the%20statuses%20mean%3F');
  });

  it('has no assistant links when the feature is off', async () => {
    renderApp('/help', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/help': () => jsonResponse(FAQ),
    });

    expect(await screen.findByText('What is my intake address?')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Ask the assistant about this' })).not.toBeInTheDocument();
  });

  it('shows an error with retry when the FAQ cannot load', async () => {
    let calls = 0;
    const user = userEvent.setup();
    renderApp('/help', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/help': () => (++calls === 1 ? jsonResponse({ error: 'internal' }, 500) : jsonResponse(FAQ)),
    });

    await user.click(await screen.findByRole('button', { name: /retry/i }));
    expect(await screen.findByText('What is my intake address?')).toBeInTheDocument();
  });
});
