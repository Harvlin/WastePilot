import { ChangeEvent, useEffect, useMemo, useState } from "react";
import { Camera, ImageUp, LoaderCircle, Save } from "lucide-react";
import { motion } from "motion/react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { OcrMaterialLine } from "@/features/internal/types";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { DataEmpty, DataError } from "@/features/internal/components/StateViews";
import { internalApi } from "@/lib/api/internal-api";
import { toast } from "sonner";

const ScanPage = () => {
  const [file, setFile] = useState<File | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<OcrMaterialLine[]>([]);
  const [saved, setSaved] = useState(false);

  const previewUrl = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  const onFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const nextFile = event.target.files?.[0] ?? null;
    setFile(nextFile);
    setSaved(false);
    setError(null);
  };

  const runOcr = async () => {
    try {
      setIsProcessing(true);
      setError(null);
      const lines = await internalApi.processOcrImage(file ?? undefined);
      setResult(lines);
      toast.success("OCR extraction completed.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "OCR could not process this image.");
      setResult([]);
    } finally {
      setIsProcessing(false);
    }
  };

  const updateLine = (id: string, key: keyof OcrMaterialLine, value: string) => {
    setResult((prev) =>
      prev.map((line) => {
        if (line.id !== id) return line;

        if (key === "quantity" || key === "price") {
          return { ...line, [key]: Number(value) };
        }

        return { ...line, [key]: value };
      }),
    );
    setSaved(false);
  };

  const handleConfirm = async () => {
    if (result.length === 0) {
      toast.error("No OCR rows to confirm.");
      return;
    }

    const invalidRows = result
      .map((line, index) => ({
        index,
        invalid: !line.materialName.trim() || line.quantity <= 0 || line.price < 0 || !line.unit.trim(),
      }))
      .filter((entry) => entry.invalid);

    if (invalidRows.length > 0) {
      toast.error(`Please fix ${invalidRows.length} invalid OCR row(s) before saving.`);
      return;
    }

    try {
      setIsProcessing(true);
      await Promise.all(
        result.map((line) =>
          internalApi.createInventoryLog({
            materialName: line.materialName.trim(),
            type: "IN",
            quantity: line.quantity,
            unit: line.unit.trim(),
            source: "OCR",
          }),
        ),
      );

      setSaved(true);
      toast.success(`${result.length} OCR row(s) saved to inventory logs.`);
      setResult([]);
      setFile(null);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save OCR rows.");
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="AI Vision Scan"
        description="Upload or capture invoice images, review extracted material data, then confirm for inventory logging."
      />

      <section className="grid grid-cols-1 xl:grid-cols-[0.8fr_1.2fr] gap-4">
        <div className="liquid-glass rounded-3xl p-5 space-y-4">
          <p className="text-white text-xl font-heading italic">Invoice Input</p>
          <p className="text-white/60 text-sm font-body">
            Upload one clear invoice image. We extract material, quantity, unit, and price.
          </p>
          <label className="block">
            <span className="text-white/60 text-sm font-body">Upload image</span>
            <Input
              type="file"
              accept="image/*"
              className="mt-2 rounded-xl bg-white/[0.04] border-white/10 text-white file:text-white"
              onChange={onFileChange}
            />
          </label>

          <label className="block">
            <span className="text-white/60 text-sm font-body">Or use camera input</span>
            <Input
              type="file"
              accept="image/*"
              capture="environment"
              className="mt-2 rounded-xl bg-white/[0.04] border-white/10 text-white file:text-white"
              onChange={onFileChange}
            />
          </label>

          <div className="flex gap-3 pt-2">
            <Button onClick={runOcr} disabled={isProcessing} className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">
              {isProcessing ? <LoaderCircle className="w-4 h-4 animate-spin" /> : <ImageUp className="w-4 h-4" />}
              {isProcessing ? "Processing" : "Run OCR"}
            </Button>
            <Button
              variant="outline"
              onClick={() => {
                setFile(null);
                setResult([]);
                setSaved(false);
                setError(null);
                toast.info("Scan form cleared.");
              }}
              className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10"
            >
              Clear
            </Button>
          </div>

          {previewUrl && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="rounded-2xl overflow-hidden border border-white/10"
            >
              <img src={previewUrl} alt="Invoice preview" className="w-full h-60 object-cover" />
            </motion.div>
          )}
        </div>

        <div className="liquid-glass rounded-3xl p-5 space-y-4">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-white text-xl font-heading italic">OCR Result</p>
              <p className="text-white/55 text-sm font-body mt-1">Review before saving to inventory logs.</p>
            </div>
            <Button
              onClick={handleConfirm}
              disabled={result.length === 0 || isProcessing}
              className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]"
            >
              <Save className="w-4 h-4" />
              Confirm & Save
            </Button>
          </div>

          {isProcessing && (
            <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-8 text-center space-y-2">
              <LoaderCircle className="w-6 h-6 mx-auto text-white/80 animate-spin" />
              <p className="text-white font-body">Gemini OCR is extracting fields...</p>
              <p className="text-white/55 text-sm font-body">This usually takes less than 3 seconds.</p>
            </div>
          )}

          {error && !isProcessing && <DataError message={error} onRetry={runOcr} />}

          {!isProcessing && !error && result.length === 0 && (
            <DataEmpty
              title="No OCR data yet"
              description="Upload an invoice image and run OCR to populate editable material rows."
              action={<Camera className="w-5 h-5 text-white/60" />}
            />
          )}

          {!isProcessing && !error && result.length > 0 && (
            <div className="space-y-3">
              <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3 text-xs text-white/60 font-body">
                Column order: <span className="text-white/80">Material Name</span>, <span className="text-white/80">Quantity</span>, <span className="text-white/80">Unit</span>, <span className="text-white/80">Price</span>.
                Example: Cotton Roll | 20 | kg | 120000.
              </div>
              {result.map((line) => (
                <div key={line.id} className="grid grid-cols-1 md:grid-cols-4 gap-3 rounded-2xl border border-white/10 p-3 bg-white/[0.03]">
                  <Input
                    value={line.materialName}
                    onChange={(e) => updateLine(line.id, "materialName", e.target.value)}
                    className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                    placeholder="Material Name"
                  />
                  <Input
                    value={line.quantity}
                    onChange={(e) => updateLine(line.id, "quantity", e.target.value)}
                    className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                    type="number"
                    min={0.01}
                    step="0.01"
                    placeholder="Quantity"
                  />
                  <Input
                    value={line.unit}
                    onChange={(e) => updateLine(line.id, "unit", e.target.value)}
                    className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                    placeholder="Unit (kg, m, pcs)"
                  />
                  <Input
                    value={line.price}
                    onChange={(e) => updateLine(line.id, "price", e.target.value)}
                    className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                    type="number"
                    min={0}
                    step="0.01"
                    placeholder="Price"
                  />
                </div>
              ))}

              {saved && (
                <div className="rounded-2xl border border-emerald-300/20 bg-emerald-500/10 p-4">
                  <p className="text-emerald-200 text-sm font-body">
                    OCR data confirmed and queued for inventory logging.
                  </p>
                </div>
              )}
            </div>
          )}
        </div>
      </section>
    </div>
  );
};

export default ScanPage;

