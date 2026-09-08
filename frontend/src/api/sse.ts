import type { AssistantEvent, Proposal } from './types';

/**
 * A small parser for the text/event-stream format: events are blocks
 * separated by a blank line, each block a few "field: value" lines. The
 * network hands us arbitrary chunks (half an event, three events at once),
 * so the parser keeps whatever is left over between pushes. Pure and
 * synchronous, which is what makes it easy to test.
 */
export class SseParser {
  private buffer = '';

  /** Feed one chunk of text; returns every complete event it finished. */
  push(chunk: string): AssistantEvent[] {
    this.buffer += chunk.replace(/\r\n/g, '\n');
    const events: AssistantEvent[] = [];
    let end = this.buffer.indexOf('\n\n');
    while (end >= 0) {
      const block = this.buffer.slice(0, end);
      this.buffer = this.buffer.slice(end + 2);
      const event = parseBlock(block);
      if (event) events.push(event);
      end = this.buffer.indexOf('\n\n');
    }
    return events;
  }
}

function parseBlock(block: string): AssistantEvent | null {
  let name = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line.startsWith(':') || line.trim() === '') continue; // comment / keep-alive
    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') name = value;
    else if (field === 'data') data.push(value);
  }
  if (data.length === 0) return null;
  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(data.join('\n'));
  } catch {
    return null;
  }
  switch (name) {
    case 'delta':
      return { type: 'delta', text: String(payload.text ?? '') };
    case 'action':
      return payload.proposal ? { type: 'action', proposal: payload.proposal as Proposal } : null;
    case 'done':
      return { type: 'done', usage: (payload.usage ?? { inputTokens: 0, outputTokens: 0 }) as { inputTokens: number; outputTokens: number } };
    case 'error':
      return { type: 'error', code: String(payload.code ?? 'internal') };
    default:
      return null;
  }
}
