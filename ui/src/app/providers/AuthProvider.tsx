import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { configureHttpClient } from '../../lib/api/httpClient';
import {
  fetchCurrentOperator,
  login as loginRequest,
  logout as logoutRequest,
  refresh as refreshRequest,
  type Operator,
} from '../../lib/api/authApi';
import { AuthContext, type AuthContextValue, type AuthStatus } from './authContext';

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<Operator | null>(null);

  async function refreshSession(): Promise<string | null> {
    const refreshed = await refreshRequest();
    if (!refreshed) {
      setAccessToken(null);
      setUser(null);
      setStatus('unauthenticated');
      return null;
    }

    setAccessToken(refreshed.accessToken);
    setUser(refreshed.operator);
    setStatus('authenticated');
    return refreshed.accessToken;
  }

  async function login(username: string, password: string) {
    const response = await loginRequest(username, password);
    setAccessToken(response.accessToken);
    setUser(response.operator);
    setStatus('authenticated');
  }

  async function logout() {
    await logoutRequest();
    setAccessToken(null);
    setUser(null);
    setStatus('unauthenticated');
  }

  useEffect(() => {
    configureHttpClient(() => accessToken, refreshSession);
  }, [accessToken]);

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      try {
        const token = await refreshSession();
        if (!token || cancelled) {
          return;
        }
        const currentUser = await fetchCurrentOperator();
        if (cancelled) {
          return;
        }
        setUser(currentUser);
        setStatus('authenticated');
      } catch {
        if (cancelled) {
          return;
        }
        setAccessToken(null);
        setUser(null);
        setStatus('unauthenticated');
      }
    }

    void bootstrap();

    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      accessToken,
      status,
      user,
      login,
      logout,
      refreshSession,
    }),
    [accessToken, status, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
