import { StrictMode, FormEvent, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { AiProvider, AiSettings, Conversation } from '@qa-desktop/shared-types';
import './styles.css';
import yunheMark from './assets/yunhe-mark.png';

type Theme = 'light' | 'dark';
type Role = 'user' | 'assistant';
type Message = { id: string; role: Role; content: string; createdAt: string };
type BackendStatus = { state: 'starting' | 'ready' | 'error' | 'stopped'; message?: string };
const providerPresets: Record<AiProvider, { label: string; baseUrl: string; models: string[]; protocol: string }> = {
  deepseek: { label: 'DeepSeek', baseUrl: 'https://api.deepseek.com', models: ['deepseek-flash', 'deepseek-v4-pro'], protocol: 'OpenAI 兼容 Chat Completions（支持 Responses API）' },
  openai: { label: 'OpenAI', baseUrl: 'https://api.openai.com/v1', models: ['gpt-6-astra', 'gpt-6.1-sol', 'gpt-6-luna', 'gpt-5.6-sol', 'gpt-5.6-cyber'], protocol: 'OpenAI Chat Completions' },
  'openai-compatible': { label: 'OpenAI 兼容服务', baseUrl: 'http://localhost:11434/v1', models: [], protocol: 'OpenAI 兼容 Chat Completions' }
};
export function App() {
  const [theme, setTheme] = useState<Theme>('light');
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastFailedQuestion, setLastFailedQuestion] = useState('');
  const [backendStatus, setBackendStatus] = useState<BackendStatus>(() => window.electronAPI ? { state: 'starting', message: '正在启动后端服务…' } : { state: 'ready' });
  const [conversationId, setConversationId] = useState('local-conversation');
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [showArchived, setShowArchived] = useState(false);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [showAccount, setShowAccount] = useState(false);
  const [settings, setSettings] = useState<AiSettings>({ provider: 'deepseek', baseUrl: providerPresets.deepseek.baseUrl, model: 'deepseek-chat', apiKey: '', memoryEnabled: false, memory: '' });
  const [settingsSaved, setSettingsSaved] = useState(false);
  const [availableModels, setAvailableModels] = useState<string[]>([]);
  const hasMessages = messages.length > 0;
  const backendReady = backendStatus.state === 'ready';
  const canSend = question.trim().length > 0 && !isLoading && backendReady;
  const modelOptions = availableModels.length ? availableModels : providerPresets[settings.provider].models;
  const themeLabel = useMemo(() => theme === 'light' ? '切换到深色主题' : '切换到浅色主题', [theme]);

  useEffect(() => {
    void window.electronAPI?.listHistory().then((items) => setConversations(items));
    void window.electronAPI?.getAiSettings?.().then((saved) => { if (saved) setSettings(saved); });
    if (!window.electronAPI) return;
    let active = true;
    void window.electronAPI.getBackendStatus().then((status) => { if (active) setBackendStatus(status); });
    const unsubscribe = window.electronAPI.onBackendStatus((status) => setBackendStatus(status));
    return () => { active = false; unsubscribe(); };
  }, []);

  useEffect(() => {
    let active = true;
    const loadModels = async () => {
      const fetched = await window.electronAPI?.listAiModels?.(settings);
      if (active && fetched?.length) {
        setAvailableModels(fetched);
        if (!fetched.includes(settings.model)) setSettings((current) => ({ ...current, model: fetched[0] }));
      }
    };
    void loadModels();
    return () => { active = false; };
  }, [settings.provider, settings.baseUrl, settings.apiKey]);

  async function restartBackend() {
    if (!window.electronAPI) return;
    setBackendStatus({ state: 'starting', message: '正在重新启动后端服务…' });
    setBackendStatus(await window.electronAPI.restartBackend());
  }

  async function saveSettings() {
    const saved = await window.electronAPI?.saveAiSettings?.(settings);
    if (saved) setSettings(saved);
    setSettingsSaved(true);
    window.setTimeout(() => setSettingsSaved(false), 1800);
    if (window.electronAPI) setBackendStatus(await window.electronAPI.restartBackend());
  }

  async function archiveConversation(id: string, archived: boolean) {
    await window.electronAPI?.setConversationArchived?.(id, archived);
    setConversations((items) => items.map((item) => item.id === id ? { ...item, archived } : item));
    if (id === conversationId && archived) { setConversationId('local-conversation'); setMessages([]); }
  }

  async function submitQuestion(event: FormEvent) {
    event.preventDefault();
    if (!canSend) return;
    const content = question.trim();
    setQuestion('');
    setErrorMessage(null);
    const createdAt = new Date().toISOString();
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: 'user', content, createdAt }]);
    setIsLoading(true);
    try {
      const requestHistory = settings.memoryEnabled && settings.memory.trim() ? [{ id: 'memory', role: 'assistant' as const, content: `用户偏好与长期记忆：${settings.memory.trim()}`, createdAt: new Date(0).toISOString() }, ...messages] : messages;
      const response = window.electronAPI
        ? await window.electronAPI.askQuestion({ conversationId, question: content, history: requestHistory })
        : await new Promise<{ success: true; data: { messageId: string; answer: string } }>((resolve) => window.setTimeout(() => resolve({ success: true, data: { messageId: crypto.randomUUID(), answer: '这是浏览器预览回答。\n\n- Electron IPC 已在桌面端接入\n- 后续阶段将连接 Spring Boot' } }), 650));
      if (response.success && response.data) {
        const assistantMessage = { id: response.data.messageId, role: 'assistant' as const, content: response.data.answer, createdAt: new Date().toISOString() };
        const nextMessages = [...messages, { id: crypto.randomUUID(), role: 'user' as const, content, createdAt }, assistantMessage];
        setMessages((current) => [...current, assistantMessage]);
        const saved = { id: conversationId, title: content.slice(0, 24), messages: nextMessages, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
        await window.electronAPI?.saveHistory(saved);
        setConversations((current) => [saved, ...current.filter((item) => item.id !== saved.id)]);
      } else {
        setLastFailedQuestion(content);
        setErrorMessage('error' in response ? response.error?.message ?? '回答生成失败，请重试。' : '回答生成失败，请重试。');
      }
    } catch (error) {
      setLastFailedQuestion(content);
      setErrorMessage(error instanceof Error ? error.message : '回答生成失败，请重试。');
    } finally {
      setIsLoading(false);
    }
  }

  const visibleConversations = conversations.filter((conversation) => Boolean(conversation.archived) === showArchived);
  return (
    <div className="app-shell" data-theme={theme}>
      <aside className="sidebar" aria-label="会话列表">
        <div className="sidebar-header"><img className="app-mark" src={yunheMark} alt="yunhe" /><span>yunhe</span></div>
        <button className="new-chat-button" type="button" onClick={() => { setConversationId(crypto.randomUUID()); setMessages([]); }}><span className="button-icon" aria-hidden="true">＋</span>新建对话</button>
        <div className="history-tabs"><button className={!showArchived ? 'selected' : ''} type="button" onClick={() => setShowArchived(false)}>历史记录</button><button className={showArchived ? 'selected' : ''} type="button" onClick={() => setShowArchived(true)}>已归档</button></div>
        <div className="conversation-list">{visibleConversations.length ? visibleConversations.map((conversation) => <div className={`conversation-item-wrap ${conversation.id === conversationId ? 'active' : ''}`} key={conversation.id}><button className="conversation-item" type="button" onClick={() => { setConversationId(conversation.id); setMessages(conversation.messages); setOpenMenuId(null); }}>{conversation.title || '未命名对话'}</button><button className="conversation-archive" type="button" aria-label="对话操作" onClick={() => setOpenMenuId(openMenuId === conversation.id ? null : conversation.id)}>…</button>{openMenuId === conversation.id && <div className="conversation-menu" role="menu"><button type="button" onClick={() => { void archiveConversation(conversation.id, !showArchived); setOpenMenuId(null); }}>{showArchived ? '恢复对话' : '归档对话'}</button><button type="button" onClick={() => { void window.electronAPI?.deleteHistory(conversation.id); setConversations((items) => items.filter((item) => item.id !== conversation.id)); setOpenMenuId(null); }}>删除对话</button></div>}</div>) : <div className="empty-history">{showArchived ? '暂无归档对话' : '暂无历史记录'}</div>}</div>
        <div className="sidebar-footer"><button className="sidebar-action" type="button" onClick={() => setShowAccount(true)}><span className="account-avatar" aria-hidden="true">你</span><span className="account-copy"><strong>本地账户</strong><small>设置 API 与记忆</small></span><span className="button-icon">⌄</span></button></div>
      </aside>

      <main className="chat-panel">
        <header className="chat-header"><span className="chat-title">{conversations.find((conversation) => conversation.id === conversationId)?.title || '新对话'}</span><div className="header-actions"><button className="icon-button" type="button" aria-label={themeLabel} title={themeLabel} onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}>{theme === 'light' ? '☾' : '☀'}</button><span className={`connection-state ${backendReady ? 'ready' : 'not-ready'}`}><span className="status-dot" />{backendReady ? '后端已就绪' : backendStatus.state === 'error' ? '后端启动失败' : '正在连接后端'}</span></div></header>
        <section className={`message-area ${hasMessages ? 'has-messages' : ''}`} aria-live="polite">
          {!hasMessages && !isLoading ? <div className="welcome-state"><img className="welcome-mark" src={yunheMark} alt="yunhe" /><h1>有什么可以帮忙的？</h1><p>输入问题，开始一段新的对话。</p></div> : <div className="message-list">
            {messages.map((message) => <article className={`message-row ${message.role}`} key={message.id}><div className="message-avatar" aria-hidden="true">{message.role === 'user' ? '你' : <img src={yunheMark} alt="yunhe" />}</div><div className="message-content">{message.role === 'assistant' ? <ReactMarkdown remarkPlugins={[remarkGfm]} components={{ a: ({ href, children, ...props }) => <a {...props} href={href} onClick={(event) => { if (href?.startsWith('https://')) { event.preventDefault(); void window.electronAPI?.openExternal?.(href); } }}>{children}</a> }}>{message.content}</ReactMarkdown> : <p>{message.content}</p>}</div></article>)}
            {isLoading && <div className="message-row assistant"><div className="message-avatar" aria-hidden="true"><img src={yunheMark} alt="yunhe" /></div><div className="message-content loading-dots" aria-label="正在生成回答"><span /><span /><span /></div></div>}
            {errorMessage && <div className="error-state" role="alert"><span>{errorMessage}</span><button type="button" onClick={() => { setQuestion(lastFailedQuestion); setErrorMessage(null); }} aria-label="重新尝试">重新尝试</button></div>}
          </div>}
          {backendStatus.state !== 'ready' && <div className={`backend-status ${backendStatus.state === 'error' ? 'error' : ''}`} role="status"><span>{backendStatus.message || '正在启动后端服务…'}</span>{backendStatus.state === 'error' && <button type="button" onClick={() => void restartBackend()}>重新启动服务</button>}</div>}
        </section>
        <form className="composer-wrap" onSubmit={submitQuestion}><div className="composer"><textarea value={question} onChange={(event) => setQuestion(event.target.value)} placeholder={backendReady ? '发送消息' : '后端连接中，可先输入问题'} rows={1} aria-label="问题输入框" disabled={isLoading} /><select className="composer-model" aria-label="模型选择" value={settings.model} onChange={(event) => setSettings({ ...settings, model: event.target.value })} disabled={isLoading}>{modelOptions.length ? modelOptions.map((model) => <option key={model} value={model}>{model}</option>) : <option value={settings.model}>{settings.model || '请在账户设置中填写模型'}</option>}{settings.model && !modelOptions.includes(settings.model) && modelOptions.length > 0 && <option value={settings.model}>{settings.model}</option>}</select><button className="send-button" type="submit" disabled={!canSend} aria-label="发送消息">↑</button></div><p className="composer-hint">AI 可能会犯错，请核对重要信息。</p></form>
      </main>
      {showAccount && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setShowAccount(false); }}><section className="account-modal" role="dialog" aria-modal="true" aria-labelledby="account-title"><div className="modal-header"><div><h2 id="account-title">账户与连接</h2><p>密钥只保存在本机，不会写入对话或日志。</p></div><button className="icon-button" type="button" aria-label="关闭账户设置" onClick={() => setShowAccount(false)}>×</button></div><label>服务类型<select value={settings.provider} onChange={(event) => { const provider = event.target.value as AiProvider; const preset = providerPresets[provider]; setSettings({ ...settings, provider, baseUrl: preset.baseUrl, model: preset.models[0] ?? settings.model }); }}><option value="deepseek">DeepSeek</option><option value="openai">OpenAI</option><option value="openai-compatible">其他 OpenAI 兼容服务</option></select></label><p className="field-hint">协议：{providerPresets[settings.provider].protocol}</p><label>API Key<input type="password" value={settings.apiKey} placeholder="sk-…" autoComplete="off" onChange={(event) => setSettings({ ...settings, apiKey: event.target.value })} /></label><label>接口地址<input value={settings.baseUrl} onChange={(event) => setSettings({ ...settings, baseUrl: event.target.value })} /></label><label>模型<select value={settings.model} onChange={(event) => setSettings({ ...settings, model: event.target.value })}>{providerPresets[settings.provider].models.map((model) => <option key={model} value={model}>{model}</option>)}{!providerPresets[settings.provider].models.includes(settings.model) && <option value={settings.model}>{settings.model}</option>}</select></label><label className="memory-toggle"><input type="checkbox" checked={settings.memoryEnabled} onChange={(event) => setSettings({ ...settings, memoryEnabled: event.target.checked })} />启用记忆</label>{settings.memoryEnabled && <label>记忆内容<textarea rows={3} value={settings.memory} placeholder="例如：我偏好简洁的中文回答。" onChange={(event) => setSettings({ ...settings, memory: event.target.value })} /></label>}<p className="field-hint">支持 DeepSeek、OpenAI，以及使用 Chat Completions 协议的兼容服务。模型名称必须以服务商实际支持的名称为准。</p><div className="modal-actions"><button className="secondary-button" type="button" onClick={() => setShowAccount(false)}>取消</button><button className="primary-button" type="button" onClick={() => void saveSettings()}>{settingsSaved ? '已保存' : '保存并重启服务'}</button></div></section></div>}
    </div>
  );
}

export default App;

const root = document.getElementById('root');
if (root) createRoot(root).render(<StrictMode><App /></StrictMode>);
