import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, XAxis, YAxis } from "recharts";
import { ChartContainer, ChartTooltip, ChartTooltipContent } from "@/components/ui/chart";
import { DataError, DataLoading } from "@/features/internal/components/StateViews";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { AnalyticsPayload } from "@/features/internal/types";
import { internalApi } from "@/lib/api/internal-api";

const trendConfig = {
  score: { label: "Circularity", color: "hsl(var(--palette-tea-green))" },
};

const efficiencyConfig = {
  efficiency: { label: "Efficiency", color: "hsl(var(--palette-light-green))" },
};

const landfillShareConfig = {
  share: { label: "Landfill Share", color: "hsl(var(--palette-primary-green))" },
};

const landfillIntensityConfig = {
  kgPerUnit: { label: "Landfill Intensity", color: "hsl(var(--palette-tea-green))" },
};

const pieColors = [
  "hsl(var(--palette-tea-green))",
  "hsl(var(--palette-light-green))",
  "hsl(var(--palette-primary-green) / 0.95)",
  "hsl(var(--palette-house-green) / 0.95)",
];

const AnalyticsPage = () => {
  const [data, setData] = useState<AnalyticsPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      const payload = await internalApi.fetchAnalytics();
      setData(payload);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load analytics");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Analytics"
        description="Dive into circularity trends, waste composition, and material efficiency to optimize internal loops."
      />

      {loading && <DataLoading rows={7} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && data && (
        <section className="grid grid-cols-1 xl:grid-cols-2 gap-4">
          <div className="liquid-glass rounded-3xl p-6 shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]">
            <p className="text-white text-xl font-heading italic mb-4">Circularity Trend</p>
            <ChartContainer config={trendConfig} className="h-[280px] w-full">
              <LineChart data={data.circularityTrend}>
                <CartesianGrid stroke="hsl(var(--palette-light-green) / 0.16)" vertical={false} />
                <XAxis dataKey="week" tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.72)" }} />
                <YAxis tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.62)" }} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Line type="monotone" dataKey="score" stroke="var(--color-score)" strokeWidth={3} dot={false} />
              </LineChart>
            </ChartContainer>
          </div>

          <div className="liquid-glass rounded-3xl p-6 shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]">
            <p className="text-white text-xl font-heading italic mb-4">Waste Breakdown</p>
            <ChartContainer config={{}} className="h-[280px] w-full">
              <PieChart>
                <ChartTooltip content={<ChartTooltipContent />} />
                <Pie data={data.wasteBreakdown} dataKey="value" nameKey="category" innerRadius={64} outerRadius={92} stroke="hsl(var(--palette-house-green) / 0.7)">
                  {data.wasteBreakdown.map((item, index) => (
                    <Cell key={item.category} fill={pieColors[index % pieColors.length]} />
                  ))}
                </Pie>
              </PieChart>
            </ChartContainer>
            <div className="mt-3 grid grid-cols-2 gap-2">
              {data.wasteBreakdown.map((item, index) => (
                <div key={item.category} className="text-sm font-body text-[hsl(var(--palette-light-green))] opacity-85 flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full" style={{ background: pieColors[index % pieColors.length] }} />
                  {item.category}: {item.value}%
                </div>
              ))}
            </div>
          </div>

          <div className="liquid-glass rounded-3xl p-6 xl:col-span-2 shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]">
            <p className="text-white text-xl font-heading italic mb-4">Material Efficiency</p>
            <ChartContainer config={efficiencyConfig} className="h-[300px] w-full">
              <BarChart data={data.efficiencyByMaterial}>
                <CartesianGrid stroke="hsl(var(--palette-light-green) / 0.16)" vertical={false} />
                <XAxis dataKey="material" tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.72)" }} />
                <YAxis tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.62)" }} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Bar dataKey="efficiency" fill="var(--color-efficiency)" radius={[12, 12, 0, 0]} />
              </BarChart>
            </ChartContainer>
          </div>

          <div className="liquid-glass rounded-3xl p-6 shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]">
            <p className="text-white text-xl font-heading italic mb-4">Landfill Share Trend</p>
            <ChartContainer config={landfillShareConfig} className="h-[260px] w-full">
              <LineChart data={data.landfillShareTrend}>
                <CartesianGrid stroke="hsl(var(--palette-light-green) / 0.16)" vertical={false} />
                <XAxis dataKey="week" tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.72)" }} />
                <YAxis tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.62)" }} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Line type="monotone" dataKey="share" stroke="var(--color-share)" strokeWidth={3} dot={false} />
              </LineChart>
            </ChartContainer>
            <p className="text-white/65 text-sm font-body mt-3">
              Lower is better. Score caps are triggered automatically when landfill share rises above defined thresholds.
            </p>
          </div>

          <div className="liquid-glass rounded-3xl p-6 shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]">
            <p className="text-white text-xl font-heading italic mb-4">Landfill Intensity (kg per unit)</p>
            <ChartContainer config={landfillIntensityConfig} className="h-[260px] w-full">
              <LineChart data={data.landfillIntensityTrend}>
                <CartesianGrid stroke="hsl(var(--palette-light-green) / 0.16)" vertical={false} />
                <XAxis dataKey="week" tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.72)" }} />
                <YAxis tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.62)" }} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Line type="monotone" dataKey="kgPerUnit" stroke="var(--color-kgPerUnit)" strokeWidth={3} dot={false} />
              </LineChart>
            </ChartContainer>
            <p className="text-white/65 text-sm font-body mt-3">
              Tracks absolute landfill impact per finished unit, not only percentage allocation.
            </p>
          </div>
        </section>
      )}
    </div>
  );
};

export default AnalyticsPage;
