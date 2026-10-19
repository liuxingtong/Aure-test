import { promises as fs } from 'node:fs';
import path from 'node:path';
import type { AiSettings } from '@qa-desktop/shared-types';

export const defaultAiSettings: AiSettings = {
  provider: 'deepseek',
  baseUrl: 'https://api.deepseek.com',
  model: 'deepseek-chat',
  apiKey: ''
};

type StoredSettings = Omit<AiSettings, 'apiKey'> & { apiKey: string };
export type SecretCodec = { encrypt(value: string): string; decrypt(value: string): string };

function normalizeModel(provider: AiSettings['provider'], model: string): string {
  if (provider === 'deepseek' && ['deepseek-flash', 'deepseek-v4-pro'].includes(model)) return 'deepseek-chat';
  if (provider === 'openai' && ['gpt-6-astra', 'gpt-6.1-sol', 'gpt-6-luna', 'gpt-5.6-sol', 'gpt-5.6-cyber'].includes(model)) return 'gpt-4.1-mini';
  return model;
}

export class SettingsStore {
  private cached: AiSettings | undefined;
  constructor(private readonly filePath: string, private readonly secretCodec?: SecretCodec) {}

  async get(): Promise<AiSettings> {
    if (this.cached) return { ...this.cached };
    try {
      const raw = JSON.parse(await fs.readFile(this.filePath, 'utf8')) as Partial<StoredSettings>;
      const storedKey = typeof raw.apiKey === 'string' ? raw.apiKey : '';
      const merged = { ...defaultAiSettings, ...raw, apiKey: this.secretCodec && storedKey.startsWith('enc:') ? this.secretCodec.decrypt(storedKey.slice(4)) : storedKey };
      this.cached = { ...merged, model: normalizeModel(merged.provider, merged.model) };
      if (this.cached.model !== merged.model) await this.write(this.cached);
    } catch (error) {
      const code = (error as NodeJS.ErrnoException).code;
      if (code !== 'ENOENT') {
        // settings.json 损坏或 keychain 解密失败时不能阻塞启动；回退默认设置并保留告警。
        console.warn('[settings] failed to load settings, using defaults', error);
      }
      this.cached = { ...defaultAiSettings };
    }
    return { ...this.cached };
  }

  async save(settings: AiSettings): Promise<AiSettings> {
    const existingKey = this.cached?.apiKey ?? '';
    this.cached = { ...defaultAiSettings, ...settings, apiKey: settings.apiKey || existingKey };
    await this.write(this.cached);
    return { ...this.cached };
  }

  private async write(settings: AiSettings): Promise<void> {
    await fs.mkdir(path.dirname(this.filePath), { recursive: true });
    const stored = { ...settings, apiKey: this.secretCodec && settings.apiKey ? `enc:${this.secretCodec.encrypt(settings.apiKey)}` : '' };
    await fs.writeFile(this.filePath, JSON.stringify(stored, null, 2), { encoding: 'utf8', mode: 0o600 });
  }
}
