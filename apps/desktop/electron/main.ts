import { app, BrowserWindow } from 'electron';
import path from 'node:path';
import { registerIpcHandlers } from './ipc';
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
  registerIpcHandlers(new HistoryRepository(path.join(app.getPath('userData'), 'conversations.json')));
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
