import { FormEvent, useEffect, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { DataEmpty, DataError, DataLoading } from "@/features/internal/components/StateViews";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { Material, ProductionTemplate, TemplateMaterialLine } from "@/features/internal/types";
import { internalApi } from "@/lib/api/internal-api";
import { toast } from "sonner";

const emptyTemplate: ProductionTemplate = {
  id: "",
  name: "",
  sku: "",
  expectedWasteKg: 0,
  updatedAt: "",
  lines: [],
};

const TemplatesPage = () => {
  const [templates, setTemplates] = useState<ProductionTemplate[]>([]);
  const [materials, setMaterials] = useState<Material[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<ProductionTemplate>(emptyTemplate);

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      const [templateData, materialData] = await Promise.all([
        internalApi.fetchTemplates(),
        internalApi.fetchMaterials(),
      ]);
      setTemplates(templateData);
      setMaterials(materialData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load templates");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const openEditor = (template?: ProductionTemplate) => {
    setDraft(template ? template : { ...emptyTemplate, id: `TPL-${Date.now()}` });
    setOpen(true);
  };

  const addLine = () => {
    const selected = materials[0];
    if (!selected) {
      toast.error("Create materials first before composing a template.");
      return;
    }

    const line: TemplateMaterialLine = {
      materialId: selected.id,
      materialName: selected.name,
      quantity: 1,
      unit: selected.unit,
    };

    setDraft((prev) => ({ ...prev, lines: [...prev.lines, line] }));
  };

  const updateLine = (index: number, patch: Partial<TemplateMaterialLine>) => {
    setDraft((prev) => ({
      ...prev,
      lines: prev.lines.map((line, i) => (i === index ? { ...line, ...patch } : line)),
    }));
  };

  const removeLine = (index: number) => {
    setDraft((prev) => ({ ...prev, lines: prev.lines.filter((_, i) => i !== index) }));
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!draft.name || draft.lines.length === 0) {
      toast.error("Please provide template name and at least one material line.");
      return;
    }

    try {
      const payload = {
        ...draft,
        expectedWasteKg: Number(draft.expectedWasteKg),
        updatedAt: new Date().toISOString(),
      };

      const updated = await internalApi.upsertTemplate(payload);
      setTemplates(updated);
      setOpen(false);
      toast.success(draft.id ? "Template updated." : "Template created.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save template.");
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Production Templates"
        description="Build reusable material compositions so teams can launch production with one selection."
        actions={
          <Button onClick={() => openEditor()} className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">
            <Plus className="w-4 h-4" />
            New Template
          </Button>
        }
      />

      {loading && <DataLoading rows={6} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {templates.length === 0 ? (
            <DataEmpty
              title="No templates yet"
              description="Create a template to speed up production batch setup."
              action={
                <Button onClick={() => openEditor()} className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">
                  Create Template
                </Button>
              }
            />
          ) : (
            templates.map((template) => (
              <div key={template.id} className="liquid-glass rounded-3xl p-5 space-y-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-white text-xl font-heading italic">{template.name}</p>
                    <p className="text-white/55 text-sm font-body mt-1">SKU {template.sku}</p>
                  </div>
                  <Button variant="ghost" className="rounded-full text-white/80 hover:bg-white/10" onClick={() => openEditor(template)}>
                    Edit
                  </Button>
                </div>

                <div className="space-y-2">
                  {template.lines.map((line, index) => (
                    <div key={`${line.materialId}-${index}`} className="rounded-2xl border border-white/10 bg-white/[0.03] p-3 flex items-center justify-between">
                      <div>
                        <p className="text-white text-sm font-body">{line.materialName}</p>
                        <p className="text-white/55 text-xs font-body mt-1">
                          {line.quantity} {line.unit}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="pt-2 border-t border-white/10 flex items-center justify-between">
                  <p className="text-white/60 text-sm">Expected waste</p>
                  <p className="text-white font-body">{template.expectedWasteKg} kg</p>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="liquid-glass-strong border-white/25 bg-black/95 text-white rounded-3xl max-w-2xl shadow-[0_30px_80px_rgba(0,0,0,0.75)] overflow-visible [&::before]:hidden">
          <DialogHeader className="pt-1">
            <DialogTitle className="font-heading italic text-2xl text-white leading-[1.2]">
              {draft.id ? "Edit Template" : "Create Template"}
            </DialogTitle>
          </DialogHeader>

          <form onSubmit={submit} className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <Input
                value={draft.name}
                onChange={(e) => setDraft((prev) => ({ ...prev, name: e.target.value }))}
                placeholder="Template name"
                className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
                required
              />
              <Input
                value={draft.sku}
                onChange={(e) => setDraft((prev) => ({ ...prev, sku: e.target.value }))}
                placeholder="SKU"
                className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
                required
              />
            </div>

            <Input
              value={draft.expectedWasteKg}
              onChange={(e) => setDraft((prev) => ({ ...prev, expectedWasteKg: Number(e.target.value) }))}
              placeholder="Expected waste (kg)"
              type="number"
              className="rounded-xl bg-white/[0.10] border-white/20 text-white placeholder:text-white/45 focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
              required
            />

            <div className="rounded-2xl border border-white/15 p-3 space-y-3 bg-white/[0.02]">
              <div className="flex items-center justify-between">
                <p className="text-white font-body text-sm">Material Composition</p>
                <Button type="button" variant="outline" onClick={addLine} className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10">
                  <Plus className="w-4 h-4" />
                  Add Line
                </Button>
              </div>

              {draft.lines.map((line, index) => (
                <div key={`${line.materialId}-${index}`} className="grid grid-cols-[1fr_120px_90px_44px] gap-2">
                  <Select
                    value={line.materialId}
                    onValueChange={(materialId) => {
                      const material = materials.find((item) => item.id === materialId);
                      if (!material) return;
                      updateLine(index, {
                        materialId: material.id,
                        materialName: material.name,
                        unit: material.unit,
                      });
                    }}
                  >
                    <SelectTrigger className="rounded-xl bg-white/[0.10] border-white/20 text-white focus:ring-1 focus:ring-white/35 focus:ring-offset-0">
                      <SelectValue placeholder="Material" />
                    </SelectTrigger>
                    <SelectContent>
                      {materials.map((material) => (
                        <SelectItem key={material.id} value={material.id}>
                          {material.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>

                  <Input
                    type="number"
                    value={line.quantity}
                    onChange={(e) => updateLine(index, { quantity: Number(e.target.value) })}
                    className="rounded-xl bg-white/[0.10] border-white/20 text-white focus-visible:ring-1 focus-visible:ring-white/35 focus-visible:ring-offset-0 focus-visible:border-white/35"
                  />

                  <Input value={line.unit} disabled className="rounded-xl bg-white/[0.06] border-white/20 text-white/80" />

                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => removeLine(index)}
                    className="rounded-xl text-white/75 hover:bg-white/10"
                  >
                    <Trash2 className="w-4 h-4" />
                  </Button>
                </div>
              ))}
            </div>

            <Button className="w-full rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">Save Template</Button>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default TemplatesPage;

