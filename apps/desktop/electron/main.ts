import { app, BrowserWindow, safeStorage } from 'electron';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { BackendStatusBroadcaster, registerIpcHandlers } from './ipc';
import { BackendProcessManager } from './backend-process-manager';
import { setBackendReady } from './backend-client';
import { SettingsStore } from './settings-store';

const hasSingleInstanceLock = app.requestSingleInstanceLock();
let isQuitting = false;
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

app.whenReady().then(async () => {
  if (!hasSingleInstanceLock) return;
  const statusBroadcaster = new BackendStatusBroadcaster();
  const userDataPath = app.getPath('userData');
  await migrateLegacyUserData(userDataPath).catch((error) => {
    console.warn('[main] legacy user data migration failed', error);
  });
  const settingsStore = new SettingsStore(path.join(userDataPath, 'settings.json'), {
    encrypt: (value) => safeStorage.isEncryptionAvailable() ? safeStorage.encryptString(value).toString('base64') : value,
    decrypt: (value) => safeStorage.isEncryptionAvailable() ? safeStorage.decryptString(Buffer.from(value, 'base64')) : value
  });
  const backendManager = new BackendProcessManager({
    mode: app.isPackaged ? 'production' : 'development',
    platform: process.platform,
    resourcesPath: process.resourcesPath,
    userDataPath,
    projectRoot: path.resolve(__dirname, '../../../..'),
    getEnvironment: () => process.env
  });
  backendManager.onStatusChange((status) => {
    setBackendReady(status.state === 'ready');
    statusBroadcaster.publish(status);
    for (const window of BrowserWindow.getAllWindows()) window.webContents.send('backend:status', status);
  });
  registerIpcHandlers(
    new HistoryRepository(path.join(userDataPath, 'conversations.json')),
    settingsStore,
    () => statusBroadcaster.current(),
    async () => {
      await backendManager.stop();
      try {
        applySettingsToEnvironment(await settingsStore.get());
      } catch (error) {
        console.warn('[main] settings reload failed, keeping current environment', error);
      }
      return backendManager.start();
    }
  );
  const startup = settingsStore.get()
    .then((settings) => {
      applySettingsToEnvironment(settings);
      return backendManager.start();
    })
    .catch((error) => {
      console.warn('[main] settings load failed, starting with current environment', error);
      return backendManager.start();
    });
  createWindow();
  void startup;
  app.on('before-quit', (event) => {
    if (isQuitting) return;
    isQuitting = true;
    event.preventDefault();
    void backendManager.stop().finally(() => app.exit(0));
  });
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('second-instance', () => {
  const window = BrowserWindow.getAllWindows()[0];
  if (window) {
    if (window.isMinimized()) window.restore();
    window.focus();
  }
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

function applySettingsToEnvironment(settings: { apiKey: string; provider: string; baseUrl: string; model: string }): void {
  // 设置文件为空时允许 .env.local 起步（开发环境常见用法）。
  const apiKey = settings.apiKey || process.env.AI_API_KEY || '';
  process.env.AI_ENABLED = apiKey ? 'true' : 'false';
  process.env.AI_PROVIDER = settings.provider;
  process.env.AI_BASE_URL = settings.baseUrl;
  process.env.AI_MODEL = settings.model;
  process.env.AI_API_KEY = apiKey;
}

async function migrateLegacyUserData(userDataPath: string): Promise<void> {
  const legacyDirectory = path.join(app.getPath('appData'), '@qa-desktop', 'desktop');
  await fs.mkdir(userDataPath, { recursive: true });
  for (const file of ['conversations.json', 'settings.json']) {
    const destination = path.join(userDataPath, file);
    try {
      await fs.access(destination);
      continue;
    } catch {
      // Destination does not exist; copy legacy data if available.
    }
    try {
      await fs.copyFile(path.join(legacyDirectory, file), destination);
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
    }
  }
}
