import { FormEvent, useEffect, useState } from "react";
import { Plus, SquarePen } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { DataEmpty, DataError, DataLoading } from "@/features/internal/components/StateViews";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { Material } from "@/features/internal/types";
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

  return (
    <div className="space-y-6">
      <PageHeader
        title="Materials"
        description="Manage raw materials with circular grades, categories, and inventory context."
        actions={
          <Button onClick={() => openEditor()} className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">
            <Plus className="w-4 h-4" />
            Add Material
          </Button>
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
                      <Button
                        variant="ghost"
                        className="text-white/80 hover:bg-white/10 rounded-full"
                        onClick={() => openEditor(item)}
                      >
                        <SquarePen className="w-4 h-4" />
                        Edit
                      </Button>
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
            <Input
              value={draft.name}
              onChange={(e) => setDraft((prev) => ({ ...prev, name: e.target.value }))}
              placeholder="Material name"
              className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
              required
            />
            <div className="grid grid-cols-2 gap-3">
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
                  <SelectItem value="A">A</SelectItem>
                  <SelectItem value="B">B</SelectItem>
                  <SelectItem value="C">C</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Input
                value={draft.stock}
                onChange={(e) => setDraft((prev) => ({ ...prev, stock: Number(e.target.value) }))}
                placeholder="Stock"
                type="number"
                className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
                required
              />
              <Input
                value={draft.unit}
                onChange={(e) => setDraft((prev) => ({ ...prev, unit: e.target.value }))}
                placeholder="Unit"
                className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
                required
              />
            </div>
            <Input
              value={draft.supplier}
              onChange={(e) => setDraft((prev) => ({ ...prev, supplier: e.target.value }))}
              placeholder="Supplier"
              className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
              required
            />

            <Button className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))] w-full">
              Save Material
            </Button>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default MaterialsPage;

