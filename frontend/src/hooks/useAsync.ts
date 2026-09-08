import { useCallback, useEffect, useState } from 'react';
import { ApiError, UnauthorizedError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

interface AsyncState<T> {
  data: T | null;
  error: string | null;
  /** HTTP status of a failed request, when the server answered at all (403 matters to the admin page). */
  status: number | null;
  loading: boolean;
}

/**
 * Load-on-mount for a page: runs the fetch, exposes data/error/loading,
 * a reload() for after a mutation and setData() for optimistic updates.
 * A 401 anywhere logs the user out (RequireAuth then redirects); any
 * other failure becomes a message the page can show.
 */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[] = []) {
  const [state, setState] = useState<AsyncState<T>>({ data: null, error: null, status: null, loading: true });
  const [tick, setTick] = useState(0);
  const { setMe } = useAuth();

  useEffect(() => {
    let alive = true;
    setState((s) => ({ ...s, loading: true, error: null }));
    load()
      .then((data) => alive && setState({ data, error: null, status: null, loading: false }))
      .catch((e) => {
        if (!alive) return;
        if (e instanceof UnauthorizedError) {
          setMe(null);
        } else {
          setState({
            data: null,
            error: 'Could not load this page. Refresh to try again.',
            status: e instanceof ApiError ? e.status : null,
            loading: false,
          });
        }
      });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick, setMe]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  // an updater form, like useState's, so a rollback can touch one row
  // without clobbering another change that landed in the meantime
  const setData = useCallback(
    (update: T | null | ((current: T | null) => T | null)) =>
      setState((s) => ({ ...s, data: typeof update === 'function' ? (update as (c: T | null) => T | null)(s.data) : update })),
    [],
  );

  return { ...state, reload, setData };
}
