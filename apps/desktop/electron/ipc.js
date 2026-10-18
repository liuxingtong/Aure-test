import { ipcMain } from 'electron';
import { askBackend } from './backend-client';
export function registerIpcHandlers(historyRepository) {
    ipcMain.handle('question:ask', async (_event, request) => {
        const question = request.question.trim();
        if (!question)
            return { success: false, error: { code: 'VALIDATION_ERROR', message: '问题不能为空', retryable: false } };
        return askBackend({ ...request, question });
    });
    ipcMain.handle('history:list', async () => historyRepository.list());
    ipcMain.handle('history:save', async (_event, conversation) => historyRepository.save(conversation));
    ipcMain.handle('history:delete', async (_event, id) => historyRepository.delete(id));
    ipcMain.handle('history:clear', async () => historyRepository.clear());
    ipcMain.handle('system:get-version', async () => '0.1.0');
    ipcMain.handle('system:check-backend', async () => false);
}
