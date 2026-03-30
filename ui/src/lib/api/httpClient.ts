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

export interface BinaryResponse {
  blob: Blob;
  fileName: string | null;
  contentType: string | null;
}

async function executeRequest(input: string, init: RequestInit = {}, allowRefresh = true): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }

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
      return executeRequest(input, init, false);
    }
  }

  return response;
}

async function ensureSuccessfulResponse(response: Response): Promise<Response> {
  if (!response.ok) {
    const message = await response.text();
    throw new HttpError(message || `Request failed with status ${response.status}`, response.status);
  }

  return response;
}

function extractDownloadFileName(contentDisposition: string | null): string | null {
  if (contentDisposition == null) {
    return null;
  }
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1] != null) {
    return decodeURIComponent(utf8Match[1]);
  }
  const quotedMatch = contentDisposition.match(/filename=\"([^\"]+)\"/i);
  if (quotedMatch?.[1] != null) {
    return quotedMatch[1];
  }
  const bareMatch = contentDisposition.match(/filename=([^;]+)/i);
  if (bareMatch?.[1] != null) {
    return bareMatch[1].trim();
  }
  return null;
}

export async function apiRequest<T>(input: string, init: RequestInit = {}, allowRefresh = true): Promise<T> {
  const response = await ensureSuccessfulResponse(await executeRequest(input, init, allowRefresh));

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export async function apiRequestBlob(
  input: string,
  init: RequestInit = {},
  allowRefresh = true,
): Promise<BinaryResponse> {
  const response = await ensureSuccessfulResponse(
    await executeRequest(
      input,
      {
        ...init,
        headers: {
          Accept: '*/*',
          ...(init.headers ?? {}),
        },
      },
      allowRefresh,
    ),
  );

  return {
    blob: await response.blob(),
    fileName: extractDownloadFileName(response.headers.get('Content-Disposition')),
    contentType: response.headers.get('Content-Type'),
  };
}
