import { useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchApplications } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ErrorBanner from '../components/ErrorBanner';
import StatusBadge from '../components/StatusBadge';
import { useAsync } from '../hooks/useAsync';
import { formatDate } from '../util/format';

/**
 * The landing view: your personal intake address (forward confirmation
 * emails there and rows appear here) and your applications at a glance.
 * The server only ever returns the session owner's rows - this page just
 * displays. Charts and metrics arrive in the next slice.
 */
export default function DashboardPage() {
  const { me } = useAuth();
  const { data: applications, error, loading, reload } = useAsync(fetchApplications);
  const [copied, setCopied] = useState(false);

  async function copyIntakeAddress() {
    if (me?.intakeAddress) {
      await navigator.clipboard.writeText(me.intakeAddress);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }

  return (
    <>
      <h1>Dashboard</h1>

      {me?.intakeAddress && (
        <section className="card intake">
          <h2>Your intake address</h2>
          <p className="muted">
            Forward "we received your application" emails here — they show up below automatically.
          </p>
          <p>
            <code>{me.intakeAddress}</code>{' '}
            <button className="link" onClick={copyIntakeAddress}>
              {copied ? 'Copied!' : 'Copy'}
            </button>
          </p>
        </section>
      )}

      <section className="card">
        <div className="card-title">
          <h2>Applications</h2>
          <Link to="/applications" className="btn btn-ghost">
            Manage
          </Link>
        </div>
        {error && <ErrorBanner message={error} onRetry={reload} />}
        {!error && loading && <p className="muted">Loading…</p>}
        {applications !== null && applications.length === 0 && (
          <p className="muted">Nothing tracked yet. Forward a confirmation email to get started.</p>
        )}
        {applications !== null && applications.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Company</th>
                <th>Position</th>
                <th>Status</th>
                <th>Applied</th>
                <th>Contact</th>
              </tr>
            </thead>
            <tbody>
              {applications.map((app) => (
                <tr key={app.id}>
                  <td>
                    <Link to={`/applications/${app.id}`}>{app.companyName}</Link>
                  </td>
                  <td>{app.positionTitle ?? '—'}</td>
                  <td>
                    <StatusBadge status={app.status} />
                  </td>
                  <td>{formatDate(app.appliedOn)}</td>
                  <td>{app.contactName ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
