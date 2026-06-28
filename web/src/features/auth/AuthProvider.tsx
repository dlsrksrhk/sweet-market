import { useQueryClient, type QueryClient } from '@tanstack/react-query';
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

const authenticatedPrivateQueryKeys = [['my-orders'], ['my-products'], ['my-settlements'], ['seller-dashboard-report'], ['my-wishlist']] as const;

export function AuthProvider({ children }: AuthProviderProps) {
  const queryClient = useQueryClient();
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
        clearAuthenticatedPrivateQueries(queryClient);
      }

      throw error;
    } finally {
      if (requestId === requestSeq.current) {
        setLoading(false);
      }
    }
  }, [queryClient]);

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
          clearAuthenticatedPrivateQueries(queryClient);
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
  }, [queryClient]);

  const login = useCallback(async (email: string, password: string) => {
    const requestId = ++requestSeq.current;

    try {
      const response = await loginRequest(email, password);

      if (requestId !== requestSeq.current) {
        return;
      }

      setMember(null);
      clearAuthenticatedPrivateQueries(queryClient);
      setAccessToken(response.accessToken);
      const currentMember = await getCurrentMember();

      if (requestId === requestSeq.current) {
        setMember(currentMember);
      }
    } catch (error) {
      if (requestId === requestSeq.current) {
        setAccessToken(null);
        setMember(null);
        clearAuthenticatedPrivateQueries(queryClient);
      }

      throw error;
    } finally {
      if (requestId === requestSeq.current) {
        setLoading(false);
      }
    }
  }, [queryClient]);

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
    clearAuthenticatedPrivateQueries(queryClient);
    setLoading(false);
  }, [queryClient]);

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

function clearAuthenticatedPrivateQueries(queryClient: QueryClient) {
  authenticatedPrivateQueryKeys.forEach((queryKey) => {
    queryClient.removeQueries({ queryKey, exact: false });
  });
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }

  return context;
}
