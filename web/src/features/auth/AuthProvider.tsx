import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { getAccessToken, setAccessToken } from '../../shared/api/http';
import {
  getCurrentMember,
  login as loginRequest,
  type CurrentMember,
  signup as signupRequest,
} from './authApi';

type AuthContextValue = {
  member: CurrentMember | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (email: string, password: string, nickname: string) => Promise<void>;
  logout: () => void;
  refreshMember: () => Promise<CurrentMember>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

type AuthProviderProps = {
  children: ReactNode;
};

export function AuthProvider({ children }: AuthProviderProps) {
  const [member, setMember] = useState<CurrentMember | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshMember = useCallback(async () => {
    const currentMember = await getCurrentMember();
    setMember(currentMember);
    return currentMember;
  }, []);

  useEffect(() => {
    let ignore = false;

    async function loadMember() {
      if (!getAccessToken()) {
        setLoading(false);
        return;
      }

      try {
        const currentMember = await getCurrentMember();

        if (!ignore) {
          setMember(currentMember);
        }
      } catch {
        setAccessToken(null);

        if (!ignore) {
          setMember(null);
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }

    void loadMember();

    return () => {
      ignore = true;
    };
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await loginRequest(email, password);
      setAccessToken(response.accessToken);
      await refreshMember();
    },
    [refreshMember],
  );

  const signup = useCallback(
    async (email: string, password: string, nickname: string) => {
      await signupRequest(email, password, nickname);
      await login(email, password);
    },
    [login],
  );

  const logout = useCallback(() => {
    setAccessToken(null);
    setMember(null);
  }, []);

  const value = useMemo(
    () => ({
      member,
      loading,
      login,
      signup,
      logout,
      refreshMember,
    }),
    [loading, login, logout, member, refreshMember, signup],
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
