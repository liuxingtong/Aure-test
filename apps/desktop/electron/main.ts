import { app, BrowserWindow } from 'electron';
import path from 'node:path';
import { BackendStatusBroadcaster, registerIpcHandlers } from './ipc';
import { BackendProcessManager } from './backend-process-manager';
import { setBackendReady } from './backend-client';
import { HistoryRepository } from './history-repository';

function createWindow() {
  const window = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 960,
    minHeight: 640,
    webPreferences: {
      preload: path.join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  if (process.env.VITE_DEV_SERVER_URL) {
    void window.loadURL(process.env.VITE_DEV_SERVER_URL);
  } else {
    void window.loadFile(path.join(__dirname, '../../dist/index.html'));
  }
}

app.whenReady().then(() => {
  const statusBroadcaster = new BackendStatusBroadcaster();
  const backendManager = new BackendProcessManager({
    mode: app.isPackaged ? 'production' : 'development',
    platform: process.platform,
    resourcesPath: process.resourcesPath,
    userDataPath: app.getPath('userData'),
    projectRoot: path.resolve(__dirname, '../../..')
  });
  backendManager.onStatusChange((status) => {
    setBackendReady(status.state === 'ready');
    statusBroadcaster.publish(status);
    for (const window of BrowserWindow.getAllWindows()) window.webContents.send('backend:status', status);
  });
  registerIpcHandlers(
    new HistoryRepository(path.join(app.getPath('userData'), 'conversations.json')),
    () => statusBroadcaster.current(),
    async () => { await backendManager.stop(); return backendManager.start(); }
  );
  const startup = backendManager.start();
  createWindow();
  void startup;
  app.on('before-quit', (event) => {
    event.preventDefault();
    void backendManager.stop().finally(() => app.exit(0));
  });
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
