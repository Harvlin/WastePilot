import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, XAxis, YAxis } from "recharts";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ChartContainer, ChartTooltip, ChartTooltipContent } from "@/components/ui/chart";
import { DataEmpty, DataError, DataLoading } from "@/features/internal/components/StateViews";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { AnalyticsPayload, ReportPeriod, ReportsPayload } from "@/features/internal/types";
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

const reportScoreTrendConfig = {
  circularScore: { label: "Circular Score", color: "hsl(var(--palette-tea-green))" },
};

const reportWasteFlowConfig = {
  recoveredKg: { label: "Recovered", color: "hsl(var(--palette-tea-green))" },
  landfillKg: { label: "Landfill", color: "hsl(var(--palette-primary-green))" },
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
  const [reportPeriod, setReportPeriod] = useState<ReportPeriod>("weekly");
  const [reportData, setReportData] = useState<ReportsPayload | null>(null);
  const [reportLoading, setReportLoading] = useState(true);
  const [reportError, setReportError] = useState<string | null>(null);

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

  const loadReports = async (period: ReportPeriod) => {
    try {
      setReportLoading(true);
      setReportError(null);
      const payload = await internalApi.fetchReportsPayload(period);
      setReportData(payload);
    } catch (err) {
      setReportError(err instanceof Error ? err.message : "Unable to load reports");
      setReportData(null);
    } finally {
      setReportLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    void loadReports(reportPeriod);
  }, [reportPeriod]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Analytics"
        description="Dive into trends and period summaries in one place for faster operational decisions."
      />

      {loading && <DataLoading rows={7} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && data && (
        <section className="space-y-4">
          <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
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
              <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-2">
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
          </div>

          <div className="liquid-glass-strong rounded-3xl p-4 md:p-6 border border-white/10 space-y-4">
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
              <div>
                <p className="text-white text-xl font-heading italic">Period Report Section</p>
                <p className="text-white/60 text-sm font-body mt-1">
                  Executive-style weekly and monthly snapshots based on activity and usage results.
                </p>
              </div>
              <Tabs
                value={reportPeriod}
                onValueChange={(value) => setReportPeriod(value as ReportPeriod)}
                className="w-full md:w-fit"
              >
                <TabsList className="liquid-glass rounded-full h-auto p-1 w-full md:w-auto justify-start overflow-x-auto whitespace-nowrap">
                  <TabsTrigger value="weekly" className="shrink-0 rounded-full data-[state=active]:bg-white/15 data-[state=active]:text-white text-white/70">
                    Weekly
                  </TabsTrigger>
                  <TabsTrigger value="monthly" className="shrink-0 rounded-full data-[state=active]:bg-white/15 data-[state=active]:text-white text-white/70">
                    Monthly
                  </TabsTrigger>
                </TabsList>
              </Tabs>
            </div>

            {reportLoading && <DataLoading rows={5} />}
            {reportError && !reportLoading && <DataError message={reportError} onRetry={() => void loadReports(reportPeriod)} />}

            {!reportLoading && !reportError && reportData && (
              <>
                <p className="text-white/55 text-sm font-body">{reportData.windowLabel}</p>

                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3">
                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3">
                    <p className="text-white/55 text-xs">Circular Score Avg</p>
                    <p className="text-white text-2xl font-heading italic mt-1">{reportData.summary.circularScoreAvg}</p>
                  </div>
                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3">
                    <p className="text-white/55 text-xs">Activities</p>
                    <p className="text-white text-2xl font-heading italic mt-1">{reportData.summary.totalActivities}</p>
                  </div>
                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3">
                    <p className="text-white/55 text-xs">On-Time Close</p>
                    <p className="text-white text-2xl font-heading italic mt-1">{reportData.summary.onTimeCloseRate}%</p>
                  </div>
                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3">
                    <p className="text-white/55 text-xs">Inventory Flow</p>
                    <p className="text-emerald-300 text-sm mt-1">IN {reportData.summary.totalInventoryIn} kg</p>
                    <p className="text-amber-300 text-sm">OUT {reportData.summary.totalInventoryOut} kg</p>
                  </div>
                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3">
                    <p className="text-white/55 text-xs">Waste Outcome</p>
                    <p className="text-emerald-300 text-sm mt-1">Recovered {reportData.summary.recoveredWasteKg} kg</p>
                    <p className="text-rose-300 text-sm">Landfill {reportData.summary.landfillWasteKg} kg</p>
                  </div>
                </div>

                <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                    <p className="text-white text-lg font-heading italic mb-3">Circular Score Trend</p>
                    <ChartContainer config={reportScoreTrendConfig} className="h-[240px] w-full">
                      <LineChart data={reportData.trend}>
                        <CartesianGrid stroke="hsl(var(--palette-light-green) / 0.16)" vertical={false} />
                        <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.72)" }} />
                        <YAxis tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.62)" }} />
                        <ChartTooltip content={<ChartTooltipContent />} />
                        <Line type="monotone" dataKey="circularScore" stroke="var(--color-circularScore)" strokeWidth={3} dot={false} />
                      </LineChart>
                    </ChartContainer>
                  </div>

                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                    <p className="text-white text-lg font-heading italic mb-3">Waste Flow Trend</p>
                    <ChartContainer config={reportWasteFlowConfig} className="h-[240px] w-full">
                      <BarChart data={reportData.trend}>
                        <CartesianGrid stroke="hsl(var(--palette-light-green) / 0.16)" vertical={false} />
                        <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.72)" }} />
                        <YAxis tickLine={false} axisLine={false} tick={{ fill: "hsl(var(--palette-light-green) / 0.62)" }} />
                        <ChartTooltip content={<ChartTooltipContent />} />
                        <Bar dataKey="recoveredKg" fill="var(--color-recoveredKg)" radius={[8, 8, 0, 0]} />
                        <Bar dataKey="landfillKg" fill="var(--color-landfillKg)" radius={[8, 8, 0, 0]} />
                      </BarChart>
                    </ChartContainer>
                  </div>
                </div>

                <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4 space-y-2">
                    <p className="text-white text-lg font-heading italic">Top Actions</p>
                    {reportData.topActions.length === 0 ? (
                      <DataEmpty title="No actions" description="No activity data for this period." />
                    ) : (
                      reportData.topActions.map((item) => (
                        <div key={item.action} className="rounded-xl border border-white/10 px-3 py-2 flex items-center justify-between">
                          <p className="text-white/80 text-sm capitalize">{item.action}</p>
                          <p className="text-white text-sm font-medium">{item.count}</p>
                        </div>
                      ))
                    )}
                  </div>

                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4 space-y-2">
                    <p className="text-white text-lg font-heading italic">Top Contributors</p>
                    {reportData.topContributors.length === 0 ? (
                      <DataEmpty title="No contributors" description="No actor data in this period." />
                    ) : (
                      reportData.topContributors.map((item) => (
                        <div key={`${item.actor}-${item.lastSeen}`} className="rounded-xl border border-white/10 px-3 py-2">
                          <div className="flex items-center justify-between">
                            <p className="text-white/85 text-sm">{item.actor}</p>
                            <p className="text-white text-sm font-medium">{item.activities}</p>
                          </div>
                          <p className="text-white/50 text-xs mt-1">Last seen {new Date(item.lastSeen).toLocaleString()}</p>
                        </div>
                      ))
                    )}
                  </div>

                  <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4 space-y-2">
                    <p className="text-white text-lg font-heading italic">Highlights</p>
                    {reportData.highlights.length === 0 ? (
                      <DataEmpty title="No highlights" description="Highlights appear when enough activity is available." />
                    ) : (
                      reportData.highlights.map((item, index) => (
                        <div key={`${item}-${index}`} className="rounded-xl border border-white/10 px-3 py-2">
                          <p className="text-white/80 text-sm">{item}</p>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </>
            )}
          </div>
        </section>
      )}
    </div>
  );
};

export default AnalyticsPage;
