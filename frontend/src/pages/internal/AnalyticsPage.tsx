import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, XAxis, YAxis } from "recharts";
import { ChartContainer, ChartTooltip, ChartTooltipContent } from "@/components/ui/chart";
import { DataError, DataLoading } from "@/features/internal/components/StateViews";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { AnalyticsPayload } from "@/features/internal/types";
import { internalApi } from "@/lib/api/internal-api";

const trendConfig = {
  score: { label: "Circularity", color: "rgba(255,255,255,0.92)" },
};

const efficiencyConfig = {
  efficiency: { label: "Efficiency", color: "rgba(255,255,255,0.82)" },
};

const pieColors = ["rgba(255,255,255,0.9)", "rgba(255,255,255,0.7)", "rgba(255,255,255,0.5)", "rgba(255,255,255,0.35)"];

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
          <div className="liquid-glass rounded-3xl p-6">
            <p className="text-white text-xl font-heading italic mb-4">Circularity Trend</p>
            <ChartContainer config={trendConfig} className="h-[280px] w-full">
              <LineChart data={data.circularityTrend}>
                <CartesianGrid stroke="rgba(255,255,255,0.08)" vertical={false} />
                <XAxis dataKey="week" tickLine={false} axisLine={false} tick={{ fill: "rgba(255,255,255,0.55)" }} />
                <YAxis tickLine={false} axisLine={false} tick={{ fill: "rgba(255,255,255,0.45)" }} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Line type="monotone" dataKey="score" stroke="var(--color-score)" strokeWidth={3} dot={false} />
              </LineChart>
            </ChartContainer>
          </div>

          <div className="liquid-glass rounded-3xl p-6">
            <p className="text-white text-xl font-heading italic mb-4">Waste Breakdown</p>
            <ChartContainer config={{}} className="h-[280px] w-full">
              <PieChart>
                <ChartTooltip content={<ChartTooltipContent />} />
                <Pie data={data.wasteBreakdown} dataKey="value" nameKey="category" innerRadius={64} outerRadius={92}>
                  {data.wasteBreakdown.map((item, index) => (
                    <Cell key={item.category} fill={pieColors[index % pieColors.length]} />
                  ))}
                </Pie>
              </PieChart>
            </ChartContainer>
            <div className="mt-3 grid grid-cols-2 gap-2">
              {data.wasteBreakdown.map((item, index) => (
                <div key={item.category} className="text-sm font-body text-white/70 flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full" style={{ background: pieColors[index % pieColors.length] }} />
                  {item.category}: {item.value}%
                </div>
              ))}
            </div>
          </div>

          <div className="liquid-glass rounded-3xl p-6 xl:col-span-2">
            <p className="text-white text-xl font-heading italic mb-4">Material Efficiency</p>
            <ChartContainer config={efficiencyConfig} className="h-[300px] w-full">
              <BarChart data={data.efficiencyByMaterial}>
                <CartesianGrid stroke="rgba(255,255,255,0.08)" vertical={false} />
                <XAxis dataKey="material" tickLine={false} axisLine={false} tick={{ fill: "rgba(255,255,255,0.55)" }} />
                <YAxis tickLine={false} axisLine={false} tick={{ fill: "rgba(255,255,255,0.45)" }} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Bar dataKey="efficiency" fill="var(--color-efficiency)" radius={[12, 12, 0, 0]} />
              </BarChart>
            </ChartContainer>
          </div>
        </section>
      )}
    </div>
  );
};

export default AnalyticsPage;
