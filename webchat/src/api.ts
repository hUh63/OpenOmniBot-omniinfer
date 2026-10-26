const API_ROOT = "/webchat/api";
const AVATAR_PAGE_VERSION = Date.now().toString(36);

interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  body?: unknown;
  query?: Record<string, string | number | boolean | null | undefined>;
  /** 单次请求上限。默认 30s：网络黑洞时 fetch 既不断也不回包。 */
  timeoutMs?: number;
}

/** 带 HTTP 状态码的错误：调用方要能区分 401（会话失效）和普通业务失败。 */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

let unauthorizedHandler: (() => void) | null = null;

/**
 * 注册 401 的集中处理。MCP 服务重启后内存里的 webchat session 表会全部失效，
 * 此时所有业务请求都返回 401；如果只在各处 showError，页面会停在「已登录」状态，
 * 而 EventSource 还会每 3 秒重试一次 401，永远回不到登录页。
 */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, query, timeoutMs = 30_000 } = options;
  const url = new URL(`${API_ROOT}${path}`, window.location.origin);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "") {
        url.searchParams.set(key, String(value));
      }
    });
  }

  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  let response: Response;
  let responseText: string;
  try {
    response = await fetch(url, {
      method,
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
    responseText = await response.text();
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      // 没有超时的话，上层 setSending(true) 的 finally 永远不执行，输入框永久禁用。
      throw new ApiError("请求超时，请重试", 0);
    }
    throw error;
  } finally {
    window.clearTimeout(timer);
  }
  let payload: unknown = null;
  if (responseText) {
    try {
      payload = JSON.parse(responseText);
    } catch {
      payload = responseText;
    }
  }
  if (!response.ok) {
    if (response.status === 401) {
      unauthorizedHandler?.();
    }
    const record = isRecord(payload) ? payload : null;
    const message = record?.error ?? record?.message ?? payload;
    throw new ApiError(String(message || `请求失败 (${response.status})`), response.status);
  }
  return payload as T;
}

export function browserFrameUrl(seed: number): string {
  return `${API_ROOT}/browser/frame?t=${Date.now()}-${seed}`;
}

export function agentAvatarUrl(): string {
  return `${API_ROOT}/avatar?v=${AVATAR_PAGE_VERSION}`;
}

export function workspaceDownloadUrl(path: string): string {
  return `${API_ROOT}/workspaces/download?path=${encodeURIComponent(path)}`;
}

export function eventsUrl(): string {
  return `${API_ROOT}/events`;
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
