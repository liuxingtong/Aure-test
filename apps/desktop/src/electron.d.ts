import type { AiSettings, AskQuestionRequest, AskQuestionResponse, Conversation } from '@qa-desktop/shared-types';
import type { BackendStatus } from '../electron/backend-process-manager';

interface ElectronAPI {
  askQuestion(request: AskQuestionRequest): Promise<AskQuestionResponse>;
  listHistory(): Promise<Conversation[]>;
  saveHistory(conversation: Conversation): Promise<void>;
  deleteHistory(id: string): Promise<void>;
  setConversationArchived?(id: string, archived: boolean): Promise<void>;
  clearHistory(): Promise<void>;
  getVersion(): Promise<string>;
  checkBackend(): Promise<boolean>;
  getBackendStatus(): Promise<BackendStatus>;
  restartBackend(): Promise<BackendStatus>;
  getAiSettings?(): Promise<AiSettings>;
  saveAiSettings?(settings: AiSettings): Promise<AiSettings>;
  listAiModels?(settings: AiSettings): Promise<string[]>;
  openExternal?(url: string): Promise<void>;
  chooseDocument?(): Promise<{ name: string; extension: string; content: string } | null>;
  onBackendStatus(listener: (status: BackendStatus) => void): () => void;
}

declare global { interface Window { electronAPI?: ElectronAPI; } }
export {};
