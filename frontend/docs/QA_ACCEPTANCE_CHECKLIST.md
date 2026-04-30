# Frontend QA Acceptance Checklist

## Scope

This checklist validates the frontend-only implementation for:
- Batch Close Assistant
- Activity Logs and Audit Trail
- Confidence and Red Flags
- Landfill-aware scoring and analytics

## Test Environment

- App builds successfully using `npm run build`
- App runs locally using `npm run dev`
- API provider set to mock (default)
- Browser coverage: Chrome latest, Edge latest

## PASS/FAIL Legend

- `PASS`: behavior exactly matches expected result
- `FAIL`: behavior missing, broken, or inconsistent
- `BLOCKED`: cannot test due to environment issue

## A. Batch Close Assistant

- [ ] A1. Running batch can be selected in Batch Close tab
  - Expected: selected batch summary loads without page reload
- [ ] A2. Auto summary shows planned input, actual input, waste total, landfill share, and variance
  - Expected: values are visible and non-empty
- [ ] A3. If variance is above threshold, close reason is required
  - Expected: user cannot close batch until reason is entered
- [ ] A4. Batch can be closed when all required fields are valid
  - Expected: success toast appears and batch status changes to completed
- [ ] A5. Overdue batch warning appears when running batch exceeds expected window
  - Expected: warning banner is visible in Operations page

## B. Integrity: Activity Logs and Audit Trail

- [ ] B1. New batch action creates activity log entry
  - Expected: `batch_started` appears in Activity Logs
- [ ] B2. Inventory and waste actions create activity log entries
  - Expected: entries appear with actor, action, and timestamp
- [ ] B3. Audit trail displays old value -> new value for tracked edits
  - Expected: at least one tracked audit row visible


## C. Scoring and Trust

- [ ] C1. Dashboard shows circular score in range 0 to 100
  - Expected: score never goes below 0 or above 100
- [ ] C2. Data Integrity Pulse displays confidence and red-flag metrics
  - Expected: confidence, open red flags, overdue closures all visible
- [ ] C3. Confidence level (high/medium/low) appears in batch close summary
  - Expected: chip reflects score band consistently
- [ ] C4. Red flags are displayed when risk conditions are triggered
  - Expected: user sees clear risk messages

## D. Landfill and Analytics

- [ ] D1. Landfill Share Trend chart renders with data
  - Expected: trend line visible with week labels
- [ ] D2. Landfill Intensity chart renders with data
  - Expected: trend line visible with kg/unit values
- [ ] D3. Waste breakdown includes landfill category
  - Expected: landfill category is present in breakdown and legend
- [ ] D4. Copy indicates score cap behavior when landfill share is high
  - Expected: user-facing explanation is visible in analytics context

## E. UX and Accessibility Sanity

- [ ] E1. Keyboard can reach primary actions in Operations tabs
  - Expected: focus order logical and actionable
- [ ] E2. Key actions have clear labels and visible affordance
  - Expected: no icon-only critical action without context
- [ ] E3. Empty and error states provide a next-step message
  - Expected: user is guided, not blocked silently

## F. Regression Safety

- [ ] F1. Existing flows still work: Dashboard, Operations, Scan, Materials, Templates, Insights, Analytics, Settings
  - Expected: all routes load under protected workspace
- [ ] F2. Landing page renders and CTA links function
  - Expected: FAQ and footer content align with updated messaging
- [ ] F3. Build succeeds
  - Expected: `npm run build` exits with code 0

## Exit Criteria

Release candidate is acceptable when:
- No `FAIL` items in sections A-D
- Maximum 2 low-risk `FAIL` items in sections E-F with mitigation notes
- Build is green and critical routes are functional
