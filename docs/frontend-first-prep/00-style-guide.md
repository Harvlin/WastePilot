# Frontend-First Prep: Style Guide & Pattern Audit

## Visual/Design System

### Color Palette
The app relies on a custom CSS-variable driven color palette in `index.css`. It does not use generic Tailwind colors (like `green-500` or `emerald-500`) for the primary branding, though it uses `emerald`, `amber`, `rose`, and `sky` for semantic status colors.

**Brand Variables:**
```css
--palette-primary-green: 140 100% 19%; /* #006241 */
--palette-light-green: 160 32% 87%; /* #d4e9e2 */
--palette-house-green: 164 31% 17%; /* #1e3932 */
--palette-tea-green: 85 84% 85%; /* #dff9ba */
```

**Common Usage Pattern:**
- Backgrounds often use `bg-black` or `bg-[hsl(var(--palette-house-green))]`.
- Highlights and primary text often use `text-[hsl(var(--palette-tea-green))]` or `text-[hsl(var(--palette-light-green))]`.
- "Glassmorphism" is achieved via custom classes: `.liquid-glass` and `.liquid-glass-strong` which use `backdrop-filter: blur()`, blend modes, and complex masking.

### Typography Scale
Fonts are defined in `tailwind.config.ts`:
```typescript
fontFamily: {
  heading: ["'Instrument Serif'", "serif"],
  body: ["'Barlow'", "sans-serif"],
},
```
**Example (PageHeader.tsx):**
```tsx
<h1 className="text-3xl md:text-4xl font-heading italic text-white tracking-tight leading-[0.9]">{title}</h1>
<p className="text-[hsl(var(--palette-light-green))] opacity-80 font-body mt-3 max-w-2xl">{description}</p>
```

### Spacing, Sizing, and Borders
- Highly rounded corners are preferred: `rounded-2xl`, `rounded-3xl`, and `rounded-full`.
- Borders are typically semi-transparent white: `border-white/10`, `border-white/20`.
- Padding is generous: `p-4`, `p-5`, `p-6` inside cards.

### Component Library Patterns
The project uses Radix/shadcn UI components (`@/components/ui/...`) heavily customized with Tailwind classes.

**Buttons (MaterialsPage.tsx):**
```tsx
<Button onClick={() => openEditor()} className="w-full sm:w-auto rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">
  <Plus className="w-4 h-4" />
  Add Material
</Button>
```
*Note: Always use `rounded-full` for primary actions.*

**Badges (MaterialsPage.tsx & DashboardPage.tsx):**
Status badges use semantic colors with a 15% opacity background and colored text.
```tsx
const gradeClass: Record<Material["circularGrade"], string> = {
  A: "bg-emerald-500/15 text-emerald-300",
  B: "bg-amber-500/15 text-amber-300",
  C: "bg-rose-500/15 text-rose-300",
};

// Usage
<span className={`px-2.5 py-1 rounded-full text-xs ${gradeClass[item.circularGrade]}`}>
  Grade {item.circularGrade}
</span>
```

### Icons
`lucide-react` is used exclusively. Icons are typically `w-4 h-4` or `w-5 h-5` and are often placed inline with text inside buttons or headers.

### Empty, Loading, and Error States
A dedicated file `StateViews.tsx` handles these states consistently using `DataLoading`, `DataEmpty`, and `DataError` components.
**Example (MaterialsPage.tsx):**
```tsx
{loading && <DataLoading rows={6} />}
{error && !loading && <DataError message={error} onRetry={load} />}
{!loading && !error && materials.length === 0 && (
  <DataEmpty
    title="No materials yet"
    description="Create your first material record to enable inventory and template planning."
    action={/* ... */}
  />
)}
```

### Animation/Transition Conventions
Framer Motion (`motion/react`) is used for page transitions and micro-interactions.
**Example (StatCard.tsx):**
```tsx
<motion.div
  whileHover={{ y: -4 }}
  transition={{ duration: 0.2 }}
  className="liquid-glass rounded-3xl p-5"
>
```

---

## Code Structure Conventions

