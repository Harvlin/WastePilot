import { useQuery } from "@tanstack/react-query";
import { formatDistanceToNow } from "date-fns";
import { Activity, Clock, Percent } from "lucide-react";
import { motion } from "framer-motion";
import { useState, useEffect } from "react";
import { internalApi } from "@/lib/api/internal-api";

export default function FloorViewPage() {
  const { data: batches = [], isLoading, isError } = useQuery({
    queryKey: ["operations", "floor-view"],
    queryFn: () => internalApi.fetchFloorViewBatches(),
    refetchInterval: 30000,
  });

  if (isLoading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <p className="text-[hsl(var(--palette-light-green))]/60 font-body text-sm">
          Loading factory floor status...
        </p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <p className="text-red-400 font-body text-sm">
          Failed to load factory floor status.
        </p>
      </div>
    );
  }

  if (batches.length === 0) {
    return (
      <div className="flex h-[400px] flex-col items-center justify-center gap-3">
        <div className="w-12 h-12 rounded-full bg-[hsl(var(--palette-house-green))]/30 flex items-center justify-center text-[hsl(var(--palette-light-green))]/40">
          <Activity className="w-6 h-6" />
        </div>
        <p className="text-[hsl(var(--palette-light-green))]/60 font-body text-sm">
          No running batches on the floor.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-heading text-2xl text-white">Live Factory Floor</h1>
        <p className="font-body text-[hsl(var(--palette-light-green))]/70 text-sm mt-1">
          Real-time status of active production batches.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        {batches.map((batch, index) => (
          <motion.div
            key={batch.batchId}
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.05 }}
            className="liquid-glass rounded-2xl p-5 flex flex-col justify-between"
          >
            <div className="flex items-start justify-between">
              <div>
                <p className="font-heading text-lg text-white">
                  {batch.templateName}
                </p>
                <div className="flex items-center gap-1.5 mt-1 text-[hsl(var(--palette-light-green))]/60 text-xs font-body">
                  <Clock className="w-3.5 h-3.5" />
                  <span>
                    Started {formatDistanceToNow(new Date(batch.startedAt))} ago
                  </span>
                </div>
              </div>
              
              <div className="flex items-center gap-2" title={`Health: ${batch.healthIndicator}`}>
                <div
                  className={`w-3 h-3 rounded-full ${
                    batch.healthIndicator === "green"
                      ? "bg-emerald-400 shadow-[0_0_10px_rgba(52,211,153,0.5)]"
                      : batch.healthIndicator === "amber"
                      ? "bg-amber-400 shadow-[0_0_10px_rgba(251,191,36,0.5)]"
                      : "bg-red-500 shadow-[0_0_10px_rgba(239,68,68,0.5)]"
                  }`}
                />
                <span className="text-xs uppercase tracking-wider font-semibold text-white/70">
                  {batch.healthIndicator}
                </span>
              </div>
            </div>

            <div className="mt-6 flex items-center gap-4">
              <div className="flex-1 liquid-glass-strong rounded-xl p-3 flex flex-col items-center justify-center text-center">
                <span className="text-[hsl(var(--palette-light-green))]/60 text-[10px] uppercase tracking-wider font-bold mb-1">
                  Runtime
                </span>
                <span className="text-white font-body text-base">
                  {batch.runningTimeMinutes}m
                </span>
              </div>
              <div className="flex-1 liquid-glass-strong rounded-xl p-3 flex flex-col items-center justify-center text-center">
                <span className="text-[hsl(var(--palette-light-green))]/60 text-[10px] uppercase tracking-wider font-bold mb-1">
                  Variance
                </span>
                <div className="flex items-center gap-1">
                  <span className="text-white font-body text-base">
                    {batch.variancePercent > 0 ? "+" : ""}
                    {batch.variancePercent.toFixed(1)}
                  </span>
                  <Percent className="w-3.5 h-3.5 text-[hsl(var(--palette-light-green))]/60" />
                </div>
              </div>
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
}
