import { ArrowLeft, Menu, Search, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { internalNav } from "@/features/internal/components/navigation";
import { resolveSearchDestination } from "@/features/internal/components/search-index";
import { signOutMockUser } from "@/lib/mock-auth";
import { toast } from "sonner";

const DESKTOP_BREAKPOINT = 1024;

const clearGlobalInteractionLocks = () => {
  const body = document.body;
  const html = document.documentElement;

  body.style.removeProperty("overflow");
  body.style.removeProperty("touch-action");
  body.style.removeProperty("pointer-events");

  html.style.removeProperty("overflow");
  html.style.removeProperty("touch-action");
  html.style.removeProperty("pointer-events");
};

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `group relative flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-body transition-colors ${
    isActive ? "text-[hsl(var(--palette-tea-green))]" : "text-[hsl(var(--palette-light-green))]/70 hover:text-[hsl(var(--palette-light-green))]"
  }`;

const SidebarContent = ({ closeMobile }: { closeMobile?: () => void }) => {
  return (
    <div className="h-full flex flex-col">
      <div className="px-3 pt-4 pb-5 border-b border-[hsl(var(--palette-house-green))]/60 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-3 hover:opacity-90 transition-opacity">
          <img
            src="/logoDevSpeak.png"
            alt="WastePilot"
            className="h-10 w-auto object-contain"
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
            className="w-9 h-9 rounded-full liquid-glass flex items-center justify-center text-[hsl(var(--palette-light-green))] lg:hidden"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      <nav className="flex-1 px-2 py-4 space-y-1.5 overflow-y-auto">
        {internalNav.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink key={item.to} to={item.to} className={navLinkClass} onClick={closeMobile}>
              {({ isActive }) => (
                <>
                  {isActive && (
                    <motion.span
                      layoutId="internal-nav-active"
                      className="absolute inset-0 rounded-2xl bg-[hsl(var(--palette-primary-green))]/35"
                      transition={{ type: "spring", stiffness: 380, damping: 34 }}
                    />
                  )}
                  <Icon
                    className={`relative z-10 w-4 h-4 ${
                      isActive
                        ? "text-[hsl(var(--palette-tea-green))]"
                        : "text-[hsl(var(--palette-light-green))]/55 group-hover:text-[hsl(var(--palette-light-green))]/85"
                    }`}
                  />
                  <span className="relative z-10">{item.label}</span>
                </>
              )}
            </NavLink>
          );
        })}
      </nav>

      <div className="px-3 pb-4 pt-3 border-t border-[hsl(var(--palette-house-green))]/60">
        <div className="liquid-glass rounded-2xl p-3">
          <p className="text-[hsl(var(--palette-light-green))] opacity-80 text-xs font-body">Circular score this week</p>
          <p className="text-2xl font-heading italic text-[hsl(var(--palette-tea-green))] mt-1">76</p>
        </div>
      </div>
    </div>
  );
};

