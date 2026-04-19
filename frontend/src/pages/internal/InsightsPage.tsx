import { useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import { ArrowRight, Sparkles, TriangleAlert, Zap } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { DataEmpty, DataError, DataLoading } from "@/features/internal/components/StateViews";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { Anomaly, CircularInsight, InsightStatus } from "@/features/internal/types";
import { internalApi } from "@/lib/api/internal-api";
import { toast } from "sonner";

const statusStyles: Record<InsightStatus, string> = {
  new: "bg-sky-500/15 text-sky-300",
  applied: "bg-emerald-500/15 text-emerald-300",
  ignored: "bg-white/10 text-white/70",
};

type QueueItem = {
  id: string;
  type: "recommendation" | "anomaly";
  title: string;
  detail: string;
  impact: string;
  tab: "recommendations" | "anomalies";
  score: number;
};

const InsightsPage = () => {
  const [insights, setInsights] = useState<CircularInsight[]>([]);
  const [anomalies, setAnomalies] = useState<Anomaly[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"recommendations" | "anomalies">("recommendations");

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      const payload = await internalApi.fetchInsights();
      setInsights(payload.recommendations);
      setAnomalies(payload.anomalies);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load insights");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const applyInsightStatus = async (id: string, status: InsightStatus) => {
    try {
      const payload = await internalApi.updateInsightStatus(id, status);
      setInsights(payload.recommendations);
      toast.success(`Insight status updated to ${status}.`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update insight status.");
    }
  };

  const applyAnomalyStatus = async (id: string, status: InsightStatus) => {
    try {
      const payload = await internalApi.updateAnomalyStatus(id, status);
      setAnomalies(payload);
      toast.success(`Anomaly marked as ${status}.`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update anomaly status.");
    }
  };

  const actionQueue = useMemo<QueueItem[]>(() => {
    const recommendationQueue: QueueItem[] = insights
      .filter((item) => item.status === "new")
      .map((item) => {
        const impactBoost = item.impactCategory === "environment" ? 2.8 : item.impactCategory === "operations" ? 2.1 : 1.6;
        return {
          id: item.id,
          type: "recommendation",
          title: item.title,
          detail: item.content,
          impact: `Estimated circular score gain: +${impactBoost.toFixed(1)} pts`,
          tab: "recommendations",
          score: Math.round(impactBoost * 10),
        };
      });

    const anomalyQueue: QueueItem[] = anomalies
      .filter((item) => item.status === "new")
      .map((item) => ({
        id: item.id,
        type: "anomaly",
        title: `Anomaly: ${item.process}`,
        detail: `z-score ${item.zScore} with ${item.wasteKg}kg unusual waste on ${item.date}.`,
        impact: `Potential avoidable waste: ${Math.max(1, Math.round(item.wasteKg * 0.35))}kg`,
        tab: "anomalies",
        score: Math.round(item.zScore * 12 + item.wasteKg),
      }));

    return [...anomalyQueue, ...recommendationQueue]
      .sort((a, b) => b.score - a.score)
      .slice(0, 6);
  }, [anomalies, insights]);

  const applyAllNewRecommendations = async () => {
    const pendingRecommendations = insights.filter((item) => item.status === "new");
    if (pendingRecommendations.length === 0) {
      toast.info("No new recommendations to apply.");
      return;
    }

    try {
      await Promise.all(pendingRecommendations.map((item) => internalApi.updateInsightStatus(item.id, "applied")));
      await load();
      setActiveTab("recommendations");
      toast.success(`${pendingRecommendations.length} recommendation(s) marked as applied.`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to apply recommendations.");
    }
  };

  const resolveTopAnomaly = async () => {
    const pendingAnomaly = anomalies
      .filter((item) => item.status === "new")
      .sort((a, b) => b.zScore - a.zScore)[0];

    if (!pendingAnomaly) {
      toast.info("No open anomalies to resolve.");
      return;
    }

    try {
      await internalApi.updateAnomalyStatus(pendingAnomaly.id, "applied");
      await load();
      setActiveTab("anomalies");
      toast.success("Top anomaly marked as resolved.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to resolve anomaly.");
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Insights"
        description="Actionable recommendations and anomaly detection in one flow, designed for immediate operational decisions."
      />

      {loading && <DataLoading rows={6} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && (
        <>
          <section className="liquid-glass-strong rounded-3xl p-4 md:p-6 border border-white/10 space-y-4">
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
              <div>
                <p className="text-white text-xl font-heading italic">Action Queue</p>
                <p className="text-white/60 text-sm font-body mt-1">
                  Prioritized actions with estimated impact so teams execute outcomes, not just updates.
                </p>
              </div>
              <div className="flex flex-col sm:flex-row gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10"
                  onClick={() => void resolveTopAnomaly()}
                >
                  Resolve Top Anomaly
                </Button>
                <Button
                  type="button"
                  className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"
                  onClick={() => void applyAllNewRecommendations()}
                >
                  Apply New Recommendations
                </Button>
              </div>
            </div>

            {actionQueue.length === 0 ? (
              <DataEmpty title="Queue is clear" description="No urgent insight or anomaly action right now." />
            ) : (
              <div className="grid grid-cols-1 xl:grid-cols-2 gap-3">
                {actionQueue.map((item) => (
                  <div key={item.id} className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-center gap-2 min-w-0">
                        {item.type === "anomaly" ? (
                          <TriangleAlert className="w-4 h-4 text-rose-300 shrink-0" />
                        ) : (
                          <Zap className="w-4 h-4 text-[hsl(var(--palette-tea-green))] shrink-0" />
                        )}
                        <p className="text-white font-body font-medium truncate">{item.title}</p>
                      </div>
                      <span className={`px-2.5 py-1 rounded-full text-xs ${item.type === "anomaly" ? "bg-rose-500/15 text-rose-300" : "bg-emerald-500/15 text-emerald-300"}`}>
                        {item.type}
                      </span>
                    </div>
                    <p className="text-white/65 text-sm font-body mt-3">{item.detail}</p>
                    <p className="text-white/50 text-xs font-body mt-2">{item.impact}</p>
                    <Button
                      type="button"
                      variant="outline"
                      className="mt-4 rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10"
                      onClick={() => setActiveTab(item.tab)}
                    >
                      Open {item.tab}
                      <ArrowRight className="w-4 h-4" />
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </section>

          <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as "recommendations" | "anomalies")} className="space-y-4">
          <TabsList className="liquid-glass rounded-full h-auto p-1 w-full justify-start overflow-x-auto whitespace-nowrap">
            <TabsTrigger value="recommendations" className="shrink-0 rounded-full data-[state=active]:bg-white/15 data-[state=active]:text-white text-white/70">
              AI Recommendations
            </TabsTrigger>
            <TabsTrigger value="anomalies" className="shrink-0 rounded-full data-[state=active]:bg-white/15 data-[state=active]:text-white text-white/70">
              Anomalies
            </TabsTrigger>
          </TabsList>

          <TabsContent value="recommendations" className="space-y-3">
            {insights.length === 0 ? (
              <DataEmpty title="No recommendations" description="Insights will appear as your production and waste history grows." />
            ) : (
              insights.map((item, index) => (
                <motion.div
                  key={item.id}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.05 }}
                  className="liquid-glass rounded-3xl p-5"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <Sparkles className="w-4 h-4 text-white/80" />
                      <p className="text-white font-body font-medium">{item.title}</p>
                    </div>
                    <span className={`px-2.5 py-1 rounded-full text-xs ${statusStyles[item.status]}`}>{item.status}</span>
                  </div>
                  <p className="text-white/65 text-sm font-body mt-3">{item.content}</p>
                  <div className="mt-4 flex flex-wrap gap-2">
                    <Button
                      size="sm"
                      className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"
                      onClick={() => applyInsightStatus(item.id, "applied")}
                    >
                      Mark Applied
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10"
                      onClick={() => applyInsightStatus(item.id, "ignored")}
                    >
                      Ignore
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      className="rounded-full text-white/80 hover:bg-white/10"
                      onClick={() => applyInsightStatus(item.id, "new")}
                    >
                      Mark New
                    </Button>
                  </div>
                </motion.div>
              ))
            )}
          </TabsContent>

          <TabsContent value="anomalies" className="space-y-3">
            {anomalies.length === 0 ? (
              <DataEmpty title="No anomalies" description="Anomalies are surfaced when waste exceeds expected Z-score thresholds." />
            ) : (
              anomalies.map((item, index) => (
                <motion.div
                  key={item.id}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.05 }}
                  className="liquid-glass rounded-3xl p-5"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <TriangleAlert className="w-4 h-4 text-rose-300" />
                      <p className="text-white font-body font-medium">{item.process}</p>
                    </div>
                    <span className={`px-2.5 py-1 rounded-full text-xs ${statusStyles[item.status]}`}>{item.status}</span>
                  </div>
                  <p className="text-white/65 text-sm font-body mt-3">{item.note}</p>
                  <p className="text-white/45 text-xs font-body mt-2">
                    z-score {item.zScore} | waste {item.wasteKg}kg | {item.date}
                  </p>
                  <div className="mt-4 flex flex-wrap gap-2">
                    <Button
                      size="sm"
                      className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"
                      onClick={() => applyAnomalyStatus(item.id, "applied")}
                    >
                      Resolve
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10"
                      onClick={() => applyAnomalyStatus(item.id, "ignored")}
                    >
                      Dismiss
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      className="rounded-full text-white/80 hover:bg-white/10"
                      onClick={() => applyAnomalyStatus(item.id, "new")}
                    >
                      Reopen
                    </Button>
                  </div>
                </motion.div>
              ))
            )}
          </TabsContent>
          </Tabs>
        </>
      )}
    </div>
  );
};

export default InsightsPage;

