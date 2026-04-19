import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, ArrowRight, ShieldCheck, Sparkles, Target } from "lucide-react";
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { internalApi } from "@/lib/api/internal-api";
import { DashboardPayload, IntegrityOverview } from "@/features/internal/types";
import { CircularGauge } from "@/features/internal/components/CircularGauge";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { StatCard } from "@/features/internal/components/StatCard";
import { DataError, DataLoading } from "@/features/internal/components/StateViews";
import { ChartContainer, ChartTooltip, ChartTooltipContent } from "@/components/ui/chart";
import { Badge } from "@/components/ui/badge";

const chartConfig = {
  waste: { label: "Waste (kg)", color: "hsl(var(--palette-tea-green))" },
  reused: { label: "Reused (kg)", color: "hsl(var(--palette-light-green) / 0.85)" },
};

type MissionItem = {
  id: string;
  title: string;
  detail: string;
  impact: string;
  to: string;
  cta: string;
  priority: number;
  tone: "high" | "medium" | "low";
};

const missionToneClass: Record<MissionItem["tone"], string> = {
  high: "bg-rose-500/15 text-rose-300",
  medium: "bg-amber-500/15 text-amber-300",
  low: "bg-emerald-500/15 text-emerald-300",
};

const DashboardPage = () => {
  const [data, setData] = useState<DashboardPayload | null>(null);
  const [integrity, setIntegrity] = useState<IntegrityOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      const [payload, integrityPayload] = await Promise.all([
        internalApi.fetchDashboardPayload(),
        internalApi.fetchIntegrityOverview(),
      ]);
      setData(payload);
      setIntegrity(integrityPayload);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load dashboard data.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const missionQueue = useMemo<MissionItem[]>(() => {
    if (!data) {
      return [];
    }

    const queue: MissionItem[] = [];
    const newRecommendationCount = data.insights.filter((item) => item.status === "new").length;

    if (integrity && integrity.openRedFlags > 0) {
      queue.push({
        id: "mission-red-flags",
        title: `Resolve ${integrity.openRedFlags} integrity red flag${integrity.openRedFlags > 1 ? "s" : ""}`,
        detail: "Unresolved red flags lower trust in operational scores and reporting.",
        impact: `Confidence risk now: ${integrity.averageConfidenceScore}% average`,
        to: "/operations",
        cta: "Open Integrity",
        priority: 100,
        tone: "high",
      });
    }

    if (data.topAnomaly.status === "new") {
      queue.push({
        id: "mission-anomaly",
        title: `Investigate anomaly in ${data.topAnomaly.process}`,
        detail: `Detected z-score ${data.topAnomaly.zScore} with ${data.topAnomaly.wasteKg}kg unusual waste signal.`,
        impact: `Potential avoidable waste: ${Math.max(1, Math.round(data.topAnomaly.wasteKg * 0.35))}kg`,
        to: "/insights",
        cta: "Handle Anomaly",
        priority: 90,
        tone: "high",
      });
    }

    if (newRecommendationCount > 0) {
      queue.push({
        id: "mission-insights",
        title: `Apply ${newRecommendationCount} new AI recommendation${newRecommendationCount > 1 ? "s" : ""}`,
        detail: "Recommendations are ready and waiting for operational confirmation.",
        impact: `Expected score lift: +${(newRecommendationCount * 1.8).toFixed(1)} pts`,
        to: "/insights",
        cta: "Apply Insights",
        priority: 75,
        tone: "medium",
      });
    }

    if (integrity && integrity.overdueBatchClosures > 0) {
      queue.push({
        id: "mission-close-overdue",
        title: `Close ${integrity.overdueBatchClosures} overdue batch${integrity.overdueBatchClosures > 1 ? "es" : ""}`,
        detail: "Open batches delay reports and increase data drift in score calculations.",
        impact: "Operational clarity improves after close snapshots are finalized.",
        to: "/operations",
        cta: "Close Batches",
        priority: 70,
        tone: "medium",
      });
    }

    if (queue.length === 0) {
      queue.push({
        id: "mission-optimize",
        title: "Optimization window available",
        detail: "No urgent blockers detected. This is a good moment to optimize process efficiency.",
        impact: "Use analytics trend to lock in current performance gains.",
        to: "/analytics",
        cta: "Open Analytics",
        priority: 30,
        tone: "low",
      });
    }

    return queue.sort((a, b) => b.priority - a.priority).slice(0, 4);
  }, [data, integrity]);

  const topMission = missionQueue[0] ?? null;

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
          <section className="liquid-glass-strong rounded-3xl p-4 md:p-6 border border-white/10 space-y-4">
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
              <div>
                <p className="text-white text-xl font-heading italic">Mission Control</p>
                <p className="text-white/60 text-sm font-body mt-1">
                  Prioritized tasks to move circular score faster, not just update records.
                </p>
              </div>
              {topMission && (
                <Link to={topMission.to}>
                  <Button className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">
                    {topMission.cta}
                    <ArrowRight className="w-4 h-4" />
                  </Button>
                </Link>
              )}
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-3">
              {missionQueue.map((mission) => (
                <div key={mission.id} className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2 min-w-0">
                      <Target className="w-4 h-4 text-[hsl(var(--palette-tea-green))] shrink-0" />
                      <p className="text-white font-body font-medium truncate">{mission.title}</p>
                    </div>
                    <span className={`px-2.5 py-1 rounded-full text-xs ${missionToneClass[mission.tone]} shrink-0`}>
                      {mission.tone}
                    </span>
                  </div>
                  <p className="text-white/65 text-sm font-body mt-3">{mission.detail}</p>
                  <p className="text-white/50 text-xs font-body mt-2">{mission.impact}</p>
                  <Link to={mission.to} className="inline-block mt-4">
                    <Button variant="outline" className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10">
                      {mission.cta}
                    </Button>
                  </Link>
                </div>
              ))}
            </div>
          </section>

          <section className="grid grid-cols-1 xl:grid-cols-[320px_1fr] gap-4">
            <div className="space-y-3">
              <CircularGauge value={data.circularScore} />
              <div className="liquid-glass rounded-2xl p-4 border border-white/10">
                <p className="text-white text-sm font-body font-medium">How the score is calculated</p>
                <p className="text-white/60 text-xs font-body mt-2">
                  Weighted by recovery rate, landfill share, batch close discipline, anomaly resolution, and post-score edits.
                </p>
              </div>
            </div>

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
            <div className="liquid-glass rounded-3xl p-6 shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]">
              <div className="mb-5">
                <p className="text-[hsl(var(--palette-light-green))]/65 text-xs uppercase tracking-[0.18em] font-body">Waste Trend</p>
                <h3 className="text-white text-2xl font-heading italic mt-2">Input, Waste, and Reuse</h3>
              </div>

              <ChartContainer config={chartConfig} className="h-[280px] w-full">
                <LineChart data={data.wasteTrend}>
                  <CartesianGrid stroke="hsl(var(--palette-light-green) / 0.16)" vertical={false} />
                  <XAxis dataKey="date" tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.72)" }} />
                  <YAxis tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.62)" }} />
                  <ChartTooltip content={<ChartTooltipContent />} />
                  <Line type="monotone" dataKey="waste" stroke="var(--color-waste)" strokeWidth={3} dot={false} />
                  <Line type="monotone" dataKey="reused" stroke="var(--color-reused)" strokeWidth={2} dot={false} />
                </LineChart>
              </ChartContainer>
            </div>

            <div className="space-y-4">
              <div className="liquid-glass rounded-3xl p-6 shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="text-[hsl(var(--palette-light-green))]/65 text-xs uppercase tracking-[0.18em] font-body">Anomaly Highlight</p>
                    <h3 className="text-white text-xl font-heading italic mt-2">{data.topAnomaly.process}</h3>
                  </div>
                  <Badge className="bg-rose-500/15 text-rose-300 border-rose-300/20">z-score {data.topAnomaly.zScore}</Badge>
                </div>
                <p className="text-[hsl(var(--palette-light-green))] opacity-82 font-body text-sm mt-4">{data.topAnomaly.note}</p>
                <div className="mt-5 flex items-center gap-2 text-[hsl(var(--palette-light-green))] opacity-86 text-sm font-body">
                  <AlertTriangle className="w-4 h-4" />
                  {data.topAnomaly.wasteKg}kg spike detected on {data.topAnomaly.date}
                </div>
              </div>

              <div className="liquid-glass rounded-3xl p-6 shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]">
                <div className="flex items-center gap-2">
                  <Sparkles className="w-4 h-4 text-[hsl(var(--palette-tea-green))]" />
                  <p className="text-[hsl(var(--palette-light-green))] font-body font-medium">AI Insights Preview</p>
                </div>
                <div className="mt-4 space-y-3">
                  {data.insights.slice(0, 3).map((insight) => (
                    <div
                      key={insight.id}
                      className="rounded-2xl bg-[hsl(var(--palette-house-green))]/35 border border-[hsl(var(--palette-house-green))]/60 p-4"
                    >
                      <p className="text-white text-sm font-body font-medium">{insight.title}</p>
                      <p className="text-[hsl(var(--palette-light-green))] opacity-82 text-sm font-body mt-2">{insight.content}</p>
                    </div>
                  ))}
                </div>
              </div>

              {integrity && (
                <div className="liquid-glass rounded-3xl p-6 shadow-[0_0_0_1px_hsl(var(--palette-house-green)_/_0.5)]">
                  <div className="flex items-center gap-2">
                    <ShieldCheck className="w-4 h-4 text-[hsl(var(--palette-light-green))]" />
                    <p className="text-[hsl(var(--palette-light-green))] font-body font-medium">Data Integrity Pulse</p>
                  </div>
                  <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm font-body">
                    <div className="rounded-2xl bg-white/[0.03] border border-white/10 p-3">
                      <p className="text-white/55">Avg Confidence</p>
                      <p className="text-white mt-1">{integrity.averageConfidenceScore}%</p>
                    </div>
                    <div className="rounded-2xl bg-white/[0.03] border border-white/10 p-3">
                      <p className="text-white/55">Open Red Flags</p>
                      <div className="mt-1 flex items-center justify-between gap-2">
                        <p className="text-white">{integrity.openRedFlags}</p>
                        <Link to="/operations">
                          <Button variant="outline" className="h-7 rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10 px-3 text-xs">
                            Resolve
                          </Button>
                        </Link>
                      </div>
                    </div>
                    <div className="rounded-2xl bg-white/[0.03] border border-white/10 p-3">
                      <p className="text-white/55">Post-Score Edits</p>
                      <p className="text-white mt-1">{integrity.postScoreEdits}</p>
                    </div>
                    <div className="rounded-2xl bg-white/[0.03] border border-white/10 p-3">
                      <p className="text-white/55">Overdue Closures</p>
                      <p className="text-white mt-1">{integrity.overdueBatchClosures}</p>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </section>
        </>
      )}
    </div>
  );
};

export default DashboardPage;
