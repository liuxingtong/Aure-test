import { StrictMode, FormEvent, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import './styles.css';

type Theme = 'light' | 'dark';
type Role = 'user' | 'assistant';
type Message = { id: string; role: Role; content: string };

function App() {
  const [theme, setTheme] = useState<Theme>('light');
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const hasMessages = messages.length > 0;
  const canSend = question.trim().length > 0 && !isLoading;
  const themeLabel = useMemo(() => theme === 'light' ? '切换到深色主题' : '切换到浅色主题', [theme]);

  function submitQuestion(event: FormEvent) {
    event.preventDefault();
    if (!canSend) return;
    const content = question.trim();
    setQuestion('');
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: 'user', content }]);
    setIsLoading(true);
    // 阶段 2 仅用于展示 UI 状态；阶段 3 将替换为 Electron IPC。
    window.setTimeout(() => {
      setMessages((current) => [...current, { id: crypto.randomUUID(), role: 'assistant', content: '这是 UI 演示回答。\n\n- 当前正在展示 Markdown 渲染\n- 后续阶段将接入 Electron IPC 和 Spring Boot' }]);
      setIsLoading(false);
    }, 650);
  }

  return (
    <div className="app-shell" data-theme={theme}>
      <aside className="sidebar" aria-label="会话列表">
        <div className="sidebar-header"><div className="app-mark" aria-hidden="true">问</div><span>问答桌面应用</span></div>
        <button className="new-chat-button" type="button" onClick={() => setMessages([])}><span className="button-icon" aria-hidden="true">＋</span>新建对话</button>
        <div className="sidebar-section-label">历史记录</div>
        <div className="conversation-list">{hasMessages ? <button className="conversation-item active" type="button">当前对话</button> : <div className="empty-history">暂无历史记录</div>}</div>
        <div className="sidebar-footer"><button className="sidebar-action" type="button" onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}><span className="button-icon" aria-hidden="true">◐</span>{themeLabel}</button></div>
      </aside>

      <main className="chat-panel">
        <header className="chat-header"><span className="chat-title">新对话</span><span className="connection-state"><span className="status-dot" />本地演示</span></header>
        <section className={`message-area ${hasMessages ? 'has-messages' : ''}`} aria-live="polite">
          {!hasMessages && !isLoading ? <div className="welcome-state"><div className="welcome-mark" aria-hidden="true">问</div><h1>有什么可以帮忙的？</h1><p>输入问题，开始一段新的对话。</p></div> : <div className="message-list">
            {messages.map((message) => <article className={`message-row ${message.role}`} key={message.id}><div className="message-avatar" aria-hidden="true">{message.role === 'user' ? '你' : '问'}</div><div className="message-content">{message.role === 'assistant' ? <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown> : <p>{message.content}</p>}</div></article>)}
            {isLoading && <div className="message-row assistant"><div className="message-avatar" aria-hidden="true">问</div><div className="message-content loading-dots" aria-label="正在生成回答"><span /><span /><span /></div></div>}
          </div>}
        </section>
        <form className="composer-wrap" onSubmit={submitQuestion}><div className="composer"><textarea value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="发送消息" rows={1} aria-label="问题输入框" disabled={isLoading} /><button className="send-button" type="submit" disabled={!canSend} aria-label="发送消息">↑</button></div><p className="composer-hint">AI 可能会犯错，请核对重要信息。</p></form>
      </main>
    </div>
  );
}

export default App;

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
