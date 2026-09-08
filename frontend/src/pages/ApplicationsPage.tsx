import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchApplications, fetchContacts, updateApplication } from '../api/client';
import { STATUSES, type ApplicationStatus, type ApplicationView, type ContactView } from '../api/types';
import EmptyState from '../components/EmptyState';
import ErrorBanner from '../components/ErrorBanner';
import ApplicationFormDialog from '../components/forms/ApplicationFormDialog';
import StatusSelect, { label } from '../components/StatusSelect';
import { useToast } from '../components/Toast';
import { useAsync } from '../hooks/useAsync';
import { formatDate } from '../util/format';

type SortKey = 'companyName' | 'status' | 'appliedOn';

/**
 * Every application, with search, a status filter, sortable columns and
 * an in-place status dropdown. A status change is applied optimistically
 * and rolled back (with a toast) if the server refuses it.
 */
export default function ApplicationsPage() {
  const { data, error, loading, reload, setData } = useAsync(fetchApplications);
  const { notify } = useToast();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<string>('');
  const [sort, setSort] = useState<{ key: SortKey; dir: 1 | -1 }>({ key: 'companyName', dir: 1 });
  const [adding, setAdding] = useState(false);
  const [contacts, setContacts] = useState<ContactView[] | null>(null);

  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const list = (data ?? []).filter(
      (a) =>
        (!status || a.status === status) &&
        (!needle ||
          a.companyName.toLowerCase().includes(needle) ||
          (a.positionTitle ?? '').toLowerCase().includes(needle)),
    );
    return [...list].sort((a, b) => {
      const av = a[sort.key] ?? '';
      const bv = b[sort.key] ?? '';
      return String(av).localeCompare(String(bv)) * sort.dir;
    });
  }, [data, q, status, sort]);

  function toggleSort(key: SortKey) {
    setSort((s) => (s.key === key ? { key, dir: s.dir === 1 ? -1 : 1 } : { key, dir: 1 }));
  }

  async function changeStatus(app: ApplicationView, next: ApplicationStatus) {
    const previous = data ?? [];
    setData(previous.map((a) => (a.id === app.id ? { ...a, status: next } : a)));
    try {
      await updateApplication(app.id, {
        companyName: app.companyName,
        positionTitle: app.positionTitle ?? null,
        status: next,
        contactId: app.contactId ?? null,
      });
      notify(`${app.companyName} moved to ${label(next)}`);
    } catch {
      setData(previous);
      notify(`Could not update ${app.companyName}. Try again.`, 'error');
    }
  }

  async function openAdd() {
    if (contacts === null) {
      try {
        setContacts(await fetchContacts());
      } catch {
        setContacts([]);
      }
    }
    setAdding(true);
  }

  return (
    <>
      <div className="page-title">
        <h1>Applications</h1>
        <button type="button" className="btn btn-primary" onClick={openAdd}>
          Add application
        </button>
      </div>

      <div className="toolbar">
        <input
          type="search"
          placeholder="Search company or position"
          aria-label="Search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <div className="chips" role="group" aria-label="Filter by status">
          <button type="button" className={`chip${status === '' ? ' active' : ''}`} onClick={() => setStatus('')}>
            All
          </button>
          {STATUSES.map((s) => (
            <button
              type="button"
              key={s}
              className={`chip${status === s ? ' active' : ''}`}
              onClick={() => setStatus(status === s ? '' : s)}
            >
              {label(s)}
            </button>
          ))}
        </div>
      </div>

      <section className="card">
        {error && <ErrorBanner message={error} onRetry={reload} />}
        {!error && loading && <p className="muted">Loading…</p>}
        {data !== null && data.length === 0 && (
          <EmptyState>Nothing tracked yet. Forward a confirmation email, or add one by hand.</EmptyState>
        )}
        {data !== null && data.length > 0 && rows.length === 0 && (
          <EmptyState>No applications match that filter.</EmptyState>
        )}
        {rows.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>
                  <button type="button" className="sort" onClick={() => toggleSort('companyName')}>
                    Company
                  </button>
                </th>
                <th>Position</th>
                <th>
                  <button type="button" className="sort" onClick={() => toggleSort('status')}>
                    Status
                  </button>
                </th>
                <th>
                  <button type="button" className="sort" onClick={() => toggleSort('appliedOn')}>
                    Applied
                  </button>
                </th>
                <th>Contact</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((app) => (
                <tr key={app.id}>
                  <td>
                    <Link to={`/applications/${app.id}`}>{app.companyName}</Link>
                  </td>
                  <td>{app.positionTitle ?? '—'}</td>
                  <td>
                    <StatusSelect
                      className={`select select-inline badge-${(app.status ?? 'unknown').toLowerCase()}`}
                      value={app.status}
                      onChange={(next) => changeStatus(app, next)}
                      aria-label={`Status for ${app.companyName}`}
                    />
                  </td>
                  <td>{formatDate(app.appliedOn)}</td>
                  <td>{app.contactName ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {adding && (
        <ApplicationFormDialog
          contacts={contacts ?? []}
          onClose={() => setAdding(false)}
          onSaved={(saved) => {
            setAdding(false);
            notify(`${saved.companyName} added`);
            reload();
          }}
        />
      )}
    </>
  );
}
