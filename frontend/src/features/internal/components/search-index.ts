export interface SearchDestination {
  label: string;
  to: string;
  keywords: string[];
  kind: "workspace" | "landing";
}

export const searchDestinations: SearchDestination[] = [
  {
    label: "Dashboard",
    to: "/dashboard",
    kind: "workspace",
    keywords: ["dashboard", "overview", "home", "score", "metrics"],
  },
  {
    label: "Operations",
    to: "/operations",
    kind: "workspace",
    keywords: ["operations", "batches", "inventory", "waste", "production", "logs"],
  },
  {
    label: "Scan",
    to: "/scan",
    kind: "workspace",
    keywords: ["scan", "ocr", "invoice", "camera", "vision", "upload"],
  },
  {
    label: "Materials",
    to: "/materials",
    kind: "workspace",
    keywords: ["materials", "supplier", "grade", "stock", "catalog"],
  },
  {
    label: "Templates",
    to: "/templates",
    kind: "workspace",
    keywords: ["templates", "composition", "formula", "recipe", "smart template"],
  },
  {
    label: "Insights",
    to: "/insights",
    kind: "workspace",
    keywords: ["insights", "recommendation", "anomaly", "alerts", "ai recommendation"],
  },
  {
    label: "Analytics",
    to: "/analytics",
    kind: "workspace",
    keywords: ["analytics", "trend", "chart", "breakdown", "efficiency", "analysis", "reports", "report", "weekly", "monthly", "period summary"],
  },
  {
    label: "How To Use",
    to: "/how-to-use",
    kind: "workspace",
    keywords: ["guide", "how to use", "onboarding", "tutorial", "help", "flow", "start here"],
  },
  {
    label: "Settings",
    to: "/settings",
    kind: "workspace",
    keywords: ["settings", "profile", "preferences", "notifications", "tokens"],
  },
  {
    label: "Landing Home",
    to: "/#home",
    kind: "landing",
    keywords: ["landing", "website", "home page", "hero"],
  },
  {
    label: "How It Works Section",
    to: "/#how-it-works",
    kind: "landing",
    keywords: ["how it works", "workflow", "process", "demo section"],
  },
  {
    label: "Features Section",
    to: "/#features",
    kind: "landing",
    keywords: ["features", "feature", "services", "service", "capabilities"],
  },
  {
    label: "Stats Section",
    to: "/#stats",
    kind: "landing",
    keywords: ["stats", "numbers", "kpi", "performance"],
  },
  {
    label: "FAQ Section",
    to: "/#faq",
    kind: "landing",
    keywords: ["faq", "questions", "help", "support"],
  },
  {
    label: "Get Started Section",
    to: "/#get-started",
    kind: "landing",
    keywords: ["get started", "start", "free", "join", "cta"],
  },
];

const normalize = (value: string) => value.toLowerCase().trim();

const scoreDestination = (query: string, destination: SearchDestination) => {
  const q = normalize(query);
  if (!q) return 0;

  let best = 0;

  for (const key of destination.keywords) {
    const normalizedKey = normalize(key);

    if (normalizedKey === q) {
      best = Math.max(best, 120);
      continue;
    }

    if (normalizedKey.startsWith(q) || q.startsWith(normalizedKey)) {
      best = Math.max(best, 95);
      continue;
    }

    if (normalizedKey.includes(q) || q.includes(normalizedKey)) {
      best = Math.max(best, 78);
      continue;
    }

    const queryWords = q.split(/\s+/);
    const keyWords = normalizedKey.split(/\s+/);
    const sharedWords = queryWords.filter((word) => keyWords.includes(word));

    if (sharedWords.length > 0) {
      best = Math.max(best, 65 + sharedWords.length * 5);
    }
  }

  const label = normalize(destination.label);
  if (label.includes(q)) {
    best = Math.max(best, 80);
  }

  return best;
};

export function resolveSearchDestination(query: string): SearchDestination | null {
  const ranked = searchDestinations
    .map((destination) => ({ destination, score: scoreDestination(query, destination) }))
    .sort((a, b) => b.score - a.score);

  if (!ranked.length || ranked[0].score < 60) {
    return null;
  }

  return ranked[0].destination;
}
