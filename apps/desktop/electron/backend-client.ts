import type { AskQuestionRequest, AskQuestionResponse } from '@qa-desktop/shared-types';

let backendReady = true;

export function setBackendReady(ready: boolean): void {
  backendReady = ready;
}

const backendUrl = process.env.QA_BACKEND_URL ?? 'http://127.0.0.1:8080';
// 后端 AI 超时默认 30s，Electron 必须比它长，否则慢回答会被 abort 后重复请求。
const REQUEST_TIMEOUT_MS = 45_000;
const MAX_ATTEMPTS = 3;

export async function askBackend(request: AskQuestionRequest): Promise<AskQuestionResponse> {
  if (!backendReady) {
    return { success: false, error: { code: 'BACKEND_STARTING', message: '后端服务正在启动，请稍后重试。', retryable: true } };
  }
  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const response = await fetch(`${backendUrl}/api/v1/questions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(request),
        signal: controller.signal
      });
      const raw = await response.text();
      let payload: AskQuestionResponse | undefined;
      if (raw.trim()) {
        try {
          payload = JSON.parse(raw) as AskQuestionResponse;
        } catch {
          // 413/网关错误可能不是我们的 JSON 结构，下面按状态码映射。
        }
      }
      if (!response.ok) {
        const retryable = payload?.error?.retryable ?? [502, 503, 504].includes(response.status);
        if (retryable && attempt < MAX_ATTEMPTS - 1) {
          await new Promise((resolve) => setTimeout(resolve, 250 * 2 ** attempt));
          continue;
        }
        return payload ?? {
          success: false,
          error: { code: `HTTP_${response.status}`, message: `后端返回 HTTP ${response.status}`, retryable }
        };
      }
      return payload ?? { success: false, error: { code: 'INVALID_RESPONSE', message: '后端返回了无法解析的响应。', retryable: false } };
    } catch (error) {
      console.warn(`[backend] request failed attempt=${attempt + 1} type=${error instanceof Error ? error.name : 'unknown'}`);
      if (attempt < MAX_ATTEMPTS - 1) await new Promise((resolve) => setTimeout(resolve, 250 * 2 ** attempt));
    } finally {
      clearTimeout(timeout);
    }
  }
  return { success: false, error: { code: 'BACKEND_UNAVAILABLE', message: '后端服务暂不可用，请确认 Spring Boot 已启动。', retryable: true } };
}
