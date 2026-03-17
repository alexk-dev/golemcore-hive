import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { configureHttpClient } from '../../lib/api/httpClient';
import {
  fetchCurrentOperator,
  login as loginRequest,
  logout as logoutRequest,
  Operator,
  refresh as refreshRequest,
} from '../../lib/api/authApi';

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

type AuthContextValue = {
  accessToken: string | null;
  status: AuthStatus;
  user: Operator | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<string | null>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

type AuthProviderProps = {
  children: ReactNode;
};

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

  async function bootstrapSession() {
    try {
      const token = await refreshSession();
      if (!token) {
        return;
      }
      const currentUser = await fetchCurrentOperator();
      setUser(currentUser);
      setStatus('authenticated');
    } catch {
      setAccessToken(null);
      setUser(null);
      setStatus('unauthenticated');
    }
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
    void bootstrapSession();
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

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
