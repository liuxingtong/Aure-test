import { describe, expect, it, vi } from 'vitest';
import { BackendStatusBroadcaster, publicAiSettings } from './ipc';

describe('BackendStatusBroadcaster', () => {
  it('broadcasts starting and ready states in order', () => {
    const broadcaster = new BackendStatusBroadcaster();
    const listener = vi.fn();
    broadcaster.subscribe(listener);

    broadcaster.publish({ state: 'starting', message: '正在启动后端服务…' });
    broadcaster.publish({ state: 'ready', message: '后端服务已就绪' });

    expect(listener.mock.calls.map(([status]) => status.state)).toEqual(['starting', 'ready']);
    expect(broadcaster.current()).toMatchObject({ state: 'ready' });
  });

  it('broadcasts an error state with its retryable message', () => {
    const broadcaster = new BackendStatusBroadcaster();
    const listener = vi.fn();
    broadcaster.subscribe(listener);

    broadcaster.publish({ state: 'error', message: '端口 8080 已被占用' });

    expect(listener).toHaveBeenCalledWith({ state: 'error', message: '端口 8080 已被占用' });
  });

  it('stops notifying a listener after unsubscribe', () => {
    const broadcaster = new BackendStatusBroadcaster();
    const listener = vi.fn();
    const unsubscribe = broadcaster.subscribe(listener);
    unsubscribe();

    broadcaster.publish({ state: 'ready' });

    expect(listener).not.toHaveBeenCalled();
  });
});

describe('publicAiSettings', () => {
  it('never exposes the stored API key to the renderer', () => {
    expect(publicAiSettings({ provider: 'deepseek', baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat', apiKey: 'secret' }))
      .toMatchObject({ apiKey: '', apiKeyConfigured: true });
  });
});
