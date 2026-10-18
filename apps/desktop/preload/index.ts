import { contextBridge, ipcRenderer } from 'electron';
import type { AskQuestionRequest, Conversation } from '@qa-desktop/shared-types';

contextBridge.exposeInMainWorld('electronAPI', {
  askQuestion: (request: AskQuestionRequest) => ipcRenderer.invoke('question:ask', request),
  listHistory: () => ipcRenderer.invoke('history:list'),
  saveHistory: (conversation: Conversation) => ipcRenderer.invoke('history:save', conversation),
  deleteHistory: (id: string) => ipcRenderer.invoke('history:delete', id),
  clearHistory: () => ipcRenderer.invoke('history:clear'),
  getVersion: () => ipcRenderer.invoke('system:get-version'),
  checkBackend: () => ipcRenderer.invoke('system:check-backend')
});
