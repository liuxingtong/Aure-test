import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { App } from './main';

describe('App', () => {
  it('shows startup state and disables sending until backend is ready', async () => {
    let statusListener: ((status: { state: 'starting' | 'ready' | 'error' | 'stopped'; message?: string }) => void) | undefined;
    window.electronAPI = {
      listHistory: vi.fn().mockResolvedValue([]),
      saveHistory: vi.fn().mockResolvedValue(undefined),
      deleteHistory: vi.fn().mockResolvedValue(undefined),
      clearHistory: vi.fn().mockResolvedValue(undefined),
      askQuestion: vi.fn(),
      getVersion: vi.fn(),
      checkBackend: vi.fn(),
      getBackendStatus: vi.fn().mockResolvedValue({ state: 'starting', message: '正在启动后端服务…' }),
      onBackendStatus: vi.fn((listener) => { statusListener = listener; return () => undefined; }),
      restartBackend: vi.fn()
    };

    render(<App />);

    expect(await screen.findByText('正在启动后端服务…')).toBeInTheDocument();
    expect(screen.getByLabelText('问题输入框')).not.toBeDisabled();
    expect(screen.getByRole('button', { name: '发送消息' })).toBeDisabled();
    statusListener?.({ state: 'ready', message: '后端服务已就绪' });
    expect(await screen.findByText('后端已就绪')).toBeInTheDocument();
  });

  it('shows a backend startup error and invokes restart when retrying', async () => {
    const user = userEvent.setup();
    window.electronAPI = {
      listHistory: vi.fn().mockResolvedValue([]),
      saveHistory: vi.fn().mockResolvedValue(undefined),
      deleteHistory: vi.fn().mockResolvedValue(undefined),
      clearHistory: vi.fn().mockResolvedValue(undefined),
      askQuestion: vi.fn(),
      getVersion: vi.fn(),
      checkBackend: vi.fn(),
      getBackendStatus: vi.fn().mockResolvedValue({ state: 'error', message: '端口 8080 已被占用' }),
      restartBackend: vi.fn().mockResolvedValue({ state: 'starting', message: '正在重新启动后端服务…' }),
      onBackendStatus: vi.fn().mockReturnValue(() => undefined)
    };

    render(<App />);

    expect(await screen.findByText('端口 8080 已被占用')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '重新启动服务' }));
    expect(window.electronAPI?.restartBackend).toHaveBeenCalledTimes(1);
  });

  it('renders the OpenAI-style empty state', async () => {
    window.electronAPI = {
      listHistory: vi.fn().mockResolvedValue([]),
      saveHistory: vi.fn().mockResolvedValue(undefined),
      deleteHistory: vi.fn().mockResolvedValue(undefined),
      clearHistory: vi.fn().mockResolvedValue(undefined),
      askQuestion: vi.fn(),
      getVersion: vi.fn(),
      checkBackend: vi.fn(),
      getBackendStatus: vi.fn().mockResolvedValue({ state: 'ready' }),
      onBackendStatus: vi.fn().mockReturnValue(() => undefined),
      restartBackend: vi.fn()
    };

    render(<App />);

    expect(screen.getByText('有什么可以帮忙的？')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '发送消息' })).toBeDisabled();
  });

  it('starts a fresh conversation instead of selecting persisted history', async () => {
    window.electronAPI = {
      listHistory: vi.fn().mockResolvedValue([{
        id: 'local-conversation',
        title: '旧会话',
        messages: [{ id: 'old-message', role: 'user', content: '旧内容', createdAt: '2026-01-01T00:00:00.000Z' }],
        createdAt: '2026-01-01T00:00:00.000Z',
        updatedAt: '2026-01-01T00:00:00.000Z'
      }]),
      saveHistory: vi.fn().mockResolvedValue(undefined),
      deleteHistory: vi.fn().mockResolvedValue(undefined),
      clearHistory: vi.fn().mockResolvedValue(undefined),
      askQuestion: vi.fn(),
      getVersion: vi.fn(),
      checkBackend: vi.fn(),
      getBackendStatus: vi.fn().mockResolvedValue({ state: 'ready' }),
      onBackendStatus: vi.fn().mockReturnValue(() => undefined),
      restartBackend: vi.fn()
    };

    render(<App />);

    expect(await screen.findByText('旧会话')).toBeInTheDocument();
    expect(screen.getByText('有什么可以帮忙的？')).toBeInTheDocument();
    expect(screen.getByText('新对话')).toBeInTheDocument();
    expect(document.querySelector('.conversation-item-wrap.active')).toBeNull();
    expect(screen.queryByText('旧内容')).not.toBeInTheDocument();
  });

  it('submits a question and renders markdown answer', async () => {
    const user = userEvent.setup();
    window.electronAPI = {
      listHistory: vi.fn().mockResolvedValue([]),
      saveHistory: vi.fn().mockResolvedValue(undefined),
      deleteHistory: vi.fn().mockResolvedValue(undefined),
      clearHistory: vi.fn().mockResolvedValue(undefined),
      askQuestion: vi.fn().mockResolvedValue({ success: true, data: { messageId: 'answer-1', answer: '# 已回答\n\n内容' } }),
      getVersion: vi.fn(),
      checkBackend: vi.fn(),
      getBackendStatus: vi.fn().mockResolvedValue({ state: 'ready' }),
      onBackendStatus: vi.fn().mockReturnValue(() => undefined),
      restartBackend: vi.fn()
    };

    render(<App />);
    await screen.findByText('后端已就绪');
    await user.type(screen.getByLabelText('问题输入框'), '测试问题');
    await user.click(screen.getByRole('button', { name: '发送消息' }));

    expect(await screen.findByText('已回答')).toBeInTheDocument();
    expect(screen.getByText('内容')).toBeInTheDocument();
    expect(window.electronAPI?.askQuestion).toHaveBeenCalledTimes(1);
  });

  it('keeps the question and shows a retryable error when backend fails', async () => {
    const user = userEvent.setup();
    window.electronAPI = {
      listHistory: vi.fn().mockResolvedValue([]),
      saveHistory: vi.fn().mockResolvedValue(undefined),
      deleteHistory: vi.fn().mockResolvedValue(undefined),
      clearHistory: vi.fn().mockResolvedValue(undefined),
      askQuestion: vi.fn().mockResolvedValue({ success: false, error: { code: 'BACKEND_UNAVAILABLE', message: '后端服务暂不可用，请稍后重试', retryable: true } }),
      getVersion: vi.fn(),
      checkBackend: vi.fn(),
      getBackendStatus: vi.fn().mockResolvedValue({ state: 'ready' }),
      onBackendStatus: vi.fn().mockReturnValue(() => undefined),
      restartBackend: vi.fn()
    };

    render(<App />);
    await screen.findByText('后端已就绪');
    await user.type(screen.getByLabelText('问题输入框'), '失败问题');
    await user.click(screen.getByRole('button', { name: '发送消息' }));

    expect(await screen.findByText('失败问题')).toBeInTheDocument();
    expect(screen.getByText('后端服务暂不可用，请稍后重试')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '重新尝试' }));
    expect(screen.getByRole('button', { name: '发送消息' })).not.toBeDisabled();
    await user.click(screen.getByRole('button', { name: '发送消息' }));
    await waitFor(() => expect(window.electronAPI?.askQuestion).toHaveBeenCalledTimes(2));
    expect(window.electronAPI?.askQuestion).toHaveBeenLastCalledWith(expect.objectContaining({ history: [] }));
  });

  it('opens http news links through the Electron shell', async () => {
    const user = userEvent.setup();
    window.electronAPI = {
      listHistory: vi.fn().mockResolvedValue([]), saveHistory: vi.fn().mockResolvedValue(undefined), deleteHistory: vi.fn().mockResolvedValue(undefined), clearHistory: vi.fn().mockResolvedValue(undefined),
      askQuestion: vi.fn().mockResolvedValue({ success: true, data: { messageId: 'answer-link', answer: '[新闻原文](http://example.com/news)' } }),
      getVersion: vi.fn(), checkBackend: vi.fn(), getBackendStatus: vi.fn().mockResolvedValue({ state: 'ready' }), onBackendStatus: vi.fn().mockReturnValue(() => undefined), restartBackend: vi.fn(), openExternal: vi.fn().mockResolvedValue(undefined)
    };
    render(<App />);
    await screen.findByText('后端已就绪');
    await user.type(screen.getByLabelText('问题输入框'), '搜索新闻');
    await user.click(screen.getByRole('button', { name: '发送消息' }));
    await user.click(await screen.findByRole('link', { name: '新闻原文' }));
    expect(window.electronAPI.openExternal).toHaveBeenCalledWith('http://example.com/news');
  });
});
