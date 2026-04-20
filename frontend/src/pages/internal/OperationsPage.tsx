import { FormEvent, useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { AlertTriangle, CheckCircle2, Clock3, Plus, ShieldCheck } from "lucide-react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Tabs, TabsContent } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import {
  ActivityLogEntry,
  AuditTrailEntry,
  BatchCloseSummary,
  OperationsPayload,
  WasteDestination,
} from "@/features/internal/types";
import { CLOSE_VARIANCE_THRESHOLD } from "@/features/internal/constants";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { DataEmpty, DataError, DataLoading } from "@/features/internal/components/StateViews";
import { internalApi } from "@/lib/api/internal-api";
import { toast } from "sonner";

type OperationTab = "batches" | "inventory" | "waste" | "batch-close" | "integrity";

const operationWorkflowSteps: Array<{
  tab: OperationTab;
  title: string;
  helper: string;
}> = [
  {
    tab: "batches",
    title: "1. Start Batch",
    helper: "Create a running batch for the current shift.",
  },
  {
    tab: "inventory",
    title: "2. Log Material",
    helper: "Record stock IN/OUT while production is active.",
  },
  {
    tab: "waste",
    title: "3. Log Waste",
    helper: "Classify waste destination and recovery status.",
  },
  {
    tab: "batch-close",
    title: "4. Close Batch",
    helper: "Review summary and confirm final output.",
  },
  {
    tab: "integrity",
    title: "5. Integrity Check",
    helper: "Review activity and audit trail before handover.",
  },
];

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

const wasteSuggestionByDestination: Record<WasteDestination, string> = {
  reuse: "Route this material for immediate reuse in secondary production.",
  repair: "Send this material for repair and quality check before re-entry.",
  dispose: "Isolate and dispose according to compliance policy.",
};

function resolveWasteSuggestion(destination: WasteDestination, materialName: string) {
  const material = materialName.trim();
  if (!material) {
    return wasteSuggestionByDestination[destination];
  }

  return `${wasteSuggestionByDestination[destination]} Material: ${material}.`;
}

function hoursSince(iso: string) {
  return (Date.now() - new Date(iso).getTime()) / (1000 * 60 * 60);
}

