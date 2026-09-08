import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ACME_DETAIL, ASSISTANT_BORIS, BORIS, bodyOf, jsonResponse, noContent, renderApp, sse, sseResponse } from './helpers';

describe('assistant page', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the answer progressively as deltas arrive', async () => {
    const user = userEvent.setup();
    renderApp('/assistant', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'POST /api/assistant/messages': () =>
        sseResponse([sse('delta', { text: 'Acme has been ' }), sse('delta', { text: 'quiet for 19 days.' }) + sse('done', { usage: { inputTokens: 1, outputTokens: 1 } })]),
    });

    await user.type(await screen.findByLabelText('Message'), 'Who should I chase?');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    expect(await screen.findByText('Who should I chase?')).toBeInTheDocument();
    expect(await screen.findByText('Acme has been quiet for 19 days.')).toBeInTheDocument();
    // the box is cleared and ready for the next question
    expect(screen.getByLabelText('Message')).toHaveValue('');
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled();
  });

  it('sends a suggested prompt with one click', async () => {
    const user = userEvent.setup();
    let sent: unknown = null;
    renderApp('/assistant', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'POST /api/assistant/messages': (init) => {
        sent = bodyOf(init);
        return sseResponse([sse('delta', { text: 'Four active.' }) + sse('done', { usage: { inputTokens: 1, outputTokens: 1 } })]);
      },
    });

    await user.click(await screen.findByRole('button', { name: 'Summarize my pipeline' }));

    await waitFor(() => expect(sent).toEqual({ message: 'Summarize my pipeline' }));
    expect(await screen.findByText('Four active.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Summarize my pipeline' })).not.toBeInTheDocument();
  });

  it('shows a proposal card and applies it through the REST API', async () => {
    const user = userEvent.setup();
    let put: unknown = null;
    renderApp('/assistant', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'POST /api/assistant/messages': () =>
        sseResponse([
          sse('delta', { text: 'Here is a proposal.' }) +
            sse('action', { proposal: { kind: 'status_change', applicationId: 1, companyName: 'Acme Corp', status: 'WITHDRAWN', reason: 'Quiet for weeks' } }) +
            sse('done', { usage: { inputTokens: 1, outputTokens: 1 } }),
        ]),
      'GET /api/applications/1': () => jsonResponse(ACME_DETAIL),
      'PUT /api/applications/1': (init) => {
        put = bodyOf(init);
        return jsonResponse({ ...ACME_DETAIL, status: 'WITHDRAWN' });
      },
    });

    await user.type(await screen.findByLabelText('Message'), 'withdraw acme');
    await user.keyboard('{Enter}');

    const card = await screen.findByTestId('proposal');
    expect(card).toHaveTextContent('Mark Acme Corp as Withdrawn');
    expect(card).toHaveTextContent('Quiet for weeks');
    await user.click(within(card).getByRole('button', { name: 'Apply' }));

    await waitFor(() =>
      expect(put).toEqual({ companyName: 'Acme Corp', positionTitle: 'Backend Engineer', status: 'WITHDRAWN', contactId: 3 }),
    );
    expect(await screen.findByText('Acme Corp marked Withdrawn')).toBeInTheDocument();
    expect(within(card).getByText('Applied')).toBeInTheDocument();
    expect(within(card).queryByRole('button', { name: 'Apply' })).not.toBeInTheDocument();
  });

  it('creates an application or a contact from the matching proposals', async () => {
    const user = userEvent.setup();
    let posted: unknown[] = [];
    renderApp('/assistant', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'POST /api/assistant/messages': () =>
        sseResponse([
          sse('action', { proposal: { kind: 'new_application', companyName: 'Initech', positionTitle: 'Dev', status: 'APPLIED' } }) +
            sse('action', { proposal: { kind: 'contact', firstName: 'Dana', lastName: 'Lee', email: 'dana@initech.example' } }) +
            sse('done', { usage: { inputTokens: 1, outputTokens: 1 } }),
        ]),
      'POST /api/applications': (init) => {
        posted.push(bodyOf(init));
        return jsonResponse({ ...ACME_DETAIL, id: 9, companyName: 'Initech' }, 201);
      },
      'POST /api/contacts': (init) => {
        posted.push(bodyOf(init));
        return jsonResponse({ id: 5, firstName: 'Dana', lastName: 'Lee', email: 'dana@initech.example', applicationCount: 0 }, 201);
      },
    });

    await user.type(await screen.findByLabelText('Message'), 'track initech and dana');
    await user.keyboard('{Enter}');
    const cards = await screen.findAllByTestId('proposal');
    expect(cards[0]).toHaveTextContent('Track Initech — Dev as Applied');
    expect(cards[1]).toHaveTextContent('Add Dana Lee <dana@initech.example> to your contacts');

    await user.click(within(cards[0]).getByRole('button', { name: 'Apply' }));
    await user.click(within(cards[1]).getByRole('button', { name: 'Apply' }));

    await waitFor(() => expect(posted).toHaveLength(2));
    expect(posted[0]).toEqual({ companyName: 'Initech', positionTitle: 'Dev', status: 'APPLIED', contactId: null });
    expect(posted[1]).toEqual({ firstName: 'Dana', lastName: 'Lee', email: 'dana@initech.example' });
    expect(await screen.findByText('Initech added')).toBeInTheDocument();
    expect(await screen.findByText('Dana Lee saved')).toBeInTheDocument();
  });

  it('dismisses a proposal without calling anything', async () => {
    const user = userEvent.setup();
    const { fetchMock } = renderApp('/assistant', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'POST /api/assistant/messages': () =>
        sseResponse([
          sse('action', { proposal: { kind: 'status_change', applicationId: 1, companyName: 'Acme Corp', status: 'REJECTED' } }) +
            sse('done', { usage: { inputTokens: 1, outputTokens: 1 } }),
        ]),
    });

    await user.type(await screen.findByLabelText('Message'), 'reject acme');
    await user.keyboard('{Enter}');
    const card = await screen.findByTestId('proposal');
    await user.click(within(card).getByRole('button', { name: 'Dismiss' }));

    expect(screen.queryByTestId('proposal')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).startsWith('/api/applications') || (init as RequestInit)?.method === 'PUT')).toBe(false);
  });

  it('keeps the card open when applying fails', async () => {
    const user = userEvent.setup();
    renderApp('/assistant', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'POST /api/assistant/messages': () =>
        sseResponse([
          sse('action', { proposal: { kind: 'status_change', applicationId: 1, companyName: 'Acme Corp', status: 'REJECTED' } }) +
            sse('done', { usage: { inputTokens: 1, outputTokens: 1 } }),
        ]),
      'GET /api/applications/1': () => jsonResponse(ACME_DETAIL),
      'PUT /api/applications/1': () => jsonResponse({ error: 'internal' }, 500),
    });

    await user.type(await screen.findByLabelText('Message'), 'reject acme');
    await user.keyboard('{Enter}');
    const card = await screen.findByTestId('proposal');
    await user.click(within(card).getByRole('button', { name: 'Apply' }));

    expect(await screen.findByText('Could not apply that change. Try it from the page instead.')).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Apply' })).toBeEnabled();
  });

  it('explains the hourly limit and offers a retry', async () => {
    const user = userEvent.setup();
    let calls = 0;
    renderApp('/assistant', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'POST /api/assistant/messages': () => {
        calls += 1;
        return calls === 1
          ? sseResponse([sse('error', { code: 'rate_limited' })])
          : sseResponse([sse('delta', { text: 'Back again.' }) + sse('done', { usage: { inputTokens: 1, outputTokens: 1 } })]);
      },
    });

    await user.type(await screen.findByLabelText('Message'), 'hello');
    await user.keyboard('{Enter}');

    expect(await screen.findByRole('alert')).toHaveTextContent('You have reached the hourly limit for the assistant.');
    await user.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText('Back again.')).toBeInTheDocument();
    expect(screen.getAllByText('hello')).toHaveLength(2);
  });

  it('turns a transport failure into a retryable error row', async () => {
    const user = userEvent.setup();
    renderApp('/assistant', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'POST /api/assistant/messages': () => jsonResponse({ error: 'assistant_disabled' }, 503),
    });

    await user.type(await screen.findByLabelText('Message'), 'hello');
    await user.keyboard('{Enter}');

    expect(await screen.findByRole('alert')).toHaveTextContent('The assistant is not configured on this deployment.');
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('starts a new conversation by forgetting the server-side history', async () => {
    const user = userEvent.setup();
    let deleted = false;
    renderApp('/assistant', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
      'POST /api/assistant/messages': () =>
        sseResponse([sse('delta', { text: 'First answer.' }) + sse('done', { usage: { inputTokens: 1, outputTokens: 1 } })]),
      'DELETE /api/assistant/conversation': () => {
        deleted = true;
        return noContent();
      },
    });

    expect(await screen.findByRole('button', { name: 'New conversation' })).toBeDisabled();
    await user.type(screen.getByLabelText('Message'), 'one');
    await user.keyboard('{Enter}');
    await screen.findByText('First answer.');
    await user.click(screen.getByRole('button', { name: 'New conversation' }));

    await waitFor(() => expect(deleted).toBe(true));
    expect(screen.queryByText('First answer.')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Summarize my pipeline' })).toBeInTheDocument();
  });

  it('prefills the question from the Help page link', async () => {
    renderApp('/assistant?q=What%20do%20the%20statuses%20mean%3F', {
      'GET /api/auth/me': () => jsonResponse(ASSISTANT_BORIS),
    });

    expect(await screen.findByLabelText('Message')).toHaveValue('What do the statuses mean?');
    expect(screen.getByRole('button', { name: 'Send' })).toBeEnabled();
  });

  it('shows the disabled notice and no input when the feature is off', async () => {
    renderApp('/assistant', {
      'GET /api/auth/me': () => jsonResponse(BORIS),
    });

    expect(await screen.findByText('The assistant is not switched on for this deployment.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Message')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Send' })).not.toBeInTheDocument();
  });
});
