import { createContext } from "react";
import type { ForgotPasswordInput, ResetPasswordInput } from "@/lib/api/internal-api";
import type { AuthUser } from "@/features/auth/auth-storage";

export interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isBootstrapping: boolean;
  login: (input: { email: string; password: string }) => Promise<void>;
  signup: (input: { fullName: string; email: string; password: string }) => Promise<void>;
  logout: () => Promise<void>;
  forgotPassword: (input: ForgotPasswordInput) => Promise<void>;
  resetPassword: (input: ResetPasswordInput) => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
