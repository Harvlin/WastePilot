import { type ReactNode, useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, XAxis, YAxis } from "recharts";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ChartContainer, ChartTooltip, ChartTooltipContent } from "@/components/ui/chart";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { chartAxisTick, chartPalette } from "@/features/internal/components/chart-theme";
import { AnalyticsPayload, ReportPeriod, ReportsPayload } from "@/features/internal/types";
import { internalApi } from "@/lib/api/internal-api";
import { DataError, DataLoading } from "@/features/internal/components/StateViews";

const trendConfig = {
  score: { label: "Per-batch index", color: chartPalette.primary },
};

const efficiencyConfig = {
  efficiency: { label: "Efficiency", color: chartPalette.primary },
};

const landfillShareConfig = {
  share: { label: "Landfill Share", color: chartPalette.primary },
};

const landfillIntensityConfig = {
  kgPerUnit: { label: "Landfill Intensity", color: chartPalette.primary },
};

const reportScoreTrendConfig = {
  circularScore: { label: "Composite score", color: chartPalette.primary },
};

const reportWasteFlowConfig = {
  recoveredKg: { label: "Recovered", color: chartPalette.primary },
  landfillKg: { label: "Landfill", color: chartPalette.tertiary },
};

const pieColors = [
  chartPalette.primary,
  chartPalette.secondary,
  chartPalette.tertiary,
  chartPalette.quaternary,
];

function ChartPanel({
  title,
  description,
  className,
  children,
}: {
  title: string;
  description?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
      </CardHeader>
      <CardContent className="min-w-0">{children}</CardContent>
    </Card>
  );
}

