# Component & UI Blueprint

## 1. Live Factory Floor View
- **Location:** `features/internal/components/LiveFloorView.tsx` (to be embedded in `OperationsPage.tsx` or `DashboardPage.tsx`).
- **Existing Patterns to Reuse:**
  - Standard Shadcn `Card` for each active line.
  - Semantic `Badge` for `healthIndicator` (e.g. `green` -> `bg-emerald-500/15 text-emerald-300`, matching `CircularGrade` badges).
  - Use `.liquid-glass-strong` wrapper for the container holding all lines.
- **New Patterns:**
  - Pulse animation for the "Running" status indicator (can use `animate-pulse` from Tailwind combined with a small colored dot).

## 2. Confidence Score Breakdown UI
- **Location:** `features/operations/components/ConfidenceBreakdown.tsx` (integrated into the existing Batch Close modal/sheet).
- **Existing Patterns to Reuse:**
  - Progress bars: Shadcn `Progress` component.
  - Typography: Use `.text-[hsl(var(--palette-light-green))]` for labels, and `.text-white` for values.
  - Layout: Grid layout similar to the `StatCard` grouping.

## 3. Role-Based UI Gating
- **Location:** `components/RoleGate.tsx`
- **Implementation:** A wrapper component that checks the `useAuth().user.role`.
- **Existing Patterns:** Similar to `ProtectedRoute.tsx`. Invisible if permission is denied, or shows a fallback lock icon/message using `DataEmpty` pattern from `StateViews.tsx`.

## 4. Notification/Alert Center
- **Location:** `features/internal/components/NotificationDropdown.tsx` (to be placed in `AppShell.tsx` header).
- **Existing Patterns to Reuse:**
  - Shadcn `Popover` or `DropdownMenu` for the tray.
  - Icons: Lucide icons (e.g. `Bell`, `TriangleAlert`).
  - Read/Unread state: Use a solid `text-[hsl(var(--palette-tea-green))]` dot for unread, faded `text-white/40` for read.

## 5. Predictive Forecasting Chart
- **Location:** `features/analytics/components/ForecastingChart.tsx` (used in `AnalyticsPage.tsx`).
- **Existing Patterns to Reuse:**
  - Recharts `LineChart` wrapped in Shadcn `ChartContainer`.
  - Use `hsl(var(--palette-tea-green))` for actuals and a dashed line `strokeDasharray="5 5"` with `hsl(var(--palette-light-green))` for predictions.
  - Container: `.liquid-glass` with `shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]` matching the Dashboard charts.

## 6. Sustainability Impact Display
- **Location:** `features/analytics/components/SustainabilityMetrics.tsx`.
- **Existing Patterns to Reuse:**
  - Reuse the exact `StatCard.tsx` component layout but add bespoke Lucide icons (e.g., `TreePine`, `Droplet`).
  - Tone: Informational (not alert-driven).

## 7. Export Triggers
- **Location:** Buttons scattered across `OperationsPage` and `AnalyticsPage`.
- **Existing Patterns to Reuse:**
  - Standard Shadcn `Button` with `variant="outline"`.
  - Class: `rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10`.
  - Icon: `Download` from Lucide.

## 8. Comparison/Trend Views
- **Location:** `features/analytics/components/ComparisonChart.tsx`.
- **Existing Patterns to Reuse:**
  - Recharts `BarChart` or `LineChart`.
  - Colors: Cycle through the established palette (`--palette-tea-green`, `--palette-light-green`, `--palette-primary-green`).

## Extrapolated Styles (For New Patterns)
Whenever a chart or complex data viz needs new colors, stick to the `[hsl(var(--palette-*-green))]` spectrum. Do not introduce pure blues, reds, or generic grays unless they are for semantic alerts (which already use specific `rose-500/15`, `amber-500/15` classes).

All new UI elements *must* retain the `rounded-2xl` or `rounded-3xl` deeply curved borders and `font-body` for text, `font-heading italic` for large titles.
