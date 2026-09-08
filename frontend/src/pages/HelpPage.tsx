import { Link } from 'react-router-dom';
import { fetchHelp } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ErrorBanner from '../components/ErrorBanner';
import { useAsync } from '../hooks/useAsync';

/**
 * The FAQ, straight from /api/help - the same entries the assistant
 * reads, so the two never disagree. Each answer links to the assistant
 * with the question prefilled when the feature is on.
 */
export default function HelpPage() {
  const { me } = useAuth();
  const { data, error, loading, reload } = useAsync(fetchHelp, []);
  const assistant = me?.features?.assistant === true;

  return (
    <>
      <h1>Help</h1>
      {error && <ErrorBanner message={error} onRetry={reload} />}
      {!error && loading && <p className="muted">Loading…</p>}
      {data && (
        <div className="faq">
          {data.map((entry) => (
            <details key={entry.id} className="card faq-entry" id={entry.id}>
              <summary>{entry.question}</summary>
              <p>{entry.answer}</p>
              {assistant && (
                <Link to={`/assistant?q=${encodeURIComponent(entry.question)}`} className="small">
                  Ask the assistant about this
                </Link>
              )}
            </details>
          ))}
        </div>
      )}
    </>
  );
}