const OperationsPage = () => {
  const [data, setData] = useState<OperationsPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<OperationTab>("batches");

  const [batchTemplate, setBatchTemplate] = useState("Plain Tee v2");
  const [batchOutput, setBatchOutput] = useState("280");
  const [batchWaste, setBatchWaste] = useState("14");

  const [inventoryMaterial, setInventoryMaterial] = useState("Cotton Roll 280gsm");
  const [inventoryType, setInventoryType] = useState<"IN" | "OUT">("IN");
  const [inventoryQty, setInventoryQty] = useState("20");
  const [selectedInventoryBatchId, setSelectedInventoryBatchId] = useState<string>("");

  const [wasteMaterial, setWasteMaterial] = useState("Cotton Trim");
  const [wasteDestination, setWasteDestination] = useState<WasteDestination>("reuse");
  const [wasteQty, setWasteQty] = useState("7");
  const [selectedWasteBatchId, setSelectedWasteBatchId] = useState<string>("");
  const [autoRecoverToInventory, setAutoRecoverToInventory] = useState(true);

  const [activeCloseBatchId, setActiveCloseBatchId] = useState<string>("");
  const [closeSummary, setCloseSummary] = useState<BatchCloseSummary | null>(null);
  const [closeOutputUnits, setCloseOutputUnits] = useState("0");
  const [closeReason, setCloseReason] = useState("");
  const [closingBatch, setClosingBatch] = useState(false);
  const [loadingCloseSummary, setLoadingCloseSummary] = useState(false);
  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false);

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
        setSelectedInventoryBatchId("");
        setSelectedWasteBatchId("");
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
        setCloseSummary(null);
        setCloseOutputUnits("0");
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

  useEffect(() => {
    if (runningBatches.length === 0) {
      setSelectedInventoryBatchId("");
      setSelectedWasteBatchId("");
      return;
    }

    const hasSelectedInventoryBatch = runningBatches.some((batch) => batch.id === selectedInventoryBatchId);
    const hasSelectedWasteBatch = runningBatches.some((batch) => batch.id === selectedWasteBatchId);

    if (selectedInventoryBatchId && !hasSelectedInventoryBatch) {
      setSelectedInventoryBatchId("");
    }
    if (selectedWasteBatchId && !hasSelectedWasteBatch) {
      setSelectedWasteBatchId("");
    }
  }, [runningBatches, selectedInventoryBatchId, selectedWasteBatchId]);

  useEffect(() => {
    if (wasteDestination === "dispose") {
      setAutoRecoverToInventory(false);
    }
  }, [wasteDestination]);

  const overdueBatches = useMemo(
    () => runningBatches.filter((item) => hoursSince(item.startedAt) > 24),
    [runningBatches],
  );

  const completedStepMap = useMemo<Record<OperationTab, boolean>>(() => ({
    batches: (data?.batches.length ?? 0) > 0,
    inventory: (data?.inventoryLogs.length ?? 0) > 0,
    waste: (data?.wasteLogs.length ?? 0) > 0,
    "batch-close": (data?.batches.some((batch) => batch.status === "completed") ?? false),
    integrity: activityLogs.length > 0 || auditTrail.length > 0,
  }), [data, activityLogs.length, auditTrail.length]);

  const workflowStepIndex = useMemo(
    () => operationWorkflowSteps.findIndex((step) => step.tab === activeTab),
    [activeTab],
  );

  const canMoveToStep = (targetIndex: number) => {
    if (targetIndex <= 0) {
      return true;
    }

    for (let index = 0; index < targetIndex; index += 1) {
      const step = operationWorkflowSteps[index];
      if (!completedStepMap[step.tab]) {
        return false;
      }
    }

    return true;
  };

  const goToStep = (targetIndex: number) => {
    if (targetIndex < 0 || targetIndex >= operationWorkflowSteps.length) {
      return;
    }

    if (!canMoveToStep(targetIndex)) {
      toast.info("Complete previous step(s) first to keep shift flow clean.");
      return;
    }

    setActiveTab(operationWorkflowSteps[targetIndex].tab);
  };

  const goToPreviousStep = () => {
    goToStep(workflowStepIndex - 1);
  };

  const goToNextStep = () => {
    const currentStep = operationWorkflowSteps[workflowStepIndex];
    if (!currentStep) {
      return;
    }

    if (!completedStepMap[currentStep.tab]) {
      toast.info("Finish the current step before moving to the next one.");
      return;
    }

    goToStep(workflowStepIndex + 1);
  };

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
      setSelectedInventoryBatchId(newBatch.id);
      setSelectedWasteBatchId(newBatch.id);
      setActiveCloseBatchId(newBatch.id);
      setActiveTab("inventory");
      await refreshIntegrity();
      toast.success(`Batch ${newBatch.id} created. Continue to material logging.`);
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

      if (inventoryType === "OUT" && !selectedInventoryBatchId) {
        toast.error("Please select a running batch for inventory OUT logs.");
        return;
      }

      if (
        inventoryType === "OUT"
        && !runningBatches.some((batch) => batch.id === selectedInventoryBatchId)
      ) {
        toast.error("Selected batch is no longer running. Please choose another batch.");
        return;
      }

      const created = await internalApi.createInventoryLog({
        batchId: inventoryType === "OUT" ? selectedInventoryBatchId : undefined,
        materialName: inventoryMaterial,
        type: inventoryType,
        quantity: Number(inventoryQty),
        unit: "kg",
        source: "Manual",
      });

      setData({ ...data, inventoryLogs: [created, ...data.inventoryLogs] });
      if (created.batchId && !selectedWasteBatchId) {
        setSelectedWasteBatchId(created.batchId);
      }
      setActiveTab("waste");
      await refreshIntegrity();
      toast.success("Inventory log added. Continue to waste logging.");
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

      if (!selectedWasteBatchId) {
        toast.error("Please select a running batch for this waste log.");
        return;
      }

      if (!runningBatches.some((batch) => batch.id === selectedWasteBatchId)) {
        toast.error("Selected batch is no longer running. Please choose another batch.");
        return;
      }

      const created = await internalApi.createWasteLog({
        batchId: selectedWasteBatchId,
        materialName: wasteMaterial,
        quantityKg: Number(wasteQty),
        destination: wasteDestination,
        reason: "Operator input",
        aiSuggestedAction: resolveWasteSuggestion(wasteDestination, wasteMaterial),
        isRepurposed: wasteDestination === "reuse",
      });

      let nextWasteLogs = [created, ...data.wasteLogs];
      let nextInventoryLogs = data.inventoryLogs;

      if (autoRecoverToInventory && (wasteDestination === "reuse" || wasteDestination === "repair")) {
        try {
          const recovery = await internalApi.recoverWasteToInventory({ wasteLogId: created.id });
          nextWasteLogs = nextWasteLogs.map((item) =>
            item.id === recovery.wasteLog.id ? recovery.wasteLog : item,
          );
          nextInventoryLogs = [recovery.inventoryLog, ...nextInventoryLogs];
          toast.success("Waste logged and converted to inventory IN.");
        } catch (recoveryError) {
          toast.error(
            recoveryError instanceof Error
              ? `Waste logged, but conversion failed: ${recoveryError.message}`
              : "Waste logged, but conversion to inventory failed.",
          );
        }
      } else {
        toast.success(
          wasteDestination === "dispose"
            ? "Waste log saved."
            : "Waste log saved with pending inventory conversion.",
        );
      }

      setData({ ...data, wasteLogs: nextWasteLogs, inventoryLogs: nextInventoryLogs });
      setActiveCloseBatchId(selectedWasteBatchId);
      setActiveTab("batch-close");
      await refreshIntegrity();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save waste log.");
    }
  };

  const handleRecoverWasteToInventory = async (wasteLogId: string) => {
    if (!data) return;

    try {
      const recovery = await internalApi.recoverWasteToInventory({ wasteLogId });

      const nextWasteLogs = data.wasteLogs.map((item) =>
        item.id === recovery.wasteLog.id ? recovery.wasteLog : item,
      );
      const nextInventoryLogs = [recovery.inventoryLog, ...data.inventoryLogs];

      setData({ ...data, wasteLogs: nextWasteLogs, inventoryLogs: nextInventoryLogs });
      await refreshIntegrity();
      toast.success("Recovered material converted to inventory IN.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to convert waste to inventory.");
    }
  };

  const getCloseValidationError = () => {
    if (!activeCloseBatchId || !closeSummary) {
      return "Please select a running batch first.";
    }

    const outputUnits = Number(closeOutputUnits);
    if (!Number.isFinite(outputUnits) || outputUnits <= 0) {
      return "Output units must be greater than zero.";
    }

    if (Math.abs(closeSummary.variancePercent) > CLOSE_VARIANCE_THRESHOLD && !closeReason.trim()) {
      return `Variance above ${CLOSE_VARIANCE_THRESHOLD}% requires a close reason.`;
    }

    return null;
  };

  const handleCloseBatch = (event: FormEvent) => {
    event.preventDefault();

    const validationError = getCloseValidationError();
    if (validationError) {
      toast.error(validationError);
      return;
    }

    setCloseConfirmOpen(true);
  };

  const confirmCloseBatch = async () => {
    const validationError = getCloseValidationError();
    if (validationError) {
      toast.error(validationError);
      setCloseConfirmOpen(false);
      return;
    }

    const outputUnits = Number(closeOutputUnits);

    try {
      setClosingBatch(true);
      await internalApi.closeBatch({
        batchId: activeCloseBatchId,
        outputUnits,
        closeReason: closeReason.trim() || undefined,
      });

      toast.success("Batch closed successfully with integrity snapshot.");
      setCloseReason("");
      setCloseConfirmOpen(false);
      await load();
      setActiveTab("integrity");
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
        description="Follow the daily shift workflow: start batch, log material, classify waste, close, and verify integrity."
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
        <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as OperationTab)} className="space-y-4">
          <div className="liquid-glass-strong rounded-3xl p-4 md:p-5 border border-white/10 space-y-4">
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
              <div>
                <p className="text-white text-lg font-heading italic">Daily Shift Workflow</p>
                <p className="text-white/60 text-sm font-body mt-1">
                  Follow this sequence to reduce input mistakes and keep handover clean.
                </p>
              </div>
              <span className="px-3 py-1 rounded-full text-xs bg-white/10 text-white/80 w-fit">
                Step {workflowStepIndex + 1} of {operationWorkflowSteps.length}
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-2">
              {operationWorkflowSteps.map((step, index) => {
                const isActive = step.tab === activeTab;
                const isCompleted = completedStepMap[step.tab];
                const unlocked = canMoveToStep(index);

                return (
                  <button
                    key={step.tab}
                    type="button"
                    onClick={() => goToStep(index)}
                    disabled={!unlocked}
                    className={`text-left rounded-2xl border px-3 py-3 transition-colors ${
                      isActive
                        ? "border-emerald-300/30 bg-emerald-500/10"
                        : unlocked
                          ? "border-white/15 bg-white/[0.02] hover:bg-white/[0.05]"
                          : "border-white/10 bg-white/[0.01] opacity-55"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-white text-sm font-medium">{step.title}</p>
                      {isCompleted ? (
                        <CheckCircle2 className="w-4 h-4 text-emerald-300" />
                      ) : (
                        <Clock3 className="w-4 h-4 text-white/45" />
                      )}
                    </div>
                    <p className="text-white/60 text-xs mt-1">{step.helper}</p>
                  </button>
                );
              })}
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={goToPreviousStep}
                disabled={workflowStepIndex <= 0}
                className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10"
              >
                Previous Step
              </Button>
              <Button
                type="button"
                onClick={goToNextStep}
                disabled={workflowStepIndex >= operationWorkflowSteps.length - 1}
                className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"
              >
                Next Step
              </Button>
            </div>
          </div>

          <AnimatePresence mode="wait">
            <TabsContent value="batches" asChild>
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 8 }}>
                <div className="grid grid-cols-1 xl:grid-cols-[0.95fr_1.05fr] gap-4">
                  <form onSubmit={handleCreateBatch} className="liquid-glass rounded-3xl p-5 space-y-4">
                    <h3 className="text-white text-xl font-heading italic">Create Batch</h3>
                    <p className="text-white/60 text-sm font-body">
                      Quick guide: <span className="text-white">280</span> is target output units, <span className="text-white">14</span> is expected waste in kg.
                    </p>
                    <div className="space-y-1">
                      <p className="text-white/75 text-xs font-body uppercase tracking-wide">Template Name</p>
                      <Input value={batchTemplate} onChange={(e) => setBatchTemplate(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="e.g. Plain Tee v2" />
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <p className="text-white/75 text-xs font-body uppercase tracking-wide">Output Units (pcs)</p>
                        <Input value={batchOutput} onChange={(e) => setBatchOutput(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="e.g. 280" type="number" min={1} />
                        <p className="text-white/50 text-xs font-body">Target finished units for this batch.</p>
                      </div>
                      <div className="space-y-1">
                        <p className="text-white/75 text-xs font-body uppercase tracking-wide">Estimated Waste (kg)</p>
                        <Input value={batchWaste} onChange={(e) => setBatchWaste(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="e.g. 14" type="number" min={0} />
                        <p className="text-white/50 text-xs font-body">Expected leftover material in kg.</p>
                      </div>
                    </div>
                    <Button className="w-full sm:w-auto rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"><Plus className="w-4 h-4" />Start Batch</Button>
                  </form>

                  <div className="liquid-glass rounded-3xl p-5 overflow-hidden">
                    <h3 className="text-white text-xl font-heading italic mb-3">Production List</h3>
                    {data.batches.length === 0 ? (
                      <DataEmpty title="No batches yet" description="Create your first production batch to begin tracking." />
                    ) : (
                      <Table className="min-w-[620px]">
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
                    <p className="text-white/60 text-sm font-body">
                      Use <span className="text-white">IN</span> for stock-in and <span className="text-white">OUT</span> for stock usage.
                    </p>

                    <div className="space-y-1">
                      <p className="text-white/75 text-xs font-body uppercase tracking-wide">Running Batch (Required for OUT)</p>
                      <Select
                        value={selectedInventoryBatchId}
                        onValueChange={setSelectedInventoryBatchId}
                      >
                        <SelectTrigger className="rounded-xl bg-white/[0.04] border-white/10 text-white">
                          <SelectValue placeholder="Select running batch (for OUT)" />
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
                    </div>

                    <div className="space-y-1">
                      <p className="text-white/75 text-xs font-body uppercase tracking-wide">Material Name</p>
                      <Input value={inventoryMaterial} onChange={(e) => setInventoryMaterial(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="e.g. Cotton Roll 280gsm" />
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <p className="text-white/75 text-xs font-body uppercase tracking-wide">Movement Type</p>
                        <Select value={inventoryType} onValueChange={(value: "IN" | "OUT") => setInventoryType(value)}>
                          <SelectTrigger className="rounded-xl bg-white/[0.04] border-white/10 text-white">
                            <SelectValue placeholder="Type" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="IN">IN (adds stock)</SelectItem>
                            <SelectItem value="OUT">OUT (uses stock)</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="space-y-1">
                        <p className="text-white/75 text-xs font-body uppercase tracking-wide">Quantity (kg)</p>
                        <Input value={inventoryQty} onChange={(e) => setInventoryQty(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="e.g. 20" type="number" min={0.1} step="0.1" />
                      </div>
                    </div>
                    <Button className="w-full sm:w-auto rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"><Plus className="w-4 h-4" />Add Log</Button>
                  </form>

                  <div className="liquid-glass rounded-3xl p-5 overflow-hidden">
                    <h3 className="text-white text-xl font-heading italic mb-3">Inventory Logs</h3>
                    {data.inventoryLogs.length === 0 ? (
                      <DataEmpty title="No inventory logs" description="Add IN/OUT movement to keep stock accurate." />
                    ) : (
                      <Table className="min-w-[740px]">
                        <TableHeader>
                          <TableRow className="border-white/10 hover:bg-transparent">
                            <TableHead className="text-white/60">Batch</TableHead>
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
                              <TableCell>{item.batchId ?? "Global"}</TableCell>
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
                    <p className="text-white/60 text-sm font-body">
                      Log waste by batch to keep traceability accurate.
                    </p>

                    {runningBatches.length === 0 && (
                      <div className="rounded-2xl border border-amber-300/20 bg-amber-500/10 p-3 text-amber-200 text-sm font-body">
                        No running batches available. Start a batch before logging waste.
                      </div>
                    )}

                    <div className="space-y-1">
                      <p className="text-white/75 text-xs font-body uppercase tracking-wide">Running Batch</p>
                      <Select value={selectedWasteBatchId} onValueChange={setSelectedWasteBatchId}>
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
                    </div>

                    <div className="space-y-1">
                      <p className="text-white/75 text-xs font-body uppercase tracking-wide">Waste Material</p>
                      <Input value={wasteMaterial} onChange={(e) => setWasteMaterial(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="e.g. Cotton Trim" />
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div className="space-y-1">
                        <p className="text-white/75 text-xs font-body uppercase tracking-wide">Destination</p>
                        <Select value={wasteDestination} onValueChange={(value: WasteDestination) => setWasteDestination(value)}>
                          <SelectTrigger className="rounded-xl bg-white/[0.04] border-white/10 text-white">
                            <SelectValue placeholder="Destination" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="reuse">Reuse (can return to stock)</SelectItem>
                            <SelectItem value="repair">Repair (fix then use)</SelectItem>
                            <SelectItem value="dispose">Dispose (cannot be reused)</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="space-y-1">
                        <p className="text-white/75 text-xs font-body uppercase tracking-wide">Quantity (kg)</p>
                        <Input value={wasteQty} onChange={(e) => setWasteQty(e.target.value)} className="rounded-xl bg-white/[0.04] border-white/10 text-white" placeholder="e.g. 7" type="number" min={0.1} step="0.1" />
                      </div>
                    </div>

                    <div className="rounded-2xl border border-white/10 bg-white/[0.03] px-3 py-2 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                      <div className="space-y-0.5">
                        <p className="text-sm text-white font-medium">Auto-convert to Inventory IN</p>
                        <p className="text-xs text-white/60">
                          For reuse or repair, create recovered stock immediately after logging waste.
                        </p>
                      </div>
                      <Switch
                        checked={autoRecoverToInventory}
                        disabled={wasteDestination === "dispose"}
                        onCheckedChange={setAutoRecoverToInventory}
                      />
                    </div>

                    <Button
                      disabled={runningBatches.length === 0}
                      className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"
                    >
                      <Plus className="w-4 h-4" />Log Waste
                    </Button>
                  </form>

                  <div className="liquid-glass rounded-3xl p-5 overflow-hidden">
                    <h3 className="text-white text-xl font-heading italic mb-3">Waste Logs</h3>
                    {data.wasteLogs.length === 0 ? (
                      <DataEmpty title="No waste logs" description="Track leftovers to improve repurpose decisions." />
                    ) : (
                      <Table className="min-w-[860px]">
                        <TableHeader>
                          <TableRow className="border-white/10 hover:bg-transparent">
                            <TableHead className="text-white/60">Batch</TableHead>
                            <TableHead className="text-white/60">Material</TableHead>
                            <TableHead className="text-white/60">Qty</TableHead>
                            <TableHead className="text-white/60">Destination</TableHead>
                            <TableHead className="text-white/60">Recovery</TableHead>
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
                              <TableCell>
                                {item.destination === "dispose" ? (
                                  <span className="px-2.5 py-1 rounded-full text-xs bg-white/10 text-white/70">
                                    not applicable
                                  </span>
                                ) : item.recoveryStatus === "converted" ? (
                                  <span className="px-2.5 py-1 rounded-full text-xs bg-emerald-500/15 text-emerald-300">
                                    converted
                                  </span>
                                ) : (
                                  <span className="px-2.5 py-1 rounded-full text-xs bg-amber-500/15 text-amber-300">
                                    pending
                                  </span>
                                )}
                              </TableCell>
                              <TableCell className="text-white/70 text-sm space-y-2">
                                <p>{item.aiSuggestedAction}</p>
                                {item.destination !== "dispose" && item.recoveryStatus !== "converted" && (
                                  <Button
                                    type="button"
                                    size="sm"
                                    className="rounded-full bg-emerald-500/20 text-emerald-100 hover:bg-emerald-500/30"
                                    onClick={() => handleRecoverWasteToInventory(item.id)}
                                  >
                                    Convert to Inventory IN
                                  </Button>
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

            <TabsContent value="batch-close" asChild>
              <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 8 }}>
                <div className="grid grid-cols-1 xl:grid-cols-[0.9fr_1.1fr] gap-4">
                  <form onSubmit={handleCloseBatch} className="liquid-glass rounded-3xl p-5 space-y-4">
                    <h3 className="text-white text-xl font-heading italic">Batch Close Assistant</h3>
                    <p className="text-white/60 text-sm font-body">
                      Close batch from auto-calculated summary. Manual entry is only required for major variance.
                    </p>

                    <div className="space-y-1">
                      <p className="text-white/75 text-xs font-body uppercase tracking-wide">Batch to Close</p>
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
                    </div>

                    <div className="space-y-1">
                      <p className="text-white/75 text-xs font-body uppercase tracking-wide">Final Output Units (pcs)</p>
                      <Input
                        value={closeOutputUnits}
                        onChange={(e) => setCloseOutputUnits(e.target.value)}
                        className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                          placeholder="e.g. 276"
                        type="number"
                        min={1}
                      />
                      <p className="text-white/50 text-xs font-body">Final finished units before close.</p>
                    </div>

                    {closeSummary && Math.abs(closeSummary.variancePercent) > CLOSE_VARIANCE_THRESHOLD && (
                      <div className="rounded-2xl border border-amber-300/20 bg-amber-500/10 p-3 text-amber-200 text-sm font-body">
                        Variance exceeds {CLOSE_VARIANCE_THRESHOLD}%. Close reason is required.
                      </div>
                    )}

                    <Input
                      value={closeReason}
                      onChange={(e) => setCloseReason(e.target.value)}
                      className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                      placeholder={
                        closeSummary && Math.abs(closeSummary.variancePercent) > CLOSE_VARIANCE_THRESHOLD
                          ? "Reason for high variance (required)"
                          : "Optional close note"
                      }
                    />

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
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm font-body">
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
                            <p className="text-white text-sm font-body font-medium">{log.action.split("_").join(" ")}</p>
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
                      <Table className="min-w-[760px]">
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

      <AlertDialog open={closeConfirmOpen} onOpenChange={setCloseConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Confirm Batch Close</AlertDialogTitle>
            <AlertDialogDescription>
              This action finalizes the selected batch and it cannot be reopened. Please confirm to continue.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={closingBatch}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={(event) => {
                event.preventDefault();
                void confirmCloseBatch();
              }}
              disabled={closingBatch}
            >
              {closingBatch ? "Closing..." : "Yes, close batch"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

export default OperationsPage;