### File/Folder Naming
- **Components/Pages:** `PascalCase.tsx` (e.g., `MaterialsPage.tsx`, `StatCard.tsx`).
- **Utilities/Hooks/Constants:** `kebab-case.ts` (e.g., `internal-api.ts`, `auth-storage.ts`, `search-index.ts`, `use-mobile.tsx`).
- **Structure:** Feature-based grouping under `src/features/` (e.g., `features/internal`, `features/auth`), and page-level components under `src/pages/`.

### Component Structure and Types
- Types are centralized in `types.ts` for feature domains (e.g., `features/internal/types.ts`).
- Props are often defined inline if they are small, or imported from `types.ts`.
- `React.FC` is generally NOT used; standard arrow functions are preferred.

### State Management & Data Fetching
- **Local State:** `useState` is used for UI state and data fetching.
- **Data Fetching:** Custom API client wrapper `internalApi` is used (from `lib/api/internal-api.ts`). Direct `useEffect` calls handle fetching, and data is stored in component state.
**Example (SettingsPage.tsx):**
```tsx
const load = async () => {
  try {
    setLoading(true);
    setError(null);
    const payload = await internalApi.fetchSettings();
    setSettings(payload);
  } catch (err) {
    setError(err instanceof Error ? err.message : "Failed to load settings");
  } finally {
    setLoading(false);
  }
};
useEffect(() => { load(); }, []);
```

### Naming Conventions
- Boolean props/state: Usually omits `is` prefix for simple flags (e.g., `loading`, `editorOpen`, `error` instead of `isLoading`, `isEditorOpen`, `hasError`). Exception: `isOnline`, `isFallbackActive` in `AppShell.tsx`.
- Event Handlers: `load`, `submit`, `removeMaterial`, rather than strictly `handleX`.

### Comment Style
Comments are extremely minimal, used only for critical implementation details. No heavy JSDoc usage on components.

### Imports
Imports are generally grouped: React/vendor imports first, then UI components, then local features/types/utils.

---

## Language & Copy Conventions

### UI Copy Language
The UI is primarily in **English**, but there is a clear presence of **Bahasa Indonesia** in subtitles and descriptions (specifically on operations-heavy pages).
**Example (MaterialsPage.tsx):**
```tsx
<p className="text-white text-xl font-heading italic">Inventory Log</p>
<p className="text-white/60 text-sm font-body">Input stok masuk/keluar per material.</p>
```
*Decision:* Maintain English for primary titles, buttons, and navigation. Use Bahasa Indonesia for descriptive subtitles and operational instructions to match the existing hybrid convention.

### Tone
The tone is professional, direct, and slightly technical but highly actionable.
**Examples (DashboardPage.tsx):**
- "Unresolved red flags lower trust in operational scores and reporting."
- "Potential avoidable waste: 15kg"
- "Keep this clean. Materials here are used by templates, inventory, and OCR save." (MaterialsPage)

### Error Messages
Error messages are concise and utilize `sonner` toasts.
```tsx
toast.error(err instanceof Error ? err.message : "Failed to load material history.");
toast.success("Material deleted.");
```

---

## Routing & Layout Conventions

### Layout Structure
`AppShell.tsx` wraps all authenticated routes. It includes a responsive sidebar (desktop) and mobile drawer, plus a sticky header with a global search and logout.

### Route Registration
Routes are registered in `App.tsx` using `react-router-dom` with `lazy` loading and a `<Suspense>` boundary.
Navigation items are registered in `features/internal/components/navigation.ts` array. New routes must be added to both `App.tsx` and `navigation.ts`.

---

## Ambiguities & Decisions
- **Chart Components:** Recharts is used (`DashboardPage.tsx`), but custom styling is wrapped inside `ChartContainer` from shadcn. We will reuse Recharts + `ChartContainer` for the new forecasting charts.
- **Forms:** Controlled inputs with simple `useState` are used instead of heavy form libraries (like `react-hook-form`), though `react-hook-form` is in `package.json`. *Decision: Stick to simple `useState` for forms unless complex validation is needed, matching `MaterialsPage.tsx` and `SettingsPage.tsx`.*
