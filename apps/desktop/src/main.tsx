import { StrictMode, FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import ReactMarkdown, { defaultUrlTransform } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import type { AiProvider, AiSettings, ChatMessage as SharedChatMessage, Conversation, DocumentAttachment } from '@qa-desktop/shared-types';
import './styles.css';
import 'katex/dist/katex.min.css';
import yunheMark from './assets/yunhe-mark.png';

type Theme = 'light' | 'dark';
type Role = 'user' | 'assistant';
type Message = SharedChatMessage;
type BackendStatus = { state: 'starting' | 'ready' | 'error' | 'stopped'; message?: string };

const MAX_HISTORY_MESSAGES = 60;
const MAX_HISTORY_CONTENT = 18_000;
const MAX_QUESTION_LENGTH = 10_000;

function newConversationId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `local-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** react-markdown 默认会清空 file: 链接；仅放行生成文档的 file://，其余仍走安全白名单。 */
function markdownUrlTransform(url: string): string {
  return /^file:\/\//i.test(url) ? url : defaultUrlTransform(url);
}

function isExternalLink(href: string | undefined): boolean {
  if (!href) return false;
  try {
    return ['http:', 'https:', 'file:'].includes(new URL(href).protocol);
  } catch {
    return false;
  }
}

/** 历史里只保留附件名，不把文档正文反复写进 conversations.json / 请求体。 */
function withoutAttachmentContent(message: Message): Message {
  if (!message.attachments?.length) return message;
  return {
    ...message,
    attachments: message.attachments.map(({ name, extension }) => ({ name, extension, content: '' }))
  };
}

/** 后端 history 有 100 条 / 单条 20000 字符上限，超出会直接 400；这里先截断。 */
function buildHistory(messages: Message[]): Message[] {
  return messages.slice(-MAX_HISTORY_MESSAGES).map((message) => ({
    ...withoutAttachmentContent(message),
    content: message.content.length > MAX_HISTORY_CONTENT
      ? message.content.slice(0, MAX_HISTORY_CONTENT)
      : message.content
  }));
}

function AttachmentChips({ attachments }: { attachments: DocumentAttachment[] }) {
  if (!attachments || attachments.length === 0) return null;
  return (
    <div className="attachment-chips inline" role="list">
      {attachments.map((att, index) => (
        <span className="attachment-chip" role="listitem" key={`${att.name}-${index}`}>
          <span className="attachment-icon" aria-hidden="true">📎</span>
          <span className="attachment-name">{att.name}</span>
        </span>
      ))}
    </div>
  );
}

function ThinkingBlock({ thinking }: { thinking: string }) {  const [open, setOpen] = useState(false);
  if (!thinking) return null;
  const preview = thinking.replace(/\s+/g, ' ').slice(0, 80);
  return (
    <div className="thinking-block">
      <button type="button" className="thinking-toggle" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
        <span className="thinking-dot" aria-hidden="true" />
        <span className="thinking-label">{open ? '隐藏思考过程' : '已思考'}</span>
        {!open && <span className="thinking-preview">{preview}{preview.length < thinking.replace(/\s+/g, ' ').length ? '…' : ''}</span>}
        <span className={`thinking-chevron ${open ? 'open' : ''}`} aria-hidden="true">▾</span>
      </button>
      {open && (
        <div className="thinking-body">
          <ReactMarkdown remarkPlugins={[remarkMath, remarkGfm]} rehypePlugins={[rehypeKatex]} urlTransform={markdownUrlTransform} components={{ a: ({ href, children, ...props }) => <a {...props} href={href} onClick={(event) => { if (isExternalLink(href)) { event.preventDefault(); void window.electronAPI?.openExternal?.(href!); } }}>{children}</a> }}>{thinking}</ReactMarkdown>
        </div>
      )}
    </div>
  );
}

const providerPresets: Record<AiProvider, { label: string; baseUrl: string; models: string[]; protocol: string }> = {
  deepseek: { label: 'DeepSeek', baseUrl: 'https://api.deepseek.com', models: ['deepseek-chat', 'deepseek-reasoner'], protocol: 'OpenAI 兼容 Chat Completions' },
  openai: { label: 'OpenAI', baseUrl: 'https://api.openai.com/v1', models: ['gpt-4.1-mini', 'gpt-4.1'], protocol: 'OpenAI Chat Completions' },
  'openai-compatible': { label: 'OpenAI 兼容服务', baseUrl: 'http://localhost:11434/v1', models: [], protocol: 'OpenAI 兼容 Chat Completions' }
};
function normalizeSettings(value: AiSettings): AiSettings {
  const preset = providerPresets[value.provider];
  const legacyModels = new Set(['deepseek-flash', 'deepseek-v4-pro', 'gpt-6-astra', 'gpt-6.1-sol', 'gpt-6-luna', 'gpt-5.6-sol', 'gpt-5.6-cyber']);
  if (legacyModels.has(value.model) || (preset.models.length > 0 && !preset.models.includes(value.model))) {
    return { ...value, model: preset.models[0] };
  }
  return value;
}
export function App() {
  const [theme, setTheme] = useState<Theme>('light');
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastFailedQuestion, setLastFailedQuestion] = useState('');
  const [backendStatus, setBackendStatus] = useState<BackendStatus>(() => window.electronAPI ? { state: 'starting', message: '正在启动后端服务…' } : { state: 'ready' });
  const [conversationId, setConversationId] = useState<string>(() => newConversationId());
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [showArchived, setShowArchived] = useState(false);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);
  const [showAccount, setShowAccount] = useState(false);
  const [settings, setSettings] = useState<AiSettings>({ provider: 'deepseek', baseUrl: providerPresets.deepseek.baseUrl, model: 'deepseek-chat', apiKey: '', memoryEnabled: false, memory: '' });
  const [settingsSaved, setSettingsSaved] = useState(false);
  const [availableModels, setAvailableModels] = useState<string[]>([]);
  const [pendingAttachments, setPendingAttachments] = useState<DocumentAttachment[]>([]);
  const [lastFailedAttachments, setLastFailedAttachments] = useState<DocumentAttachment[]>([]);
  const conversationRef = useRef(conversationId);
  useEffect(() => { conversationRef.current = conversationId; }, [conversationId]);
  const hasMessages = messages.length > 0;
  const backendReady = backendStatus.state === 'ready';
  const canSend = (question.trim().length > 0 || pendingAttachments.length > 0) && !isLoading && backendReady;
  const modelOptions = availableModels.length ? availableModels : providerPresets[settings.provider].models;
  const themeLabel = useMemo(() => theme === 'light' ? '切换到深色主题' : '切换到浅色主题', [theme]);

  useEffect(() => {
    void window.electronAPI?.listHistory().then((items) => setConversations(items));
    void window.electronAPI?.getAiSettings?.().then((saved) => { if (saved) setSettings(normalizeSettings(saved)); });
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
        const allowed = settings.provider === 'deepseek' ? providerPresets.deepseek.models : settings.provider === 'openai' ? providerPresets.openai.models : fetched;
        const filtered = fetched.filter((model) => allowed.includes(model));
        const models = filtered.length ? filtered : (settings.provider === 'openai-compatible' ? fetched : allowed);
        setAvailableModels(models);
        if (!models.includes(settings.model)) setSettings((current) => ({ ...current, model: models[0] }));
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
    const normalized = normalizeSettings(settings);
    setSettings(normalized);
    const saved = await window.electronAPI?.saveAiSettings?.(normalized);
    if (saved) setSettings(saved);
    setSettingsSaved(true);
    window.setTimeout(() => setSettingsSaved(false), 1800);
    if (window.electronAPI) setBackendStatus(await window.electronAPI.restartBackend());
  }

  async function archiveConversation(id: string, archived: boolean) {
    await window.electronAPI?.setConversationArchived?.(id, archived);
    setConversations((items) => items.map((item) => item.id === id ? { ...item, archived } : item));
    if (id === conversationId && archived) resetConversation();
  }

  async function deleteConversation(id: string) {
    await window.electronAPI?.deleteHistory(id);
    setConversations((items) => items.filter((item) => item.id !== id));
    if (id === conversationId) resetConversation();
    setOpenMenuId(null);
  }

  async function clearAllHistory() {
    if (!window.confirm('清空全部本地历史记录？此操作不可撤销。')) return;
    await window.electronAPI?.clearHistory();
    setConversations([]);
    resetConversation();
  }

  function resetConversation() {
    setConversationId(newConversationId());
    setMessages([]);
    setPendingAttachments([]);
    setErrorMessage(null);
    setLastFailedAttachments([]);
  }

  async function attachDocuments() {
    if (!window.electronAPI?.chooseDocument) return;
    try {
      const document = await window.electronAPI.chooseDocument();
      if (!document) return;
      setPendingAttachments((current) => [...current, document]);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '文件读取失败，请重新选择。');
    }
  }

  function removePendingAttachment(index: number) {
    setPendingAttachments((current) => current.filter((_, i) => i !== index));
  }

  async function submitQuestion(event: FormEvent) {
    event.preventDefault();
    if (!canSend) return;
    const attachments = pendingAttachments;
    // 允许“只挂附件不写问题”，给后端一个明确的任务，而不是被 @NotBlank 拒绝。
    const content = question.trim() || (attachments.length > 0 ? '请阅读并总结我附加的文档' : '');
    if (!content) return;
    setQuestion('');
    setErrorMessage(null);
    setPendingAttachments([]);
    const createdAt = new Date().toISOString();
    const userMessage: Message = { id: crypto.randomUUID(), role: 'user', content, createdAt, attachments: attachments.length > 0 ? attachments : undefined };
    setMessages((current) => [...current, userMessage]);
    setIsLoading(true);
    try {
      const requestHistory = buildHistory(messages);
      const memory = settings.memory.trim().slice(0, MAX_HISTORY_CONTENT);
      const historyWithMemory = settings.memoryEnabled && memory
        ? [{ id: 'memory', role: 'assistant' as const, content: `用户偏好与长期记忆：${memory}`, createdAt: new Date(0).toISOString() }, ...requestHistory]
        : requestHistory;
      let activeAttachments = attachments;
      const request = { conversationId, question: content, history: historyWithMemory, model: settings.model, documents: activeAttachments.length > 0 ? activeAttachments : undefined };
      let response = window.electronAPI
        ? await window.electronAPI.askQuestion(request)
        : await new Promise<{ success: true; data: { messageId: string; answer: string; thinking?: string } }>((resolve) => window.setTimeout(() => resolve({ success: true, data: { messageId: crypto.randomUUID(), answer: '这是浏览器预览回答。\n\n- Electron IPC 已在桌面端接入\n- 后续阶段将连接 Spring Boot', thinking: '' } }), 650));
      if (!response.success && response.error?.code === 'DOCUMENT_SELECTION_REQUIRED' && window.electronAPI?.chooseDocument) {
        const document = await window.electronAPI.chooseDocument();
        if (document) {
          activeAttachments = [document];
          response = await window.electronAPI.askQuestion({ ...request, documents: activeAttachments });
        }
      }
      if (response.success && response.data) {
        const assistantMessage: Message = { id: response.data.messageId, role: 'assistant', content: response.data.answer, createdAt: new Date().toISOString(), thinking: response.data.thinking ?? '' };
        const finalUserMessage = withoutAttachmentContent({ ...userMessage, attachments: activeAttachments.length > 0 ? activeAttachments : undefined });
        const nextMessages = [...messages, finalUserMessage, assistantMessage];
        if (conversationRef.current === conversationId) setMessages((current) => [...current, assistantMessage]);
        const existing = conversations.find((conversation) => conversation.id === conversationId);
        const saved = {
          id: conversationId,
          title: existing?.title || content.slice(0, 24) || activeAttachments[0]?.name || '新对话',
          messages: nextMessages,
          createdAt: existing?.createdAt ?? new Date().toISOString(),
          updatedAt: new Date().toISOString()
        };
        await window.electronAPI?.saveHistory(saved);
        setConversations((current) => [saved, ...current.filter((item) => item.id !== saved.id)]);
        setLastFailedAttachments([]);
      } else {
        setLastFailedQuestion(content);
        setLastFailedAttachments(attachments);
        setErrorMessage('error' in response ? response.error?.message ?? '回答生成失败，请重试。' : '回答生成失败，请重试。');
      }
    } catch (error) {
      setLastFailedQuestion(content);
      setLastFailedAttachments(attachments);
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
        <button className="new-chat-button" type="button" onClick={() => { if (isLoading) return; resetConversation(); }}><span className="button-icon" aria-hidden="true">＋</span>新建对话</button>
        <div className="history-tabs"><button className={!showArchived ? 'selected' : ''} type="button" onClick={() => setShowArchived(false)}>历史记录</button><button className={showArchived ? 'selected' : ''} type="button" onClick={() => setShowArchived(true)}>已归档</button><button type="button" className="clear-history" onClick={() => void clearAllHistory()} disabled={conversations.length === 0}>清空</button></div>
        <div className="conversation-list">{visibleConversations.length ? visibleConversations.map((conversation) => <div className={`conversation-item-wrap ${conversation.id === conversationId ? 'active' : ''}`} key={conversation.id}><button className="conversation-item" type="button" onClick={() => { if (isLoading) return; setConversationId(conversation.id); setMessages(conversation.messages); setErrorMessage(null); setPendingAttachments([]); setOpenMenuId(null); }}>{conversation.title || '未命名对话'}</button><button className="conversation-archive" type="button" aria-label="对话操作" onClick={() => setOpenMenuId(openMenuId === conversation.id ? null : conversation.id)}>…</button>{openMenuId === conversation.id && <div className="conversation-menu" role="menu"><button type="button" onClick={() => { void archiveConversation(conversation.id, !showArchived); setOpenMenuId(null); }}>{showArchived ? '恢复对话' : '归档对话'}</button><button type="button" onClick={() => { void deleteConversation(conversation.id); }}>删除对话</button></div>}</div>) : <div className="empty-history">{showArchived ? '暂无归档对话' : '暂无历史记录'}</div>}</div>
        <div className="sidebar-footer"><button className="sidebar-action" type="button" onClick={() => setShowAccount(true)}><span className="account-avatar" aria-hidden="true">你</span><span className="account-copy"><strong>本地账户</strong><small>设置 API 与记忆</small></span><span className="button-icon">⌄</span></button></div>
      </aside>

      <main className="chat-panel">
        <header className="chat-header"><span className="chat-title">{conversations.find((conversation) => conversation.id === conversationId)?.title || '新对话'}</span><div className="header-actions"><button className="icon-button" type="button" aria-label={themeLabel} title={themeLabel} onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}>{theme === 'light' ? '☾' : '☀'}</button><span className={`connection-state ${backendReady ? 'ready' : 'not-ready'}`}><span className="status-dot" />{backendReady ? '后端已就绪' : backendStatus.state === 'error' ? '后端启动失败' : '正在连接后端'}</span></div></header>
        <section className={`message-area ${hasMessages ? 'has-messages' : ''}`} aria-live="polite">
          {!hasMessages && !isLoading ? <div className="welcome-state"><img className="welcome-mark" src={yunheMark} alt="yunhe" /><h1>有什么可以帮忙的？</h1><p>输入问题，开始一段新的对话。</p></div> : <div className="message-list">
            {messages.map((message) => <article className={`message-row ${message.role}`} key={message.id}><div className="message-avatar" aria-hidden="true">{message.role === 'user' ? '你' : <img src={yunheMark} alt="yunhe" />}</div><div className="message-content">{message.role === 'assistant' ? <><ThinkingBlock thinking={message.thinking ?? ''} /><ReactMarkdown remarkPlugins={[remarkGfm, remarkMath]} rehypePlugins={[rehypeKatex]} urlTransform={markdownUrlTransform} components={{ a: ({ href, children, ...props }) => <a {...props} href={href} onClick={(event) => { if (isExternalLink(href)) { event.preventDefault(); void window.electronAPI?.openExternal?.(href!); } }}>{children}</a> }}>{message.content}</ReactMarkdown></> : <><AttachmentChips attachments={message.attachments ?? []} /><p>{message.content}</p></>}</div></article>)}
            {isLoading && <div className="message-row assistant"><div className="message-avatar" aria-hidden="true"><img src={yunheMark} alt="yunhe" /></div><div className="message-content loading-dots" aria-label="正在生成回答"><span /><span /><span /></div></div>}
            {errorMessage && <div className="error-state" role="alert"><span>{errorMessage}</span><button type="button" onClick={() => { setMessages((current) => { const last = current[current.length - 1]; return last?.role === 'user' && last.content === lastFailedQuestion ? current.slice(0, -1) : current; }); setQuestion(lastFailedQuestion); setPendingAttachments(lastFailedAttachments); setErrorMessage(null); }} aria-label="重新尝试">重新尝试</button></div>}
          </div>}
          {backendStatus.state !== 'ready' && <div className={`backend-status ${backendStatus.state === 'error' ? 'error' : ''}`} role="status"><span>{backendStatus.message || '正在启动后端服务…'}</span>{backendStatus.state === 'error' && <button type="button" onClick={() => void restartBackend()}>重新启动服务</button>}</div>}
        </section>
        <form className="composer-wrap" onSubmit={submitQuestion}>
          {pendingAttachments.length > 0 && (
            <div className="attachment-chips" role="list">
              {pendingAttachments.map((att, index) => (
                <span className="attachment-chip" role="listitem" key={`${att.name}-${index}`}>
                  <span className="attachment-icon" aria-hidden="true">📎</span>
                  <span className="attachment-name">{att.name}</span>
                  <button type="button" className="attachment-remove" aria-label={`移除 ${att.name}`} onClick={() => removePendingAttachment(index)}>×</button>
                </span>
              ))}
            </div>
          )}
          <div className="composer">
            <button type="button" className="composer-attach" onClick={() => void attachDocuments()} aria-label="添加文件" title="添加文件">＋</button>
            <textarea value={question} onChange={(event) => setQuestion(event.target.value)} placeholder={backendReady ? '发送消息' : '后端连接中，可先输入问题'} rows={1} maxLength={MAX_QUESTION_LENGTH} aria-label="问题输入框" disabled={isLoading} />
            <select className="composer-model" aria-label="模型选择" value={settings.model} onChange={(event) => setSettings({ ...settings, model: event.target.value })} disabled={isLoading}>{modelOptions.length ? modelOptions.map((model) => <option key={model} value={model}>{model}</option>) : <option value={settings.model}>{settings.model || '请在账户设置中填写模型'}</option>}{settings.model && !modelOptions.includes(settings.model) && modelOptions.length > 0 && <option value={settings.model}>{settings.model}</option>}</select>
            <button className="send-button" type="submit" disabled={!canSend} aria-label="发送消息">↑</button>
          </div>
          <p className="composer-hint">AI 可能会犯错，请核对重要信息。</p>
        </form>
      </main>
      {showAccount && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setShowAccount(false); }}><section className="account-modal" role="dialog" aria-modal="true" aria-labelledby="account-title"><div className="modal-header"><div><h2 id="account-title">账户与连接</h2><p>密钥只保存在本机，不会写入对话或日志。</p></div><button className="icon-button" type="button" aria-label="关闭账户设置" onClick={() => setShowAccount(false)}>×</button></div><label>服务类型<select value={settings.provider} onChange={(event) => { const provider = event.target.value as AiProvider; const preset = providerPresets[provider]; setSettings({ ...settings, provider, baseUrl: preset.baseUrl, model: preset.models[0] ?? settings.model }); }}><option value="deepseek">DeepSeek</option><option value="openai">OpenAI</option><option value="openai-compatible">其他 OpenAI 兼容服务</option></select></label><p className="field-hint">协议：{providerPresets[settings.provider].protocol}</p><label>API Key<input type="password" value={settings.apiKey} placeholder="sk-…" autoComplete="off" onChange={(event) => setSettings({ ...settings, apiKey: event.target.value })} /></label><label>接口地址<input value={settings.baseUrl} onChange={(event) => setSettings({ ...settings, baseUrl: event.target.value })} /></label><label>模型<select value={settings.model} onChange={(event) => setSettings({ ...settings, model: event.target.value })}>{providerPresets[settings.provider].models.map((model) => <option key={model} value={model}>{model}</option>)}{!providerPresets[settings.provider].models.includes(settings.model) && <option value={settings.model}>{settings.model}</option>}</select></label><label className="memory-toggle"><input type="checkbox" checked={settings.memoryEnabled} onChange={(event) => setSettings({ ...settings, memoryEnabled: event.target.checked })} />启用记忆</label>{settings.memoryEnabled && <label>记忆内容<textarea rows={3} maxLength={MAX_HISTORY_CONTENT} value={settings.memory} placeholder="例如：我偏好简洁的中文回答。" onChange={(event) => setSettings({ ...settings, memory: event.target.value })} /></label>}<p className="field-hint">支持 DeepSeek、OpenAI，以及使用 Chat Completions 协议的兼容服务。模型名称必须以服务商实际支持的名称为准。</p><div className="modal-actions"><button className="secondary-button" type="button" onClick={() => setShowAccount(false)}>取消</button><button className="primary-button" type="button" onClick={() => void saveSettings()}>{settingsSaved ? '已保存' : '保存并重启服务'}</button></div></section></div>}
    </div>
  );
}

export default App;

const root = document.getElementById('root');
if (root) createRoot(root).render(<StrictMode><App /></StrictMode>);
