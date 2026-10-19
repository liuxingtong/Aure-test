import path from 'node:path';

export const DOCUMENT_MAX_BYTES = 1024 * 1024;
const allowedExtensions = new Set(['.md', '.txt', '.json']);

export interface SelectedDocument { name: string; extension: string; content: string; }

export function validateDocumentBuffer(filePath: string, buffer: Buffer): SelectedDocument {
  const extension = path.extname(filePath).toLowerCase();
  if (!allowedExtensions.has(extension)) throw new Error('仅支持 md、txt、json 文件扩展名');
  if (buffer.byteLength > DOCUMENT_MAX_BYTES) throw new Error('文档文件过大，不能超过 1 MiB');
  let content: string;
  try {
    content = new TextDecoder('utf-8', { fatal: true }).decode(buffer);
  } catch {
    throw new Error('文件内容不是有效的 UTF-8 文本');
  }
  return { name: path.basename(filePath), extension: extension.slice(1), content };
}
