import { describe, expect, it } from 'vitest';
import { validateDocumentBuffer } from './document-picker';

describe('validateDocumentBuffer', () => {
  it('accepts UTF-8 markdown, text, and json within the limit', () => {
    expect(validateDocumentBuffer('/tmp/notes.md', Buffer.from('# Notes', 'utf8'))).toMatchObject({ extension: 'md', content: '# Notes' });
    expect(validateDocumentBuffer('/tmp/data.json', Buffer.from('{"ok":true}', 'utf8'))).toMatchObject({ extension: 'json' });
  });

  it('rejects unsupported extensions, oversized files, and invalid UTF-8', () => {
    expect(() => validateDocumentBuffer('/tmp/app.exe', Buffer.from('x'))).toThrow('扩展名');
    expect(() => validateDocumentBuffer('/tmp/large.txt', Buffer.alloc(1024 * 1024 + 1))).toThrow('过大');
    expect(() => validateDocumentBuffer('/tmp/bad.txt', Buffer.from([0xc3, 0x28]))).toThrow('UTF-8');
  });
});
