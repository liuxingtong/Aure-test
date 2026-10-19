import { ipcMain, shell } from 'electron';
import type { AiSettings, AskQuestionRequest, AskQuestionResponse, Conversation } from '@qa-desktop/shared-types';
import { askBackend } from './backend-client';
import type { BackendStatus } from './backend-process-manager';
import { HistoryRepository } from './history-repository';
import { SettingsStore } from './settings-store';

export class BackendStatusBroadcaster {
  private status: BackendStatus = { state: 'stopped' };
  private readonly listeners = new Set<(status: BackendStatus) => void>();

  current(): BackendStatus {
    return { ...this.status };
  }

  publish(status: BackendStatus): void {
    this.status = { ...status };
    for (const listener of this.listeners) listener(this.current());
  }

  subscribe(listener: (status: BackendStatus) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}

export function registerIpcHandlers(
  historyRepository: HistoryRepository,
  settingsStore: SettingsStore = new SettingsStore('settings.json'),
  getBackendStatus: () => BackendStatus = () => ({ state: 'stopped' }),
  restartBackend: () => Promise<BackendStatus> = async () => getBackendStatus()
) {
  ipcMain.handle('question:ask', async (_event, request: AskQuestionRequest): Promise<AskQuestionResponse> => {
    const question = request.question.trim();
    if (!question) return { success: false, error: { code: 'VALIDATION_ERROR', message: '问题不能为空', retryable: false } };
    return askBackend({ ...request, question });
  });
  ipcMain.handle('history:list', async (): Promise<Conversation[]> => historyRepository.list());
  ipcMain.handle('history:save', async (_event, conversation: Conversation): Promise<void> => historyRepository.save(conversation));
  ipcMain.handle('history:delete', async (_event, id: string): Promise<void> => historyRepository.delete(id));
  ipcMain.handle('history:set-archived', async (_event, id: string, archived: boolean): Promise<void> => historyRepository.setArchived(id, archived));
  ipcMain.handle('history:clear', async (): Promise<void> => historyRepository.clear());
  ipcMain.handle('system:get-version', async (): Promise<string> => '0.1.0');
  ipcMain.handle('system:check-backend', async (): Promise<boolean> => getBackendStatus().state === 'ready');
  ipcMain.handle('backend:status', async (): Promise<BackendStatus> => getBackendStatus());
  ipcMain.handle('backend:restart', async (): Promise<BackendStatus> => restartBackend());
  ipcMain.handle('settings:get-ai', async (): Promise<AiSettings> => settingsStore.get());
  ipcMain.handle('settings:save-ai', async (_event, settings: AiSettings): Promise<AiSettings> => settingsStore.save(settings));
  ipcMain.handle('settings:list-models', async (_event, settings: AiSettings): Promise<string[]> => {
    const endpoint = `${settings.baseUrl.replace(/\/$/, '')}/models`;
    try {
      const response = await fetch(endpoint, { headers: settings.apiKey ? { Authorization: `Bearer ${settings.apiKey}` } : {} });
      if (!response.ok) return [];
      const payload = await response.json() as { data?: Array<{ id?: string }> };
      return (payload.data ?? []).flatMap((model) => model.id && !isNonChatModel(model.id) ? [model.id] : []);
    } catch {
      return [];
    }
  });
  ipcMain.handle('shell:open-external', async (_event, url: string): Promise<void> => {
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:') throw new Error('仅允许打开 HTTPS 外部链接');
    await shell.openExternal(parsed.toString());
  });
}

function isNonChatModel(id: string): boolean {
  return /(?:embedding|moderation|whisper|tts|dall-e|image|audio|realtime|transcribe|search-preview)/i.test(id);
}
