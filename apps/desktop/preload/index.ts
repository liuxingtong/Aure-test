import { contextBridge, ipcRenderer } from 'electron';
import type { AiSettings, AskQuestionRequest, Conversation } from '@qa-desktop/shared-types';
import type { BackendStatus } from '../electron/backend-process-manager';

contextBridge.exposeInMainWorld('electronAPI', {
  askQuestion: (request: AskQuestionRequest) => ipcRenderer.invoke('question:ask', request),
  listHistory: () => ipcRenderer.invoke('history:list'),
  saveHistory: (conversation: Conversation) => ipcRenderer.invoke('history:save', conversation),
  deleteHistory: (id: string) => ipcRenderer.invoke('history:delete', id),
  setConversationArchived: (id: string, archived: boolean) => ipcRenderer.invoke('history:set-archived', id, archived),
  clearHistory: () => ipcRenderer.invoke('history:clear'),
  getVersion: () => ipcRenderer.invoke('system:get-version'),
  checkBackend: () => ipcRenderer.invoke('system:check-backend'),
  getBackendStatus: () => ipcRenderer.invoke('backend:status'),
  restartBackend: () => ipcRenderer.invoke('backend:restart'),
  getAiSettings: () => ipcRenderer.invoke('settings:get-ai'),
  saveAiSettings: (settings: AiSettings) => ipcRenderer.invoke('settings:save-ai', settings),
  listAiModels: (settings: AiSettings) => ipcRenderer.invoke('settings:list-models', settings),
  openExternal: (url: string) => ipcRenderer.invoke('shell:open-external', url),
  chooseDocument: () => ipcRenderer.invoke('document:choose'),
  onBackendStatus: (listener: (status: BackendStatus) => void) => {
    const wrapped = (_event: Electron.IpcRendererEvent, status: BackendStatus) => listener(status);
    ipcRenderer.on('backend:status', wrapped);
    return () => ipcRenderer.removeListener('backend:status', wrapped);
  }
});
