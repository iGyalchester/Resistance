import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ACME_DETAIL, APPLICATIONS, BORIS, CONTACTS, bodyOf, jsonResponse, noContent, renderApp } from './helpers';

describe('application detail page', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the fields and the timeline newest first with who made each change', async () => {
    renderApp('/applications/1', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications/1': () => jsonResponse(ACME_DETAIL),
    });

    expect(await screen.findByRole('heading', { name: 'Acme Corp' })).toBeInTheDocument();
    expect(screen.getByText('Backend Engineer')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dana Reyes' })).toBeInTheDocument();

    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Applied → Interview');
    expect(items[0]).toHaveTextContent('by you');
    expect(items[1]).toHaveTextContent('Tracked as Applied');
    expect(items[1]).toHaveTextContent('from email');
  });

  it('deletes after confirmation and returns to the list', async () => {
    const user = userEvent.setup();
    let deleted = false;
    renderApp('/applications/1', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications/1': () => jsonResponse(ACME_DETAIL),
      'DELETE /api/applications/1': () => {
        deleted = true;
        return noContent();
      },
      'GET /api/applications': () => jsonResponse(APPLICATIONS.slice(1)),
    });

    await screen.findByRole('heading', { name: 'Acme Corp' });
    await user.click(screen.getByRole('button', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete application?' });
    expect(deleted).toBe(false);
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(deleted).toBe(true));
    expect(await screen.findByRole('heading', { name: 'Applications' })).toBeInTheDocument();
    expect(await screen.findByText('Acme Corp deleted')).toBeInTheDocument();
  });

  it('edits through the dialog and reloads', async () => {
    const user = userEvent.setup();
    let sent: unknown = null;
    renderApp('/applications/1', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications/1': () =>
        jsonResponse(sent ? { ...ACME_DETAIL, positionTitle: 'Staff Engineer' } : ACME_DETAIL),
      'GET /api/contacts': () => jsonResponse(CONTACTS),
      'PUT /api/applications/1': (init) => {
        sent = bodyOf(init);
        return jsonResponse({ ...ACME_DETAIL, positionTitle: 'Staff Engineer' });
      },
    });

    await screen.findByRole('heading', { name: 'Acme Corp' });
    await user.click(screen.getByRole('button', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit application' });
    expect(within(dialog).queryByLabelText('Applied on')).not.toBeInTheDocument();
    await user.clear(within(dialog).getByLabelText('Position'));
    await user.type(within(dialog).getByLabelText('Position'), 'Staff Engineer');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent).not.toBeNull());
    expect(sent).toMatchObject({ companyName: 'Acme Corp', positionTitle: 'Staff Engineer', contactId: 3 });
    expect(await screen.findByText('Staff Engineer')).toBeInTheDocument();
  });

  it("sends you back to the list when the id is not yours", async () => {
    renderApp('/applications/42', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/applications/42': () => jsonResponse({ error: 'not_found' }, 404),
      'GET /api/applications': () => jsonResponse(APPLICATIONS),
    });

    expect(await screen.findByRole('heading', { name: 'Applications' })).toBeInTheDocument();
  });
});
