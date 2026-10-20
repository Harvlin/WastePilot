# WastePilot UI Redesign — Pattern Decisions

**Phase:** 1 — token translation and design gaps
**Status:** Phase 1 decisions are implemented in shared primitives. Phase 5 Dashboard and Analytics adoption is complete; remaining pages are deferred to their approved later phases.

## Token source

`frontend/src/index.css` remains the canonical CSS-variable source; `frontend/tailwind.config.ts` maps those variables into Tailwind utilities and defines the existing shadcn radius, typography, and shadow aliases. No separate theme engine is being added.

| Token | Proposed value | Application |
|---|---|---|
| Canvas | `#F4F4F4` | Page background |
| Surface | `#FFFFFF` | Cards, popovers, controls |
| Muted surface | `#E5E7EB` | Quiet fills and selected neutral states |
| Primary ink | `#141414` | Headlines, primary labels, solid primary actions |
| Secondary ink | `#242424` | Secondary emphasis |
| Tertiary ink | `#898989` | Muted copy, captions, metadata |
| Default border | `#E5E7EB` | Card and control hairlines |
| Strong border | `#767676` | Dividers or outlines needing higher definition |
| Focus ring | `#3C81F5` | Keyboard focus only |
| Radius | `4 / 8 / 12 / 29 / 100px` | Tags / controls / cards / large frames / pills |
| Spacing | `4px` base; `8 / 12 / 24px` common gaps | Existing Tailwind spacing scale |
| Card shadow | Reference's tight, low-elevation shadow | Raised card separation, not decorative depth |
| Popover shadow | Reference's slightly cooler tight shadow | Menus and floating overlays |
| Typeface | Inter, then system sans fallback | Cal Sans is not assumed available; Inter is named in the reference as its secondary UI sans and avoids a proprietary-font dependency |

The design reference specifies Cal Sans / Cal Sans UI Variable Light but provides no font files in its token specification. Inter is proposed for both display and body roles; use weight and size for hierarchy rather than retaining Instrument Serif/Barlow or approximating Cal Sans with a decorative face.

## Patterns missing from the reference

These patterns were approved before Phase 2. Implementations below are limited to reusable primitives; no product page has been migrated.

| Pattern | Approved adaptation | Implementation / rationale |
|---|---|---|
| Data tables | White card, compact editorial rows, subtle horizontal hairlines, near-black column labels, muted metadata; avoid a dark header slab. On narrow screens preserve a `640px` minimum width and scroll inside the card. | Applied to the shared Table primitive; preserves dense tabular data without crushing columns on mobile. |
| Status badges | Neutral by default; blue for active/running or informational, green for completed/healthy, amber for warning, red for critical/failed. Pair every color with text and a dot; never rely on hue alone. Use restrained text/border treatment, not large saturated fills. | `StatusBadge` is implemented with semantic status tokens and a visible label. Current token hues: info `#2457E8`, success `#167D3F`, warning `#8A5A00`, critical `#B92318`. |
| Shift workflow / stepper | Keep the existing five-step workflow and tab-panel behavior. Present it as numbered, equal-span white step cards inspired by the reference's numbered-step cards; distinguish the current step through ink, outline, and a small functional number mark. Do not change order, permissions, or action semantics. | `StepTabs` is implemented as an accessible Radix Tabs wrapper. The Operations page still uses its existing tabs; no page was modified. |
| Charts | Use an ink/gray-first palette, thin gridlines and labels in the three-stop gray ramp. Use blue only for focus/selection/highlight; use line styles, markers, direct labels, and legend text to distinguish series. Reserve status colors for genuinely semantic health/risk data. | Approved for later chart/page adoption. Waste destinations are not inherently health states, so a multicolor jewel palette is not assumed. |
| Forms and validation | 8px controls, white fill, thin neutral border, near-black text, blue visible focus ring; errors use text plus a functional red cue. Keep existing labels, validation, and submit behavior. | Applied to shared Button and Input primitives; validation logic and page-level forms are unchanged. |

## Phase 3 sequencing risk

The internal pages contained explicit dark backgrounds, white text, and `liquid-glass` surfaces rather than inheriting all colors from the app shell. During Phase 3, a shell-scoped compatibility layer was applied to neutralize those legacy surfaces and map their low-contrast text and fills to the approved light tokens; page-level redesign was deferred to subsequent phases.

The mock-API status is already rendered as a banner when fallback is active. Phase 0 initially missed this conditional during the first scan; the shell restyle will preserve the banner and give it the approved restrained informational treatment, without adding new behavior.

## Phase 5 — Dashboard and Analytics application

The Dashboard and Analytics pages use the Phase 2 card, badge, button, and tab primitives. Their charts share `src/features/internal/components/chart-theme.ts`, which references the existing gray text, border, and foreground tokens. Lines and bars use ink/gray values, thin neutral gridlines, and direct labels; the reused Dashboard series is dashed. Waste-breakdown slices use distinct neutral grays for legibility, not to imply different health states. Period-report charts follow the same palette.

The existing fetches, payload fields, metric values, period selection, and mission-queue calculations remain unchanged. Empty chart states are presentation-only and appear when the corresponding existing dataset is empty.

### Circular Score formula discrepancy — preserved, not resolved

The Dashboard and Analytics response values are not calculated with the same backend formula. Dashboard uses `100 × (0.30 × recoveryRate + 0.25 × wasteEfficiency + 0.45 × landfillAvoidance)` before its landfill-share cap; Analytics per-batch circularity trend uses `100 × (0.40 × recoveryRate + 0.60 × landfillAvoidance)` before the same cap. Mock scoring has additional differences from both backend paths. This is a known product/business decision: Phase 5 displays each existing response as supplied and does not normalize or recalculate either score.

## Bug-fix pass: brand accent
- Brand green: --brand = 160 100% 19.2% (#006241), solid fill + white text; used for default Button, active nav, logo, Circular Score. Status green (--status-success) stays a tinted badge with dot/icon.
- Modals (dialog/alert-dialog/sheet/drawer) use bg-card + border-border + shadow-popover.
- Sidebar 302px; header border-b border-border-strong/50; --muted-foreground 38% L, --border 220 10% 84%.
- Boot/route loaders now use bg-background.
