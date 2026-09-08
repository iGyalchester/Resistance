import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import App from '../App';
import { AuthProvider } from '../auth/AuthContext';
import type { AnalyticsView, ApplicationDetailView, ApplicationView, ContactView, Me, ProfileView } from '../api/types';

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

export const ANALYTICS: AnalyticsView = {
  total: 5,
  active: 4,
  countsByStatus: { APPLIED: 1, SCREENING: 1, INTERVIEW: 1, OFFER: 1, ACCEPTED: 0, REJECTED: 1, WITHDRAWN: 0 },
  responseRate: 0.8,
  offerRate: 0.2,
  medianDaysToFirstResponse: 15.5,
  medianDaysInStage: { APPLIED: 15.5 },
  weeklyApplications: Array.from({ length: 12 }, (_, i) => ({
    weekStart: `2026-0${i < 3 ? 6 : i < 8 ? 7 : 8}-${String(((i * 7) % 28) + 1).padStart(2, '0')}`,
    created: i === 11 ? 2 : 0,
  })),
  stale: [
    { id: 1, companyName: 'Acme Corp', positionTitle: 'Backend Engineer', status: 'APPLIED', contactId: 3, daysSinceChange: 19 },
  ],
  recentActivity: [
    { applicationId: 4, companyName: 'Umbrella Labs', fromStatus: 'APPLIED', toStatus: 'OFFER', changedAt: '2026-08-27T17:45:00Z', source: 'INTAKE' },
    { applicationId: 1, companyName: 'Acme Corp', fromStatus: null, toStatus: 'APPLIED', changedAt: '2026-08-20T09:00:00Z', source: 'INTAKE' },
  ],
};

export const EMPTY_ANALYTICS: AnalyticsView = {
  total: 0,
  active: 0,
  countsByStatus: { APPLIED: 0, SCREENING: 0, INTERVIEW: 0, OFFER: 0, ACCEPTED: 0, REJECTED: 0, WITHDRAWN: 0 },
  responseRate: null,
  offerRate: null,
  medianDaysToFirstResponse: null,
  medianDaysInStage: {},
  weeklyApplications: [],
  stale: [],
  recentActivity: [],
};

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

export const ASSISTANT_BORIS: Me = { ...BORIS, features: { assistant: true } };

export const FAQ = [
  { id: 'intake-address', question: 'What is my intake address?', answer: 'The track+alias address on your dashboard.' },
  { id: 'statuses', question: 'What do the statuses mean?', answer: 'Applied, Screening, Interview, Offer, then the end states.' },
];

/** One SSE block: `event:<name>` + JSON data + blank line. */
export function sse(name: string, data: unknown): string {
  return `event:${name}\ndata:${JSON.stringify(data)}\n\n`;
}

/**
 * A text/event-stream response delivered in the given chunks, one per
 * read, the way a real network hands them over.
 */
export function sseResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
      controller.close();
    },
  });
  return new Response(stream, { status, headers: { 'Content-Type': 'text/event-stream' } });
}
