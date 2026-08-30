import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { login } from '../api/client';
import { useAuth } from '../auth/AuthContext';

/**
 * Step 2: submit the 6-digit code. On success the server hands back the
 * authenticated user; we store it in the auth context and land on the
 * dashboard. Arriving here without an email (deep link, refresh) sends
 * you back to step 1.
 */
export default function LoginCodePage() {
  const location = useLocation();
  const email = (location.state as { email?: string } | null)?.email;
  const [code, setCode] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { setMe } = useAuth();
  const navigate = useNavigate();

  if (!email) {
    return <Navigate to="/login" replace />;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const me = await login(email!, code);
      setMe(me);
      navigate('/dashboard', { replace: true });
    } catch {
      setError("That code didn't work. Check it or request a new one.");
      setSubmitting(false);
    }
  }

  return (
    <main className="card narrow">
      <h1>Check your email</h1>
      <p className="muted">
        We sent a 6-digit code to <strong>{email}</strong>. It expires in 10 minutes.
      </p>
      <form onSubmit={onSubmit}>
        <label htmlFor="code">Login code</label>
        <input
          id="code"
          inputMode="numeric"
          pattern="[0-9]{6}"
          maxLength={6}
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="123456"
          required
          autoFocus
        />
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Checking…' : 'Log in'}
        </button>
      </form>
      <p className="muted">
        <a href="/login">Use a different email</a>
      </p>
    </main>
  );
}
