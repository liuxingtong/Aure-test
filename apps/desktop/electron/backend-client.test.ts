import { afterEach, describe, expect, it, vi } from 'vitest';
import { askBackend, setBackendReady } from './backend-client';

describe('askBackend', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    setBackendReady(true);
  });

  it('retries 503 responses before returning a later success', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: false, error: { code: 'TEMPORARY', message: 'busy', retryable: true } }), { status: 503 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { messageId: 'm1', answer: 'ok' } }), { status: 200 }));

    await expect(askBackend({ conversationId: 'c1', question: 'hello', history: [] })).resolves.toMatchObject({ success: true });
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('does not retry validation responses', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ success: false, error: { code: 'VALIDATION_ERROR', message: 'bad', retryable: false } }), { status: 400 })
    );

    await expect(askBackend({ conversationId: 'c1', question: 'hello', history: [] })).resolves.toMatchObject({ error: { code: 'VALIDATION_ERROR' } });
    expect(fetch).toHaveBeenCalledOnce();
  });
});
