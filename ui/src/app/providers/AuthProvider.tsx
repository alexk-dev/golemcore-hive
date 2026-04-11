import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
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
  const accessTokenRef = useRef<string | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<Operator | null>(null);

  const setSessionAccessToken = useCallback((token: string | null) => {
    accessTokenRef.current = token;
    setAccessToken(token);
  }, []);

  const refreshSession = useCallback(async (): Promise<string | null> => {
    const refreshed = await refreshRequest();
    if (!refreshed) {
      setSessionAccessToken(null);
      setUser(null);
      setStatus('unauthenticated');
      return null;
    }

    setSessionAccessToken(refreshed.accessToken);
    setUser(refreshed.operator);
    setStatus('authenticated');
    return refreshed.accessToken;
  }, [setSessionAccessToken]);

  const login = useCallback(async (username: string, password: string) => {
    const response = await loginRequest(username, password);
    setSessionAccessToken(response.accessToken);
    setUser(response.operator);
    setStatus('authenticated');
  }, [setSessionAccessToken]);

  const logout = useCallback(async () => {
    await logoutRequest();
    setSessionAccessToken(null);
    setUser(null);
    setStatus('unauthenticated');
  }, [setSessionAccessToken]);

  useEffect(() => {
    configureHttpClient(() => accessTokenRef.current, refreshSession);
  }, [refreshSession]);

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
        setSessionAccessToken(null);
        setUser(null);
        setStatus('unauthenticated');
      }
    }

    void bootstrap();

    return () => {
      cancelled = true;
    };
  }, [refreshSession, setSessionAccessToken]);

  const value = useMemo<AuthContextValue>(
    () => ({
      accessToken,
      status,
      user,
      login,
      logout,
      refreshSession,
    }),
    [accessToken, login, logout, refreshSession, status, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
