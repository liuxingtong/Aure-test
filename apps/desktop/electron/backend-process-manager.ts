import { appendFileSync, mkdirSync, statSync, renameSync } from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { spawn as systemSpawn, type ChildProcess } from 'node:child_process';
import process from 'node:process';

export type BackendState = 'starting' | 'ready' | 'error' | 'stopped';

export type BackendStatus = {
  state: BackendState;
  message?: string;
};

type SpawnedChild = {
  killed?: boolean;
  kill: (signal?: NodeJS.Signals) => boolean;
  once: (event: string, listener: (code?: number) => void) => unknown;
  stdout?: { on: (event: string, listener: (chunk: unknown) => void) => unknown };
  stderr?: { on: (event: string, listener: (chunk: unknown) => void) => unknown };
};

type BackendMode = 'development' | 'production';

type Clock = {
  now?: () => number;
  sleep: (milliseconds: number) => Promise<void>;
};

type BackendProcessManagerOptions = {
  mode: BackendMode;
  platform: NodeJS.Platform;
  arch?: string;
  resourcesPath: string;
  userDataPath: string;
  projectRoot?: string;
  spawn?: (command: string, args: string[], options: { cwd?: string; env?: NodeJS.ProcessEnv }) => SpawnedChild;
  fetch?: typeof globalThis.fetch;
  clock?: Clock;
  portInUse?: () => Promise<boolean>;
  getEnvironment?: () => NodeJS.ProcessEnv;
};

const BACKEND_HOST = '127.0.0.1';
const BACKEND_PORT = 8080;
const HEALTH_URL = `http://${BACKEND_HOST}:${BACKEND_PORT}/api/v1/health`;
const HEALTH_TIMEOUT_MS = 30_000;
const STOP_TIMEOUT_MS = 3_000;
const POLL_INTERVAL_MS = 500;
const MAX_LOG_BYTES = 2 * 1024 * 1024;

export class BackendProcessManager {
  private readonly options: Required<Pick<BackendProcessManagerOptions, 'mode' | 'platform' | 'resourcesPath' | 'userDataPath'>> & BackendProcessManagerOptions;
  private readonly spawn: NonNullable<BackendProcessManagerOptions['spawn']>;
  private readonly fetch: typeof globalThis.fetch;
  private readonly clock: Required<Clock>;
  private readonly portInUse: () => Promise<boolean>;
  private child: SpawnedChild | undefined;
  private childExitMessage: string | undefined;
  private status: BackendStatus = { state: 'stopped' };
  private readonly statusListeners = new Set<(status: BackendStatus) => void>();

