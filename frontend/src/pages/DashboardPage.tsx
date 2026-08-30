import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchApplications, logout, UnauthorizedError } from '../api/client';
import type { ApplicationView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import StatusBadge from '../components/StatusBadge';

/**
 * The logged-in view: your personal intake address (forward confirmation
 * emails there and rows appear here) and your applications. The server
 * only ever returns the session owner's rows - this page just displays.
 */
export default function DashboardPage() {
  const { me, setMe } = useAuth();
  const [applications, setApplications] = useState<ApplicationView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    fetchApplications()
      .then(setApplications)
      .catch((e) => {
        if (e instanceof UnauthorizedError) {
          setMe(null);
        } else {
          setError('Could not load your applications. Refresh to try again.');
        }
      });
  }, [setMe]);

  async function onLogout() {
    try {
      await logout();
    } finally {
      setMe(null);
      navigate('/login', { replace: true });
    }
  }

  async function copyIntakeAddress() {
    if (me?.intakeAddress) {
      await navigator.clipboard.writeText(me.intakeAddress);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }

  return (
    <main className="page">
      <header className="topbar">
        <h1>Resistance</h1>
        <div className="topbar-right">
          <span className="muted">{me?.fullName}</span>
          <button className="link" onClick={onLogout}>
            Log out
          </button>
        </div>
      </header>

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
        <h2>Applications</h2>
        {error && <p className="error">{error}</p>}
        {!error && applications === null && <p className="muted">Loading…</p>}
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
                  <td>{app.companyName}</td>
                  <td>{app.positionTitle ?? '—'}</td>
                  <td>
                    <StatusBadge status={app.status} />
                  </td>
                  <td>{app.appliedOn ?? '—'}</td>
                  <td>{app.contactName ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </main>
  );
}
