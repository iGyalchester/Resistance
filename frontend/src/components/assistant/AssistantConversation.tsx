import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import {
  ApiError,
  UnauthorizedError,
  createApplication,
  createContact,
  fetchApplication,
  resetAssistant,
  streamAssistant,
  updateApplication,
} from '../../api/client';
import type { ApplicationStatus, AssistantEvent, Proposal } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { useToast } from '../Toast';

/**
 * The chat itself, shared by the drawer and the full page. State is a list
 * of turns; the assistant's turn fills in as delta events stream, gains a
 * card per action event, and ends with done or an error. Proposals are
 * applied from here through the ordinary REST calls - the assistant never
 * gets a write path of its own, so a wrong suggestion costs one click.
 */

interface ProposalCard {
  id: number;
  proposal: Proposal;
  state: 'open' | 'applying' | 'applied' | 'dismissed';
}

interface Turn {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  proposals: ProposalCard[];
  /** Still streaming. */
  pending?: boolean;
  /** An error code from the server or the network; shown instead of an empty bubble. */
  error?: string;
}

export const SUGGESTIONS = [
  'Which applications should I follow up on?',
  'Summarize my pipeline',
  'Which companies ghosted me?',
];

export const ERROR_MESSAGES: Record<string, string> = {
  rate_limited: 'You have reached the hourly limit for the assistant. Try again in a little while.',
  assistant_unavailable: 'The assistant could not be reached. Try again.',
  assistant_disabled: 'The assistant is not configured on this deployment.',
  aborted: 'Stopped.',
  internal: 'Something went wrong. Try again.',
};

let nextId = 1;

export function describeProposal(p: Proposal): string {
  switch (p.kind) {
    case 'status_change':
      return `Mark ${p.companyName ?? 'this application'} as ${labelStatus(p.status)}`;
    case 'new_application':
      return `Track ${p.companyName}${p.positionTitle ? ` — ${p.positionTitle}` : ''} as ${labelStatus(p.status)}`;
    case 'contact':
      return `Add ${[p.firstName, p.lastName].filter(Boolean).join(' ')}${p.email ? ` <${p.email}>` : ''} to your contacts`;
    default:
      return 'Suggested change';
  }
}

function labelStatus(status?: string | null): string {
  const s = status ?? 'APPLIED';
  return s.charAt(0) + s.slice(1).toLowerCase();
}

interface Props {
  /** A question typed into the box but not sent (from the Help page). */
  initialMessage?: string;
  compact?: boolean;
}

