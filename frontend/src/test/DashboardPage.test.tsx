import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { APPLICATIONS, BORIS, jsonResponse, renderApp } from './helpers';

describe('dashboard', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the intake address and the applications table', async () => {
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
    });

    expect(await screen.findByText('track+boris2k4mp9@resistance.example')).toBeInTheDocument();
    expect(await screen.findByText('Acme Corp')).toBeInTheDocument();
    expect(screen.getByText('Backend Engineer')).toBeInTheDocument();
    expect(screen.getByText('INTERVIEW')).toBeInTheDocument();
    expect(screen.getByText('Dana Reyes')).toBeInTheDocument();
    // nulls render as em dashes, not "null"
    expect(screen.queryByText('null')).not.toBeInTheDocument();
  });

  it('shows the empty state when nothing is tracked yet', async () => {
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse([]),
    });

    expect(await screen.findByText(/nothing tracked yet/i)).toBeInTheDocument();
  });
});
