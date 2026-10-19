import { contextBridge, ipcRenderer } from 'electron';
import type { AskQuestionRequest, Conversation } from '@qa-desktop/shared-types';
import type { BackendStatus } from '../electron/backend-process-manager';

contextBridge.exposeInMainWorld('electronAPI', {
  askQuestion: (request: AskQuestionRequest) => ipcRenderer.invoke('question:ask', request),
  listHistory: () => ipcRenderer.invoke('history:list'),
  saveHistory: (conversation: Conversation) => ipcRenderer.invoke('history:save', conversation),
  deleteHistory: (id: string) => ipcRenderer.invoke('history:delete', id),
  clearHistory: () => ipcRenderer.invoke('history:clear'),
  getVersion: () => ipcRenderer.invoke('system:get-version'),
  checkBackend: () => ipcRenderer.invoke('system:check-backend'),
  getBackendStatus: () => ipcRenderer.invoke('backend:status'),
  onBackendStatus: (listener: (status: BackendStatus) => void) => {
    const wrapped = (_event: Electron.IpcRendererEvent, status: BackendStatus) => listener(status);
    ipcRenderer.on('backend:status', wrapped);
    return () => ipcRenderer.removeListener('backend:status', wrapped);
  }
});
