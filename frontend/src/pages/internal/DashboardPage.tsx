import { useEffect, useState } from "react";
import { AlertTriangle, Sparkles } from "lucide-react";
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts";
import { internalApi } from "@/lib/api/internal-api";
import { DashboardPayload } from "@/features/internal/types";
import { CircularGauge } from "@/features/internal/components/CircularGauge";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { StatCard } from "@/features/internal/components/StatCard";
import { DataError, DataLoading } from "@/features/internal/components/StateViews";
import { ChartContainer, ChartTooltip, ChartTooltipContent } from "@/components/ui/chart";
import { Badge } from "@/components/ui/badge";

const chartConfig = {
  waste: { label: "Waste (kg)", color: "rgba(255,255,255,0.9)" },
  reused: { label: "Reused (kg)", color: "rgba(255,255,255,0.45)" },
};

const DashboardPage = () => {
  const [data, setData] = useState<DashboardPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      const payload = await internalApi.fetchDashboardPayload();
      setData(payload);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load dashboard data.");
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
        title="Circular Operations Dashboard"
        description="Monitor internal circularity, waste behavior, and AI-driven operational actions from one command surface."
      />

      {loading && <DataLoading rows={6} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && data && (
        <>
          <section className="grid grid-cols-1 xl:grid-cols-[320px_1fr] gap-4">
            <CircularGauge value={data.circularScore} />

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {data.metrics.map((metric) => (
                <StatCard
                  key={metric.id}
                  label={metric.label}
                  value={metric.value}
                  unit={metric.unit}
                  delta={metric.delta}
                />
              ))}
            </div>
          </section>

          <section className="grid grid-cols-1 xl:grid-cols-[1.2fr_0.8fr] gap-4">
            <div className="liquid-glass rounded-3xl p-6">
              <div className="mb-5">
                <p className="text-white/45 text-xs uppercase tracking-[0.18em] font-body">Waste Trend</p>
                <h3 className="text-white text-2xl font-heading italic mt-2">Input, Waste, and Reuse</h3>
              </div>

              <ChartContainer config={chartConfig} className="h-[280px] w-full">
                <LineChart data={data.wasteTrend}>
                  <CartesianGrid stroke="rgba(255,255,255,0.08)" vertical={false} />
                  <XAxis dataKey="date" tickLine={false} axisLine={false} tick={{ fill: "rgba(255,255,255,0.55)" }} />
                  <YAxis tickLine={false} axisLine={false} tick={{ fill: "rgba(255,255,255,0.45)" }} />
                  <ChartTooltip content={<ChartTooltipContent />} />
                  <Line type="monotone" dataKey="waste" stroke="var(--color-waste)" strokeWidth={3} dot={false} />
                  <Line type="monotone" dataKey="reused" stroke="var(--color-reused)" strokeWidth={2} dot={false} />
                </LineChart>
              </ChartContainer>
            </div>

            <div className="space-y-4">
              <div className="liquid-glass rounded-3xl p-6">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="text-white/45 text-xs uppercase tracking-[0.18em] font-body">Anomaly Highlight</p>
                    <h3 className="text-white text-xl font-heading italic mt-2">{data.topAnomaly.process}</h3>
                  </div>
                  <Badge className="bg-rose-500/15 text-rose-300 border-rose-300/20">z-score {data.topAnomaly.zScore}</Badge>
                </div>
                <p className="text-white/65 font-body text-sm mt-4">{data.topAnomaly.note}</p>
                <div className="mt-5 flex items-center gap-2 text-white/70 text-sm font-body">
                  <AlertTriangle className="w-4 h-4" />
                  {data.topAnomaly.wasteKg}kg spike detected on {data.topAnomaly.date}
                </div>
              </div>

              <div className="liquid-glass rounded-3xl p-6">
                <div className="flex items-center gap-2">
                  <Sparkles className="w-4 h-4 text-white/80" />
                  <p className="text-white font-body font-medium">AI Insights Preview</p>
                </div>
                <div className="mt-4 space-y-3">
                  {data.insights.slice(0, 3).map((insight) => (
                    <div key={insight.id} className="rounded-2xl bg-white/[0.04] border border-white/10 p-4">
                      <p className="text-white text-sm font-body font-medium">{insight.title}</p>
                      <p className="text-white/60 text-sm font-body mt-2">{insight.content}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </section>
        </>
      )}
    </div>
  );
};

export default DashboardPage;
