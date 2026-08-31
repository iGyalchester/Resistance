import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { fetchMe } from '../api/client';
import type { Me } from '../api/types';

/**
 * Who is logged in, app-wide. On first load we ask the server
 * (GET /api/auth/me - the session cookie decides); until that answers,
 * `loading` keeps guarded pages from flashing a redirect.
 */
interface AuthState {
  me: Me | null;
  loading: boolean;
  setMe: (me: Me | null) => void;
}

const AuthContext = createContext<AuthState>({ me: null, loading: true, setMe: () => {} });

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchMe()
      .then(setMe)
      .catch(() => setMe(null))
      .finally(() => setLoading(false));
  }, []);

  return <AuthContext.Provider value={{ me, loading, setMe }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  return useContext(AuthContext);
}

/** Route guard: anonymous visitors are sent to the login page. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth();
  if (loading) {
    return <p className="muted">Loading…</p>;
  }
  if (!me) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}
