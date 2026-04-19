import { useEffect, useState } from "react";
import { motion } from "motion/react";
import { Sparkles, TriangleAlert } from "lucide-react";
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

const InsightsPage = () => {
  const [insights, setInsights] = useState<CircularInsight[]>([]);
  const [anomalies, setAnomalies] = useState<Anomaly[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  return (
    <div className="space-y-6">
      <PageHeader
        title="Insights"
        description="Actionable recommendations and anomaly detection in one flow, designed for immediate operational decisions."
      />

      {loading && <DataLoading rows={6} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && (
        <Tabs defaultValue="recommendations" className="space-y-4">
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
      )}
    </div>
  );
};

export default InsightsPage;

