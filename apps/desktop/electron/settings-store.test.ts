import { mkdtemp, readFile, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { SettingsStore } from './settings-store';

describe('SettingsStore', () => {
  const directories: string[] = [];
  afterEach(async () => { await Promise.all(directories.splice(0).map((directory) => rm(directory, { recursive: true, force: true }))); });

  it('persists provider while encrypting the API key', async () => {
    const directory = await mkdtemp(path.join(os.tmpdir(), 'qa-settings-')); directories.push(directory);
    const file = path.join(directory, 'settings.json');
    const codec = { encrypt: (value: string) => `cipher:${value}`, decrypt: (value: string) => value.replace('cipher:', '') };
    const settings = { provider: 'openai-compatible' as const, baseUrl: 'http://localhost:11434/v1', model: 'llama3', apiKey: 'secret' };
    await new SettingsStore(file, codec).save(settings);
    expect(JSON.parse(await readFile(file, 'utf8')).apiKey).toBe('enc:cipher:secret');
    await expect(new SettingsStore(file, codec).get()).resolves.toEqual(settings);
  });

  it('keeps the stored key when the UI saves settings without entering it again', async () => {
    const directory = await mkdtemp(path.join(os.tmpdir(), 'qa-settings-')); directories.push(directory);
    const file = path.join(directory, 'settings.json');
    const codec = { encrypt: (value: string) => `cipher:${value}`, decrypt: (value: string) => value.replace('cipher:', '') };
    const store = new SettingsStore(file, codec);
    await store.save({ provider: 'deepseek', baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat', apiKey: 'secret' });
    await store.save({ provider: 'deepseek', baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat', apiKey: '' });

    await expect(store.get()).resolves.toMatchObject({ apiKey: 'secret' });
  });
});
