import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
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
  const requestSeq = useRef(0);

  const refreshMember = useCallback(async () => {
    const requestId = ++requestSeq.current;

    try {
      const currentMember = await getCurrentMember();

      if (requestId === requestSeq.current) {
        setMember(currentMember);
      }

      return currentMember;
    } catch (error) {
      if (requestId === requestSeq.current) {
        setAccessToken(null);
        setMember(null);
      }

      throw error;
    } finally {
      if (requestId === requestSeq.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    const requestId = ++requestSeq.current;

    async function loadMember() {
      if (!getAccessToken()) {
        if (requestId === requestSeq.current) {
          setLoading(false);
        }

        return;
      }

      try {
        const currentMember = await getCurrentMember();

        if (requestId === requestSeq.current) {
          setMember(currentMember);
        }
      } catch {
        if (requestId === requestSeq.current) {
          setAccessToken(null);
          setMember(null);
        }
      } finally {
        if (requestId === requestSeq.current) {
          setLoading(false);
        }
      }
    }

    void loadMember();

    return () => {
      requestSeq.current += 1;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const requestId = ++requestSeq.current;

    try {
      const response = await loginRequest(email, password);

      if (requestId !== requestSeq.current) {
        return;
      }

      setAccessToken(response.accessToken);
      const currentMember = await getCurrentMember();

      if (requestId === requestSeq.current) {
        setMember(currentMember);
      }
    } catch (error) {
      if (requestId === requestSeq.current) {
        setAccessToken(null);
        setMember(null);
      }

      throw error;
    } finally {
      if (requestId === requestSeq.current) {
        setLoading(false);
      }
    }
  }, []);

  const signup = useCallback(
    async (email: string, password: string, nickname: string) => {
      await signupRequest(email, password, nickname);
      await login(email, password);
    },
    [login],
  );

  const logout = useCallback(() => {
    requestSeq.current += 1;
    setAccessToken(null);
    setMember(null);
    setLoading(false);
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
