const AUTH_KEY = "wastepilot_mock_auth";
const SESSION_MAX_AGE_MS = 8 * 60 * 60 * 1000;

export interface MockAuthUser {
  email: string;
  name?: string;
  authenticatedAt: string;
}

export function getMockAuthUser(): MockAuthUser | null {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(AUTH_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as MockAuthUser;
    const authenticatedAt = new Date(parsed.authenticatedAt).getTime();

    if (!Number.isFinite(authenticatedAt) || Date.now() - authenticatedAt > SESSION_MAX_AGE_MS) {
      window.localStorage.removeItem(AUTH_KEY);
      return null;
    }

    return parsed;
  } catch {
    window.localStorage.removeItem(AUTH_KEY);
    return null;
  }
}

export function isMockAuthenticated(): boolean {
  return Boolean(getMockAuthUser());
}

export function signInMockUser(email: string, name?: string) {
  if (typeof window === "undefined") {
    return;
  }

  const payload: MockAuthUser = {
    email,
    name,
    authenticatedAt: new Date().toISOString(),
  };

  window.localStorage.setItem(AUTH_KEY, JSON.stringify(payload));
}

export function signOutMockUser() {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.removeItem(AUTH_KEY);
}
