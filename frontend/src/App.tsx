import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import Index from "./pages/Index.tsx";
import Auth from "./pages/Auth.tsx";
import NotFound from "./pages/NotFound.tsx";
import ProtectedRoute from "@/components/ProtectedRoute";
import AppShell from "@/features/internal/components/AppShell";
import DashboardPage from "@/pages/internal/DashboardPage";
import OperationsPage from "@/pages/internal/OperationsPage";
import ScanPage from "@/pages/internal/ScanPage";
import MaterialsPage from "@/pages/internal/MaterialsPage";
import TemplatesPage from "@/pages/internal/TemplatesPage";
import InsightsPage from "@/pages/internal/InsightsPage";
import AnalyticsPage from "@/pages/internal/AnalyticsPage";
import SettingsPage from "@/pages/internal/SettingsPage";

const queryClient = new QueryClient();

const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <Toaster />
      <Sonner />
      <BrowserRouter>
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
              <Route path="/settings" element={<SettingsPage />} />
            </Route>
          </Route>
          {/* ADD ALL CUSTOM ROUTES ABOVE THE CATCH-ALL "*" ROUTE */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
