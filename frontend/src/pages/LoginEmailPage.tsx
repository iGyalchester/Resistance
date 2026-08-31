import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { requestLoginCode } from '../api/client';

/**
 * Step 1 of the passwordless login: enter an email, get a one-time code.
 * The server answers identically whether or not the address is known
 * (no account enumeration), so this page always moves on to step 2.
 */
export default function LoginEmailPage() {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await requestLoginCode(email);
      navigate('/login/code', { state: { email: email.trim() } });
    } catch {
      setError('Something went wrong. Try again in a moment.');
      setSubmitting(false);
    }
  }

  return (
    <main className="card narrow">
      <h1>Resistance</h1>
      <p className="muted">Job application tracker — no password, just your email.</p>
      <form onSubmit={onSubmit}>
        <label htmlFor="email">Email address</label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
          required
          autoFocus
        />
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Sending…' : 'Send me a login code'}
        </button>
      </form>
    </main>
  );
}
