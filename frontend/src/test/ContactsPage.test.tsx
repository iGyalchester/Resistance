import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { BORIS, CONTACTS, bodyOf, jsonResponse, noContent, renderApp } from './helpers';

describe('contacts page', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lists contacts with their application counts', async () => {
    renderApp('/contacts', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/contacts': () => jsonResponse(CONTACTS),
    });

    expect(await screen.findByText('Dana Reyes')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'dana.reyes@acme.example' })).toBeInTheDocument();
    const rows = screen.getAllByRole('row').slice(1);
    expect(rows[0]).toHaveTextContent('1');
    expect(rows[1]).toHaveTextContent('Marcus Lee');
    expect(rows[1]).toHaveTextContent('—');
  });

  it('adds a contact and reloads', async () => {
    const user = userEvent.setup();
    let sent: unknown = null;
    renderApp('/contacts', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/contacts': () =>
        jsonResponse(sent ? [...CONTACTS, { id: 9, firstName: 'Sam', lastName: null, email: null, applicationCount: 0 }] : CONTACTS),
      'POST /api/contacts': (init) => {
        sent = bodyOf(init);
        return jsonResponse({ id: 9, firstName: 'Sam', lastName: null, email: null, applicationCount: 0 }, 201);
      },
    });

    await screen.findByText('Dana Reyes');
    await user.click(screen.getByRole('button', { name: 'Add contact' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add contact' });
    await user.type(within(dialog).getByLabelText('First name'), 'Sam');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(sent).toEqual({ firstName: 'Sam', lastName: null, email: null }));
    expect(await screen.findByText('Sam saved')).toBeInTheDocument();
    expect(await screen.findByText('Sam')).toBeInTheDocument();
  });

  it('edits a contact with its current values prefilled', async () => {
    const user = userEvent.setup();
    let sent: unknown = null;
    renderApp('/contacts', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/contacts': () => jsonResponse(CONTACTS),
      'PUT /api/contacts/3': (init) => {
        sent = bodyOf(init);
        return jsonResponse({ ...CONTACTS[0], lastName: 'Reyes-Ortiz' });
      },
    });

    await screen.findByText('Dana Reyes');
    await user.click(screen.getAllByRole('button', { name: 'Edit' })[0]);
    const dialog = await screen.findByRole('dialog', { name: 'Edit contact' });
    expect(within(dialog).getByLabelText('First name')).toHaveValue('Dana');
    await user.clear(within(dialog).getByLabelText('Last name'));
    await user.type(within(dialog).getByLabelText('Last name'), 'Reyes-Ortiz');
    await user.click(within(dialog).getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(sent).toEqual({ firstName: 'Dana', lastName: 'Reyes-Ortiz', email: 'dana.reyes@acme.example' }),
    );
  });

  it('deletes only after confirmation', async () => {
    const user = userEvent.setup();
    let deleted = false;
    renderApp('/contacts', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
      'GET /api/contacts': () => jsonResponse(deleted ? CONTACTS.slice(1) : CONTACTS),
      'DELETE /api/contacts/3': () => {
        deleted = true;
        return noContent();
      },
    });

    await screen.findByText('Dana Reyes');
    await user.click(screen.getAllByRole('button', { name: 'Delete' })[0]);
    const dialog = await screen.findByRole('dialog', { name: 'Delete contact?' });
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(deleted).toBe(false);

    await user.click(screen.getAllByRole('button', { name: 'Delete' })[0]);
    const again = await screen.findByRole('dialog', { name: 'Delete contact?' });
    await user.click(within(again).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(deleted).toBe(true));
    await waitFor(() => expect(screen.queryByText('Dana Reyes')).not.toBeInTheDocument());
    expect(screen.getByText('Marcus Lee')).toBeInTheDocument();
  });
});
