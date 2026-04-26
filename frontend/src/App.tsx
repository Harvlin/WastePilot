import { lazy, Suspense, useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import BootLoadingScreen from "@/components/BootLoadingScreen";

const Index = lazy(() => import("./pages/Index.tsx"));
const Auth = lazy(() => import("./pages/Auth.tsx"));
const NotFound = lazy(() => import("./pages/NotFound.tsx"));
const ProtectedRoute = lazy(() => import("@/components/ProtectedRoute"));
const AppShell = lazy(() => import("@/features/internal/components/AppShell"));
const DashboardPage = lazy(() => import("@/pages/internal/DashboardPage"));
const OperationsPage = lazy(() => import("@/pages/internal/OperationsPage"));
const ScanPage = lazy(() => import("@/pages/internal/ScanPage"));
const MaterialsPage = lazy(() => import("@/pages/internal/MaterialsPage"));
const TemplatesPage = lazy(() => import("@/pages/internal/TemplatesPage"));
const InsightsPage = lazy(() => import("@/pages/internal/InsightsPage"));
const AnalyticsPage = lazy(() => import("@/pages/internal/AnalyticsPage"));
const GuidePage = lazy(() => import("@/pages/internal/GuidePage"));
const SettingsPage = lazy(() => import("@/pages/internal/SettingsPage"));

const queryClient = new QueryClient();

const AppRouteFallback = () => (
  <div className="min-h-screen bg-black flex items-center justify-center">
    <p className="text-sm font-body text-[hsl(var(--palette-light-green))]/80">Loading workspace...</p>
  </div>
);

const App = () => {
  const [showBootLoading, setShowBootLoading] = useState(true);
  const [bootProgress, setBootProgress] = useState(0);

  useEffect(() => {
    if (!showBootLoading) {
      return;
    }

    let animationFrameId = 0;
    const start = performance.now();
    const durationMs = 1400;

    const tick = (time: number) => {
      const ratio = Math.min((time - start) / durationMs, 1);
      const easedRatio = 1 - (1 - ratio) ** 3;
      setBootProgress(Math.round(easedRatio * 100));

      if (ratio < 1) {
        animationFrameId = window.requestAnimationFrame(tick);
        return;
      }

      window.setTimeout(() => {
        setShowBootLoading(false);
      }, 120);
    };

    animationFrameId = window.requestAnimationFrame(tick);

    return () => {
      window.cancelAnimationFrame(animationFrameId);
    };
  }, [showBootLoading]);

  if (showBootLoading) {
    return <BootLoadingScreen progress={bootProgress} />;
  }

  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <Toaster />
        <Sonner />
        <BrowserRouter>
          <Suspense fallback={<AppRouteFallback />}>
            <Routes>
              <Route path="/" element={<Index />} />
              <Route path="/auth" element={<Auth />} />
              <Route element={<ProtectedRoute />}>
                <Route element={<AppShell />}>
                  <Route path="/dashboard" element={<DashboardPage />} />
                  <Route path="/operations" element={<OperationsPage />} />
                  <Route path="/scan" element={<ScanPage />} />
                  <Route path="/materials" element={<MaterialsPage />} />
                  <Route path="/templates" element={<TemplatesPage />} />
                  <Route path="/insights" element={<InsightsPage />} />
                  <Route path="/analytics" element={<AnalyticsPage />} />
                  <Route path="/how-to-use" element={<GuidePage />} />
                  <Route path="/settings" element={<SettingsPage />} />
                </Route>
              </Route>
              {/* ADD ALL CUSTOM ROUTES ABOVE THE CATCH-ALL "*" ROUTE */}
              <Route path="*" element={<NotFound />} />
            </Routes>
          </Suspense>
        </BrowserRouter>
      </TooltipProvider>
    </QueryClientProvider>
  );
};

export default App;
