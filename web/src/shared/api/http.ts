export type ApiError = {
  code: string;
  message: string;
  fieldErrors?: {
    field: string;
    message: string;
  }[];
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const TOKEN_KEY = 'sweet-market-token';

type ApiResponse<T> = {
  data: T;
};

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setAccessToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
    return;
  }

  localStorage.removeItem(TOKEN_KEY);
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getAccessToken();

  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers,
    });
  } catch {
    throw {
      code: 'NETWORK_ERROR',
      message: '서버에 연결할 수 없습니다.',
      fieldErrors: [],
    } satisfies ApiError;
  }

  const payload = await parseJson(response);

  if (!response.ok) {
    throw normalizeError(payload);
  }

  return (payload as ApiResponse<T>).data;
}

async function parseJson(response: Response): Promise<unknown> {
  if (response.status === 204) {
    return { data: null };
  }

  const text = await response.text();

  if (!text) {
    return { data: null };
  }

  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function normalizeError(payload: unknown): ApiError {
  if (isApiError(payload)) {
    return payload;
  }

  return {
    code: 'UNKNOWN_ERROR',
    message: '요청을 처리하지 못했습니다.',
    fieldErrors: [],
  };
}

function isApiError(payload: unknown): payload is ApiError {
  if (!payload || typeof payload !== 'object') {
    return false;
  }

  const maybeError = payload as Partial<ApiError>;

  return typeof maybeError.code === 'string' && typeof maybeError.message === 'string';
}
