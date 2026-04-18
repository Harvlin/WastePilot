import { FormEvent, useEffect, useMemo, useState } from "react";
import { History, Plus, SquarePen } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { DataEmpty, DataError, DataLoading } from "@/features/internal/components/StateViews";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { ActivityLogEntry, InventoryLog, Material } from "@/features/internal/types";
import { internalApi } from "@/lib/api/internal-api";
import { toast } from "sonner";

const blankMaterial: Material = {
  id: "",
  name: "",
  category: "Recyclable",
  unit: "kg",
  circularGrade: "A",
  stock: 0,
  supplier: "",
};

const gradeClass: Record<Material["circularGrade"], string> = {
  A: "bg-emerald-500/15 text-emerald-300",
  B: "bg-amber-500/15 text-amber-300",
  C: "bg-rose-500/15 text-rose-300",
};

const MaterialsPage = () => {
  const [materials, setMaterials] = useState<Material[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [draft, setDraft] = useState<Material>(blankMaterial);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyMaterial, setHistoryMaterial] = useState<Material | null>(null);
  const [historyMaterialName, setHistoryMaterialName] = useState("");
  const [historyRows, setHistoryRows] = useState<InventoryLog[]>([]);
  const [historyActivityMap, setHistoryActivityMap] = useState<Record<string, ActivityLogEntry>>({});

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      const payload = await internalApi.fetchMaterials();
      setMaterials(payload);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch materials");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const openEditor = (item?: Material) => {
    setDraft(item ? item : blankMaterial);
    setEditorOpen(true);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();

    try {
      if (!draft.name.trim() || !draft.supplier.trim() || Number(draft.stock) < 0) {
        toast.error("Please complete material details with valid values.");
        return;
      }

      const payload: Material = {
        ...draft,
        id: draft.id || "",
        stock: Number(draft.stock),
      };

      const updated = await internalApi.upsertMaterial(payload);
      setMaterials(updated);
      setEditorOpen(false);
      toast.success(draft.id ? "Material updated." : "Material created.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save material.");
    }
  };

  const openHistory = async (material: Material) => {
    setHistoryOpen(true);
    setHistoryLoading(true);
    setHistoryError(null);
    setHistoryMaterial(material);
    setHistoryMaterialName(material.name);

    try {
      const [payload, activityLogs] = await Promise.all([
        internalApi.fetchOperationsPayload(),
        internalApi.fetchActivityLogs(),
      ]);
      const normalizedName = material.name.trim().toLowerCase();
      const rows = payload.inventoryLogs
        .filter((log) => log.materialName.trim().toLowerCase() === normalizedName)
        .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

      const activityMap = activityLogs
        .filter((log) => log.entity === "inventory")
        .reduce<Record<string, ActivityLogEntry>>((accumulator, log) => {
          accumulator[log.entityId] = log;
          return accumulator;
        }, {});

      setHistoryRows(rows);
      setHistoryActivityMap(activityMap);
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : "Failed to load material history.");
      setHistoryRows([]);
      setHistoryActivityMap({});
    } finally {
      setHistoryLoading(false);
    }
  };

  const openAllHistory = async () => {
    setHistoryOpen(true);
    setHistoryLoading(true);
    setHistoryError(null);
    setHistoryMaterial(null);
    setHistoryMaterialName("");

    try {
      const [payload, activityLogs] = await Promise.all([
        internalApi.fetchOperationsPayload(),
        internalApi.fetchActivityLogs(),
      ]);
      const rows = payload.inventoryLogs.sort(
        (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime(),
      );

      const activityMap = activityLogs
        .filter((log) => log.entity === "inventory")
        .reduce<Record<string, ActivityLogEntry>>((accumulator, log) => {
          accumulator[log.entityId] = log;
          return accumulator;
        }, {});

      setHistoryRows(rows);
      setHistoryActivityMap(activityMap);
    } catch (err) {
      setHistoryError(err instanceof Error ? err.message : "Failed to load material history.");
      setHistoryRows([]);
      setHistoryActivityMap({});
    } finally {
      setHistoryLoading(false);
    }
  };

  const historySummary = useMemo(() => {
    const totals = historyRows.reduce(
      (accumulator, row) => {
        if (row.type === "IN") {
          accumulator.totalIn += row.quantity;
        } else {
          accumulator.totalOut += row.quantity;
        }

        accumulator.units.add(row.unit);
        return accumulator;
      },
      { totalIn: 0, totalOut: 0, units: new Set<string>() },
    );

    const unitLabel = totals.units.size === 1 ? Array.from(totals.units)[0] : "mixed units";

    return {
      totalIn: totals.totalIn,
      totalOut: totals.totalOut,
      net: totals.totalIn - totals.totalOut,
      unitLabel,
      transactions: historyRows.length,
    };
  }, [historyRows]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Materials"
        description="Manage raw materials with circular grades, categories, and inventory context."
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              onClick={() => void openAllHistory()}
              className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10"
            >
              <History className="w-4 h-4" />
              Stock History
            </Button>
            <Button onClick={() => openEditor()} className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">
              <Plus className="w-4 h-4" />
              Add Material
            </Button>
          </div>
        }
      />

      {loading && <DataLoading rows={6} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && (
        <div className="liquid-glass rounded-3xl p-5 overflow-hidden">
          {materials.length === 0 ? (
            <DataEmpty
              title="No materials yet"
              description="Create your first material record to enable inventory and template planning."
              action={
                <Button onClick={() => openEditor()} className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">
                  Add Material
                </Button>
              }
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="border-white/10 hover:bg-transparent">
                  <TableHead className="text-white/55">Name</TableHead>
                  <TableHead className="text-white/55">Category</TableHead>
                  <TableHead className="text-white/55">Grade</TableHead>
                  <TableHead className="text-white/55">Stock</TableHead>
                  <TableHead className="text-white/55">Supplier</TableHead>
                  <TableHead className="text-white/55 text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {materials.map((item) => (
                  <TableRow key={item.id} className="border-white/10 hover:bg-white/[0.03]">
                    <TableCell>{item.name}</TableCell>
                    <TableCell>{item.category}</TableCell>
                    <TableCell>
                      <span className={`px-2.5 py-1 rounded-full text-xs ${gradeClass[item.circularGrade]}`}>
                        Grade {item.circularGrade}
                      </span>
                    </TableCell>
                    <TableCell>
                      {item.stock} {item.unit}
                    </TableCell>
                    <TableCell>{item.supplier}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-2">
                        <Button
                          variant="ghost"
                          className="text-white/80 hover:bg-white/10 rounded-full"
                          onClick={() => openHistory(item)}
                        >
                          <History className="w-4 h-4" />
                          History
                        </Button>
                        <Button
                          variant="ghost"
                          className="text-white/80 hover:bg-white/10 rounded-full"
                          onClick={() => openEditor(item)}
                        >
                          <SquarePen className="w-4 h-4" />
                          Edit
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>
      )}

      <Dialog open={editorOpen} onOpenChange={setEditorOpen}>
        <DialogContent className="liquid-glass-strong border-white/25 bg-black/95 text-white rounded-3xl shadow-[0_30px_80px_rgba(0,0,0,0.75)] overflow-visible [&::before]:hidden">
          <DialogHeader className="pt-1">
            <DialogTitle className="font-heading italic text-2xl text-white leading-[1.2]">
              {draft.id ? "Edit Material" : "Add Material"}
            </DialogTitle>
          </DialogHeader>

          <form onSubmit={submit} className="space-y-3">
            <p className="text-white/60 text-sm font-body">
              Keep this clean. Materials here are used by templates, inventory, and OCR save.
            </p>
            <div className="space-y-1">
              <p className="text-white/75 text-xs font-body uppercase tracking-wide">Material Name</p>
              <Input
                value={draft.name}
                onChange={(e) => setDraft((prev) => ({ ...prev, name: e.target.value }))}
                placeholder="e.g. Cotton Roll 280gsm"
                className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
                required
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <p className="text-white/75 text-xs font-body uppercase tracking-wide">Category</p>
                <Select
                  value={draft.category}
                  onValueChange={(value: Material["category"]) => setDraft((prev) => ({ ...prev, category: value }))}
                >
                  <SelectTrigger className="rounded-xl bg-white/[0.10] border-white/20 text-white focus:ring-1 focus:ring-white/35 focus:ring-offset-0">
                    <SelectValue placeholder="Category" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Recyclable">Recyclable</SelectItem>
                    <SelectItem value="Biodegradable">Biodegradable</SelectItem>
                    <SelectItem value="Composite">Composite</SelectItem>
                    <SelectItem value="Reusable">Reusable</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <p className="text-white/75 text-xs font-body uppercase tracking-wide">Circular Grade</p>
                <Select
                  value={draft.circularGrade}
                  onValueChange={(value: Material["circularGrade"]) =>
                    setDraft((prev) => ({ ...prev, circularGrade: value }))
                  }
                >
                  <SelectTrigger className="rounded-xl bg-white/[0.10] border-white/20 text-white focus:ring-1 focus:ring-white/35 focus:ring-offset-0">
                    <SelectValue placeholder="Circular grade" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="A">A (best)</SelectItem>
                    <SelectItem value="B">B (good)</SelectItem>
                    <SelectItem value="C">C (needs work)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <p className="text-white/75 text-xs font-body uppercase tracking-wide">Current Stock</p>
                <Input
                  value={draft.stock}
                  onChange={(e) => setDraft((prev) => ({ ...prev, stock: Number(e.target.value) }))}
                  placeholder="e.g. 320"
                  type="number"
                  min={0}
                  className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
                  required
                />
              </div>
              <div className="space-y-1">
                <p className="text-white/75 text-xs font-body uppercase tracking-wide">Unit</p>
                <Input
                  value={draft.unit}
                  onChange={(e) => setDraft((prev) => ({ ...prev, unit: e.target.value }))}
                  placeholder="e.g. kg"
                  className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
                  required
                />
              </div>
            </div>
            <div className="space-y-1">
              <p className="text-white/75 text-xs font-body uppercase tracking-wide">Supplier</p>
              <Input
                value={draft.supplier}
                onChange={(e) => setDraft((prev) => ({ ...prev, supplier: e.target.value }))}
                placeholder="e.g. Textile Nusantara Ltd"
                className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
                required
              />
            </div>

            <Button className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))] w-full">
              Save Material
            </Button>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={historyOpen} onOpenChange={setHistoryOpen}>
        <DialogContent className="liquid-glass-strong border-white/25 bg-black/95 text-white rounded-3xl max-w-3xl shadow-[0_30px_80px_rgba(0,0,0,0.75)] overflow-visible [&::before]:hidden">
          <DialogHeader className="pt-1">
            <DialogTitle className="font-heading italic text-2xl text-white leading-[1.2]">
              Stock History
            </DialogTitle>
            <p className="text-white/55 text-sm font-body mt-1">
              {historyMaterialName ? `${historyMaterialName} - IN/OUT movement log` : "All materials - IN/OUT movement log"}
            </p>
          </DialogHeader>

          {historyLoading && <DataLoading rows={4} />}
          {historyError && !historyLoading && (
            <DataError
              message={historyError}
              onRetry={() => {
                if (historyMaterial) {
                  void openHistory(historyMaterial);
                } else {
                  void openAllHistory();
                }
              }}
            />
          )}

          {!historyLoading && !historyError && historyRows.length === 0 && (
            <DataEmpty
              title="No movement history"
              description="No IN/OUT records found for this material yet."
            />
          )}

          {!historyLoading && !historyError && historyRows.length > 0 && (
            <>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3">
                  <p className="text-white/55 text-xs">Transactions</p>
                  <p className="text-white text-lg mt-1">{historySummary.transactions}</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-emerald-500/10 p-3">
                  <p className="text-emerald-200/80 text-xs">Total IN</p>
                  <p className="text-emerald-200 text-lg mt-1">{historySummary.totalIn} {historySummary.unitLabel}</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-amber-500/10 p-3">
                  <p className="text-amber-200/80 text-xs">Total OUT</p>
                  <p className="text-amber-200 text-lg mt-1">{historySummary.totalOut} {historySummary.unitLabel}</p>
                </div>
                <div className="rounded-2xl border border-white/10 bg-sky-500/10 p-3">
                  <p className="text-sky-200/80 text-xs">Net Flow</p>
                  <p className="text-sky-200 text-lg mt-1">{historySummary.net} {historySummary.unitLabel}</p>
                </div>
              </div>

              <div className="max-h-[420px] overflow-auto rounded-2xl border border-white/10">
                <Table>
                  <TableHeader>
                    <TableRow className="border-white/10 hover:bg-transparent">
                      <TableHead className="text-white/60">Time</TableHead>
                      <TableHead className="text-white/60">Material</TableHead>
                      <TableHead className="text-white/60">Flow</TableHead>
                      <TableHead className="text-white/60">Quantity</TableHead>
                      <TableHead className="text-white/60">Source</TableHead>
                      <TableHead className="text-white/60">Batch</TableHead>
                      <TableHead className="text-white/60">Reference</TableHead>
                      <TableHead className="text-white/60">Logged By</TableHead>
                      <TableHead className="text-white/60">Notes</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {historyRows.map((row) => {
                      const activity = historyActivityMap[row.id];

                      return (
                        <TableRow key={row.id} className="border-white/10 hover:bg-white/[0.03]">
                          <TableCell>{new Date(row.timestamp).toLocaleString()}</TableCell>
                          <TableCell>{row.materialName}</TableCell>
                          <TableCell>
                            <span className={`px-2.5 py-1 rounded-full text-xs ${row.type === "IN" ? "bg-emerald-500/15 text-emerald-300" : "bg-amber-500/15 text-amber-300"}`}>
                              {row.type}
                            </span>
                          </TableCell>
                          <TableCell>
                            <span className={row.type === "IN" ? "text-emerald-300" : "text-amber-300"}>
                              {row.type === "IN" ? "+" : "-"}
                              {row.quantity}
                            </span>{" "}
                            {row.unit}
                          </TableCell>
                          <TableCell>{row.source}</TableCell>
                          <TableCell>{row.batchId ?? "Global"}</TableCell>
                          <TableCell>
                            <div className="text-xs text-white/75 space-y-1">
                              <p>{row.id}</p>
                              {row.recoveryWasteLogId && <p>from {row.recoveryWasteLogId}</p>}
                            </div>
                          </TableCell>
                          <TableCell>{activity?.actor ?? "system"}</TableCell>
                          <TableCell className="max-w-[220px] text-white/70 text-xs">
                            {activity?.details ?? "No additional note"}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default MaterialsPage;

