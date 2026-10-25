import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, ArrowRight, ShieldCheck, Sparkles, Target } from "lucide-react";
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts";
import { Link } from "react-router-dom";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ChartContainer, ChartTooltip, ChartTooltipContent } from "@/components/ui/chart";
import { StatusBadge } from "@/components/ui/status-badge";
import { DashboardPayload, InsightStatus, IntegrityOverview } from "@/features/internal/types";
import { CircularGauge } from "@/features/internal/components/CircularGauge";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { StatCard } from "@/features/internal/components/StatCard";
import { chartAxisTick, chartPalette } from "@/features/internal/components/chart-theme";
import { DataError, DataLoading } from "@/features/internal/components/StateViews";
import { internalApi } from "@/lib/api/internal-api";

const chartConfig = {
  input: { label: "Input (kg)", color: chartPalette.primary },
  waste: { label: "Waste (kg)", color: chartPalette.secondary },
  reused: { label: "Reused (kg)", color: chartPalette.tertiary },
};

function describeInsightStatus(status: InsightStatus | string): { label: string; tone: string } {
  const normalized = typeof status === "string" ? status.toLowerCase() : status;
  switch (normalized) {
    case InsightStatus.New:
    case "new":
      return { label: "New", tone: "bg-sky-100 text-sky-800" };
    case InsightStatus.Applied:
    case "applied":
      return { label: "Applied", tone: "bg-emerald-100 text-emerald-800" };
    case InsightStatus.Ignored:
    case "ignored":
      return { label: "Ignored", tone: "border border-border text-muted-foreground" };
    default:
      return { label: String(status), tone: "border border-border text-muted-foreground" };
  }
}

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
    const newRecommendationCount = data.insights.filter((item) => item.status === InsightStatus.New).length;

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

    if (data.topAnomaly?.status === InsightStatus.New) {
      queue.push({
        id: "mission-anomaly",
        title: `Investigate anomaly in ${data.topAnomaly.process}`,
        detail: data.topAnomaly.detectionMode === "PROVISIONAL_THRESHOLD"
          ? `Cold-start signal: ${data.topAnomaly.wasteKg}kg met the provisional ${data.topAnomaly.provisionalThresholdKg}kg threshold.`
          : `Detected z-score ${data.topAnomaly.zScore} with ${data.topAnomaly.wasteKg}kg unusual waste signal.`,
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
        appearance="editorial"
      />

      {loading && <DataLoading rows={6} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && data && (
        <>
          <Card>
            <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between space-y-0">
              <div>
                <CardTitle>Mission Control</CardTitle>
                <CardDescription className="mt-1">
                  Prioritized tasks to move circular score faster, not just update records.
                </CardDescription>
              </div>
              {topMission && (
                <Link to={topMission.to}>
                  <Button className="w-full sm:w-auto">
                    {topMission.cta}
                    <ArrowRight className="h-4 w-4" />
                  </Button>
                </Link>
              )}
            </CardHeader>
            <CardContent className="grid grid-cols-1 gap-3 xl:grid-cols-2">
              {missionQueue.map((mission) => (
                <div key={mission.id} className="rounded-lg border border-border p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex min-w-0 items-center gap-2">
                      <Target className="h-4 w-4 shrink-0 text-muted-foreground" />
                      <p className="truncate font-medium text-foreground">{mission.title}</p>
                    </div>
                    {mission.tone === "high" ? (
                      <StatusBadge status="critical">High priority</StatusBadge>
                    ) : mission.tone === "medium" ? (
                      <StatusBadge status="warning">Medium priority</StatusBadge>
                    ) : (
                      <Badge variant="outline">Low priority</Badge>
                    )}
                  </div>
                  <p className="mt-3 text-sm text-secondary-foreground">{mission.detail}</p>
                  <p className="mt-2 text-xs text-muted-foreground">{mission.impact}</p>
                  <Link to={mission.to} className="mt-4 inline-block">
                    <Button variant="outline">{mission.cta}</Button>
                  </Link>
                </div>
              ))}
            </CardContent>
          </Card>

          <section className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(240px,0.8fr)_minmax(0,2fr)]">
            <div className="space-y-3">
              <CircularGauge value={data.circularScore} />
              <Card>
                <CardContent className="p-4">
                  <p className="text-sm font-medium text-foreground">Composite Circularity Score</p>
                  <p className="mt-2 text-xs text-muted-foreground">
                    Displayed from the current dashboard response. Analytics trend scores are reported separately.
                  </p>
                </CardContent>
              </Card>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
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

          <section className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
            <Card className="min-w-0">
              <CardHeader>
                <CardTitle>7-day waste trend</CardTitle>
                <CardDescription>Material input, waste output, and reuse recorded each day.</CardDescription>
              </CardHeader>
              <CardContent className="min-w-0">
                {data.wasteTrend.length === 0 ? (
                  <div className="flex h-[280px] items-center justify-center rounded-md border border-dashed border-border">
                    <p className="px-6 text-center text-sm text-muted-foreground">
                      No trend data is available for this period.
                    </p>
                  </div>
                ) : (
                  <>
                    <ChartContainer config={chartConfig} className="h-[280px] w-full">
                      <LineChart data={data.wasteTrend} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                        <CartesianGrid stroke={chartPalette.grid} vertical={false} />
                        <XAxis dataKey="date" tickLine={false} axisLine={false} tick={chartAxisTick} />
                        <YAxis tickLine={false} axisLine={false} tick={chartAxisTick} />
                        <ChartTooltip content={<ChartTooltipContent />} />
                        <Line type="monotone" dataKey="input" stroke="var(--color-input)" strokeWidth={2} dot={false} />
                        <Line type="monotone" dataKey="waste" stroke="var(--color-waste)" strokeWidth={2} dot={false} />
                        <Line type="monotone" dataKey="reused" stroke="var(--color-reused)" strokeWidth={2} strokeDasharray="5 4" dot={false} />
                      </LineChart>
                    </ChartContainer>
                    <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-xs text-muted-foreground" aria-label="Chart series">
                      {Object.entries(chartConfig).map(([key, series]) => (
                        <span key={key} className="inline-flex items-center gap-2">
                          <span
                            aria-hidden="true"
                            className={`h-0.5 w-4 ${key === "reused" ? "border-t border-dashed border-muted-foreground" : ""}`}
                            style={key === "reused" ? undefined : { backgroundColor: series.color }}
                          />
                          {series.label}
                        </span>
                      ))}
                    </div>
                  </>
                )}
              </CardContent>
            </Card>

            <div className="space-y-4">
              {data.topAnomaly && (
                <Card>
                  <CardHeader className="flex-row items-start justify-between gap-4 space-y-0">
                    <div>
                      <CardTitle>Anomaly highlight</CardTitle>
                      <CardDescription className="mt-1">{data.topAnomaly.date}</CardDescription>
                    </div>
                    <StatusBadge status="critical">
                      {data.topAnomaly.detectionMode === "PROVISIONAL_THRESHOLD"
                        ? `Provisional ${data.topAnomaly.provisionalThresholdKg}kg threshold`
                        : `Z-score ${data.topAnomaly.zScore}`}
                    </StatusBadge>
                  </CardHeader>
                  <CardContent>
                    <h3 className="font-medium text-foreground">{data.topAnomaly.process}</h3>
                    <p className="mt-3 text-sm text-secondary-foreground">{data.topAnomaly.note}</p>
                    <div className="mt-4 flex items-center gap-2 text-sm text-status-critical">
                      <AlertTriangle className="h-4 w-4" />
                      {data.topAnomaly.wasteKg}kg spike detected
                    </div>
                  </CardContent>
                </Card>
              )}

              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Sparkles className="h-4 w-4 text-muted-foreground" />
                    AI insights preview
                  </CardTitle>
                  <CardDescription>Recent recommendations and their current review status.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  {data.insights.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No insights are available yet.</p>
                  ) : (
                    data.insights.slice(0, 3).map((insight) => (
                      <div key={insight.id} className="rounded-md border border-border p-3">
                        <div className="flex items-start justify-between gap-3">
                          <p className="text-sm font-medium text-foreground">{insight.title}</p>
                          <span className={`rounded-full px-2 py-1 text-xs font-medium ${describeInsightStatus(insight.status).tone}`}>
                            {describeInsightStatus(insight.status).label}
                          </span>
                        </div>
                        <p className="mt-2 text-sm text-secondary-foreground">{insight.content}</p>
                      </div>
                    ))
                  )}
                  {data.insights.length > 0 && (
                    <Link to="/insights" className="inline-flex items-center gap-1 text-sm font-medium text-foreground underline underline-offset-4">
                      View all insights <ArrowRight className="h-3.5 w-3.5" />
                    </Link>
                  )}
                </CardContent>
              </Card>

              {integrity && (
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <ShieldCheck className="h-4 w-4 text-muted-foreground" />
                      Data integrity pulse
                    </CardTitle>
                    <CardDescription>Quality indicators from the current integrity overview.</CardDescription>
                  </CardHeader>
                  <CardContent className="grid grid-cols-1 gap-3 sm:grid-cols-3 xl:grid-cols-1 2xl:grid-cols-3">
                    <div className="rounded-md border border-border p-3">
                      <p className="text-xs text-muted-foreground">Average confidence</p>
                      <p className="mt-1 font-medium text-foreground">{integrity.averageConfidenceScore}%</p>
                    </div>
                    <div className="rounded-md border border-border p-3">
                      <p className="text-xs text-muted-foreground">Open red flags</p>
                      <div className="mt-1 flex items-center justify-between gap-2">
                        <StatusBadge status={integrity.openRedFlags > 0 ? "critical" : "completed"}>
                          {integrity.openRedFlags}
                        </StatusBadge>
                        <Link to="/operations">
                          <Button variant="outline" size="sm">Resolve</Button>
                        </Link>
                      </div>
                    </div>
                    <div className="rounded-md border border-border p-3">
                      <p className="text-xs text-muted-foreground">Overdue closures</p>
                      <div className="mt-1">
                        <StatusBadge status={integrity.overdueBatchClosures > 0 ? "warning" : "completed"}>
                          {integrity.overdueBatchClosures}
                        </StatusBadge>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              )}
            </div>
          </section>
        </>
      )}
    </div>
  );
};

export default DashboardPage;
