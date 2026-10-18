import { ipcMain } from 'electron';
import type { AskQuestionRequest, AskQuestionResponse, Conversation } from '@qa-desktop/shared-types';

const conversations = new Map<string, Conversation>();

export function registerIpcHandlers() {
  ipcMain.handle('question:ask', async (_event, request: AskQuestionRequest): Promise<AskQuestionResponse> => {
    const question = request.question.trim();
    if (!question) return { success: false, error: { code: 'VALIDATION_ERROR', message: '问题不能为空', retryable: false } };
    return { success: true, data: { messageId: crypto.randomUUID(), answer: `这是通过 Electron IPC 返回的演示回答。\n\n你刚刚问的是：**${question}**`, toolCalls: [] } };
  });
  ipcMain.handle('history:list', async (): Promise<Conversation[]> => [...conversations.values()]);
  ipcMain.handle('history:save', async (_event, conversation: Conversation): Promise<void> => { conversations.set(conversation.id, conversation); });
  ipcMain.handle('history:delete', async (_event, id: string): Promise<void> => { conversations.delete(id); });
  ipcMain.handle('history:clear', async (): Promise<void> => { conversations.clear(); });
  ipcMain.handle('system:get-version', async (): Promise<string> => '0.1.0');
  ipcMain.handle('system:check-backend', async (): Promise<boolean> => false);
}
