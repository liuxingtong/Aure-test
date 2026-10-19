export type MessageRole = 'user' | 'assistant';

export interface ChatMessage {
  id: string;
  role: MessageRole;
  content: string;
  createdAt: string;
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
  memoryEnabled: boolean;
  memory: string;
  apiKeyConfigured?: boolean;
}

export interface AskQuestionRequest {
  conversationId: string;
  question: string;
  history: ChatMessage[];
  model?: string;
  document?: { name: string; extension: string; content: string };
}

export interface AskQuestionResponse {
  success: boolean;
  data?: { messageId: string; answer: string; toolCalls?: string[] };
  error?: { code: string; message: string; retryable: boolean };
}
