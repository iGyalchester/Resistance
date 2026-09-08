import { useState } from 'react';
import { deleteContact, fetchContacts } from '../api/client';
import type { ContactView } from '../api/types';
import ConfirmDialog from '../components/ConfirmDialog';
import EmptyState from '../components/EmptyState';
import ErrorBanner from '../components/ErrorBanner';
import ContactFormDialog from '../components/forms/ContactFormDialog';
import { useToast } from '../components/Toast';
import { useAsync } from '../hooks/useAsync';
import { fullName } from '../util/format';

/**
 * Your address book: recruiters and referrals, each with how many of
 * your applications point at them. Deleting a contact keeps the
 * applications; they just lose the link.
 */
export default function ContactsPage() {
  const { data, error, loading, reload } = useAsync(fetchContacts);
  const { notify } = useToast();
  const [editing, setEditing] = useState<ContactView | null | 'new'>(null);
  const [deleting, setDeleting] = useState<ContactView | null>(null);

  async function remove(contact: ContactView) {
    await deleteContact(contact.id);
    setDeleting(null);
    notify(`${fullName(contact.firstName, contact.lastName)} deleted`);
    reload();
  }

  return (
    <>
      <div className="page-title">
        <h1>Contacts</h1>
        <button type="button" className="btn btn-primary" onClick={() => setEditing('new')}>
          Add contact
        </button>
      </div>

      <section className="card">
        {error && <ErrorBanner message={error} onRetry={reload} />}
        {!error && loading && <p className="muted">Loading…</p>}
        {data !== null && data.length === 0 && (
          <EmptyState>No contacts yet. Intake adds recruiters automatically when an email names one.</EmptyState>
        )}
        {data !== null && data.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Applications</th>
                <th>
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.map((c) => (
                <tr key={c.id}>
                  <td>{fullName(c.firstName, c.lastName)}</td>
                  <td>{c.email ? <a href={`mailto:${c.email}`}>{c.email}</a> : '—'}</td>
                  <td>{c.applicationCount}</td>
                  <td className="row-actions">
                    <button type="button" className="btn btn-ghost" onClick={() => setEditing(c)}>
                      Edit
                    </button>
                    <button type="button" className="btn btn-ghost danger" onClick={() => setDeleting(c)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {editing && (
        <ContactFormDialog
          initial={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(saved) => {
            setEditing(null);
            notify(`${fullName(saved.firstName, saved.lastName)} saved`);
            reload();
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete contact?"
          message={`${fullName(deleting.firstName, deleting.lastName)} will be removed. Applications that reference them keep their row.`}
          onConfirm={() => remove(deleting)}
          onClose={() => setDeleting(null)}
        />
      )}
    </>
  );
}
