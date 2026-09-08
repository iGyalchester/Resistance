import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import App from '../App';
import { AuthProvider } from '../auth/AuthContext';
import type { ApplicationDetailView, ApplicationView, ContactView, Me, ProfileView } from '../api/types';

export const BORIS: Me = {
  fullName: 'Boris Gerard',
  email: 'boris@gmail.com',
  intakeAddress: 'track+boris2k4mp9@resistance.example',
  roles: ['USER'],
  features: { assistant: false },
};

export const ADMIN_BORIS: Me = { ...BORIS, roles: ['USER', 'ADMIN'] };

export const APPLICATIONS: ApplicationView[] = [
  {
    id: 1,
    companyName: 'Acme Corp',
    positionTitle: 'Backend Engineer',
    status: 'INTERVIEW',
    appliedOn: '2026-08-01',
    contactId: 3,
    contactName: 'Dana Reyes',
  },
  {
    id: 2,
    companyName: 'Globex',
    positionTitle: null,
    status: 'APPLIED',
    appliedOn: null,
    contactId: null,
    contactName: null,
  },
];

export const ACME_DETAIL: ApplicationDetailView = {
  ...APPLICATIONS[0],
  updatedAt: '2026-08-26T11:00:00Z',
  history: [
    { fromStatus: null, toStatus: 'APPLIED', changedAt: '2026-08-01T09:00:00Z', source: 'INTAKE' },
    { fromStatus: 'APPLIED', toStatus: 'INTERVIEW', changedAt: '2026-08-26T11:00:00Z', source: 'MANUAL' },
  ],
};

export const CONTACTS: ContactView[] = [
  { id: 3, firstName: 'Dana', lastName: 'Reyes', email: 'dana.reyes@acme.example', applicationCount: 1 },
  { id: 4, firstName: 'Marcus', lastName: 'Lee', email: null, applicationCount: 0 },
];

export const PROFILE: ProfileView = { fullName: 'Boris Gerard', email: 'boris@gmail.com', phone: '+1 555 0100' };

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function noContent(): Response {
  return new Response(null, { status: 204 });
}

/**
 * Stubs fetch with a per-endpoint response table ("METHOD path" keys)
 * and renders the app at the given route. Anything not in the table
 * answers 401 - the same as an anonymous session against the real API.
 * Handlers receive the request init so a test can assert on the body.
 */
export function renderApp(
  path: string,
  routes: Record<string, (init?: RequestInit) => Response> = {},
) {
  const fetchMock = vi.fn(async (url: unknown, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(url)}`;
    const handler = routes[key];
    return handler ? handler(init) : jsonResponse({ error: 'unauthenticated' }, 401);
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

/** The JSON body a stubbed handler was called with. */
export function bodyOf(init?: RequestInit): unknown {
  return init?.body ? JSON.parse(String(init.body)) : undefined;
}

export { jsonResponse };
