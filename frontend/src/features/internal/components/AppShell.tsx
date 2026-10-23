import { ArrowLeft, Menu, Search, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { MouseEvent as ReactMouseEvent, useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useMatch, useNavigate } from "react-router-dom";
import { internalNav } from "@/features/internal/components/navigation";
import { resolveSearchDestination } from "@/features/internal/components/search-index";
import { useAuth } from "@/features/auth/use-auth";
import { internalApiRuntimeConfig, subscribeInternalApiStatus } from "@/lib/api/internal-api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { toast } from "sonner";

const DESKTOP_BREAKPOINT = 1024;

const clearGlobalInteractionLocks = () => {
  if (typeof document === "undefined") {
    return;
  }

  const body = document.body;
  const html = document.documentElement;

  body.style.removeProperty("overflow");
  body.style.removeProperty("touch-action");
  body.style.removeProperty("pointer-events");

  html.style.removeProperty("overflow");
  html.style.removeProperty("touch-action");
  html.style.removeProperty("pointer-events");
};

const scheduleGlobalUnlock = () => {
  if (typeof window === "undefined") {
    return;
  }

  const unlock = () => clearGlobalInteractionLocks();

  unlock();
  window.requestAnimationFrame(unlock);
  window.setTimeout(unlock, 0);
  window.setTimeout(unlock, 120);
};

/** Standalone nav item — uses useMatch() as the single source of truth for all active-state
 * visual indicators: className (text colour) and the AnimatePresence background pill.
 *
 * Key design decisions:
 *  1. useMatch() rather than NavLink's className/style function props — in jsdom (test env) the
 *     function props receive isActive:false while aria-current="page" is set correctly, making
 *     function-prop-based assertions unreliable. useMatch() is consistent across all environments.
 *  2. text-brand-foreground (not text-white) — the CSS legacy-remap rule in index.css only targets
 *     .app-shell-page-content descendants; the sidebar never enters that scope, so using the brand
 *     token directly is safe and correct.
 *  3. AnimatePresence without layoutId — the sidebar <aside> has overflow-hidden which clips
 *     cross-sibling shared-layout animations. Per-item enter/exit animations work correctly.
 *  4. Background colour via inline style — bypasses Tailwind's alpha-value template and any
 *     remaining cascade specificity concerns on the motion span itself.
 */
const NavItem = ({
  to,
  icon: Icon,
  label,
  onClick,
}: {
  to: string;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  onClick?: () => void;
}) => {
  const isActive = Boolean(useMatch({ path: to, end: true }));
  return (
    <NavLink
      to={to}
      onClick={onClick}
      className={`group relative isolate flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-body transition-colors ${
        isActive
          ? "text-brand-foreground"
          : "text-secondary-foreground hover:bg-muted hover:text-foreground"
      }`}
    >
      <AnimatePresence>
        {isActive && (
          <motion.span
            key="active-bg"
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            transition={{ duration: 0.15, ease: "easeOut" }}
            className="absolute inset-0 -z-10 rounded-md"
            style={{ backgroundColor: "hsl(var(--brand))" }}
          />
        )}
      </AnimatePresence>
      <Icon className="relative z-10 h-4 w-4 shrink-0" />
      <span className="relative z-10">{label}</span>
    </NavLink>
  );
};


const SidebarContent = ({ closeMobile }: { closeMobile?: () => void }) => {
  return (
    <div className="h-full flex flex-col">
      <div className="flex items-center justify-between border-b border-border px-4 py-5">
        <Link to="/" className="flex min-w-0 items-center hover:opacity-80 transition-opacity">
          <img
            src="/logo-wordmark-dark.png"
            alt="WastePilot"
            className="h-7 w-auto max-w-full object-contain"
          />
        </Link>

        {closeMobile && (
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              closeMobile();
            }}
            aria-label="Close navigation"
            className="flex h-9 w-9 items-center justify-center rounded-full border border-border bg-white text-muted-foreground transition-colors hover:bg-muted hover:text-foreground lg:hidden"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      <nav aria-label="Workspace navigation" className="flex-1 space-y-1 overflow-y-auto px-3 py-5">
        <p className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
          Workspace
        </p>
        {internalNav.map((item) => (
          <NavItem
            key={item.to}
            to={item.to}
            icon={item.icon}
            label={item.label}
            onClick={closeMobile}
          />
        ))}
      </nav>

      <div className="border-t border-border p-4">
        <div className="rounded-lg border border-border bg-muted/40 p-3">
          <p className="text-xs font-body text-muted-foreground">Circular score this week</p>
          <p className="mt-1 text-2xl font-heading font-semibold tabular-nums text-brand">76</p>
        </div>
      </div>
    </div>
  );
};

const AppShell = () => {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchText, setSearchText] = useState("");
  const [isOnline, setIsOnline] = useState(() => (typeof navigator === "undefined" ? true : navigator.onLine));
  const [isFallbackActive, setIsFallbackActive] = useState(false);
  const [fallbackOperations, setFallbackOperations] = useState<string[]>([]);
  const mobileNavigationRef = useRef<HTMLElement>(null);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const pageLabel = internalNav.find((item) => item.to === location.pathname)?.label ?? "Workspace";

  const openMobileNav = useCallback(() => {
    setMobileOpen(true);
  }, []);

  const closeMobileNav = useCallback(() => {
    setMobileOpen(false);
    scheduleGlobalUnlock();
  }, []);

  const handleMobileMenuButtonClick = useCallback((event: ReactMouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    if (mobileOpen) {
      closeMobileNav();
      return;
    }
    openMobileNav();
  }, [closeMobileNav, mobileOpen, openMobileNav]);

  useEffect(() => {
    // Close drawer on any navigation transition, including same-path navigations with different location keys.
    closeMobileNav();
  }, [closeMobileNav, location.key]);

  useEffect(() => {
    // Defensive cleanup for intermittent UI locks left by layered components during route transitions.
    if (!mobileOpen) {
      scheduleGlobalUnlock();
    }
  }, [location.key, mobileOpen]);

  useEffect(() => {
    if (!mobileOpen) {
      clearGlobalInteractionLocks();
      return;
    }

    const body = document.body;
    const html = document.documentElement;

    body.style.overflow = "hidden";
    body.style.touchAction = "none";
    body.style.pointerEvents = "auto";
    html.style.overflow = "hidden";
    html.style.touchAction = "none";
    html.style.pointerEvents = "auto";

    return () => {
      scheduleGlobalUnlock();
    };
  }, [mobileOpen]);

  useEffect(() => {
    if (!mobileOpen) {
      return;
    }

    // Some layered components can temporarily re-apply pointer locks; keep drawer interaction healthy while open.
    const syncPointerState = () => {
      document.body.style.pointerEvents = "auto";
      document.documentElement.style.pointerEvents = "auto";
    };

    syncPointerState();
    const intervalId = window.setInterval(syncPointerState, 120);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [mobileOpen]);

  useEffect(() => () => {
    scheduleGlobalUnlock();
  }, []);

  useEffect(() => {
    if (!mobileOpen) {
      return;
    }

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        closeMobileNav();
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [closeMobileNav, mobileOpen]);

  useEffect(() => {
    const onResize = () => {
      if (window.innerWidth >= DESKTOP_BREAKPOINT) {
        closeMobileNav();
      }
    };

    window.addEventListener("resize", onResize);
    return () => {
      window.removeEventListener("resize", onResize);
    };
  }, [closeMobileNav]);

  useEffect(() => {
    const unsubscribe = subscribeInternalApiStatus((status) => {
      setIsFallbackActive(status.fallbackActive);
      setFallbackOperations(status.fallbackOperations);
    });
    return unsubscribe;
  }, []);

  useEffect(() => {
    const navigation = mobileNavigationRef.current;
    if (navigation) {
      if (mobileOpen) {
        navigation.removeAttribute("inert");
      } else {
        navigation.setAttribute("inert", "");
      }
    }
  }, [mobileOpen]);

  useLayoutEffect(() => {
    document.body.classList.add("internal-workspace");
    return () => {
      document.body.classList.remove("internal-workspace");
    };
  }, []);

  useEffect(() => {
    const onOnline = () => setIsOnline(true);
    const onOffline = () => setIsOnline(false);

    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);

    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, []);

  const handleSearchSubmit = () => {
    const query = searchText.trim();
    if (!query) {
      return;
    }

    const match = resolveSearchDestination(query);

    if (!match) {
      toast.info("No matching section found. Try words like OCR, get started, features, or analytics.");
      return;
    }

    navigate(match.to);
    setSearchText("");
    toast.success(`Opened ${match.label}.`);
  };

  const handleLogout = async () => {
    closeMobileNav();
    await logout();
    toast.success("You have been logged out.");
    navigate("/");
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 10, filter: "blur(8px)" }}
      animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
      transition={{ duration: 0.32, ease: "easeOut" }}
      className="relative min-h-screen w-full overflow-x-clip bg-background text-foreground"
    >
      <div className="relative z-10 flex min-h-screen">
        <div className="hidden w-[302px] shrink-0 p-4 lg:block">
          <aside className="sticky top-4 h-[calc(100vh-2rem)] overflow-hidden rounded-xl border border-border bg-white shadow-card">
            <SidebarContent />
          </aside>
        </div>

        <div className="flex-1 min-w-0">
          <header className="sticky top-0 z-[70] border-b border-border-strong/50 bg-background">
            <div className="mx-auto flex max-w-screen-2xl flex-col gap-3 px-4 py-3 md:px-8">
              <div className="flex items-center justify-between gap-3 md:gap-4">
                <div className="flex min-w-0 items-center gap-3">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    className="shrink-0 border-border bg-white shadow-none lg:hidden"
                    onClick={handleMobileMenuButtonClick}
                    aria-label={mobileOpen ? "Close navigation" : "Open navigation"}
                    aria-controls="mobile-workspace-navigation"
                    aria-expanded={mobileOpen}
                  >
                    {mobileOpen ? <X /> : <Menu />}
                  </Button>
                  <div className="min-w-0">
                    <p className="font-body text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                      Workspace
                    </p>
                    <p className="truncate font-body text-base font-semibold text-foreground">{pageLabel}</p>
                  </div>
                </div>

                <div className="hidden w-[min(380px,36vw)] items-center gap-2 rounded-md border border-input bg-white px-2 shadow-inset-control md:flex">
                  <button
                    type="button"
                    onClick={handleSearchSubmit}
                    className="flex h-9 w-9 shrink-0 items-center justify-center rounded-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    aria-label="Search"
                  >
                    <Search className="h-4 w-4" />
                  </button>
                  <Input
                    className="h-10 min-w-0 border-0 bg-transparent px-1 shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
                    placeholder="Search features, services, sections..."
                    value={searchText}
                    onChange={(event) => setSearchText(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        handleSearchSubmit();
                      }
                    }}
                  />
                </div>

              <div className="flex items-center gap-3">
                {user && (
                  <div className="hidden min-w-0 flex-col items-end sm:flex">
                    <span className="max-w-40 truncate text-sm font-medium text-foreground">{user.fullName}</span>
                    <span
                      aria-label="User role"
                      className="mt-0.5 rounded-sm border border-border bg-white px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground"
                    >
                      {user.role || "OPERATOR"}
                    </span>
                  </div>
                )}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleLogout}
                  className="shrink-0 border-border bg-white font-body"
                >
                  <ArrowLeft />
                  <span className="hidden sm:inline">Log out</span>
                </Button>
              </div>
            </div>

              <div className="flex items-center gap-2 rounded-md border border-input bg-white px-2 shadow-inset-control md:hidden">
                <button
                  type="button"
                  onClick={handleSearchSubmit}
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  aria-label="Search"
                >
                  <Search className="h-4 w-4" />
                </button>
                <Input
                  className="h-10 min-w-0 border-0 bg-transparent px-1 shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
                  placeholder="Jump to page..."
                  value={searchText}
                  onChange={(event) => setSearchText(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      handleSearchSubmit();
                    }
                  }}
                />
              </div>
            </div>
          </header>

          {!isOnline && (
            <div className="mx-auto mt-3 max-w-screen-2xl px-4 md:px-8">
              <div role="alert" className="rounded-md border border-status-warning/50 bg-status-warning/10 px-4 py-3 font-body text-sm font-medium text-status-warning">
                You are offline. Data updates may fail until the connection is restored.
              </div>
            </div>
          )}

          {isFallbackActive && (
            <div className="mx-auto mt-3 max-w-screen-2xl px-4 md:px-8">
              <div role="alert" aria-live="assertive" className="rounded-md border-2 border-status-warning bg-status-warning/15 px-4 py-4 font-body text-sm text-foreground shadow-card">
                <p className="font-bold uppercase tracking-wide text-status-warning">Demo/offline data is active</p>
                <p className="mt-1">
                  These sections are showing simulated data after a backend failure. All write actions are blocked until the backend recovers.
                </p>
                {fallbackOperations.length > 0 && (
                  <p className="mt-1 text-xs">Affected sections: {fallbackOperations.join(", ")}</p>
                )}
              </div>
            </div>
          )}
          {internalApiRuntimeConfig.provider === "mock" && (
            <div className="mx-auto mt-3 max-w-screen-2xl px-4 md:px-8">
              <div role="alert" className="rounded-md border-2 border-status-warning bg-status-warning/15 px-4 py-3 font-body text-sm font-semibold text-foreground">
                Demo mode is active. All data and changes are simulated; no backend records are affected.
              </div>
            </div>
          )}

          <main className="mx-auto w-full max-w-screen-2xl p-4 pt-6 md:p-8">
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={location.pathname}
                initial={{ opacity: 0, y: 12, filter: "blur(8px)" }}
                animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
                exit={{ opacity: 0, y: -8, filter: "blur(8px)" }}
                transition={{ duration: 0.28, ease: "easeOut" }}
                className="app-shell-page-content"
              >
                <Outlet />
              </motion.div>
            </AnimatePresence>
          </main>
        </div>
      </div>

      <button
        type="button"
        onClick={(event) => {
          event.stopPropagation();
          closeMobileNav();
        }}
        aria-label="Close navigation overlay"
        aria-hidden={!mobileOpen}
        tabIndex={mobileOpen ? 0 : -1}
        className={`fixed inset-0 z-[90] bg-foreground/30 transition-opacity duration-200 lg:hidden ${
          mobileOpen ? "opacity-100 pointer-events-auto" : "opacity-0 pointer-events-none"
        }`}
      />

      <aside
        ref={mobileNavigationRef}
        id="mobile-workspace-navigation"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Mobile navigation"
        aria-hidden={!mobileOpen}
        className={`fixed left-0 top-0 z-[100] h-full w-[88vw] max-w-[320px] border-r border-border bg-white shadow-popover transition-transform duration-200 ease-out lg:hidden ${
          mobileOpen ? "translate-x-0 pointer-events-auto" : "-translate-x-full pointer-events-none"
        }`}
      >
        <SidebarContent closeMobile={() => closeMobileNav()} />
      </aside>
    </motion.div>
  );
};

export default AppShell;