export default function AssistantConversation({ initialMessage, compact = false }: Props) {
  const { me, setMe } = useAuth();
  const { notify } = useToast();
  const enabled = me?.features?.assistant === true;
  const [turns, setTurns] = useState<Turn[]>([]);
  const [draft, setDraft] = useState(initialMessage ?? '');
  const [streaming, setStreaming] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (initialMessage) setDraft(initialMessage);
  }, [initialMessage]);

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' });
  }, [turns]);

  // leaving the page stops the reply: no updates to an unmounted component,
  // and the server stops the model stream on the next delta
  useEffect(() => () => abortRef.current?.abort(), []);

  const patch = useCallback((id: number, update: (turn: Turn) => Turn) => {
    setTurns((list) => list.map((t) => (t.id === id ? update(t) : t)));
  }, []);

  async function send(text: string) {
    const message = text.trim();
    if (!message || streaming) return;
    const replyId = nextId++;
    setTurns((list) => [
      ...list,
      { id: nextId++, role: 'user', text: message, proposals: [] },
      { id: replyId, role: 'assistant', text: '', proposals: [], pending: true },
    ]);
    setDraft('');
    setStreaming(true);
    const controller = new AbortController();
    abortRef.current = controller;

    const onEvent = (event: AssistantEvent) => {
      patch(replyId, (turn) => {
        switch (event.type) {
          case 'delta':
            return { ...turn, text: turn.text + event.text };
          case 'action':
            return { ...turn, proposals: [...turn.proposals, { id: nextId++, proposal: event.proposal, state: 'open' }] };
          case 'done':
            return { ...turn, pending: false };
          case 'error':
            return { ...turn, pending: false, error: event.code };
        }
      });
      if (event.type === 'error' && event.code === 'unauthenticated') {
        setMe(null);
      }
    };

    try {
      await streamAssistant(message, onEvent, controller.signal);
      // the stream ended without done or error (server timeout, dropped
      // connection): say so rather than leave an empty bubble
      patch(replyId, (turn) =>
        turn.pending ? { ...turn, pending: false, error: turn.text ? undefined : 'internal' } : turn,
      );
    } catch (e) {
      if (e instanceof UnauthorizedError) {
        setMe(null);
        return;
      }
      const code =
        e instanceof ApiError ? e.message : e instanceof Error && e.name === 'AbortError' ? 'aborted' : 'internal';
      patch(replyId, (turn) => ({ ...turn, pending: false, error: code }));
    } finally {
      abortRef.current = null;
      setStreaming(false);
    }
  }

  function stop() {
    abortRef.current?.abort();
  }

  async function startOver() {
    stop();
    setTurns([]);
    try {
      await resetAssistant();
    } catch {
      // the server-side history expires with the session anyway
    }
  }

  function retry() {
    const lastUser = [...turns].reverse().find((t) => t.role === 'user');
    if (lastUser) void send(lastUser.text);
  }

  async function apply(turnId: number, card: ProposalCard) {
    const p = card.proposal;
    patch(turnId, (t) => ({ ...t, proposals: t.proposals.map((c) => (c.id === card.id ? { ...c, state: 'applying' } : c)) }));
    try {
      if (p.kind === 'status_change' && p.applicationId != null && p.status) {
        const current = await fetchApplication(p.applicationId);
        await updateApplication(p.applicationId, {
          companyName: current.companyName,
          positionTitle: current.positionTitle ?? null,
          status: p.status as ApplicationStatus,
          contactId: current.contactId ?? null,
        });
        notify(`${current.companyName} marked ${labelStatus(p.status)}`);
      } else if (p.kind === 'new_application' && p.companyName) {
        await createApplication({
          companyName: p.companyName,
          positionTitle: p.positionTitle ?? null,
          status: (p.status ?? 'APPLIED') as ApplicationStatus,
          contactId: null,
        });
        notify(`${p.companyName} added`);
      } else if (p.kind === 'contact' && p.firstName) {
        await createContact({ firstName: p.firstName, lastName: p.lastName ?? null, email: p.email ?? null });
        notify(`${[p.firstName, p.lastName].filter(Boolean).join(' ')} saved`);
      } else {
        throw new Error('unknown proposal');
      }
      patch(turnId, (t) => ({ ...t, proposals: t.proposals.map((c) => (c.id === card.id ? { ...c, state: 'applied' } : c)) }));
    } catch (e) {
      if (e instanceof UnauthorizedError) {
        setMe(null);
        return;
      }
      notify('Could not apply that change. Try it from the page instead.', 'error');
      patch(turnId, (t) => ({ ...t, proposals: t.proposals.map((c) => (c.id === card.id ? { ...c, state: 'open' } : c)) }));
    }
  }

  function dismiss(turnId: number, card: ProposalCard) {
    patch(turnId, (t) => ({ ...t, proposals: t.proposals.filter((c) => c.id !== card.id) }));
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void send(draft);
  }

  function onKey(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void send(draft);
    }
  }

  if (!enabled) {
    return (
      <div className="assistant-disabled">
        <p>The assistant is not switched on for this deployment.</p>
        <p className="muted small">
          Whoever runs it can enable it by setting <code>ANTHROPIC_API_KEY</code> on the tracker service. Everything
          else works without it.
        </p>
      </div>
    );
  }

  return (
    <div className={`assistant${compact ? ' assistant-compact' : ''}`}>
      <div className="assistant-log" role="log" aria-live="polite" aria-label="Conversation">
        {turns.length === 0 && (
          <div className="assistant-empty">
            <p className="muted">
              Ask about your own applications. Answers come from what is in your tracker, and any change is a card
              you confirm.
            </p>
            <div className="chips">
              {SUGGESTIONS.map((s) => (
                <button key={s} type="button" className="chip" onClick={() => void send(s)}>
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
        {turns.map((turn) => (
          <div key={turn.id} className={`bubble bubble-${turn.role}`}>
            {turn.text && <p className="bubble-text">{turn.text}</p>}
            {turn.pending && <span className="caret" aria-hidden="true" />}
            {turn.error && (
              <div className="bubble-error" role="alert">
                <span>{ERROR_MESSAGES[turn.error] ?? ERROR_MESSAGES.internal}</span>{' '}
                {turn.error !== 'assistant_disabled' && (
                  <button type="button" className="link" onClick={retry} disabled={streaming}>
                    Retry
                  </button>
                )}
              </div>
            )}
            {turn.proposals.map((card) => (
              <div key={card.id} className="proposal" data-testid="proposal">
                <div>
                  <strong>{describeProposal(card.proposal)}</strong>
                  {card.proposal.reason && <div className="muted small">{card.proposal.reason}</div>}
                </div>
                {card.state === 'applied' ? (
                  <span className="muted small">Applied</span>
                ) : (
                  <div className="proposal-actions">
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={card.state === 'applying'}
                      onClick={() => void apply(turn.id, card)}
                    >
                      Apply
                    </button>
                    <button type="button" className="btn btn-ghost" onClick={() => dismiss(turn.id, card)}>
                      Dismiss
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        ))}
        <div ref={endRef} />
      </div>

      <form className="assistant-form" onSubmit={onSubmit}>
        <label htmlFor="assistant-input" className="sr-only">
          Message
        </label>
        <textarea
          id="assistant-input"
          rows={compact ? 2 : 3}
          value={draft}
          placeholder="Ask about your applications…"
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKey}
          maxLength={4000}
        />
        <div className="assistant-actions">
          <button type="button" className="btn btn-ghost" onClick={() => void startOver()} disabled={turns.length === 0}>
            New conversation
          </button>
          {streaming ? (
            <button type="button" className="btn" onClick={stop}>
              Stop
            </button>
          ) : (
            <button type="submit" className="btn btn-primary" disabled={!draft.trim()}>
              Send
            </button>
          )}
        </div>
      </form>
    </div>
  );
}
