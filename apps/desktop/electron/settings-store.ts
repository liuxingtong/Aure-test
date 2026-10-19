import { promises as fs } from 'node:fs';
import path from 'node:path';
import type { AiSettings } from '@qa-desktop/shared-types';

export const defaultAiSettings: AiSettings = {
  provider: 'deepseek',
  baseUrl: 'https://api.deepseek.com',
  model: 'deepseek-chat',
  apiKey: '',
  memoryEnabled: false,
  memory: ''
};

type StoredSettings = Omit<AiSettings, 'apiKey'> & { apiKey: string };
export type SecretCodec = { encrypt(value: string): string; decrypt(value: string): string };

export class SettingsStore {
  private cached: AiSettings | undefined;
  constructor(private readonly filePath: string, private readonly secretCodec?: SecretCodec) {}

  async get(): Promise<AiSettings> {
    if (this.cached) return { ...this.cached };
    try {
      const raw = JSON.parse(await fs.readFile(this.filePath, 'utf8')) as Partial<StoredSettings>;
      const storedKey = typeof raw.apiKey === 'string' ? raw.apiKey : '';
      this.cached = { ...defaultAiSettings, ...raw, apiKey: this.secretCodec && storedKey.startsWith('enc:') ? this.secretCodec.decrypt(storedKey.slice(4)) : storedKey };
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
      this.cached = { ...defaultAiSettings };
    }
    return { ...this.cached };
  }

  async save(settings: AiSettings): Promise<AiSettings> {
    this.cached = { ...defaultAiSettings, ...settings };
    await fs.mkdir(path.dirname(this.filePath), { recursive: true });
    const stored = { ...this.cached, apiKey: this.secretCodec && this.cached.apiKey ? `enc:${this.secretCodec.encrypt(this.cached.apiKey)}` : '' };
    await fs.writeFile(this.filePath, JSON.stringify(stored, null, 2), { encoding: 'utf8', mode: 0o600 });
    return { ...this.cached };
  }
}