const AppShell = () => {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchText, setSearchText] = useState("");
  const [isOnline, setIsOnline] = useState(() => (typeof navigator === "undefined" ? true : navigator.onLine));
  const mobileDrawerRef = useRef<HTMLElement | null>(null);
  const location = useLocation();
  const navigate = useNavigate();
  const pageLabel = internalNav.find((item) => item.to === location.pathname)?.label ?? "Workspace";

  const openMobileNav = useCallback(() => {
    setMobileOpen(true);
  }, []);

  const closeMobileNav = useCallback(() => {
    setMobileOpen(false);
  }, []);

  const handleMobileMenuButtonClick = useCallback((event: React.MouseEvent<HTMLButtonElement>) => {
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
      clearGlobalInteractionLocks();
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
    html.style.overflow = "hidden";
    html.style.touchAction = "none";

    return () => {
      clearGlobalInteractionLocks();
    };
  }, [mobileOpen]);

  useEffect(() => () => {
    clearGlobalInteractionLocks();
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
    if (!mobileOpen) {
      return;
    }

    const onPointerDown = (event: PointerEvent) => {
      const target = event.target as Node | null;
      const drawer = mobileDrawerRef.current;

      if (!target || !drawer) {
        return;
      }

      if (!drawer.contains(target)) {
        closeMobileNav();
      }
    };

    document.addEventListener("pointerdown", onPointerDown, true);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown, true);
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

  const handleLogout = () => {
    closeMobileNav();
    signOutMockUser();
    toast.success("You have been logged out.");
    navigate("/");
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 10, filter: "blur(8px)" }}
      animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
      transition={{ duration: 0.32, ease: "easeOut" }}
      className="relative min-h-screen w-full overflow-x-clip bg-black text-white"
    >
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute -top-40 -right-20 w-[420px] h-[420px] rounded-full bg-[hsl(var(--palette-primary-green))]/20 blur-3xl" />
        <div className="absolute top-[30%] -left-24 w-[340px] h-[340px] rounded-full bg-[hsl(var(--palette-house-green))]/24 blur-3xl" />
      </div>

      <div className="relative z-10 flex min-h-screen">
        <div className="hidden lg:block w-[302px] p-4">
          <aside className="sticky top-4 h-[calc(100vh-2rem)] liquid-glass-strong rounded-3xl border border-[hsl(var(--palette-house-green))]/70 overflow-hidden">
            <SidebarContent />
          </aside>
        </div>

        <div className="flex-1 min-w-0">
          <header className="sticky top-3 md:top-4 z-30 px-3 md:px-8">
            <div className="px-3 md:px-6 py-3 liquid-glass-strong rounded-2xl border border-[hsl(var(--palette-house-green))]/70">
              <div className="flex items-center justify-between gap-3 md:gap-4">
                <div className="flex items-center gap-3 min-w-0">
                <button
                  type="button"
                  className="lg:hidden w-10 h-10 rounded-full liquid-glass flex items-center justify-center"
                  onClick={handleMobileMenuButtonClick}
                  aria-label={mobileOpen ? "Close navigation" : "Open navigation"}
                >
                  {mobileOpen
                    ? <X className="w-5 h-5 text-[hsl(var(--palette-light-green))]" />
                    : <Menu className="w-5 h-5 text-[hsl(var(--palette-light-green))]" />}
                </button>
                <div className="min-w-0">
                  <p className="text-[hsl(var(--palette-light-green))]/60 text-xs uppercase tracking-[0.18em] font-body">Workspace</p>
                  <p className="text-white font-body font-medium truncate">{pageLabel}</p>
                </div>
              </div>

              <div className="hidden md:flex items-center gap-2 liquid-glass rounded-full px-3 py-2 w-[340px] lg:w-[380px]">
                <button
                  type="button"
                  onClick={handleSearchSubmit}
                  className="text-[hsl(var(--palette-light-green))]/65 hover:text-[hsl(var(--palette-tea-green))] transition-colors"
                  aria-label="Search"
                >
                  <Search className="w-4 h-4" />
                </button>
                <input
                  className="bg-transparent w-full text-sm font-body text-white placeholder:text-[hsl(var(--palette-light-green))]/45 focus:outline-none"
                  placeholder="Search features, services, sections..."
                  value={searchText}
                  onChange={(e) => setSearchText(e.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      handleSearchSubmit();
                    }
                  }}
                />
              </div>

              <button
                type="button"
                onClick={handleLogout}
                className="inline-flex items-center gap-2 rounded-full px-3 sm:px-4 py-2 text-sm font-body text-[hsl(var(--palette-light-green))]/90 liquid-glass hover:bg-[hsl(var(--palette-house-green))]/45 transition-colors"
              >
                <ArrowLeft className="w-4 h-4" />
                <span className="hidden sm:inline">Log out</span>
              </button>
            </div>

              <div className="md:hidden mt-3 flex items-center gap-2 liquid-glass rounded-full px-3 py-2">
                <button
                  type="button"
                  onClick={handleSearchSubmit}
                  className="text-[hsl(var(--palette-light-green))]/65 hover:text-[hsl(var(--palette-tea-green))] transition-colors"
                  aria-label="Search"
                >
                  <Search className="w-4 h-4" />
                </button>
                <input
                  className="bg-transparent w-full text-sm font-body text-white placeholder:text-[hsl(var(--palette-light-green))]/45 focus:outline-none"
                  placeholder="Jump to page..."
                  value={searchText}
                  onChange={(e) => setSearchText(e.target.value)}
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
            <div className="px-3 md:px-8 mt-3">
              <div className="rounded-2xl border border-amber-300/25 bg-amber-500/10 px-4 py-3 text-amber-100 text-sm font-body">
                You are offline. Data updates may fail until the connection is restored.
              </div>
            </div>
          )}

          <main className="p-4 md:p-8 pt-6 md:pt-8">
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={location.pathname}
                initial={{ opacity: 0, y: 12, filter: "blur(8px)" }}
                animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
                exit={{ opacity: 0, y: -8, filter: "blur(8px)" }}
                transition={{ duration: 0.28, ease: "easeOut" }}
              >
                <Outlet />
              </motion.div>
            </AnimatePresence>
          </main>
        </div>
      </div>

      <AnimatePresence initial={false}>
        {mobileOpen && (
          <>
            <motion.button
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={(event) => {
                event.stopPropagation();
                closeMobileNav();
              }}
              aria-label="Close navigation overlay"
              className="fixed inset-0 bg-black/60 z-40 lg:hidden"
            />
            <motion.aside
              ref={mobileDrawerRef}
              initial={{ x: -320, opacity: 0 }}
              animate={{ x: 0, opacity: 1 }}
              exit={{ x: -320, opacity: 0 }}
              transition={{ duration: 0.24, ease: "easeOut" }}
              onClick={(event) => event.stopPropagation()}
              role="dialog"
              aria-modal="true"
              aria-label="Mobile navigation"
              className="fixed top-0 left-0 h-full w-[88vw] max-w-[320px] z-50 border-r border-[hsl(var(--palette-house-green))]/70 bg-black/95 backdrop-blur-2xl lg:hidden"
            >
              <SidebarContent closeMobile={() => closeMobileNav()} />
            </motion.aside>
          </>
        )}
      </AnimatePresence>
    </motion.div>
  );
};

export default AppShell;
