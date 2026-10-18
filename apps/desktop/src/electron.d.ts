import type { AskQuestionRequest, AskQuestionResponse, Conversation } from '@qa-desktop/shared-types';

interface ElectronAPI {
  askQuestion(request: AskQuestionRequest): Promise<AskQuestionResponse>;
  listHistory(): Promise<Conversation[]>;
  saveHistory(conversation: Conversation): Promise<void>;
  deleteHistory(id: string): Promise<void>;
  clearHistory(): Promise<void>;
  getVersion(): Promise<string>;
  checkBackend(): Promise<boolean>;
}

declare global { interface Window { electronAPI?: ElectronAPI; } }
export {};
