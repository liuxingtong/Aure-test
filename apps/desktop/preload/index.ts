import { contextBridge } from 'electron';

contextBridge.exposeInMainWorld('electronAPI', {
  version: '0.1.0'
});
