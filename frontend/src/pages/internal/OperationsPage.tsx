import { FormEvent, useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Plus } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { OperationsPayload, WasteDestination } from "@/features/internal/types";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { DataEmpty, DataError, DataLoading } from "@/features/internal/components/StateViews";
import { internalApi } from "@/lib/api/internal-api";
import { toast } from "sonner";

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

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      const payload = await internalApi.fetchOperationsPayload();
      setData(payload);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load operations");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const summary = useMemo(() => {
    if (!data) return { running: 0, completed: 0, wasteTotal: 0 };
    return {
      running: data.batches.filter((item) => item.status === "running").length,
      completed: data.batches.filter((item) => item.status === "completed").length,
      wasteTotal: data.wasteLogs.reduce((acc, item) => acc + item.quantityKg, 0),
    };
  }, [data]);

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
      toast.success("Waste log saved.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save waste log.");
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Operations"
        description="Run day-to-day production with smart templates, inventory movement, and waste classifications in one super page."
      />

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
                    <Button className="rounded-full bg-white text-black hover:bg-white/90"><Plus className="w-4 h-4" />Start Batch</Button>
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
                    <Button className="rounded-full bg-white text-black hover:bg-white/90"><Plus className="w-4 h-4" />Add Log</Button>
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
                    <Button className="rounded-full bg-white text-black hover:bg-white/90"><Plus className="w-4 h-4" />Log Waste</Button>
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
          </AnimatePresence>
        </Tabs>
      )}
    </div>
  );
};

export default OperationsPage;
