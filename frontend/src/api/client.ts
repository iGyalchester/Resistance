import { SseParser } from './sse';
import type {
  AdminAccount,
  AdminOverview,
  AnalyticsView,
  ApplicationDetailView,
  ApplicationRequest,
  ApplicationView,
  AssistantEvent,
  ContactRequest,
  ContactView,
  FaqEntry,
  Me,
  ProfileRequest,
  ProfileView,
} from './types';

/**
 * Thin fetch wrapper for the mvc-service JSON API. Two things every call
 * needs handled once: the CSRF token (Spring Security writes it into the
 * readable XSRF-TOKEN cookie; state-changing requests must echo it back
 * in the X-XSRF-TOKEN header) and 401s (thrown as UnauthorizedError so
 * the router can send the user to /login). Every other failure is an
 * ApiError carrying the server's error code and, for validation, the
 * per-field messages.
 */

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly fields: Record<string, string> = {},
  ) {
    super(message);
  }
}

export class UnauthorizedError extends ApiError {
  constructor() {
    super(401, 'unauthenticated');
  }
}

function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function headersFor(init: RequestInit, accept: string): Record<string, string> {
  const headers: Record<string, string> = { Accept: accept };
  if (init.body) {
    headers['Content-Type'] = 'application/json';
  }
  if (init.method && init.method !== 'GET') {
    const token = csrfToken();
    if (token) {
      headers['X-XSRF-TOKEN'] = token;
    }
  }
  return headers;
}

/** Turns a failed response into the matching error; never returns. */
async function fail(response: Response): Promise<never> {
  if (response.status === 401) {
    throw new UnauthorizedError();
  }
  let message = `request failed (${response.status})`;
  let fields: Record<string, string> = {};
  try {
    const body = await response.json();
    if (body && typeof body.error === 'string') {
      message = body.error;
    }
    if (body && body.fields && typeof body.fields === 'object') {
      fields = body.fields;
    }
  } catch {
    // non-JSON error body - keep the generic message
  }
  throw new ApiError(response.status, message, fields);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, { ...init, headers: headersFor(init, 'application/json') });

  if (!response.ok) {
    await fail(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

function json(method: 'POST' | 'PUT', body: unknown): RequestInit {
  return { method, body: JSON.stringify(body) };
}

// --- auth ---------------------------------------------------------------

export function requestLoginCode(email: string): Promise<{ message: string }> {
  return request('/api/auth/code', json('POST', { email }));
}

export function login(email: string, code: string): Promise<Me> {
  return request('/api/auth/login', json('POST', { email, code }));
}

export function fetchMe(): Promise<Me> {
  return request('/api/auth/me');
}

export function logout(): Promise<void> {
  return request('/api/auth/logout', { method: 'POST' });
}

// --- applications -------------------------------------------------------

export function fetchApplications(): Promise<ApplicationView[]> {
  return request('/api/applications');
}

export function fetchApplication(id: number): Promise<ApplicationDetailView> {
  return request(`/api/applications/${id}`);
}

export function createApplication(body: ApplicationRequest): Promise<ApplicationDetailView> {
  return request('/api/applications', json('POST', body));
}

export function updateApplication(id: number, body: ApplicationRequest): Promise<ApplicationDetailView> {
  return request(`/api/applications/${id}`, json('PUT', body));
}

export function deleteApplication(id: number): Promise<void> {
  return request(`/api/applications/${id}`, { method: 'DELETE' });
}

// --- contacts -----------------------------------------------------------

export function fetchContacts(): Promise<ContactView[]> {
  return request('/api/contacts');
}

export function createContact(body: ContactRequest): Promise<ContactView> {
  return request('/api/contacts', json('POST', body));
}

export function updateContact(id: number, body: ContactRequest): Promise<ContactView> {
  return request(`/api/contacts/${id}`, json('PUT', body));
}

export function deleteContact(id: number): Promise<void> {
  return request(`/api/contacts/${id}`, { method: 'DELETE' });
}

// --- profile ------------------------------------------------------------

export function fetchProfile(): Promise<ProfileView> {
  return request('/api/profile');
}

export function updateProfile(body: ProfileRequest): Promise<ProfileView> {
  return request('/api/profile', json('PUT', body));
}

// --- analytics ----------------------------------------------------------

export function fetchAnalytics(): Promise<AnalyticsView> {
  return request('/api/analytics/summary');
}

// --- assistant ----------------------------------------------------------

/**
 * Sends one chat message and delivers the reply's events as they stream
 * in. A plain fetch rather than EventSource because EventSource can only
 * GET and cannot carry the CSRF header. Resolves when the stream ends;
 * the caller aborts through the signal.
 */
export async function streamAssistant(
  message: string,
  onEvent: (event: AssistantEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const init: RequestInit = { method: 'POST', body: JSON.stringify({ message }), signal };
  const response = await fetch('/api/assistant/messages', {
    ...init,
    headers: headersFor(init, 'text/event-stream'),
  });
  if (!response.ok) {
    await fail(response);
  }
  if (!response.body) {
    return;
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const parser = new SseParser();
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    for (const event of parser.push(decoder.decode(value, { stream: true }))) {
      onEvent(event);
    }
  }
  for (const event of parser.push('\n\n')) {
    onEvent(event);
  }
}

export function resetAssistant(): Promise<void> {
  return request('/api/assistant/conversation', { method: 'DELETE' });
}

// --- help ---------------------------------------------------------------

export function fetchHelp(): Promise<FaqEntry[]> {
  return request('/api/help');
}

// --- admin --------------------------------------------------------------

export function fetchAdminOverview(): Promise<AdminOverview> {
  return request('/api/admin/overview');
}

export function fetchAdminAccounts(): Promise<AdminAccount[]> {
  return request('/api/admin/accounts');
}
