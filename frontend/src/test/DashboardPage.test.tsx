import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ACME_DETAIL, ANALYTICS, BORIS, EMPTY_ANALYTICS, bodyOf, jsonResponse, renderApp } from './helpers';

describe('dashboard', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the intake address, the headline numbers and the charts', async () => {
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/analytics/summary': () => jsonResponse(ANALYTICS),
    });

    expect(await screen.findByText('track+boris2k4mp9@resistance.example')).toBeInTheDocument();

    // stat tiles
    const active = await screen.findByText('Active');
    expect(active.parentElement).toHaveTextContent(/^4Active/);
    expect(screen.getByText('80%')).toBeInTheDocument();
    expect(screen.getByText('20%')).toBeInTheDocument();
    expect(screen.getByText('First response').parentElement).toHaveTextContent(/^15.5 d/);

    // every chart is described for assistive tech and lists every stage
    const funnel = screen.getByRole('img', { name: /applications by stage/i });
    expect(funnel).toHaveAccessibleName(/Applied 1, Screening 1, Interview 1, Offer 1, Accepted 0, Rejected 1, Withdrawn 0/);
    expect(screen.getByRole('img', { name: /applications per week/i })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /median days in stage: Applied 15.5/i })).toBeInTheDocument();

    // needs attention + recent activity
    expect(screen.getByText(/quiet for 19 days/)).toBeInTheDocument();
    expect(screen.getByText('Umbrella Labs')).toBeInTheDocument();
    expect(screen.getByText(/tracked as/)).toBeInTheDocument();
    expect(screen.queryByText('null')).not.toBeInTheDocument();
  });

  it('can switch a chart to its table view', async () => {
    const user = userEvent.setup();
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/analytics/summary': () => jsonResponse(ANALYTICS),
    });

    await screen.findByRole('img', { name: /applications by stage/i });
    await user.click(screen.getAllByRole('button', { name: 'View as table' })[0]);

    const table = screen.getAllByRole('table')[0];
    expect(within(table).getByText('Stage')).toBeInTheDocument();
    expect(within(table).getByText('Rejected')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /applications by stage/i })).not.toBeInTheDocument();
  });

  it('marks a quiet application withdrawn with one click', async () => {
    const user = userEvent.setup();
    let sent: unknown = null;
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/analytics/summary': () => jsonResponse(sent ? { ...ANALYTICS, stale: [] } : ANALYTICS),
      'PUT /api/applications/1': (init) => {
        sent = bodyOf(init);
        return jsonResponse({ ...ACME_DETAIL, status: 'WITHDRAWN' });
      },
    });

    await screen.findByText(/quiet for 19 days/);
    await user.click(screen.getByRole('button', { name: 'Mark withdrawn' }));

    await waitFor(() =>
      expect(sent).toEqual({
        companyName: 'Acme Corp',
        positionTitle: 'Backend Engineer',
        status: 'WITHDRAWN',
        contactId: 3,
      }),
    );
    expect(await screen.findByText('Acme Corp marked withdrawn')).toBeInTheDocument();
    expect(await screen.findByText(/nothing is going quiet/i)).toBeInTheDocument();
  });

  it('shows the empty state and no charts when nothing is tracked yet', async () => {
    renderApp('/dashboard', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/analytics/summary': () => jsonResponse(EMPTY_ANALYTICS),
    });

    expect(await screen.findByText(/nothing tracked yet/i)).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.queryByText('Response rate')).not.toBeInTheDocument();
  });
});
