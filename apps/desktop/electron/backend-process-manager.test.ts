import { describe, expect, it, vi } from 'vitest';
import { BackendProcessManager } from './backend-process-manager';

type FakeChild = {
  killed: boolean;
  kill: (signal?: NodeJS.Signals) => boolean;
  once: (event: string, listener: (code?: number) => void) => unknown;
  stdout: { on: (event: string, listener: (chunk: unknown) => void) => unknown };
  stderr: { on: (event: string, listener: (chunk: unknown) => void) => unknown };
};

function childProcess(): FakeChild {
  return {
    killed: false,
    kill: vi.fn(function (this: FakeChild) {
      this.killed = true;
      return true;
    }) as unknown as FakeChild['kill'],
    once: vi.fn() as unknown as FakeChild['once'],
    stdout: { on: vi.fn() as unknown as FakeChild['stdout']['on'] },
    stderr: { on: vi.fn() as unknown as FakeChild['stderr']['on'] }
  };
}

describe('BackendProcessManager', () => {
  it('starts the backend and becomes ready after health polling succeeds', async () => {
    const child = childProcess();
    const spawn = vi.fn(() => child);
    const fetch = vi.fn()
      .mockRejectedValueOnce(new Error('not ready'))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'UP' }), { status: 200 }));
    const manager = new BackendProcessManager({
      mode: 'development',
      platform: 'darwin',
      resourcesPath: '/resources',
      userDataPath: '/tmp/qa-desktop',
      spawn,
      fetch,
      clock: { sleep: async () => undefined }
    });

    await expect(manager.start()).resolves.toMatchObject({ state: 'ready' });
    expect(spawn).toHaveBeenCalledOnce();
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(manager.getStatus()).toMatchObject({ state: 'ready' });
  });

  it('reports an early child-process exit with a retryable error', async () => {
    const child = childProcess();
    (child.once as unknown as ReturnType<typeof vi.fn>).mockImplementation((event: string, listener: (code: number) => void) => {
      if (event === 'exit') queueMicrotask(() => listener(1));
      return child;
    });
    const manager = new BackendProcessManager({
      mode: 'production',
      platform: 'darwin',
      resourcesPath: '/resources',
      userDataPath: '/tmp/qa-desktop',
      spawn: vi.fn(() => child),
      fetch: vi.fn().mockRejectedValue(new Error('connection refused')),
      clock: { sleep: async () => undefined }
    });

    await expect(manager.start()).resolves.toMatchObject({ state: 'error', message: expect.stringContaining('退出') });
  });

  it('times out after 30 seconds of unsuccessful health checks', async () => {
    const child = childProcess();
    let elapsed = 0;
    const manager = new BackendProcessManager({
      mode: 'production',
      platform: 'win32',
      resourcesPath: 'C:\\resources',
      userDataPath: 'C:\\Users\\test\\AppData',
      spawn: vi.fn(() => child),
      fetch: vi.fn().mockRejectedValue(new Error('not ready')),
      clock: {
        now: () => elapsed,
        sleep: async (milliseconds: number) => { elapsed += milliseconds; }
      }
    });

    await expect(manager.start()).resolves.toMatchObject({ state: 'error', message: expect.stringContaining('超时') });
    expect(elapsed).toBeGreaterThanOrEqual(30_000);
    expect(child.kill).toHaveBeenCalled();
  });

  it('fails before spawning when the fixed backend port is occupied', async () => {
    const manager = new BackendProcessManager({
      mode: 'development',
      platform: 'darwin',
      resourcesPath: '/resources',
      userDataPath: '/tmp/qa-desktop',
      spawn: vi.fn(),
      fetch: vi.fn().mockResolvedValue(new Response(null, { status: 200 })),
      portInUse: async () => true,
      clock: { sleep: async () => undefined }
    });

    await expect(manager.start()).resolves.toMatchObject({ state: 'error', message: expect.stringContaining('8080') });
    expect(manager.getStatus()).toMatchObject({ state: 'error' });
  });

  it('gracefully stops only the child process it started', async () => {
    const child = childProcess();
    const manager = new BackendProcessManager({
      mode: 'development',
      platform: 'darwin',
      resourcesPath: '/resources',
      userDataPath: '/tmp/qa-desktop',
      spawn: vi.fn(() => child),
      fetch: vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'UP' }), { status: 200 })),
      clock: { sleep: async () => undefined }
    });

    await manager.start();
    await manager.stop();

    expect(child.kill).toHaveBeenCalledWith('SIGTERM');
    expect(manager.getStatus()).toEqual({ state: 'stopped' });
  });
});
