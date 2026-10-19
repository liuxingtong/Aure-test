import { mkdtemp, readFile, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { HistoryRepository } from './history-repository';

describe('HistoryRepository', () => {
  const directories: string[] = [];
  afterEach(async () => { await Promise.all(directories.splice(0).map((directory) => rm(directory, { recursive: true, force: true }))); });

  it('archives and restores conversations durably', async () => {
    const directory = await mkdtemp(path.join(os.tmpdir(), 'qa-history-')); directories.push(directory);
    const file = path.join(directory, 'conversations.json');
    const repository = new HistoryRepository(file);
    await repository.save({ id: 'c1', title: '测试', messages: [], createdAt: '2026-01-01', updatedAt: '2026-01-01' });
    await repository.setArchived('c1', true);
    expect((await repository.list())[0].archived).toBe(true);
    await repository.setArchived('c1', false);
    expect((await new HistoryRepository(file).list())[0].archived).toBe(false);
    expect(JSON.parse(await readFile(file, 'utf8'))[0].id).toBe('c1');
  });
});