  constructor(options: BackendProcessManagerOptions) {
    this.options = { ...options, arch: options.arch ?? process.arch };
    this.spawn = options.spawn ?? ((command, args, spawnOptions) => systemSpawn(command, args, { ...spawnOptions, stdio: ['pipe', 'pipe', 'pipe'] }));
    this.fetch = options.fetch ?? globalThis.fetch;
    this.clock = { now: Date.now, sleep: (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds)), ...options.clock };
    this.portInUse = options.portInUse ?? (() => isPortInUse(BACKEND_PORT));
  }

  getStatus(): BackendStatus {
    return { ...this.status };
  }

  onStatusChange(listener: (status: BackendStatus) => void): () => void {
    this.statusListeners.add(listener);
    return () => this.statusListeners.delete(listener);
  }

  async start(): Promise<BackendStatus> {
    if (this.status.state === 'ready' || this.status.state === 'starting') return this.getStatus();

    this.setStatus({ state: 'starting', message: '正在启动后端服务…' });
    this.childExitMessage = undefined;

    if (await this.portInUse()) {
      return this.fail(`端口 ${BACKEND_PORT} 已被占用，请关闭占用该端口的服务后重试。`);
    }

    const command = this.command();
    const child = this.spawn(command.command, command.args, {
      cwd: command.cwd,
      env: {
        ...process.env,
        ...(this.options.getEnvironment?.() ?? {}),
        AI_ENABLED: this.options.getEnvironment?.().AI_ENABLED ?? process.env.AI_ENABLED ?? 'false',
        AI_TRACE_DIRECTORY: path.join(this.options.userDataPath, 'traces')
      }
    });
    this.child = child;
    this.attachOutput(child);
    child.once('exit', (code) => {
      this.childExitMessage = `后端进程已退出（代码 ${code ?? 'unknown'}）。`;
      if (this.status.state === 'starting' || this.status.state === 'ready') this.setStatus({ state: 'error', message: this.childExitMessage });
    });

    const deadline = this.clock.now() + HEALTH_TIMEOUT_MS;
    while (this.clock.now() < deadline) {
      if (this.childExitMessage) return this.fail(this.childExitMessage);
      try {
        const response = await this.fetch(HEALTH_URL, { signal: AbortSignal.timeout(2_000) });
        if (response.ok) {
          this.setStatus({ state: 'ready', message: '后端服务已就绪' });
          return this.getStatus();
        }
      } catch {
        // The process may need several polling intervals to bind its port.
      }
      await this.clock.sleep(POLL_INTERVAL_MS);
    }

    return this.fail('后端服务启动超时（30 秒），请重试。');
  }

  async stop(): Promise<void> {
    const child = this.child;
    this.child = undefined;
    if (child && !child.killed) {
      let exited = false;
      const exitedPromise = new Promise<void>((resolve) => {
        child.once('exit', () => { exited = true; resolve(); });
      });
      child.kill('SIGTERM');
      await Promise.race([exitedPromise, this.clock.sleep(STOP_TIMEOUT_MS)]);
      // 优雅退出失败时必须强制结束，否则应用退出后会残留 Java 进程。
      if (!exited) child.kill('SIGKILL');
    }
    this.setStatus({ state: 'stopped' });
  }

  private fail(message: string): BackendStatus {
    this.setStatus({ state: 'error', message });
    if (this.child && !this.child.killed) this.child.kill('SIGKILL');
    return this.getStatus();
  }

  private command(): { command: string; args: string[]; cwd?: string } {
    if (this.options.mode === 'development') {
      return { command: 'mvn', args: ['-q', '-f', 'apps/backend/pom.xml', 'spring-boot:run'], cwd: this.options.projectRoot ?? process.cwd() };
    }
    const platformDir = this.options.platform === 'win32' ? 'win-x64' : 'mac-arm64';
    const javaName = this.options.platform === 'win32' ? 'java.exe' : 'java';
    const java = path.join(this.options.resourcesPath, 'jre', platformDir, 'bin', javaName);
    const jar = path.join(this.options.resourcesPath, 'backend', 'qa-backend-0.1.0.jar');
    return { command: java, args: ['-jar', jar, '--server.address=' + BACKEND_HOST, '--server.port=' + BACKEND_PORT] };
  }

  private attachOutput(child: SpawnedChild): void {
    const logDirectory = path.join(this.options.userDataPath, 'logs');
    const logFile = path.join(logDirectory, 'backend.log');
    mkdirSync(logDirectory, { recursive: true });
    const write = (chunk: unknown) => {
      const text = String(chunk).replace(/(AI_API_KEY=|Authorization: Bearer )[^\s]+/g, '$1[REDACTED]');
      try {
        if (statSync(logFile).size + Buffer.byteLength(text) > MAX_LOG_BYTES) renameSync(logFile, `${logFile}.1`);
      } catch {
        // The log file may not exist on the first output chunk.
      }
      appendFileSync(logFile, text, 'utf8');
    };
    child.stdout?.on('data', write);
    child.stderr?.on('data', write);
  }

  private setStatus(status: BackendStatus): void {
    this.status = { ...status };
    for (const listener of this.statusListeners) listener(this.getStatus());
  }
}

function isPortInUse(port: number): Promise<boolean> {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host: BACKEND_HOST, port });
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => { socket.destroy(); resolve(false); });
  });
}
