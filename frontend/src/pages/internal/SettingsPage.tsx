import { FormEvent, useEffect, useState } from "react";
import { Save } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { DataError, DataLoading } from "@/features/internal/components/StateViews";
import { UserSettings } from "@/features/internal/types";
import { internalApi } from "@/lib/api/internal-api";
import { toast } from "sonner";

const SettingsPage = () => {
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const load = async () => {
    try {
      setLoading(true);
      setError(null);
      const payload = await internalApi.fetchSettings();
      setSettings(payload);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load settings");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!settings) return;

    try {
      const updated = await internalApi.saveSettings(settings);
      setSettings(updated);
      setSaved(true);
      toast.success("Settings saved.");
      setTimeout(() => setSaved(false), 2200);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save settings.");
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Settings"
        description="Manage account profile, notification preferences, and AI token controls."
      />

      {loading && <DataLoading rows={6} />}
      {error && !loading && <DataError message={error} onRetry={load} />}

      {!loading && !error && settings && (
        <form onSubmit={submit} className="grid grid-cols-1 xl:grid-cols-[1fr_0.85fr] gap-4">
          <div className="liquid-glass rounded-3xl p-6 space-y-4">
            <h3 className="text-white text-xl font-heading italic">Profile</h3>

            <div className="grid gap-2">
              <Label className="text-white/75">Company Name</Label>
              <Input
                className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                value={settings.companyName}
                onChange={(e) => setSettings((prev) => (prev ? { ...prev, companyName: e.target.value } : prev))}
              />
            </div>

            <div className="grid gap-2">
              <Label className="text-white/75">Email</Label>
              <Input
                className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                value={settings.email}
                onChange={(e) => setSettings((prev) => (prev ? { ...prev, email: e.target.value } : prev))}
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-2">
                <Label className="text-white/75">Role</Label>
                <Input
                  className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                  value={settings.role}
                  onChange={(e) => setSettings((prev) => (prev ? { ...prev, role: e.target.value } : prev))}
                />
              </div>
              <div className="grid gap-2">
                <Label className="text-white/75">Timezone</Label>
                <Input
                  className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                  value={settings.timezone}
                  onChange={(e) => setSettings((prev) => (prev ? { ...prev, timezone: e.target.value } : prev))}
                />
              </div>
            </div>
          </div>

          <div className="liquid-glass rounded-3xl p-6 space-y-5">
            <h3 className="text-white text-xl font-heading italic">Preferences</h3>

            <div className="flex items-center justify-between rounded-2xl border border-white/10 px-4 py-3 bg-white/[0.02]">
              <div>
                <p className="text-white text-sm font-body">Anomaly notifications</p>
                <p className="text-white/50 text-xs font-body mt-1">Alerts when Z-score spikes are detected</p>
              </div>
              <Switch
                checked={settings.notifyAnomalies}
                onCheckedChange={(checked) => setSettings((prev) => (prev ? { ...prev, notifyAnomalies: checked } : prev))}
              />
            </div>

            <div className="flex items-center justify-between rounded-2xl border border-white/10 px-4 py-3 bg-white/[0.02]">
              <div>
                <p className="text-white text-sm font-body">Recommendation notifications</p>
                <p className="text-white/50 text-xs font-body mt-1">Updates when new AI circular actions are ready</p>
              </div>
              <Switch
                checked={settings.notifyInsights}
                onCheckedChange={(checked) => setSettings((prev) => (prev ? { ...prev, notifyInsights: checked } : prev))}
              />
            </div>

            <div className="grid gap-2">
              <Label className="text-white/75">Daily Gemini token budget</Label>
              <Input
                type="number"
                className="rounded-xl bg-white/[0.04] border-white/10 text-white"
                value={settings.dailyTokenBudget}
                onChange={(e) =>
                  setSettings((prev) => (prev ? { ...prev, dailyTokenBudget: Number(e.target.value) } : prev))
                }
              />
            </div>

            <Button className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))] w-full">
              <Save className="w-4 h-4" />
              Save Preferences
            </Button>

            {saved && (
              <div className="rounded-2xl border border-emerald-300/20 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-200">
                Settings saved successfully.
              </div>
            )}
          </div>
        </form>
      )}
    </div>
  );
};

export default SettingsPage;

