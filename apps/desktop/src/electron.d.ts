import type { AskQuestionRequest, AskQuestionResponse, Conversation } from '@qa-desktop/shared-types';
import type { BackendStatus } from '../electron/backend-process-manager';

interface ElectronAPI {
  askQuestion(request: AskQuestionRequest): Promise<AskQuestionResponse>;
  listHistory(): Promise<Conversation[]>;
  saveHistory(conversation: Conversation): Promise<void>;
  deleteHistory(id: string): Promise<void>;
  clearHistory(): Promise<void>;
  getVersion(): Promise<string>;
  checkBackend(): Promise<boolean>;
  getBackendStatus(): Promise<BackendStatus>;
  onBackendStatus(listener: (status: BackendStatus) => void): () => void;
}

declare global { interface Window { electronAPI?: ElectronAPI; } }
export {};
