import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { internalApi } from "@/lib/api/internal-api";
import type { ForgotPasswordInput, ResetPasswordInput } from "@/lib/api/internal-api";
import {
  AuthSession,
  AuthUser,
  clearStoredAuthSession,
  getStoredAuthSession,
  setStoredAuthSession,
} from "@/features/auth/auth-storage";

interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isBootstrapping: boolean;
  login: (input: { email: string; password: string }) => Promise<void>;
  signup: (input: { fullName: string; email: string; password: string }) => Promise<void>;
  logout: () => Promise<void>;
  forgotPassword: (input: ForgotPasswordInput) => Promise<void>;
  resetPassword: (input: ResetPasswordInput) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [isBootstrapping, setIsBootstrapping] = useState(true);

  useEffect(() => {
    const bootstrap = async () => {
      // Bypass auth in development mode
      if (import.meta.env.DEV) {
        const dummySession: AuthSession = {
          accessToken: "dummy-dev-token",
          tokenType: "Bearer",
          user: {
            id: "usr-dev",
            fullName: "Dev User",
            email: "dev@wastepilot.io",
          },
        };
        setStoredAuthSession(dummySession);
        setSession(dummySession);
        setIsBootstrapping(false);
        return;
      }

      const stored = getStoredAuthSession();
      if (!stored?.accessToken) {
        setIsBootstrapping(false);
        return;
      }

      try {
        const user = await internalApi.fetchCurrentUser();
        const validSession: AuthSession = { ...stored, user };
        setStoredAuthSession(validSession);
        setSession(validSession);
      } catch {
        clearStoredAuthSession();
        setSession(null);
      } finally {
        setIsBootstrapping(false);
      }
    };

    void bootstrap();
  }, []);

  useEffect(() => {
    const onStorage = () => {
      setSession(getStoredAuthSession());
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const login = useCallback(async (input: { email: string; password: string }) => {
    const nextSession = await internalApi.login(input);
    setStoredAuthSession(nextSession);
    setSession(nextSession);
  }, []);

  const signup = useCallback(async (input: { fullName: string; email: string; password: string }) => {
    const nextSession = await internalApi.signup(input);
    setStoredAuthSession(nextSession);
    setSession(nextSession);
  }, []);

  const logout = useCallback(async () => {
    try {
      await internalApi.logout();
    } finally {
      clearStoredAuthSession();
      setSession(null);
    }
  }, []);

  const forgotPassword = useCallback(async (input: ForgotPasswordInput) => {
    await internalApi.forgotPassword(input);
  }, []);

  const resetPassword = useCallback(async (input: ResetPasswordInput) => {
    await internalApi.resetPassword(input);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    user: session?.user ?? null,
    isAuthenticated: Boolean(session?.accessToken),
    isBootstrapping,
    login,
    signup,
    logout,
    forgotPassword,
    resetPassword,
  }), [session, isBootstrapping, login, signup, logout, forgotPassword, resetPassword]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
