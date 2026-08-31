import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import App from '../App';
import { AuthProvider } from '../auth/AuthContext';
import type { ApplicationView, Me } from '../api/types';

export const BORIS: Me = {
  fullName: 'Boris Gerard',
  email: 'boris@gmail.com',
  intakeAddress: 'track+boris2k4mp9@resistance.example',
};

export const APPLICATIONS: ApplicationView[] = [
  {
    id: 1,
    companyName: 'Acme Corp',
    positionTitle: 'Backend Engineer',
    status: 'INTERVIEW',
    appliedOn: '2026-08-01',
    contactName: 'Dana Reyes',
  },
  {
    id: 2,
    companyName: 'Globex',
    positionTitle: null,
    status: 'APPLIED',
    appliedOn: null,
    contactName: null,
  },
];

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/**
 * Stubs fetch with a per-endpoint response table ("METHOD path" keys)
 * and renders the app at the given route. Anything not in the table
 * answers 401 - the same as an anonymous session against the real API.
 */
export function renderApp(path: string, routes: Record<string, () => Response> = {}) {
  const fetchMock = vi.fn(async (url: unknown, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(url)}`;
    const handler = routes[key];
    return handler ? handler() : jsonResponse({ error: 'unauthenticated' }, 401);
  });
  vi.stubGlobal('fetch', fetchMock);

  const view = render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
  return { view, fetchMock, jsonResponse };
}

export { jsonResponse };
