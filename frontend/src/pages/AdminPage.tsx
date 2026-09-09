import { useMemo, useState } from 'react';
import { fetchAdminAccounts, fetchAdminOverview } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ActivityPerDayChart from '../charts/ActivityPerDayChart';
import ErrorBanner from '../components/ErrorBanner';
import StatCard from '../components/StatCard';
import { label } from '../components/StatusSelect';
import { useAsync } from '../hooks/useAsync';
import { formatDateTime } from '../util/format';
import { PRICE_PER_MILLION_TOKENS, estimateCost } from './admin/pricing';

function percent(value?: number | null): string {
  return value == null ? '—' : `${Math.round(value * 100)}%`;
}

/**
 * The ops view: is intake flowing, is parsing keeping up, what did the
 * login flow and the assistant do since the process started, and who has
 * an account. The server enforces the ADMIN role; the client-side guard
 * only saves a non-admin from a confusing page.
 */
export default function AdminPage() {
  const { me } = useAuth();
  const isAdmin = me?.roles?.includes('ADMIN') === true;
  // non-admins never call the endpoints: the guard below is the answer
  const overview = useAsync(() => (isAdmin ? fetchAdminOverview() : Promise.resolve(null)), [isAdmin]);
  const accounts = useAsync(() => (isAdmin ? fetchAdminAccounts() : Promise.resolve(null)), [isAdmin]);
  const [query, setQuery] = useState('');

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = accounts.data ?? [];
    return q ? list.filter((a) => a.email.toLowerCase().includes(q) || (a.fullName ?? '').toLowerCase().includes(q)) : list;
  }, [accounts.data, query]);

  if (!isAdmin || overview.status === 403 || accounts.status === 403) {
    return (
      <>
        <h1>Admin</h1>
        <section className="card">
          <p>You are not authorized to see this page.</p>
          <p className="muted small">Admins are the addresses listed in the tracker's admin configuration.</p>
        </section>
      </>
    );
  }

  const o = overview.data;
  const cost = o ? estimateCost(o.assistant.inputTokens, o.assistant.outputTokens) : 0;

  return (
    <>
      <h1>Admin</h1>
      {overview.error && <ErrorBanner message={overview.error} onRetry={overview.reload} />}
      {!overview.error && overview.loading && <p className="muted">Loading…</p>}

      {o && (
        <>
          <div className="stats">
            <StatCard label="Accounts" value={String(o.accounts)} hint="people with a tracker" />
            <StatCard label="Applications" value={String(o.applications)} hint="tracked across everyone" />
            <StatCard label="Untitled" value={percent(o.unparsedTitleShare)} hint="applications the parser found no title for" />
            <StatCard
              label="Active"
              value={String(
                (o.applicationsByStatus.APPLIED ?? 0) +
                  (o.applicationsByStatus.SCREENING ?? 0) +
                  (o.applicationsByStatus.INTERVIEW ?? 0) +
                  (o.applicationsByStatus.OFFER ?? 0),
              )}
              hint="still in play"
            />
          </div>

          <div className="grid-2">
            <ActivityPerDayChart intake={o.intakeEventsLast30Days} manual={o.manualEventsLast30Days} />
            <section className="card">
              <h2>Applications by status</h2>
              <table>
                <tbody>
                  {Object.entries(o.applicationsByStatus).map(([status, count]) => (
                    <tr key={status}>
                      <td>{label(status)}</td>
                      <td>{count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          </div>

          <div className="grid-2">
            <section className="card">
              <h2>Login flow</h2>
              <p className="muted small">Since this process started ({formatDateTime(o.countersSince)}); each instance counts its own.</p>
              <dl className="kv">
                <dt>Codes requested</dt>
                <dd>{o.auth.otpRequested}</dd>
                <dt>Requests throttled</dt>
                <dd>{o.auth.otpThrottled}</dd>
                <dt>Logins</dt>
                <dd>{o.auth.loginSuccess}</dd>
                <dt>Failed codes</dt>
                <dd>{o.auth.loginFailure}</dd>
              </dl>
            </section>
            <section className="card">
              <h2>Assistant</h2>
              <p className="muted small">Since this process started; resets on restart.</p>
              <dl className="kv">
                <dt>Messages</dt>
                <dd>{o.assistant.messages}</dd>
                <dt>Tokens in / out</dt>
                <dd>
                  {o.assistant.inputTokens.toLocaleString()} / {o.assistant.outputTokens.toLocaleString()}
                </dd>
                <dt>Refusals / errors / throttled</dt>
                <dd>
                  {o.assistant.refusals} / {o.assistant.errors} / {o.assistant.throttled}
                </dd>
                <dt>Estimated cost</dt>
                <dd>
                  ${cost.toFixed(2)}{' '}
                  <span className="muted small">
                    (at ${PRICE_PER_MILLION_TOKENS.input}/${PRICE_PER_MILLION_TOKENS.output} per million tokens in/out)
                  </span>
                </dd>
              </dl>
            </section>
          </div>
        </>
      )}

      <section className="card">
        <div className="toolbar">
          <h2>Accounts</h2>
          <input
            type="search"
            aria-label="Search accounts"
            placeholder="Search by email or name"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        {accounts.error && <ErrorBanner message={accounts.error} onRetry={accounts.reload} />}
        {accounts.data && (
          <table>
            <thead>
              <tr>
                <th>Email</th>
                <th>Name</th>
                <th>Applications</th>
                <th>Last activity</th>
                <th>Intake</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((a) => (
                <tr key={a.id}>
                  <td>{a.email}</td>
                  <td>{a.fullName ?? '—'}</td>
                  <td>{a.applicationCount}</td>
                  <td>{a.lastActivity ? formatDateTime(a.lastActivity) : '—'}</td>
                  <td>{a.hasAlias ? 'alias assigned' : 'no alias yet'}</td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">
                    No accounts match.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
