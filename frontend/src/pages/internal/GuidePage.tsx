import { ArrowRight, Boxes, ClipboardList, QrCode, Sparkles, Target, Zap } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/features/internal/components/PageHeader";

const quickStartSteps = [
  {
    icon: QrCode,
    title: "Scan Incoming Data",
    description: "Use Scan to capture invoice rows quickly, then confirm to push into inventory IN logs.",
    to: "/scan",
    cta: "Open Scan",
  },
  {
    icon: Boxes,
    title: "Validate Materials",
    description: "Check material names, units, and grades so templates and stock movement stay clean.",
    to: "/materials",
    cta: "Open Materials",
  },
  {
    icon: ClipboardList,
    title: "Run Daily Operations",
    description: "Start batch, log inventory usage, classify waste, then close batch with confidence.",
    to: "/operations",
    cta: "Open Operations",
  },
  {
    icon: Sparkles,
    title: "Review Insights",
    description: "Apply AI recommendations and anomalies to improve score and reduce landfill share.",
    to: "/insights",
    cta: "Open Insights",
  },
];

const firstDayFlow = [
  "Create or import materials first.",
  "Build one production template with realistic waste estimate.",
  "Start one test batch and log at least one IN, one OUT, and one waste entry.",
  "Close the batch and check the integrity flags.",
  "Open Analytics for weekly report snapshot.",
];

const GuidePage = () => {
  return (
    <div className="space-y-6">
      <PageHeader
        title="How To Use"
        description="A clean operational guide for new team members. Follow this flow to get value fast on desktop or phone."
      />

      <section className="liquid-glass-strong rounded-3xl border border-white/10 p-4 md:p-6 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-white text-xl font-heading italic">Quick Start Flow</p>
            <p className="text-white/60 text-sm font-body mt-1">Recommended order for first-time users.</p>
          </div>
          <Link to="/dashboard">
            <Button className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">
              Back to Dashboard
              <ArrowRight className="w-4 h-4" />
            </Button>
          </Link>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          {quickStartSteps.map((step, index) => {
            const Icon = step.icon;
            return (
              <div key={step.title} className="rounded-2xl border border-white/10 bg-white/[0.03] p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-2">
                    <span className="w-8 h-8 rounded-full bg-white/10 text-white/80 text-sm font-body flex items-center justify-center">
                      {index + 1}
                    </span>
                    <Icon className="w-4 h-4 text-[hsl(var(--palette-tea-green))]" />
                    <p className="text-white font-body font-medium">{step.title}</p>
                  </div>
                </div>
                <p className="text-white/65 text-sm font-body mt-3">{step.description}</p>
                <Link to={step.to} className="inline-block mt-4">
                  <Button variant="outline" className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10">
                    {step.cta}
                  </Button>
                </Link>
              </div>
            );
          })}
        </div>
      </section>

      <section className="grid grid-cols-1 xl:grid-cols-[1.1fr_0.9fr] gap-4">
        <div className="liquid-glass rounded-3xl p-5 space-y-3">
          <div className="flex items-center gap-2">
            <Target className="w-4 h-4 text-[hsl(var(--palette-light-green))]" />
            <p className="text-white text-xl font-heading italic">First Day Checklist</p>
          </div>

          <div className="space-y-2">
            {firstDayFlow.map((item, index) => (
              <div key={item} className="rounded-2xl border border-white/10 bg-white/[0.03] px-3 py-2 flex items-start gap-3">
                <span className="mt-0.5 text-xs text-white/70 font-body">{index + 1}.</span>
                <p className="text-white/80 text-sm font-body">{item}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="liquid-glass rounded-3xl p-5 space-y-3">
          <div className="flex items-center gap-2">
            <Zap className="w-4 h-4 text-[hsl(var(--palette-tea-green))]" />
            <p className="text-white text-xl font-heading italic">Field Cheatsheet</p>
          </div>

          <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3 space-y-2">
            <p className="text-white/60 text-xs uppercase tracking-[0.16em]">Batch Input</p>
            <p className="text-white/80 text-sm font-body">Output units: total finished product count (example 280).</p>
            <p className="text-white/80 text-sm font-body">Estimated waste kg: expected leftover material (example 14).</p>
          </div>

          <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3 space-y-2">
            <p className="text-white/60 text-xs uppercase tracking-[0.16em]">Mobile Tips</p>
            <p className="text-white/80 text-sm font-body">Use horizontal swipe on data tables for full details.</p>
            <p className="text-white/80 text-sm font-body">Use top search bar to jump quickly between modules.</p>
            <p className="text-white/80 text-sm font-body">Keep entries short and structured for better AI insights quality.</p>
          </div>

          <div className="rounded-2xl border border-emerald-300/20 bg-emerald-500/10 p-3">
            <p className="text-emerald-200 text-sm font-body">
              Ready for Spring Boot API mode: this UI flow stays the same, only data provider changes.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
};

export default GuidePage;
