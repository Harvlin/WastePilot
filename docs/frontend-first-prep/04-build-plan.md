# Frontend-First Build Plan

This outlines the chronological order of execution for frontend development, prioritizing independent UI components and relying on the frozen mock API layer.

## Build Order

1. **Role-Based UI Gating**
   - *Dependencies:* Extended `AuthUser` mock in `auth-storage.ts` / `mock-api.ts`.
   - *Reuses:* N/A (creates new `RoleGate` wrapper).
   - *Impact:* Lowest risk. We build this first so subsequent features can be gated immediately.

2. **Notification/Alert Center**
   - *Dependencies:* `GET /notifications` mock.
   - *Reuses:* `AppShell.tsx` header modifications, Shadcn Popover.
   - *Impact:* High visibility. If contract changes, only the mapping inside the Popover breaks.

3. **Live Factory Floor View**
   - *Dependencies:* `GET /operations/live-floor` mock.
   - *Reuses:* Dashboard `Card`, semantic badges, `.liquid-glass-strong`.
   - *Impact:* If contract changes, the mapping of `healthIndicator` to colors breaks.

4. **Explainable Confidence Score UI**
   - *Dependencies:* Extended `BatchCloseSummary` mock.
   - *Reuses:* Shadcn `Progress`, `OperationsPage.tsx` batch close modal.
   - *Impact:* Modifies existing critical flow. Ensure fallback to 0% if breakdown object is missing from backend during transition.

5. **Sustainability Impact Display**
   - *Dependencies:* `GET /analytics/sustainability` mock.
   - *Reuses:* `StatCard.tsx`, `AnalyticsPage.tsx` layout.

6. **Comparison/Trend Views**
   - *Dependencies:* `GET /analytics/comparison` mock.
   - *Reuses:* Recharts standard config, `ChartContainer`.

7. **Predictive Forecasting Chart**
   - *Dependencies:* `GET /ai/forecast` mock.
   - *Reuses:* Recharts `LineChart`, `.liquid-glass` cards.
   - *Impact:* Complex UI. If the `predictions` array contract changes, the chart rendering breaks.

8. **Export Triggers**
   - *Dependencies:* `POST /reports/export` mock.
   - *Reuses:* Outline buttons.
   - *Impact:* Requires implementing a mock blob download mechanism (e.g., creating a local URL blob with dummy CSV data) to simulate the real experience.

9. **Polish**
   - Add loading skeletons matching `DataLoading` to all new sections.
   - Check mobile responsiveness for the new charts and Live Floor view.
   - Test offline/fallback banner triggering via mock toggles.
