import { fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useAuth } from "@/features/auth/use-auth";
import AppShell from "@/features/internal/components/AppShell";

vi.mock("@/features/auth/use-auth", () => ({
  useAuth: vi.fn(),
}));

vi.mock("@/lib/api/internal-api", () => ({
  internalApiRuntimeConfig: { provider: "spring" },
  subscribeInternalApiStatus: (
    listener: (status: { fallbackActive: boolean; fallbackOperations: string[] }) => void,
  ) => {
    listener({ fallbackActive: true, fallbackOperations: ["dashboard"] });
    return () => undefined;
  },
}));

const makeAuthValue = (role: "OPERATOR" | "SUPERVISOR"): ReturnType<typeof useAuth> => ({
  user: { id: "user-1", fullName: "Test User", email: "test@example.com", role },
  isAuthenticated: true,
  isBootstrapping: false,
  login: async () => undefined,
  signup: async () => undefined,
  logout: async () => undefined,
  forgotPassword: async () => undefined,
  resetPassword: async () => undefined,
});

afterEach(() => {
  vi.clearAllMocks();
});

describe.each(["OPERATOR", "SUPERVISOR"] as const)("AppShell for %s", (role) => {
  it("keeps the same workspace navigation and displays the current role", () => {
    vi.mocked(useAuth).mockReturnValue(makeAuthValue(role));

    render(
      <MemoryRouter
        initialEntries={["/dashboard"]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <AppShell />
      </MemoryRouter>,
    );

    expect(screen.getByText(role)).toHaveAttribute("aria-label", "User role");
    const navigationGroups = document.querySelectorAll<HTMLElement>('nav[aria-label="Workspace navigation"]');
    expect(navigationGroups).toHaveLength(2);

    for (const nav of navigationGroups) {
      const links = within(nav).getAllByRole("link", { hidden: true });
      expect(links).toHaveLength(10);
      expect(within(nav).getByRole("link", { name: "Dashboard", hidden: true })).toHaveAttribute("href", "/dashboard");
      expect(within(nav).getByRole("link", { name: "Settings", hidden: true })).toHaveAttribute("href", "/settings");
    }

    expect(screen.getByRole("alert")).toHaveTextContent("Demo/offline data is active");
    expect(screen.getByRole("alert")).toHaveTextContent("All write actions are blocked");
    expect(screen.getByRole("main").querySelector(".app-shell-page-content")).toBeInTheDocument();
    expect(document.body).toHaveClass("internal-workspace");
  });

  it("keeps the closed mobile navigation inaccessible and supports Escape to close it", () => {
    vi.mocked(useAuth).mockReturnValue(makeAuthValue(role));

    const { unmount } = render(
      <MemoryRouter
        initialEntries={["/dashboard"]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <AppShell />
      </MemoryRouter>,
    );

    const dialog = document.getElementById("mobile-workspace-navigation");
    const openButton = screen.getByRole("button", { name: "Open navigation" });

    if (!dialog) {
      throw new Error("The mobile navigation dialog is missing.");
    }

    expect(dialog).toHaveAttribute("aria-hidden", "true");
    expect(dialog).toHaveAttribute("inert");
    expect(openButton).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(openButton);

    expect(dialog).toHaveAttribute("aria-hidden", "false");
    expect(dialog).not.toHaveAttribute("inert");
    expect(openButton).toHaveAttribute("aria-expanded", "true");

    fireEvent.keyDown(window, { key: "Escape" });

    expect(dialog).toHaveAttribute("aria-hidden", "true");
    expect(dialog).toHaveAttribute("inert");

    unmount();
    expect(document.body).not.toHaveClass("internal-workspace");
  });
});
