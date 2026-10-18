import { StrictMode, FormEvent, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import './styles.css';

type Theme = 'light' | 'dark';
type Role = 'user' | 'assistant';
type Message = { id: string; role: Role; content: string; createdAt: string };
export function App() {
  const [theme, setTheme] = useState<Theme>('light');
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastFailedQuestion, setLastFailedQuestion] = useState('');
  const [conversationId, setConversationId] = useState('local-conversation');
  const [conversations, setConversations] = useState<Array<{ id: string; title: string; messages: Message[]; createdAt: string; updatedAt: string }>>([]);
  const hasMessages = messages.length > 0;
  const canSend = question.trim().length > 0 && !isLoading;
  const themeLabel = useMemo(() => theme === 'light' ? '切换到深色主题' : '切换到浅色主题', [theme]);

  useEffect(() => {
    void window.electronAPI?.listHistory().then((items) => setConversations(items));
  }, []);

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
      const response = window.electronAPI
        ? await window.electronAPI.askQuestion({ conversationId, question: content, history: messages })
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

  return (
    <div className="app-shell" data-theme={theme}>
      <aside className="sidebar" aria-label="会话列表">
        <div className="sidebar-header"><div className="app-mark" aria-hidden="true">问</div><span>问答桌面应用</span></div>
        <button className="new-chat-button" type="button" onClick={() => { setConversationId(crypto.randomUUID()); setMessages([]); }}><span className="button-icon" aria-hidden="true">＋</span>新建对话</button>
        <div className="sidebar-section-label">历史记录</div>
        <div className="conversation-list">{conversations.length ? conversations.map((conversation) => <button className={`conversation-item ${conversation.id === conversationId ? 'active' : ''}`} key={conversation.id} type="button" onClick={() => { setConversationId(conversation.id); setMessages(conversation.messages); }}>{conversation.title || '未命名对话'}</button>) : <div className="empty-history">暂无历史记录</div>}</div>
        <div className="sidebar-footer"><button className="sidebar-action" type="button" onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}><span className="button-icon" aria-hidden="true">◐</span>{themeLabel}</button></div>
      </aside>

      <main className="chat-panel">
        <header className="chat-header"><span className="chat-title">{conversations.find((conversation) => conversation.id === conversationId)?.title || '新对话'}</span><span className="connection-state"><span className="status-dot" />本地演示</span></header>
        <section className={`message-area ${hasMessages ? 'has-messages' : ''}`} aria-live="polite">
          {!hasMessages && !isLoading ? <div className="welcome-state"><div className="welcome-mark" aria-hidden="true">问</div><h1>有什么可以帮忙的？</h1><p>输入问题，开始一段新的对话。</p></div> : <div className="message-list">
            {messages.map((message) => <article className={`message-row ${message.role}`} key={message.id}><div className="message-avatar" aria-hidden="true">{message.role === 'user' ? '你' : '问'}</div><div className="message-content">{message.role === 'assistant' ? <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown> : <p>{message.content}</p>}</div></article>)}
            {isLoading && <div className="message-row assistant"><div className="message-avatar" aria-hidden="true">问</div><div className="message-content loading-dots" aria-label="正在生成回答"><span /><span /><span /></div></div>}
            {errorMessage && <div className="error-state" role="alert"><span>{errorMessage}</span><button type="button" onClick={() => { setQuestion(lastFailedQuestion); setErrorMessage(null); }} aria-label="重新尝试">重新尝试</button></div>}
          </div>}
        </section>
        <form className="composer-wrap" onSubmit={submitQuestion}><div className="composer"><textarea value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="发送消息" rows={1} aria-label="问题输入框" disabled={isLoading} /><button className="send-button" type="submit" disabled={!canSend} aria-label="发送消息">↑</button></div><p className="composer-hint">AI 可能会犯错，请核对重要信息。</p></form>
      </main>
    </div>
  );
}

export default App;

const root = document.getElementById('root');
if (root) createRoot(root).render(<StrictMode><App /></StrictMode>);
