import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createApplication, fetchApplications, login, UnauthorizedError } from '../api/client';

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

  it('carries the per-field messages of a validation failure', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'validation', fields: { companyName: 'required' } }, 400)),
    );

    const error = await createApplication({ companyName: '', positionTitle: null, status: 'APPLIED', contactId: null })
      .then(() => null)
      .catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.message).toBe('validation');
    expect(error.fields).toEqual({ companyName: 'required' });
  });
});

describe('assistant client', () => {
  it('streams events from the reply and finishes when the stream closes', async () => {
    const { streamAssistant } = await import('../api/client');
    const { sse, sseResponse } = await import('./helpers');
    const fetchMock = vi.fn(async () =>
      sseResponse([sse('delta', { text: 'Hel' }), sse('delta', { text: 'lo' }) + sse('done', { usage: { inputTokens: 3, outputTokens: 1 } })]),
    );
    vi.stubGlobal('fetch', fetchMock);
    document.cookie = 'XSRF-TOKEN=tok123';

    const events: unknown[] = [];
    await streamAssistant('hi', (e) => events.push(e));

    expect(events).toEqual([
      { type: 'delta', text: 'Hel' },
      { type: 'delta', text: 'lo' },
      { type: 'done', usage: { inputTokens: 3, outputTokens: 1 } },
    ]);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/assistant/messages');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ message: 'hi' });
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('tok123');
    expect((init.headers as Record<string, string>)['Accept']).toBe('text/event-stream');
    vi.unstubAllGlobals();
  });

  it('throws the API error codes for 503 and 401 before reading anything', async () => {
    const { streamAssistant, ApiError, UnauthorizedError } = await import('../api/client');
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ error: 'assistant_disabled' }), { status: 503 })));
    await expect(streamAssistant('hi', () => {})).rejects.toMatchObject({ status: 503, message: 'assistant_disabled' });
    await expect(streamAssistant('hi', () => {})).rejects.toBeInstanceOf(ApiError);

    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ error: 'unauthenticated' }), { status: 401 })));
    await expect(streamAssistant('hi', () => {})).rejects.toBeInstanceOf(UnauthorizedError);
    vi.unstubAllGlobals();
  });

  it('resets the conversation and fetches help', async () => {
    const { resetAssistant, fetchHelp } = await import('../api/client');
    const fetchMock = vi.fn(async (url: string, _init?: RequestInit) =>
      url === '/api/help'
        ? new Response(JSON.stringify([{ id: 'x', question: 'Q', answer: 'A' }]), { status: 200 })
        : new Response(null, { status: 204 }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await resetAssistant();
    expect(await fetchHelp()).toEqual([{ id: 'x', question: 'Q', answer: 'A' }]);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/assistant/conversation');
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('DELETE');
    vi.unstubAllGlobals();
  });
});
