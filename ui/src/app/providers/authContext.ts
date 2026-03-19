import { createContext } from 'react';
import { Operator } from '../../lib/api/authApi';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

export type AuthContextValue = {
  accessToken: string | null;
  status: AuthStatus;
  user: Operator | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<string | null>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
