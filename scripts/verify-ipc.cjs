const { app, BrowserWindow } = require('electron');
const path = require('node:path');

app.whenReady().then(async () => {
  const window = new BrowserWindow({
    show: false,
    webPreferences: {
      preload: path.resolve('apps/desktop/dist-electron/preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  require(path.resolve('apps/desktop/dist-electron/electron/ipc.js')).registerIpcHandlers();
  await window.loadFile(path.resolve('apps/desktop/dist/index.html'));
  const result = await window.webContents.executeJavaScript(`(async () => {
    const version = await window.electronAPI.getVersion();
    const question = await window.electronAPI.askQuestion({ conversationId: 'verify', question: 'IPC 验证', history: [] });
    await window.electronAPI.saveHistory({ id: 'verify', title: 'IPC 验证', messages: [], createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() });
    const history = await window.electronAPI.listHistory();
    await window.electronAPI.clearHistory();
    return { version, questionSuccess: question.success, answer: question.data.answer, historyCount: history.length };
  })()`);
  console.log(JSON.stringify(result));
  app.quit();
});
