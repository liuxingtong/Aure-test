import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { App } from './main';

describe('App', () => {
  it('renders the OpenAI-style empty state', async () => {
    window.electronAPI = {
      listHistory: vi.fn().mockResolvedValue([]),
      saveHistory: vi.fn().mockResolvedValue(undefined),
      deleteHistory: vi.fn().mockResolvedValue(undefined),
      clearHistory: vi.fn().mockResolvedValue(undefined),
      askQuestion: vi.fn(),
      getVersion: vi.fn(),
      checkBackend: vi.fn()
    };

    render(<App />);

    expect(screen.getByText('有什么可以帮忙的？')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '发送消息' })).toBeDisabled();
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
      checkBackend: vi.fn()
    };

    render(<App />);
    await user.type(screen.getByLabelText('问题输入框'), '测试问题');
    await user.click(screen.getByRole('button', { name: '发送消息' }));

    expect(await screen.findByText('已回答')).toBeInTheDocument();
    expect(screen.getByText('内容')).toBeInTheDocument();
    expect(window.electronAPI.askQuestion).toHaveBeenCalledTimes(1);
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
      checkBackend: vi.fn()
    };

    render(<App />);
    await user.type(screen.getByLabelText('问题输入框'), '失败问题');
    await user.click(screen.getByRole('button', { name: '发送消息' }));

    expect(await screen.findByText('失败问题')).toBeInTheDocument();
    expect(screen.getByText('后端服务暂不可用，请稍后重试')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '重新尝试' }));
    expect(screen.getByRole('button', { name: '发送消息' })).not.toBeDisabled();
  });
});
