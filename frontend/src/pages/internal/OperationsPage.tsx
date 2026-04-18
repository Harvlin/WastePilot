import { FormEvent, useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { AlertTriangle, CheckCircle2, Clock3, Plus, ShieldCheck } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  ActivityLogEntry,
  AuditTrailEntry,
  BatchCloseSummary,
  OperationsPayload,
  WasteDestination,
} from "@/features/internal/types";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { DataEmpty, DataError, DataLoading } from "@/features/internal/components/StateViews";
import { internalApi } from "@/lib/api/internal-api";
import { toast } from "sonner";

const CLOSE_VARIANCE_THRESHOLD = 5;

const confidenceTone = {
  high: "bg-emerald-500/15 text-emerald-300",
  medium: "bg-amber-500/15 text-amber-300",
  low: "bg-rose-500/15 text-rose-300",
} as const;

const severityTone = {
  low: "bg-sky-500/15 text-sky-300",
  medium: "bg-amber-500/15 text-amber-300",
  high: "bg-rose-500/15 text-rose-300",
} as const;

function hoursSince(iso: string) {
  return (Date.now() - new Date(iso).getTime()) / (1000 * 60 * 60);
}

const OperationsPage = () => {
  const [data, setData] = useState<OperationsPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [batchTemplate, setBatchTemplate] = useState("Plain Tee v2");
  const [batchOutput, setBatchOutput] = useState("280");
  const [batchWaste, setBatchWaste] = useState("14");

  const [inventoryMaterial, setInventoryMaterial] = useState("Cotton Roll 280gsm");
  const [inventoryType, setInventoryType] = useState<"IN" | "OUT">("IN");
  const [inventoryQty, setInventoryQty] = useState("20");

  const [wasteMaterial, setWasteMaterial] = useState("Cotton Trim");
  const [wasteDestination, setWasteDestination] = useState<WasteDestination>("reuse");
  const [wasteQty, setWasteQty] = useState("7");

  const [activeCloseBatchId, setActiveCloseBatchId] = useState<string>("");
  const [closeSummary, setCloseSummary] = useState<BatchCloseSummary | null>(null);
  const [closeOutputUnits, setCloseOutputUnits] = useState("0");
  const [closeReason, setCloseReason] = useState("");
  const [closingBatch, setClosingBatch] = useState(false);
  const [loadingCloseSummary, setLoadingCloseSummary] = useState(false);

  const [activityLogs, setActivityLogs] = useState<ActivityLogEntry[]>([]);
  const [auditTrail, setAuditTrail] = useState<AuditTrailEntry[]>([]);

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      const [payload, logs, audits] = await Promise.all([
        internalApi.fetchOperationsPayload(),
        internalApi.fetchActivityLogs(),
        internalApi.fetchAuditTrail(),
      ]);
      setData(payload);
      setActivityLogs(logs);
      setAuditTrail(audits);

      const runningBatch = payload.batches.find((item) => item.status === "running");
      if (runningBatch) {
        setActiveCloseBatchId((prev) => prev || runningBatch.id);
        setCloseOutputUnits(String(runningBatch.outputUnits));
      } else {
        setActiveCloseBatchId("");
        setCloseSummary(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load operations");
    } finally {
      setLoading(false);
    }
  };

  const refreshIntegrity = async () => {
    const [logs, audits] = await Promise.all([
      internalApi.fetchActivityLogs(),
      internalApi.fetchAuditTrail(),
    ]);
    setActivityLogs(logs);
    setAuditTrail(audits);
  };

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    if (!activeCloseBatchId) {
      setCloseSummary(null);
      return;
    }

    let mounted = true;

    const fetchCloseSummary = async () => {
      try {
        setLoadingCloseSummary(true);
        const summary = await internalApi.fetchBatchCloseSummary(activeCloseBatchId);
        if (!mounted) {
          return;
        }
        setCloseSummary(summary);
        setCloseOutputUnits(String(summary.outputUnits));
      } catch (err) {
        if (mounted) {
          toast.error(err instanceof Error ? err.message : "Failed to load batch close summary.");
        }
      } finally {
        if (mounted) {
          setLoadingCloseSummary(false);
        }
      }
    };

    fetchCloseSummary();

    return () => {
      mounted = false;
    };
  }, [activeCloseBatchId]);

  const summary = useMemo(() => {
    if (!data) return { running: 0, completed: 0, wasteTotal: 0 };
    return {
      running: data.batches.filter((item) => item.status === "running").length,
      completed: data.batches.filter((item) => item.status === "completed").length,
      wasteTotal: data.wasteLogs.reduce((acc, item) => acc + item.quantityKg, 0),
    };
  }, [data]);

  const runningBatches = useMemo(
    () => data?.batches.filter((item) => item.status === "running") ?? [],
    [data],
  );

  const overdueBatches = useMemo(
    () => runningBatches.filter((item) => hoursSince(item.startedAt) > 24),
    [runningBatches],
  );

  const handleCreateBatch = async (e: FormEvent) => {
    e.preventDefault();
    if (!data) return;

    try {
      if (!batchTemplate.trim() || Number(batchOutput) <= 0 || Number(batchWaste) < 0) {
        toast.error("Please complete the batch form with valid values.");
        return;
      }

      const newBatch = await internalApi.createBatch({
        templateName: batchTemplate,
        outputUnits: Number(batchOutput),
        wasteKg: Number(batchWaste),
      });

      setData({ ...data, batches: [newBatch, ...data.batches] });
      await refreshIntegrity();
      toast.success(`Batch ${newBatch.id} created.`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to create batch.");
    }
  };

  const handleInventoryAdd = async (e: FormEvent) => {
    e.preventDefault();
    if (!data) return;

    try {
      if (!inventoryMaterial.trim() || Number(inventoryQty) <= 0) {
        toast.error("Please enter a valid inventory movement.");
        return;
      }

      const created = await internalApi.createInventoryLog({
        materialName: inventoryMaterial,
        type: inventoryType,
        quantity: Number(inventoryQty),
        unit: "kg",
        source: "Manual",
      });

      setData({ ...data, inventoryLogs: [created, ...data.inventoryLogs] });
      await refreshIntegrity();
      toast.success("Inventory log added.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to add inventory log.");
    }
  };

  const handleWasteAdd = async (e: FormEvent) => {
    e.preventDefault();
    if (!data) return;

    try {
      if (!wasteMaterial.trim() || Number(wasteQty) <= 0) {
        toast.error("Please enter a valid waste record.");
        return;
      }

      const created = await internalApi.createWasteLog({
        batchId: data.batches[0]?.id ?? "B-NEW",
        materialName: wasteMaterial,
        quantityKg: Number(wasteQty),
        destination: wasteDestination,
        reason: "Operator input",
        aiSuggestedAction: "Collect for secondary accessory production.",
        isRepurposed: wasteDestination === "reuse",
      });

      setData({ ...data, wasteLogs: [created, ...data.wasteLogs] });
      await refreshIntegrity();
      toast.success("Waste log saved.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save waste log.");
    }
  };

  const handleCloseBatch = async (event: FormEvent) => {
    event.preventDefault();
    if (!activeCloseBatchId || !closeSummary) {
      toast.error("Please select a running batch first.");
      return;
    }

    const outputUnits = Number(closeOutputUnits);
    if (!Number.isFinite(outputUnits) || outputUnits <= 0) {
      toast.error("Output units must be greater than zero.");
      return;
    }

    if (Math.abs(closeSummary.variancePercent) > CLOSE_VARIANCE_THRESHOLD && !closeReason.trim()) {
      toast.error(`Variance above ${CLOSE_VARIANCE_THRESHOLD}% requires a close reason.`);
      return;
    }

    try {
      setClosingBatch(true);
      await internalApi.closeBatch({
        batchId: activeCloseBatchId,
        outputUnits,
        closeReason: closeReason.trim() || undefined,
      });

      toast.success("Batch closed successfully with integrity snapshot.");
      setCloseReason("");
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to close batch.");
    } finally {
      setClosingBatch(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Operations"
        description="Run day-to-day production with smart templates, inventory movement, and waste classifications in one super page."
      />

      {overdueBatches.length > 0 && (
        <div className="rounded-2xl border border-amber-300/25 bg-amber-500/10 px-4 py-3 flex flex-wrap items-center gap-2 text-amber-100 text-sm font-body">
          <Clock3 className="w-4 h-4" />
          <span>
            {overdueBatches.length} running batch{overdueBatches.length > 1 ? "es are" : " is"} pending closure.
            Close overdue batches to keep confidence score healthy.
          </span>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="liquid-glass rounded-2xl p-4">
          <p className="text-white/60 text-sm">Running Batches</p>
          <p className="text-3xl font-heading italic text-white mt-2">{summary.running}</p>
        </div>
        <div className="liquid-glass rounded-2xl p-4">
          <p className="text-white/60 text-sm">Completed Today</p>
          <p className="text-3xl font-heading italic text-white mt-2">{summary.completed}</p>
        </div>
        <div className="liquid-glass rounded-2xl p-4">
          <p className="text-white/60 text-sm">Logged Waste</p>
          <p className="text-3xl font-heading italic text-white mt-2">{summary.wasteTotal} kg</p>
        </div>
      </div>

      {loading && <DataLoading rows={5} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && data && (
        <Tabs defaultValue="batches" className="space-y-4">
          <TabsList className="liquid-glass rounded-full h-auto p-1">
            <TabsTrigger value="batches" className="rounded-full data-[state=active]:bg-white/15 data-[state=active]:text-white text-white/70">
              Batches
            </TabsTrigger>
            <TabsTrigger value="inventory" className="rounded-full data-[state=active]:bg-white/15 data-[state=active]:text-white text-white/70">
              Inventory
            </TabsTrigger>
            <TabsTrigger value="waste" className="rounded-full data-[state=active]:bg-white/15 data-[state=active]:text-white text-white/70">
              Waste
            </TabsTrigger>
            <TabsTrigger value="batch-close" className="rounded-full data-[state=active]:bg-white/15 data-[state=active]:text-white text-white/70">
              Batch Close
            </TabsTrigger>
            <TabsTrigger value="integrity" className="rounded-full data-[state=active]:bg-white/15 data-[state=active]:text-white text-white/70">
              Integrity
            </TabsTrigger>
          </TabsList>

          <AnimatePresence mode="wait">
            <TabsContent value="batches" asChild>
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 8 }}>
                <div className="grid grid-cols-1 xl:grid-cols-[0.95fr_1.05fr] gap-4">
                  <form onSubmit={handleCreateBatch} className="liquid-glass rounded-3xl p-5 space-y-4">
                    <h3 className="text-white text-xl font-heading italic">Create Batch</h3>
                    <Input value={batchTemplate} onChange={(e) => setBatchTemplate(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="Template name" />
                    <div className="grid grid-cols-2 gap-3">
                      <Input value={batchOutput} onChange={(e) => setBatchOutput(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="Output units" />
                      <Input value={batchWaste} onChange={(e) => setBatchWaste(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="Waste kg" />
                    </div>
                    <Button className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"><Plus className="w-4 h-4" />Start Batch</Button>
                  </form>

                  <div className="liquid-glass rounded-3xl p-5 overflow-hidden">
                    <h3 className="text-white text-xl font-heading italic mb-3">Production List</h3>
                    {data.batches.length === 0 ? (
                      <DataEmpty title="No batches yet" description="Create your first production batch to begin tracking." />
                    ) : (
                      <Table>
                        <TableHeader>
                          <TableRow className="border-white/10 hover:bg-transparent">
                            <TableHead className="text-white/60">Batch</TableHead>
                            <TableHead className="text-white/60">Template</TableHead>
                            <TableHead className="text-white/60">Output</TableHead>
                            <TableHead className="text-white/60">Waste</TableHead>
                            <TableHead className="text-white/60">Status</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {data.batches.map((batch) => (
                            <TableRow key={batch.id} className="border-white/10 hover:bg-white/[0.03]">
                              <TableCell>{batch.id}</TableCell>
                              <TableCell>{batch.templateName}</TableCell>
                              <TableCell>{batch.outputUnits}</TableCell>
                              <TableCell>{batch.wasteKg} kg</TableCell>
                              <TableCell>
                                <span className={`px-2.5 py-1 rounded-full text-xs ${batch.status === "running" ? "bg-emerald-500/15 text-emerald-300" : "bg-white/10 text-white/70"}`}>
                                  {batch.status}
                                </span>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </div>
                </div>
              </motion.div>
            </TabsContent>

            <TabsContent value="inventory" asChild>
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 8 }}>
                <div className="grid grid-cols-1 xl:grid-cols-[0.95fr_1.05fr] gap-4">
                  <form onSubmit={handleInventoryAdd} className="liquid-glass rounded-3xl p-5 space-y-4">
                    <h3 className="text-white text-xl font-heading italic">Inventory Input</h3>
                    <Input value={inventoryMaterial} onChange={(e) => setInventoryMaterial(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="Material" />
                    <div className="grid grid-cols-2 gap-3">
                      <Select value={inventoryType} onValueChange={(value: "IN" | "OUT") => setInventoryType(value)}>
                        <SelectTrigger className="rounded-xl bg-white/[0.04] border-white/10 text-white">
                          <SelectValue placeholder="Type" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="IN">IN</SelectItem>
                          <SelectItem value="OUT">OUT</SelectItem>
                        </SelectContent>
                      </Select>
                      <Input value={inventoryQty} onChange={(e) => setInventoryQty(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="Quantity (kg)" />
                    </div>
                    <Button className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"><Plus className="w-4 h-4" />Add Log</Button>
                  </form>

                  <div className="liquid-glass rounded-3xl p-5 overflow-hidden">
                    <h3 className="text-white text-xl font-heading italic mb-3">Inventory Logs</h3>
                    {data.inventoryLogs.length === 0 ? (
                      <DataEmpty title="No inventory logs" description="Add IN/OUT movement to keep stock accurate." />
                    ) : (
                      <Table>
                        <TableHeader>
                          <TableRow className="border-white/10 hover:bg-transparent">
                            <TableHead className="text-white/60">Material</TableHead>
                            <TableHead className="text-white/60">Type</TableHead>
                            <TableHead className="text-white/60">Quantity</TableHead>
                            <TableHead className="text-white/60">Source</TableHead>
                            <TableHead className="text-white/60">Time</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {data.inventoryLogs.map((item) => (
                            <TableRow key={item.id} className="border-white/10 hover:bg-white/[0.03]">
                              <TableCell>{item.materialName}</TableCell>
                              <TableCell>
                                <span className={`px-2.5 py-1 rounded-full text-xs ${item.type === "IN" ? "bg-emerald-500/15 text-emerald-300" : "bg-amber-500/15 text-amber-300"}`}>
                                  {item.type}
                                </span>
                              </TableCell>
                              <TableCell>{item.quantity} {item.unit}</TableCell>
                              <TableCell>{item.source}</TableCell>
                              <TableCell>{new Date(item.timestamp).toLocaleTimeString()}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </div>
                </div>
              </motion.div>
            </TabsContent>

            <TabsContent value="waste" asChild>
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 8 }}>
                <div className="grid grid-cols-1 xl:grid-cols-[0.95fr_1.05fr] gap-4">
                  <form onSubmit={handleWasteAdd} className="liquid-glass rounded-3xl p-5 space-y-4">
                    <h3 className="text-white text-xl font-heading italic">Waste Classification</h3>
                    <Input value={wasteMaterial} onChange={(e) => setWasteMaterial(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="Material" />
                    <div className="grid grid-cols-2 gap-3">
                      <Select value={wasteDestination} onValueChange={(value: WasteDestination) => setWasteDestination(value)}>
                        <SelectTrigger className="rounded-xl bg-white/[0.04] border-white/10 text-white">
                          <SelectValue placeholder="Destination" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="reuse">Reuse</SelectItem>
                          <SelectItem value="repair">Repair</SelectItem>
                          <SelectItem value="dispose">Dispose</SelectItem>
                        </SelectContent>
                      </Select>
                      <Input value={wasteQty} onChange={(e) => setWasteQty(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="Quantity (kg)" />
                    </div>
                    <Button className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"><Plus className="w-4 h-4" />Log Waste</Button>
                  </form>

                  <div className="liquid-glass rounded-3xl p-5 overflow-hidden">
                    <h3 className="text-white text-xl font-heading italic mb-3">Waste Logs</h3>
                    {data.wasteLogs.length === 0 ? (
                      <DataEmpty title="No waste logs" description="Track leftovers to improve repurpose decisions." />
                    ) : (
                      <Table>
                        <TableHeader>
                          <TableRow className="border-white/10 hover:bg-transparent">
                            <TableHead className="text-white/60">Batch</TableHead>
                            <TableHead className="text-white/60">Material</TableHead>
                            <TableHead className="text-white/60">Qty</TableHead>
                            <TableHead className="text-white/60">Destination</TableHead>
                            <TableHead className="text-white/60">Action</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {data.wasteLogs.map((item) => (
                            <TableRow key={item.id} className="border-white/10 hover:bg-white/[0.03]">
                              <TableCell>{item.batchId}</TableCell>
                              <TableCell>{item.materialName}</TableCell>
                              <TableCell>{item.quantityKg} kg</TableCell>
                              <TableCell className="capitalize">{item.destination}</TableCell>
                              <TableCell className="text-white/70 text-sm">{item.aiSuggestedAction}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </div>
                </div>
              </motion.div>
            </TabsContent>

            <TabsContent value="batch-close" asChild>
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 8 }}>
                <div className="grid grid-cols-1 xl:grid-cols-[0.9fr_1.1fr] gap-4">
                  <form onSubmit={handleCloseBatch} className="liquid-glass rounded-3xl p-5 space-y-4">
                    <h3 className="text-white text-xl font-heading italic">Batch Close Assistant</h3>
                    <p className="text-white/60 text-sm font-body">
                      Close batch from auto-calculated summary. Manual entry is only required for major variance.
                    </p>

                    <Select
                      value={activeCloseBatchId}
                      onValueChange={(value) => {
                        setActiveCloseBatchId(value);
                        setCloseReason("");
                      }}
                    >
                      <SelectTrigger className="rounded-xl bg-white/[0.04] border-white/10 text-white">
                        <SelectValue placeholder="Select running batch" />
                      </SelectTrigger>
                      <SelectContent>
                        {runningBatches.length === 0 ? (
                          <SelectItem value="__none" disabled>
                            No running batches
                          </SelectItem>
                        ) : (
                          runningBatches.map((batch) => (
                            <SelectItem key={batch.id} value={batch.id}>
                              {batch.id} - {batch.templateName}
                            </SelectItem>
                          ))
                        )}
                      </SelectContent>
                    </Select>

                    <Input
                      value={closeOutputUnits}
                      onChange={(e) => setCloseOutputUnits(e.target.value)}
                      className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                      placeholder="Final output units"
                      type="number"
                    />

                    {closeSummary && Math.abs(closeSummary.variancePercent) > CLOSE_VARIANCE_THRESHOLD && (
                      <Input
                        value={closeReason}
                        onChange={(e) => setCloseReason(e.target.value)}
                        className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                        placeholder="Reason for high variance"
                      />
                    )}

                    <Button
                      disabled={!activeCloseBatchId || closingBatch || runningBatches.length === 0}
                      className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      {closingBatch ? "Closing batch..." : "Confirm Batch Close"}
                    </Button>
                  </form>

                  <div className="liquid-glass rounded-3xl p-5 space-y-4">
                    <h3 className="text-white text-xl font-heading italic">Auto Summary</h3>

                    {loadingCloseSummary && <DataLoading rows={4} />}

                    {!loadingCloseSummary && !closeSummary && (
                      <DataEmpty
                        title="No running batch selected"
                        description="Choose a running batch to see the auto-close summary and confidence preview."
                      />
                    )}

                    {!loadingCloseSummary && closeSummary && (
                      <>
                        <div className="grid grid-cols-2 gap-3 text-sm font-body">
                          <div className="rounded-2xl bg-white/[0.04] border border-white/10 p-3">
                            <p className="text-white/55">Planned Input</p>
                            <p className="text-white mt-1">{closeSummary.plannedInputKg} kg</p>
                          </div>
                          <div className="rounded-2xl bg-white/[0.04] border border-white/10 p-3">
                            <p className="text-white/55">Actual Input</p>
                            <p className="text-white mt-1">{closeSummary.actualInputKg} kg</p>
                          </div>
                          <div className="rounded-2xl bg-white/[0.04] border border-white/10 p-3">
                            <p className="text-white/55">Waste Total</p>
                            <p className="text-white mt-1">{closeSummary.wasteTotalKg} kg</p>
                          </div>
                          <div className="rounded-2xl bg-white/[0.04] border border-white/10 p-3">
                            <p className="text-white/55">Landfill Share</p>
                            <p className="text-white mt-1">{(closeSummary.landfillShare * 100).toFixed(1)}%</p>
                          </div>
                        </div>

                        <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4 space-y-2 text-sm font-body">
                          <p className="text-white/70">Variance</p>
                          <p className={`text-lg ${Math.abs(closeSummary.variancePercent) > CLOSE_VARIANCE_THRESHOLD ? "text-amber-300" : "text-emerald-300"}`}>
                            {closeSummary.variancePercent}%
                          </p>
                          <p className="text-white/60">
                            Confidence: <span className="text-white">{closeSummary.confidenceScore}%</span>
                            <span className={`ml-2 px-2 py-1 rounded-full text-xs ${confidenceTone[closeSummary.confidenceLevel]}`}>
                              {closeSummary.confidenceLevel}
                            </span>
                          </p>
                        </div>

                        <div className="space-y-2">
                          <p className="text-white/75 text-sm font-body">Active Red Flags</p>
                          {closeSummary.redFlags.length === 0 ? (
                            <div className="rounded-2xl border border-emerald-300/20 bg-emerald-500/10 px-3 py-2 text-emerald-200 text-sm font-body">
                              No active red flags. This batch is safe to close.
                            </div>
                          ) : (
                            closeSummary.redFlags.map((flag) => (
                              <div key={flag.id} className="rounded-2xl border border-white/10 bg-white/[0.03] px-3 py-2 text-sm font-body flex items-start gap-2">
                                <AlertTriangle className="w-4 h-4 mt-0.5 text-amber-300" />
                                <div>
                                  <span className={`px-2 py-0.5 rounded-full text-xs ${severityTone[flag.severity]}`}>{flag.severity}</span>
                                  <p className="text-white/80 mt-1">{flag.message}</p>
                                </div>
                              </div>
                            ))
                          )}
                        </div>
                      </>
                    )}
                  </div>
                </div>
              </motion.div>
            </TabsContent>

            <TabsContent value="integrity" asChild>
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 8 }}>
                <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
                  <div className="liquid-glass rounded-3xl p-5 overflow-hidden">
                    <div className="flex items-center gap-2 mb-3">
                      <ShieldCheck className="w-4 h-4 text-[hsl(var(--palette-tea-green))]" />
                      <h3 className="text-white text-xl font-heading italic">Activity Logs</h3>
                    </div>
                    {activityLogs.length === 0 ? (
                      <DataEmpty title="No activity logs" description="System events and operator actions will appear here." />
                    ) : (
                      <div className="space-y-2 max-h-[420px] overflow-y-auto pr-1">
                        {activityLogs.slice(0, 18).map((log) => (
                          <div key={log.id} className="rounded-2xl border border-white/10 bg-white/[0.03] p-3">
                            <p className="text-white text-sm font-body font-medium">{log.action.replaceAll("_", " ")}</p>
                            <p className="text-white/60 text-xs mt-1">{log.actor} • {new Date(log.timestamp).toLocaleString()}</p>
                            {log.details && <p className="text-white/70 text-sm mt-2">{log.details}</p>}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="liquid-glass rounded-3xl p-5 overflow-hidden">
                    <div className="flex items-center gap-2 mb-3">
                      <ShieldCheck className="w-4 h-4 text-[hsl(var(--palette-light-green))]" />
                      <h3 className="text-white text-xl font-heading italic">Audit Trail</h3>
                    </div>
                    {auditTrail.length === 0 ? (
                      <DataEmpty title="No audit trail entries" description="Field-level edits will be tracked for integrity review." />
                    ) : (
                      <Table>
                        <TableHeader>
                          <TableRow className="border-white/10 hover:bg-transparent">
                            <TableHead className="text-white/60">Field</TableHead>
                            <TableHead className="text-white/60">Change</TableHead>
                            <TableHead className="text-white/60">Editor</TableHead>
                            <TableHead className="text-white/60">Flags</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {auditTrail.slice(0, 14).map((entry) => (
                            <TableRow key={entry.id} className="border-white/10 hover:bg-white/[0.03]">
                              <TableCell>{entry.field}</TableCell>
                              <TableCell className="text-white/70 text-sm">{entry.oldValue} → {entry.newValue}</TableCell>
                              <TableCell className="text-white/75 text-sm">{entry.editedBy}</TableCell>
                              <TableCell>
                                {entry.postScoreEditFlag ? (
                                  <span className="px-2 py-1 rounded-full text-xs bg-rose-500/15 text-rose-300">post-score edit</span>
                                ) : (
                                  <span className="px-2 py-1 rounded-full text-xs bg-emerald-500/15 text-emerald-300">tracked</span>
                                )}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    )}
                  </div>
                </div>
              </motion.div>
            </TabsContent>
          </AnimatePresence>
        </Tabs>
      )}
    </div>
  );
};

export default OperationsPage;

