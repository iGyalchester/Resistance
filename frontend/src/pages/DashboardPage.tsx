import { useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchAnalytics, updateApplication } from '../api/client';
import type { StaleApplication } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import ErrorBanner from '../components/ErrorBanner';
import StatCard from '../components/StatCard';
import StatusBadge from '../components/StatusBadge';
import { label } from '../components/StatusSelect';
import { useToast } from '../components/Toast';
import StatusFunnelChart from '../charts/StatusFunnelChart';
import TimeInStageChart from '../charts/TimeInStageChart';
import WeeklyApplicationsChart from '../charts/WeeklyApplicationsChart';
import { useAsync } from '../hooks/useAsync';
import { formatDateTime, relativeDays } from '../util/format';

function percent(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${Math.round(value * 100)}%`;
}

function daysText(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${value} d`;
}

/**
 * The landing view: your intake address, the four numbers that matter,
 * three charts, the applications that have gone quiet, and what changed
 * recently. Everything comes from one call to /api/analytics/summary,
 * computed server-side for the session account only.
 */
export default function DashboardPage() {
  const { me } = useAuth();
  const { notify } = useToast();
  const { data, error, loading, reload, setData } = useAsync(fetchAnalytics);
  const [copied, setCopied] = useState(false);

  async function copyIntakeAddress() {
    if (me?.intakeAddress) {
      await navigator.clipboard.writeText(me.intakeAddress);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }

  async function withdraw(app: StaleApplication) {
    if (!data) return;
    setData({ ...data, stale: data.stale.filter((s) => s.id !== app.id) });
    try {
      await updateApplication(app.id, {
        companyName: app.companyName,
        positionTitle: app.positionTitle ?? null,
        status: 'WITHDRAWN',
        contactId: app.contactId ?? null,
      });
      notify(`${app.companyName} marked withdrawn`);
      reload();
    } catch {
      setData(data);
      notify(`Could not update ${app.companyName}. Try again.`, 'error');
    }
  }

  return (
    <>
      <h1>Dashboard</h1>

      {me?.intakeAddress && (
        <section className="card intake">
          <h2>Your intake address</h2>
          <p className="muted">
            Forward "we received your application" emails here — they show up automatically.
          </p>
          <p>
            <code>{me.intakeAddress}</code>{' '}
            <button className="link" onClick={copyIntakeAddress}>
              {copied ? 'Copied!' : 'Copy'}
            </button>
          </p>
        </section>
      )}

      {error && <ErrorBanner message={error} onRetry={reload} />}
      {!error && loading && <p className="muted">Loading…</p>}

      {data && data.total === 0 && (
        <section className="card">
          <p className="muted">Nothing tracked yet. Forward a confirmation email to get started, or</p>
          <Link to="/applications" className="btn btn-primary">
            add an application by hand
          </Link>
        </section>
      )}

      {data && data.total > 0 && (
        <>
          <div className="stats">
            <StatCard label="Active" value={String(data.active)} hint={`of ${data.total} tracked`} />
            <StatCard label="Response rate" value={percent(data.responseRate)} hint="heard back at all" />
            <StatCard label="Offers" value={percent(data.offerRate)} hint="reached an offer" />
            <StatCard
              label="First response"
              value={daysText(data.medianDaysToFirstResponse)}
              hint="median wait for the first reply"
            />
          </div>

          <StatusFunnelChart counts={data.countsByStatus} />

          <div className="grid-2">
            <WeeklyApplicationsChart weeks={data.weeklyApplications} />
            <TimeInStageChart medians={data.medianDaysInStage} />
          </div>

          <div className="grid-2">
            <section className="card">
              <h2>Needs attention</h2>
              <p className="muted small">Waiting, and nothing has happened for two weeks or more.</p>
              {data.stale.length === 0 && <p className="muted">Nothing is going quiet. Nice.</p>}
              {data.stale.length > 0 && (
                <ul className="plain-list">
                  {data.stale.map((s) => (
                    <li key={s.id} className="attention-row">
                      <div>
                        <Link to={`/applications/${s.id}`}>{s.companyName}</Link>{' '}
                        <span className="muted">{s.positionTitle ?? ''}</span>
                        <div className="muted small">
                          <StatusBadge status={s.status} /> · quiet for {s.daysSinceChange} days
                        </div>
                      </div>
                      <button type="button" className="btn btn-ghost" onClick={() => withdraw(s)}>
                        Mark withdrawn
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="card">
              <h2>Recent activity</h2>
              {data.recentActivity.length === 0 && <p className="muted">No changes yet.</p>}
              <ul className="plain-list">
                {data.recentActivity.map((a, i) => (
                  <li key={i} className="activity-row">
                    <div>
                      <Link to={`/applications/${a.applicationId}`}>{a.companyName}</Link>{' '}
                      {a.fromStatus ? (
                        <>
                          {label(a.fromStatus)} → <strong>{label(a.toStatus)}</strong>
                        </>
                      ) : (
                        <>
                          tracked as <strong>{label(a.toStatus)}</strong>
                        </>
                      )}
                    </div>
                    <div className="muted small">
                      {formatDateTime(a.changedAt)} · {relativeDays(a.changedAt)} ·{' '}
                      {a.source === 'INTAKE' ? 'from email' : 'by you'}
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          </div>
        </>
      )}
    </>
  );
}
