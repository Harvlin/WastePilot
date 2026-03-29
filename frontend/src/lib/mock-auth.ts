const AUTH_KEY = "wastepilot_mock_auth";

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
    return JSON.parse(raw) as MockAuthUser;
  } catch {
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
