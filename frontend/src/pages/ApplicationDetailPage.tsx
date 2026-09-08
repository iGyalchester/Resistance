import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { ApiError, deleteApplication, fetchApplication, fetchContacts } from '../api/client';
import type { ContactView } from '../api/types';
import ConfirmDialog from '../components/ConfirmDialog';
import ErrorBanner from '../components/ErrorBanner';
import ApplicationFormDialog from '../components/forms/ApplicationFormDialog';
import StatusBadge from '../components/StatusBadge';
import { label } from '../components/StatusSelect';
import { useToast } from '../components/Toast';
import { useAsync } from '../hooks/useAsync';
import { formatDate, formatDateTime, relativeDays } from '../util/format';

/**
 * One application: its fields, its contact and the full status timeline
 * (every change, whether an email or a person made it). Edit and delete
 * live here; a 404 - which is also what someone else's id looks like -
 * sends you back to the list.
 */
export default function ApplicationDetailPage() {
  const { id } = useParams();
  const applicationId = Number(id);
  const navigate = useNavigate();
  const { notify } = useToast();
  const [notFound, setNotFound] = useState(false);
  const { data: app, error, loading, reload } = useAsync(async () => {
    try {
      return await fetchApplication(applicationId);
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setNotFound(true);
        return null;
      }
      throw e;
    }
  }, [applicationId]);
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [contacts, setContacts] = useState<ContactView[] | null>(null);

  if (notFound) {
    return <Navigate to="/applications" replace />;
  }

  async function openEdit() {
    if (contacts === null) {
      try {
        setContacts(await fetchContacts());
      } catch {
        setContacts([]);
      }
    }
    setEditing(true);
  }

  async function remove() {
    await deleteApplication(applicationId);
    notify(`${app?.companyName} deleted`);
    navigate('/applications', { replace: true });
  }

  return (
    <>
      <p>
        <Link to="/applications" className="muted">
          ← All applications
        </Link>
      </p>
      {error && <ErrorBanner message={error} onRetry={reload} />}
      {!error && loading && <p className="muted">Loading…</p>}
      {app && (
        <>
          <div className="page-title">
            <div>
              <h1>{app.companyName}</h1>
              <p className="muted">{app.positionTitle ?? 'Position not recorded'}</p>
            </div>
            <div className="actions">
              <button type="button" className="btn" onClick={openEdit}>
                Edit
              </button>
              <button type="button" className="btn btn-danger" onClick={() => setDeleting(true)}>
                Delete
              </button>
            </div>
          </div>

          <div className="grid-2">
            <section className="card">
              <h2>Details</h2>
              <dl className="details">
                <dt>Status</dt>
                <dd>
                  <StatusBadge status={app.status} />
                </dd>
                <dt>Applied</dt>
                <dd>{formatDate(app.appliedOn)}</dd>
                <dt>Last change</dt>
                <dd>
                  {formatDateTime(app.updatedAt)}{' '}
                  <span className="muted">{app.updatedAt ? `(${relativeDays(app.updatedAt)})` : ''}</span>
                </dd>
                <dt>Contact</dt>
                <dd>{app.contactName ? <Link to="/contacts">{app.contactName}</Link> : '—'}</dd>
              </dl>
            </section>

            <section className="card">
              <h2>Timeline</h2>
              {app.history.length === 0 && <p className="muted">No changes recorded yet.</p>}
              <ol className="timeline">
                {[...app.history].reverse().map((h, i) => (
                  <li key={i}>
                    <div className="timeline-dot" aria-hidden="true" />
                    <div>
                      <div>
                        {h.fromStatus ? (
                          <>
                            {label(h.fromStatus)} → <strong>{label(h.toStatus)}</strong>
                          </>
                        ) : (
                          <>
                            Tracked as <strong>{label(h.toStatus)}</strong>
                          </>
                        )}{' '}
                        <span className={`source source-${h.source.toLowerCase()}`}>
                          {h.source === 'INTAKE' ? 'from email' : 'by you'}
                        </span>
                      </div>
                      <div className="muted small">
                        {formatDateTime(h.changedAt)} · {relativeDays(h.changedAt)}
                      </div>
                    </div>
                  </li>
                ))}
              </ol>
            </section>
          </div>
        </>
      )}

      {editing && app && (
        <ApplicationFormDialog
          initial={app}
          contacts={contacts ?? []}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false);
            notify('Saved');
            reload();
          }}
        />
      )}
      {deleting && app && (
        <ConfirmDialog
          title="Delete application?"
          message={`${app.companyName} and its timeline will be removed. This cannot be undone.`}
          onConfirm={remove}
          onClose={() => setDeleting(false)}
        />
      )}
    </>
  );
}
