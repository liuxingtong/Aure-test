import { promises as fs } from 'node:fs';
import path from 'node:path';
export class HistoryRepository {
    filePath;
    cache = new Map();
    loaded = false;
    constructor(filePath) {
        this.filePath = filePath;
    }
    async list() {
        await this.ensureLoaded();
        return [...this.cache.values()].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    }
    async save(conversation) {
        await this.ensureLoaded();
        this.cache.set(conversation.id, conversation);
        await this.flush();
    }
    async delete(id) {
        await this.ensureLoaded();
        this.cache.delete(id);
        await this.flush();
    }
    async clear() {
        await this.ensureLoaded();
        this.cache.clear();
        await this.flush();
    }
    async ensureLoaded() {
        if (this.loaded)
            return;
        this.loaded = true;
        try {
            const content = await fs.readFile(this.filePath, 'utf8');
            const conversations = JSON.parse(content);
            if (Array.isArray(conversations))
                conversations.forEach((conversation) => this.cache.set(conversation.id, conversation));
        }
        catch (error) {
            if (error.code !== 'ENOENT')
                throw error;
        }
    }
    async flush() {
        await fs.mkdir(path.dirname(this.filePath), { recursive: true });
        const temporaryPath = `${this.filePath}.tmp`;
        await fs.writeFile(temporaryPath, JSON.stringify([...this.cache.values()], null, 2), 'utf8');
        await fs.rename(temporaryPath, this.filePath);
    }
}
