export type MessageRole = 'user' | 'assistant';

export interface ChatMessage {
  id: string;
  role: MessageRole;
  content: string;
  createdAt: string;
  thinking?: string;
  /** 这次消息附带的文件列表（仅在 role=user 的新发消息上有值）。 */
  attachments?: DocumentAttachment[];
}

export interface DocumentAttachment {
  name: string;
  extension: string;
  /** 文档内容（utf-8 文本），由 Electron 文件选择器读取后注入。 */
  content: string;
}

export interface Conversation {
  id: string;
  title: string;
  messages: ChatMessage[];
  createdAt: string;
  updatedAt: string;
  archived?: boolean;
}

export type AiProvider = 'deepseek' | 'openai' | 'openai-compatible';

export interface AiSettings {
  provider: AiProvider;
  baseUrl: string;
  model: string;
  apiKey: string;
  apiKeyConfigured?: boolean;
}

export interface AskQuestionRequest {
  conversationId: string;
  question: string;
  history: ChatMessage[];
  model?: string;
  /** 旧版：单文件附件，保留向后兼容。新逻辑请使用 documents。 */
  document?: DocumentAttachment;
  /** 新版：多文件附件，会作为 read_document 工具的 context 提供给模型。 */
  documents?: DocumentAttachment[];
}

export interface AskQuestionResponse {
  success: boolean;
  data?: { messageId: string; answer: string; toolCalls?: string[]; thinking?: string };
  error?: { code: string; message: string; retryable: boolean };
}
