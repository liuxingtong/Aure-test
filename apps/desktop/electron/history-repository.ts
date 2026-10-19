import { promises as fs } from 'node:fs';
import path from 'node:path';
import type { Conversation } from '@qa-desktop/shared-types';

export class HistoryRepository {
  private cache = new Map<string, Conversation>();
  private loaded = false;

  constructor(private readonly filePath: string) {}

  async list(): Promise<Conversation[]> {
    await this.ensureLoaded();
    return [...this.cache.values()].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  }

  async save(conversation: Conversation): Promise<void> {
    await this.ensureLoaded();
    this.cache.set(conversation.id, conversation);
    await this.flush();
  }

  async delete(id: string): Promise<void> {
    await this.ensureLoaded();
    this.cache.delete(id);
    await this.flush();
  }

  async setArchived(id: string, archived: boolean): Promise<void> {
    await this.ensureLoaded();
    const conversation = this.cache.get(id);
    if (!conversation) return;
    this.cache.set(id, { ...conversation, archived, updatedAt: new Date().toISOString() });
    await this.flush();
  }

  async clear(): Promise<void> {
    await this.ensureLoaded();
    this.cache.clear();
    await this.flush();
  }

  private async ensureLoaded(): Promise<void> {
    if (this.loaded) return;
    try {
      const content = await fs.readFile(this.filePath, 'utf8');
      const conversations = JSON.parse(content) as Conversation[];
      if (Array.isArray(conversations)) conversations.forEach((conversation) => this.cache.set(conversation.id, conversation));
    } catch (error) {
      const code = (error as NodeJS.ErrnoException).code;
      if (code !== 'ENOENT') {
        // 损坏的 conversations.json 先备份再以空历史启动，避免下一次保存把它直接覆盖。
        console.warn('[history] failed to load conversations, backing up the corrupt file', error);
        try {
          await fs.rename(this.filePath, `${this.filePath}.corrupt-${Date.now()}`);
        } catch {
          // Ignore backup failures; still start with an empty in-memory cache.
        }
      }
    }
    this.loaded = true;
  }

  private async flush(): Promise<void> {
    await fs.mkdir(path.dirname(this.filePath), { recursive: true });
    const temporaryPath = `${this.filePath}.tmp`;
    await fs.writeFile(temporaryPath, JSON.stringify([...this.cache.values()], null, 2), 'utf8');
    await fs.rename(temporaryPath, this.filePath);
  }
}
