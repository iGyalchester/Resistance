import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchApplications, login, UnauthorizedError } from '../api/client';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('echoes the XSRF-TOKEN cookie as a header on POSTs', async () => {
    document.cookie = 'XSRF-TOKEN=abc-123';
    const fetchMock = vi.fn(async () => jsonResponse({ fullName: 'B', email: 'b@x', intakeAddress: null }));
    vi.stubGlobal('fetch', fetchMock);

    await login('b@x', '123456');

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('abc-123');
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json');
  });

  it('sends no CSRF header on GETs', async () => {
    document.cookie = 'XSRF-TOKEN=abc-123';
    const fetchMock = vi.fn(async () => jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    await fetchApplications();

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('turns a 401 into UnauthorizedError', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ error: 'unauthenticated' }, 401)));

    await expect(fetchApplications()).rejects.toBeInstanceOf(UnauthorizedError);
  });

  it('surfaces the server error field for other failures', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ error: 'invalid_code' }, 400)));

    await expect(login('b@x', '000000')).rejects.toThrow('invalid_code');
  });
});
