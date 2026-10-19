import { BrowserWindow, dialog, ipcMain, shell } from 'electron';
import { promises as fs } from 'node:fs';
import type { AiSettings, AskQuestionRequest, AskQuestionResponse, Conversation } from '@qa-desktop/shared-types';
import { askBackend } from './backend-client';
import type { BackendStatus } from './backend-process-manager';
import { HistoryRepository } from './history-repository';
import { SettingsStore } from './settings-store';
import { validateDocumentBuffer } from './document-picker';

export function publicAiSettings(settings: AiSettings): AiSettings {
  return { ...settings, apiKey: '', apiKeyConfigured: Boolean(settings.apiKey) };
}

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
  ipcMain.handle('settings:get-ai', async (): Promise<AiSettings> => publicAiSettings(await settingsStore.get()));
  ipcMain.handle('settings:save-ai', async (_event, settings: AiSettings): Promise<AiSettings> => publicAiSettings(await settingsStore.save(settings)));
  ipcMain.handle('settings:list-models', async (_event, settings: AiSettings): Promise<string[]> => {
    const stored = await settingsStore.get();
    const endpoint = `${settings.baseUrl.replace(/\/$/, '')}/models`;
    try {
      const response = await fetch(endpoint, { headers: stored.apiKey ? { Authorization: `Bearer ${stored.apiKey}` } : {} });
      if (!response.ok) return [];
      const payload = await response.json() as { data?: Array<{ id?: string }> };
      return (payload.data ?? []).flatMap((model) => model.id && !isNonChatModel(model.id) ? [model.id] : []);
    } catch {
      return [];
    }
  });
  ipcMain.handle('shell:open-external', async (_event, url: string): Promise<void> => {
    const parsed = new URL(url);
    if (!['https:', 'http:'].includes(parsed.protocol)) throw new Error('仅允许打开 HTTP/HTTPS 外部链接');
    await shell.openExternal(parsed.toString());
  });
  ipcMain.handle('document:choose', async (event) => {
    const window = BrowserWindow.fromWebContents(event.sender);
    const options = {
      properties: ['openFile'],
      filters: [{ name: '文档', extensions: ['md', 'txt', 'json'] }]
    };
    const result = window
      ? await dialog.showOpenDialog(window, options)
      : await dialog.showOpenDialog(options);
    if (result.canceled || result.filePaths.length === 0) return null;
    const filePath = result.filePaths[0];
    try {
      return validateDocumentBuffer(filePath, await fs.readFile(filePath));
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'ENOENT') throw new Error('文件不存在');
      throw error;
    }
  });
}

function isNonChatModel(id: string): boolean {
  return /(?:embedding|moderation|whisper|tts|dall-e|image|audio|realtime|transcribe|search-preview)/i.test(id);
}
