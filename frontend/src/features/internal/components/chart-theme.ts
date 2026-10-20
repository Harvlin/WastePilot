export const chartPalette = {
  primary: "hsl(var(--foreground))",
  secondary: "hsl(var(--text-secondary))",
  tertiary: "hsl(var(--muted-foreground))",
  quaternary: "hsl(var(--border-strong))",
  grid: "hsl(var(--border))",
  axis: "hsl(var(--muted-foreground))",
  surface: "hsl(var(--card))",
} as const;

export const chartAxisTick = {
  fill: chartPalette.axis,
  fontSize: 11,
} as const;
