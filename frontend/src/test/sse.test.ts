import { describe, expect, it } from 'vitest';
import { SseParser } from '../api/sse';

describe('SseParser', () => {
  it('parses one complete event', () => {
    const parser = new SseParser();
    expect(parser.push('event:delta\ndata:{"text":"Hi"}\n\n')).toEqual([{ type: 'delta', text: 'Hi' }]);
  });

  it('holds a partial event until the rest arrives', () => {
    const parser = new SseParser();
    expect(parser.push('event:delta\ndata:{"te')).toEqual([]);
    expect(parser.push('xt":"Hello"}\n')).toEqual([]);
    expect(parser.push('\nevent:done\ndata:{"usage":{"inputTokens":1,"outputTokens":2}}\n\n')).toEqual([
      { type: 'delta', text: 'Hello' },
      { type: 'done', usage: { inputTokens: 1, outputTokens: 2 } },
    ]);
  });

  it('returns several events from one chunk, in order', () => {
    const parser = new SseParser();
    const events = parser.push(
      'event:delta\ndata:{"text":"a"}\n\nevent:action\ndata:{"proposal":{"kind":"contact","firstName":"Dana"}}\n\nevent:error\ndata:{"code":"rate_limited"}\n\n',
    );
    expect(events).toEqual([
      { type: 'delta', text: 'a' },
      { type: 'action', proposal: { kind: 'contact', firstName: 'Dana' } },
      { type: 'error', code: 'rate_limited' },
    ]);
  });

  it('accepts optional spaces after the colon and CRLF line endings', () => {
    const parser = new SseParser();
    expect(parser.push('event: delta\r\ndata: {"text":"x"}\r\n\r\n')).toEqual([{ type: 'delta', text: 'x' }]);
  });

  it('handles a CRLF pair split across two chunks', () => {
    const parser = new SseParser();
    expect(parser.push('event:delta\r\ndata:{"text":"x"}\r')).toEqual([]);
    expect(parser.push('\n\r\n')).toEqual([{ type: 'delta', text: 'x' }]);
  });

  it('ignores comments, unknown events, and unparsable data', () => {
    const parser = new SseParser();
    expect(parser.push(':keep-alive\n\nevent:mystery\ndata:{}\n\nevent:delta\ndata:not json\n\n')).toEqual([]);
  });
});