function ChartEmpty({ message }: { message: string }) {
  return (
    <div className="flex h-full min-h-48 items-center justify-center rounded-md border border-dashed border-border px-6">
      <p className="text-center text-sm text-muted-foreground">{message}</p>
    </div>
  );
}

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

  const breakdownConfig = Object.fromEntries(
    (data?.wasteBreakdown ?? []).map((item, index) => [
      item.category,
      { label: item.category, color: pieColors[index % pieColors.length] },
    ]),
  );

  return (
    <div className="space-y-6">
      <PageHeader
        title="Analytics"
        description="Dive into trends and period summaries in one place for faster operational decisions."
        appearance="editorial"
      />

      {loading && <DataLoading rows={7} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && data && (
        <section className="space-y-4">
          <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-2">
            <ChartPanel
              title="Per-batch circularity index"
              description="Per-batch recovery and landfill-avoidance index for the five latest completed batches."
            >
              {data.circularityTrend.length === 0 ? (
                <ChartEmpty message="No circularity trend data is available yet." />
              ) : (
                <ChartContainer config={trendConfig} className="h-[280px] w-full">
                  <LineChart data={data.circularityTrend} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                    <CartesianGrid stroke={chartPalette.grid} vertical={false} />
                    <XAxis dataKey="batchLabel" tickLine={false} axisLine={false} tick={chartAxisTick} />
                    <YAxis tickLine={false} axisLine={false} tick={chartAxisTick} />
                    <ChartTooltip content={<ChartTooltipContent />} />
                    <Line type="monotone" dataKey="score" stroke="var(--color-score)" strokeWidth={2} dot={{ r: 2 }} activeDot={{ r: 4 }} />
                  </LineChart>
                </ChartContainer>
              )}
            </ChartPanel>

            <ChartPanel
              title="Waste breakdown"
              description="Distribution across the waste categories returned by analytics."
            >
              {data.wasteBreakdown.length === 0 ? (
                <ChartEmpty message="No waste breakdown data is available yet." />
              ) : (
                <>
                  <ChartContainer config={breakdownConfig} className="h-[280px] w-full">
                    <PieChart>
                      <ChartTooltip content={<ChartTooltipContent />} />
                      <Pie
                        data={data.wasteBreakdown}
                        dataKey="value"
                        nameKey="category"
                        innerRadius={64}
                        outerRadius={92}
                        stroke={chartPalette.surface}
                        strokeWidth={2}
                      >
                        {data.wasteBreakdown.map((item, index) => (
                          <Cell key={item.category} fill={pieColors[index % pieColors.length]} />
                        ))}
                      </Pie>
                    </PieChart>
                  </ChartContainer>
                  <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2">
                    {data.wasteBreakdown.map((item, index) => (
                      <div key={item.category} className="flex items-center gap-2 text-sm text-secondary-foreground">
                        <span
                          aria-hidden="true"
                          className="h-2.5 w-2.5 shrink-0 rounded-full"
                          style={{ backgroundColor: pieColors[index % pieColors.length] }}
                        />
                        <span className="min-w-0 flex-1 truncate">{item.category}</span>
                        <span className="tabular-nums text-muted-foreground">{item.value}%</span>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </ChartPanel>

            <ChartPanel
              title="Material efficiency"
              description="Efficiency by material using the values supplied by analytics."
              className="xl:col-span-2"
            >
              {data.efficiencyByMaterial.length === 0 ? (
                <ChartEmpty message="No material efficiency data is available yet." />
              ) : (
                <ChartContainer config={efficiencyConfig} className="h-[300px] w-full">
                  <BarChart data={data.efficiencyByMaterial} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                    <CartesianGrid stroke={chartPalette.grid} vertical={false} />
                    <XAxis dataKey="material" tickLine={false} axisLine={false} tick={chartAxisTick} />
                    <YAxis tickLine={false} axisLine={false} tick={chartAxisTick} />
                    <ChartTooltip content={<ChartTooltipContent />} />
                    <Bar dataKey="efficiency" fill="var(--color-efficiency)" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ChartContainer>
              )}
            </ChartPanel>

            <ChartPanel
              title="Landfill share trend"
              description="Lower is better. Score caps apply above defined landfill-share thresholds."
            >
              {data.landfillShareTrend.length === 0 ? (
                <ChartEmpty message="No landfill share trend data is available yet." />
              ) : (
                <ChartContainer config={landfillShareConfig} className="h-[260px] w-full">
                  <LineChart data={data.landfillShareTrend} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                    <CartesianGrid stroke={chartPalette.grid} vertical={false} />
                    <XAxis dataKey="batchLabel" tickLine={false} axisLine={false} tick={chartAxisTick} />
                    <YAxis tickLine={false} axisLine={false} tick={chartAxisTick} />
                    <ChartTooltip content={<ChartTooltipContent />} />
                    <Line type="monotone" dataKey="share" stroke="var(--color-share)" strokeWidth={2} dot={{ r: 2 }} activeDot={{ r: 4 }} />
                  </LineChart>
                </ChartContainer>
              )}
            </ChartPanel>

            <ChartPanel
              title="Landfill intensity (kg per unit)"
              description="Absolute landfill impact per finished unit, alongside landfill-share percentage."
            >
              {data.landfillIntensityTrend.length === 0 ? (
                <ChartEmpty message="No landfill intensity trend data is available yet." />
              ) : (
                <ChartContainer config={landfillIntensityConfig} className="h-[260px] w-full">
                  <LineChart data={data.landfillIntensityTrend} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                    <CartesianGrid stroke={chartPalette.grid} vertical={false} />
                    <XAxis dataKey="batchLabel" tickLine={false} axisLine={false} tick={chartAxisTick} />
                    <YAxis tickLine={false} axisLine={false} tick={chartAxisTick} />
                    <ChartTooltip content={<ChartTooltipContent />} />
                    <Line type="monotone" dataKey="kgPerUnit" stroke="var(--color-kgPerUnit)" strokeWidth={2} dot={{ r: 2 }} activeDot={{ r: 4 }} />
                  </LineChart>
                </ChartContainer>
              )}
            </ChartPanel>
          </div>

          <Card>
            <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle>Period report</CardTitle>
                <CardDescription className="mt-1">
                  Executive-style weekly and monthly snapshots based on activity and usage results.
                </CardDescription>
              </div>
              <Tabs
                value={reportPeriod}
                onValueChange={(value) => setReportPeriod(value as ReportPeriod)}
                className="w-full sm:w-fit"
              >
                <TabsList className="w-full sm:w-fit">
                  <TabsTrigger value="weekly" className="flex-1 sm:flex-none">Weekly</TabsTrigger>
                  <TabsTrigger value="monthly" className="flex-1 sm:flex-none">Monthly</TabsTrigger>
                </TabsList>
              </Tabs>
            </CardHeader>
            <CardContent className="space-y-5">
              {reportLoading && <DataLoading rows={5} />}
              {reportError && !reportLoading && (
                <DataError message={reportError} onRetry={() => void loadReports(reportPeriod)} />
              )}

              {!reportLoading && !reportError && reportData && (
                <>
                  <p className="text-sm text-muted-foreground">{reportData.windowLabel}</p>

                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5">
                    <SummaryCard label="Composite circularity score avg" value={reportData.summary.circularScoreAvg} />
                    <SummaryCard label="Activities" value={reportData.summary.totalActivities} />
                    <SummaryCard label="On-time close" value={`${reportData.summary.onTimeCloseRate}%`} />
                    <SummaryCard
                      label="Inventory flow"
                      value={`IN ${reportData.summary.totalInventoryIn} kg`}
                      detail={`OUT ${reportData.summary.totalInventoryOut} kg`}
                    />
                    <SummaryCard
                      label="Waste outcome"
                      value={`Recovered ${reportData.summary.recoveredWasteKg} kg`}
                      detail={`Landfill ${reportData.summary.landfillWasteKg} kg`}
                    />
                  </div>

                  <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
                    <ChartPanel title="Composite circularity score trend">
                      {reportData.trend.length === 0 ? (
                        <ChartEmpty message="No circular score trend data is available for this period." />
                      ) : (
                        <ChartContainer config={reportScoreTrendConfig} className="h-[240px] w-full">
                          <LineChart data={reportData.trend} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                            <CartesianGrid stroke={chartPalette.grid} vertical={false} />
                            <XAxis dataKey="label" tickLine={false} axisLine={false} tick={chartAxisTick} />
                            <YAxis tickLine={false} axisLine={false} tick={chartAxisTick} />
                            <ChartTooltip content={<ChartTooltipContent />} />
                            <Line type="monotone" dataKey="circularScore" stroke="var(--color-circularScore)" strokeWidth={2} dot={{ r: 2 }} activeDot={{ r: 4 }} />
                          </LineChart>
                        </ChartContainer>
                      )}
                    </ChartPanel>

                    <ChartPanel title="Waste flow trend">
                      {reportData.trend.length === 0 ? (
                        <ChartEmpty message="No waste flow trend data is available for this period." />
                      ) : (
                        <>
                          <ChartContainer config={reportWasteFlowConfig} className="h-[240px] w-full">
                            <BarChart data={reportData.trend} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
                              <CartesianGrid stroke={chartPalette.grid} vertical={false} />
                              <XAxis dataKey="label" tickLine={false} axisLine={false} tick={chartAxisTick} />
                              <YAxis tickLine={false} axisLine={false} tick={chartAxisTick} />
                              <ChartTooltip content={<ChartTooltipContent />} />
                              <Bar dataKey="recoveredKg" fill="var(--color-recoveredKg)" radius={[4, 4, 0, 0]} />
                              <Bar dataKey="landfillKg" fill="var(--color-landfillKg)" radius={[4, 4, 0, 0]} />
                            </BarChart>
                          </ChartContainer>
                          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-xs text-muted-foreground">
                            <SeriesLabel color={chartPalette.primary} label="Recovered" />
                            <SeriesLabel color={chartPalette.tertiary} label="Landfill" />
                          </div>
                        </>
                      )}
                    </ChartPanel>
                  </div>

                  <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
                    <ListPanel title="Top actions">
                      {reportData.topActions.length === 0 ? (
                        <InlineEmpty>No activity data for this period.</InlineEmpty>
                      ) : (
                        reportData.topActions.map((item) => (
                          <div key={item.action} className="flex items-center justify-between gap-3 border-b border-border py-2 last:border-0">
                            <p className="text-sm capitalize text-secondary-foreground">{item.action}</p>
                            <p className="text-sm font-medium tabular-nums text-foreground">{item.count}</p>
                          </div>
                        ))
                      )}
                    </ListPanel>

                    <ListPanel title="Top contributors">
                      {reportData.topContributors.length === 0 ? (
                        <InlineEmpty>No actor data in this period.</InlineEmpty>
                      ) : (
                        reportData.topContributors.map((item) => (
                          <div key={`${item.actor}-${item.lastSeen}`} className="border-b border-border py-2 last:border-0">
                            <div className="flex items-center justify-between gap-3">
                              <p className="truncate text-sm text-secondary-foreground">{item.actor}</p>
                              <p className="text-sm font-medium tabular-nums text-foreground">{item.activities}</p>
                            </div>
                            <p className="mt-1 text-xs text-muted-foreground">Last seen {new Date(item.lastSeen).toLocaleString()}</p>
                          </div>
                        ))
                      )}
                    </ListPanel>

                    <ListPanel title="Highlights">
                      {reportData.highlights.length === 0 ? (
                        <InlineEmpty>Highlights appear when enough activity is available.</InlineEmpty>
                      ) : (
                        reportData.highlights.map((item, index) => (
                          <div key={`${item}-${index}`} className="border-b border-border py-2 last:border-0">
                            <p className="text-sm text-secondary-foreground">{item}</p>
                          </div>
                        ))
                      )}
                    </ListPanel>
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        </section>
      )}
    </div>
  );
};

function SummaryCard({ label, value, detail }: { label: string; value: string | number; detail?: string }) {
  return (
    <div className="rounded-md border border-border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums text-foreground">{value}</p>
      {detail && <p className="mt-1 text-sm text-secondary-foreground">{detail}</p>}
    </div>
  );
}

function SeriesLabel({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span aria-hidden="true" className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}

function ListPanel({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-1">{children}</CardContent>
    </Card>
  );
}

function InlineEmpty({ children }: { children: ReactNode }) {
  return <p className="py-4 text-sm text-muted-foreground">{children}</p>;
}

export default AnalyticsPage;
