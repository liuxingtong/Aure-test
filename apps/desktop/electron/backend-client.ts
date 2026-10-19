import type { AskQuestionRequest, AskQuestionResponse } from '@qa-desktop/shared-types';

let backendReady = true;

export function setBackendReady(ready: boolean): void {
  backendReady = ready;
}

const backendUrl = process.env.QA_BACKEND_URL ?? 'http://127.0.0.1:8080';

export async function askBackend(request: AskQuestionRequest): Promise<AskQuestionResponse> {
  if (!backendReady) {
    return { success: false, error: { code: 'BACKEND_STARTING', message: '后端服务正在启动，请稍后重试。', retryable: true } };
  }
  let lastError: unknown;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10_000);
    try {
      const response = await fetch(`${backendUrl}/api/v1/questions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(request),
        signal: controller.signal
      });
      const payload = await response.json() as AskQuestionResponse;
      if (!response.ok) {
        if (payload.error?.retryable && attempt < 2) {
          await new Promise((resolve) => setTimeout(resolve, 250 * 2 ** attempt));
          continue;
        }
        return payload;
      }
      return payload;
    } catch (error) {
      lastError = error;
      console.warn(`[backend] request failed attempt=${attempt + 1} type=${error instanceof Error ? error.name : 'unknown'}`);
      if (attempt < 2) await new Promise((resolve) => setTimeout(resolve, 250 * 2 ** attempt));
    } finally {
      clearTimeout(timeout);
    }
  }
  return { success: false, error: { code: 'BACKEND_UNAVAILABLE', message: '后端服务暂不可用，请确认 Spring Boot 已启动。', retryable: true } };
}
