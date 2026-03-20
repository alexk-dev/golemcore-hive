type AccessTokenProvider = () => string | null;
type RefreshHandler = () => Promise<string | null>;

let accessTokenProvider: AccessTokenProvider = () => null;
let refreshHandler: RefreshHandler = () => Promise.resolve(null);

export class HttpError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
  }
}

export function configureHttpClient(nextAccessTokenProvider: AccessTokenProvider, nextRefreshHandler: RefreshHandler) {
  accessTokenProvider = nextAccessTokenProvider;
  refreshHandler = nextRefreshHandler;
}

export async function apiRequest<T>(input: string, init: RequestInit = {}, allowRefresh = true): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');

  const accessToken = accessTokenProvider();
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(input, {
    ...init,
    headers,
    credentials: 'include',
  });

  if (response.status === 401 && allowRefresh) {
    const refreshedToken = await refreshHandler();
    if (refreshedToken) {
      return apiRequest<T>(input, init, false);
    }
  }

  if (!response.ok) {
    const message = await response.text();
    throw new HttpError(message || `Request failed with status ${response.status}`, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
