import type { ApplicationView, Me } from './types';

/**
 * Thin fetch wrapper for the mvc-service JSON API. Two things every call
 * needs handled once: the CSRF token (Spring Security writes it into the
 * readable XSRF-TOKEN cookie; state-changing requests must echo it back
 * in the X-XSRF-TOKEN header) and 401s (thrown as UnauthorizedError so
 * the router can send the user to /login).
 */

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
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

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (init.body) {
    headers['Content-Type'] = 'application/json';
  }
  if (init.method && init.method !== 'GET') {
    const token = csrfToken();
    if (token) {
      headers['X-XSRF-TOKEN'] = token;
    }
  }

  const response = await fetch(path, { ...init, headers });

  if (response.status === 401) {
    throw new UnauthorizedError();
  }
  if (!response.ok) {
    let message = `request failed (${response.status})`;
    try {
      const body = await response.json();
      if (body && typeof body.error === 'string') {
        message = body.error;
      }
    } catch {
      // non-JSON error body - keep the generic message
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function requestLoginCode(email: string): Promise<{ message: string }> {
  return request('/api/auth/code', { method: 'POST', body: JSON.stringify({ email }) });
}

export function login(email: string, code: string): Promise<Me> {
  return request('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, code }) });
}

export function fetchMe(): Promise<Me> {
  return request('/api/auth/me');
}

export function logout(): Promise<void> {
  return request('/api/auth/logout', { method: 'POST' });
}

export function fetchApplications(): Promise<ApplicationView[]> {
  return request('/api/applications');
}
