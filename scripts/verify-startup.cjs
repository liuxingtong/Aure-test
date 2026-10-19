const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const platform = value('--platform', process.platform);
const arch = value('--arch', process.arch);
const managerPath = path.resolve(__dirname, '../apps/desktop/dist-electron/electron/backend-process-manager.js');
const { BackendProcessManager } = require(managerPath);

let killedWith;
const exitListeners = [];
const child = {
  killed: false,
  kill(signal) {
    killedWith = signal;
    this.killed = true;
    // 模拟子进程收到 SIGTERM 后退出，避免 stop() 超时后在测试里发出 SIGKILL。
    for (const listener of exitListeners.splice(0)) listener(0);
    return true;
  },
  once(event, listener) {
    if (event === 'exit') exitListeners.push(listener);
    return this;
  },
  stdout: { on() {} },
  stderr: { on() {} }
};

const manager = new BackendProcessManager({
  mode: 'production',
  platform,
  arch,
  resourcesPath: path.join(os.tmpdir(), 'qa-runtime-resources'),
  userDataPath: fs.mkdtempSync(path.join(os.tmpdir(), 'qa-startup-')),
  spawn: () => child,
  fetch: async () => new Response(JSON.stringify({ status: 'UP' }), { status: 200 }),
  portInUse: async () => false,
  clock: { sleep: async () => undefined }
});

(async () => {
  const ready = await manager.start();
  assert.equal(ready.state, 'ready');
  await manager.stop();
  assert.equal(killedWith, 'SIGTERM');
  assert.equal(manager.getStatus().state, 'stopped');
  console.log(JSON.stringify({ platform, arch, startup: 'ready', cleanup: 'stopped' }));
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

function value(name, fallback) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}
