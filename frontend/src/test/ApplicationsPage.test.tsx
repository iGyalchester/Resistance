import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ACME_DETAIL, APPLICATIONS, BORIS, CONTACTS, bodyOf, jsonResponse, renderApp } from './helpers';

describe('applications page', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lists applications and filters by status chip and search', async () => {
    const user = userEvent.setup();
    renderApp('/applications', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
    });

    expect(await screen.findByRole('link', { name: 'Acme Corp' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Globex' })).toBeInTheDocument();

    const chips = within(screen.getByRole('group', { name: 'Filter by status' }));
    await user.click(chips.getByRole('button', { name: 'Applied' }));
    expect(screen.queryByRole('link', { name: 'Acme Corp' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Globex' })).toBeInTheDocument();

    await user.click(chips.getByRole('button', { name: 'All' }));
    await user.type(screen.getByLabelText('Search'), 'backend');
    expect(screen.getByRole('link', { name: 'Acme Corp' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Globex' })).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText('Search'));
    await user.type(screen.getByLabelText('Search'), 'zzz');
    expect(screen.getByText(/no applications match/i)).toBeInTheDocument();
  });

  it('changes a status in place with the whole request body and confirms with a toast', async () => {
    const user = userEvent.setup();
    let sent: unknown = null;
    renderApp('/applications', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
      'PUT /api/applications/1': (init) => {
        sent = bodyOf(init);
        return jsonResponse({ ...ACME_DETAIL, status: 'OFFER' });
      },
    });

    await screen.findByRole('link', { name: 'Acme Corp' });
    await user.selectOptions(screen.getByLabelText('Status for Acme Corp'), 'OFFER');

    await waitFor(() => expect(sent).not.toBeNull());
    expect(sent).toEqual({
      companyName: 'Acme Corp',
      positionTitle: 'Backend Engineer',
      status: 'OFFER',
      contactId: 3,
    });
    expect(await screen.findByText('Acme Corp moved to Offer')).toBeInTheDocument();
    expect((screen.getByLabelText('Status for Acme Corp') as HTMLSelectElement).value).toBe('OFFER');
  });

  it('rolls the status back when the server refuses the change', async () => {
    const user = userEvent.setup();
    renderApp('/applications', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
      'PUT /api/applications/1': () => jsonResponse({ error: 'internal' }, 500),
    });

    await screen.findByRole('link', { name: 'Acme Corp' });
    await user.selectOptions(screen.getByLabelText('Status for Acme Corp'), 'REJECTED');

    expect(await screen.findByText(/could not update acme corp/i)).toBeInTheDocument();
    await waitFor(() =>
      expect((screen.getByLabelText('Status for Acme Corp') as HTMLSelectElement).value).toBe('INTERVIEW'),
    );
  });

  it('rolls back only the failed row, keeping a change that landed meanwhile', async () => {
    const user = userEvent.setup();
    let failAcme: (r: Response) => void = () => {};
    renderApp('/applications', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
      // Acme's PUT stays in flight until we say so; Globex's answers at once
      'PUT /api/applications/1': () => new Promise<Response>((resolve) => (failAcme = resolve)) as unknown as Response,
      'PUT /api/applications/2': () => jsonResponse({ ...APPLICATIONS[1], status: 'SCREENING', history: [] }),
    });

    await screen.findByRole('link', { name: 'Acme Corp' });
    await user.selectOptions(screen.getByLabelText('Status for Acme Corp'), 'REJECTED');
    await user.selectOptions(screen.getByLabelText('Status for Globex'), 'SCREENING');
    expect(await screen.findByText(/globex moved to screening/i)).toBeInTheDocument();

    failAcme(jsonResponse({ error: 'internal' }, 500));

    expect(await screen.findByText(/could not update acme corp/i)).toBeInTheDocument();
    await waitFor(() =>
      expect((screen.getByLabelText('Status for Acme Corp') as HTMLSelectElement).value).toBe('INTERVIEW'),
    );
    expect((screen.getByLabelText('Status for Globex') as HTMLSelectElement).value).toBe('SCREENING');
  });

  it('keeps focus inside the dialog and returns it to the opener on Escape', async () => {
    const user = userEvent.setup();
    renderApp('/applications', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
      'GET /api/contacts': () => jsonResponse([]),
    });

    await screen.findByRole('link', { name: 'Acme Corp' });
    const opener = screen.getByRole('button', { name: 'Add application' });
    await user.click(opener);
    const dialog = await screen.findByRole('dialog', { name: 'Add application' });
    expect(dialog.contains(document.activeElement)).toBe(true);

    // tabbing past the last control wraps to the first, never to the page behind
    for (let i = 0; i < 12; i++) {
      await user.tab();
      expect(dialog.contains(document.activeElement)).toBe(true);
    }

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.activeElement).toBe(opener);
  });

  it('adds an application through the dialog, requiring a company first', async () => {
    const user = userEvent.setup();
    let sent: unknown = null;
    let listCalls = 0;
    renderApp('/applications', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => {
        listCalls += 1;
        return jsonResponse(APPLICATIONS);
      },
      'GET /api/contacts': () => jsonResponse(CONTACTS),
      'POST /api/applications': (init) => {
        sent = bodyOf(init);
        return jsonResponse({ ...ACME_DETAIL, id: 9, companyName: 'Initech' }, 201);
      },
    });

    await screen.findByRole('link', { name: 'Acme Corp' });
    await user.click(screen.getByRole('button', { name: 'Add application' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add application' });

    await user.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('required');
    expect(sent).toBeNull();

    await user.type(within(dialog).getByLabelText('Company'), 'Initech');
    await user.type(within(dialog).getByLabelText('Position'), 'Java Developer');
    await user.selectOptions(within(dialog).getByLabelText('Status'), 'SCREENING');
    await user.selectOptions(within(dialog).getByLabelText('Contact'), '3');
    await user.type(within(dialog).getByLabelText('Applied on'), '2026-08-12');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent).not.toBeNull());
    expect(sent).toEqual({
      companyName: 'Initech',
      positionTitle: 'Java Developer',
      status: 'SCREENING',
      contactId: 3,
      appliedOn: '2026-08-12',
    });
    expect(await screen.findByText('Initech added')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    // the list reloads after a save
    await waitFor(() => expect(listCalls).toBeGreaterThanOrEqual(2));
  });

  it('shows the server field errors inside the dialog', async () => {
    const user = userEvent.setup();
    renderApp('/applications', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
      'GET /api/contacts': () => jsonResponse([]),
      'POST /api/applications': () =>
        jsonResponse({ error: 'validation', fields: { companyName: 'at most 90 characters' } }, 400),
    });

    await screen.findByRole('link', { name: 'Acme Corp' });
    await user.click(screen.getByRole('button', { name: 'Add application' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Company'), 'x');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(await within(dialog).findByText('at most 90 characters')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('redirects to login when the session is gone', async () => {
    renderApp('/applications');
    expect(await screen.findByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Applications' })).not.toBeInTheDocument();
  });
});
