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
}

export interface AskQuestionRequest {
  conversationId: string;
  question: string;
  history: ChatMessage[];
}

export interface AskQuestionResponse {
  success: boolean;
  data?: { messageId: string; answer: string; toolCalls?: string[] };
  error?: { code: string; message: string; retryable: boolean };
}
