import { apiRequest } from './httpClient';

export interface Operator {
  id: string;
  username: string;
  displayName: string;
  roles: string[];
}

export interface LoginResponse {
  accessToken: string;
  operator: Operator;
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    throw new Error('Invalid credentials');
  }

  return response.json() as Promise<LoginResponse>;
}

export async function refresh(): Promise<LoginResponse | null> {
  const response = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
    },
  });

  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error('Unable to refresh session');
  }
  return response.json() as Promise<LoginResponse>;
}

export async function logout(): Promise<void> {
  await fetch('/api/v1/auth/logout', {
    method: 'POST',
    credentials: 'include',
  });
}

export async function fetchCurrentOperator(): Promise<Operator> {
  return apiRequest<Operator>('/api/v1/auth/me');
}
