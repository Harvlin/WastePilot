import {
  AlertTriangle,
  ArrowRight,
  BarChart3,
  Boxes,
  CheckCircle2,
  ClipboardList,
  Clock3,
  QrCode,
  Sparkles,
  Target,
  Zap,
} from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/features/internal/components/PageHeader";
import { CLOSE_VARIANCE_THRESHOLD } from "@/features/internal/constants";

const coreShiftSteps = [
  {
    icon: ClipboardList,
    title: "1. Start Batch",
    duration: "1-2 min",
    why: "A running batch is required before the shift can be tracked correctly.",
    actions: [
      "Open Operations and fill Template Name, Output Units, and Estimated Waste.",
      "Click Start Batch to open one running batch for this shift.",
    ],
    doneWhen: "The new batch appears in Production List with status running.",
    to: "/operations",
    cta: "Open Operations",
  },
  {
    icon: Boxes,
    title: "2. Log Material",
    duration: "2-3 min",
    why: "Inventory movement keeps stock and production usage auditable.",
    actions: [
      "Use Inventory Input to add movement type IN or OUT.",
      "For OUT, select Running Batch before submitting Add Log.",
    ],
    doneWhen: "You see at least one row in Inventory Logs with the right batch and quantity.",
    to: "/operations",
    cta: "Open Operations",
  },
  {
    icon: Target,
    title: "3. Log Waste",
    duration: "1-3 min",
    why: "Waste destination quality directly affects recovery and landfill reporting.",
    actions: [
      "Select Running Batch, Waste Material, Destination, and Quantity.",
      "Use Auto-convert to Inventory IN for reuse or repair when appropriate.",
    ],
    doneWhen: "Waste Logs show destination and recovery status for the recorded item.",
    to: "/operations",
    cta: "Open Operations",
  },
  {
    icon: Zap,
    title: "4. Close Batch",
    duration: "2-4 min",
    why: "Batch close finalizes shift output and protects reporting confidence.",
    actions: [
      "Use Batch Close Assistant and review auto summary before confirming.",
      `If variance exceeds ${CLOSE_VARIANCE_THRESHOLD}, fill Close Reason before confirm.`,
    ],
    doneWhen: "Batch status changes to completed with close summary recorded.",
    to: "/operations",
    cta: "Open Operations",
  },
  {
    icon: CheckCircle2,
    title: "5. Integrity Check",
    duration: "1-2 min",
    why: "End-of-shift integrity check reduces hidden data issues during handover.",
    actions: [
      "Open Integrity tab and review Activity Logs and Audit Trail.",
      "Make sure no critical red flag is left unresolved before shift handover.",
    ],
    doneWhen: "Activity and audit data are visible for the batch that just closed.",
    to: "/operations",
    cta: "Open Operations",
  },
];

const supportingModules = [
  {
    icon: QrCode,
    title: "AI Vision Scan",
    to: "/scan",
    cta: "Open Scan",
    when: "Use when invoice image is available.",
    action: "Run OCR, correct rows, then Confirm & Save to inventory IN.",
  },
  {
    icon: Boxes,
    title: "Materials",
    to: "/materials",
    cta: "Open Materials",
    when: "Use when materials are new or inconsistent.",
    action: "Add or edit material, unit, grade, and supplier before shift logging.",
  },
  {
    icon: ClipboardList,
    title: "Production Templates",
    to: "/templates",
    cta: "Open Templates",
    when: "Use before repeated production runs.",
    action: "Create reusable composition and expected waste baseline.",
  },
  {
    icon: Sparkles,
    title: "Insights",
    to: "/insights",
    cta: "Open Insights",
    when: "Use after data logging is stable.",
    action: "Work through Action Queue, apply recommendations, and resolve anomalies.",
  },
  {
    icon: BarChart3,
    title: "Analytics",
    to: "/analytics",
    cta: "Open Analytics",
    when: "Use at daily/weekly/monthly review time.",
    action: "Review trend, waste breakdown, and period report snapshots.",
  },
];

const firstDayFlow = [
  "Prepare base data: materials list and one template (recommended).",
  "Complete the 5 Operations steps once from Start Batch to Integrity Check.",
  "Use Scan at least once for invoice-to-inventory flow validation.",
  "Review one item in Action Queue and mark clear status.",
  "Open Analytics period report to validate shift outcome visibility.",
];

const goldenRules = [
  "Always ensure one running batch exists before OUT or waste logging.",
  "Record quantity and unit clearly in every entry form.",
  `Provide close reason when variance exceeds ${CLOSE_VARIANCE_THRESHOLD}.`,
];

const commonMistakes = [
  {
    bad: "Submitting inventory OUT without selecting Running Batch.",
    fix: "In Inventory Input, select batch first, then submit Add Log.",
  },
  {
    bad: "Skipping close reason on major variance.",
    fix: `If variance is above ${CLOSE_VARIANCE_THRESHOLD}, fill Close Reason before confirm.`,
  },
  {
    bad: "Saving OCR rows before selecting running batch.",
    fix: "Choose running batch in Scan page, then Confirm & Save.",
  },
];

const scenarioPlaybooks = [
  {
    title: "No Running Batch At Shift Start",
    signal: "You open Operations and no batch is marked as running.",
    doNow: [
      "Create a new batch in Step 1 using a valid template, output units, and estimated waste.",
      "Confirm the batch appears in Production List with running status.",
      "Continue to Step 2 before logging OUT or waste.",
    ],
    expected: "Inventory and waste logging become valid for the active shift.",
  },
  {
    title: "Invoice Arrives During Shift",
    signal: "You need fast stock-in logging from supplier invoice image.",
    doNow: [
      "Open Scan and run OCR from uploaded or camera image.",
      "Correct material, quantity, unit, and price fields before save.",
      "Select a running batch and click Confirm & Save.",
    ],
    expected: "Rows are saved as inventory IN logs with traceable source data.",
  },
  {
    title: "Inventory OUT Gets Blocked",
    signal: "OUT submission fails or cannot proceed in Inventory Input.",
    doNow: [
      "Check that movement type is OUT and a running batch is selected.",
      "Confirm quantity is above zero and material name is not empty.",
      "Submit Add Log again after correcting fields.",
    ],
    expected: "OUT movement is accepted and appears in Inventory Logs.",
  },
  {
    title: "Variance Is Too High At Batch Close",
    signal: `Close summary shows variance above ${CLOSE_VARIANCE_THRESHOLD}.`,
    doNow: [
      "Re-check output and waste quantities first.",
      `Fill Close Reason because variance exceeds ${CLOSE_VARIANCE_THRESHOLD}.`,
      "Confirm close only after reason and values are complete.",
    ],
    expected: "Batch closes with complete audit context and no validation block.",
  },
  {
    title: "Anomaly Appears In Insights",
    signal: "Action Queue shows a new anomaly with high impact.",
    doNow: [
      "Open Insights and jump to the anomaly item.",
      "Review process context and supporting notes.",
      "Mark status as resolved or ignored with clear operational decision.",
    ],
    expected: "Team handover is clearer and unresolved anomaly count is reduced.",
  },
  {
    title: "Need Weekly Or Monthly Review",
    signal: "Supervisor asks for performance update and trend snapshot.",
    doNow: [
      "Open Analytics and switch to weekly or monthly tab.",
      "Review circular score trend, waste flow trend, and top actions.",
      "Use summary cards to discuss on-time close and landfill impact.",
    ],
    expected: "You can share evidence-based status using current reporting views.",
  },
];

const GuidePage = () => {
  return (
    <div className="space-y-6">
      <PageHeader
        title="How To Use"
        description="This guide is aligned with the currently active workspace features. Follow the core flow first, then use supporting modules as needed."
      />

      <section className="liquid-glass-strong rounded-3xl border border-white/10 p-4 md:p-6 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-white text-xl font-heading italic">Core Shift Flow (Operations)</p>
            <p className="text-white/60 text-sm font-body mt-1">
              The sequence below follows the exact step labels shown in Operations.
            </p>
          </div>
          <Link to="/dashboard">
            <Button className="rounded-full bg-[hsl(var(--palette-tea-green))] text-[hsl(var(--palette-house-green))] hover:bg-[hsl(var(--palette-light-green))]">
              Back to Dashboard
              <ArrowRight className="w-4 h-4" />
            </Button>
          </Link>
        </div>

        <div className="rounded-2xl border border-emerald-300/20 bg-emerald-500/10 px-4 py-3">
          <div className="flex items-start gap-2">
            <Clock3 className="w-4 h-4 mt-0.5 text-emerald-200" />
            <p className="text-emerald-100 text-sm font-body">
              Typical first-time onboarding: 8-15 minutes for one complete batch cycle.
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 xl:grid-cols-2 gap-3">
          {coreShiftSteps.map((step, index) => {
            const Icon = step.icon;
            return (
              <div key={step.title} className="rounded-2xl border border-white/10 bg-white/[0.03] p-4 space-y-3">
                <div className="flex items-start justify-between gap-3 flex-wrap">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="w-8 h-8 rounded-full bg-white/10 text-white/80 text-sm font-body flex items-center justify-center">
                      {index + 1}
                    </span>
                    <Icon className="w-4 h-4 text-[hsl(var(--palette-tea-green))]" />
                    <p className="text-white font-body font-medium">{step.title}</p>
                  </div>
                  <span className="text-xs font-body rounded-full border border-white/20 bg-white/[0.04] px-2 py-1 text-white/70">
                    {step.duration}
                  </span>
                </div>

                <div className="rounded-xl border border-white/10 bg-black/20 p-3 space-y-2">
                  <p className="text-white/60 text-xs uppercase tracking-[0.16em]">Why this matters</p>
                  <p className="text-white/80 text-sm font-body">{step.why}</p>
                </div>

                <div className="space-y-2">
                  <p className="text-white/60 text-xs uppercase tracking-[0.16em]">Do this now</p>
                  {step.actions.map((action) => (
                    <div
                      key={action}
                      className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2 flex items-start gap-2"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5 mt-0.5 text-[hsl(var(--palette-light-green))]" />
                      <p className="text-white/80 text-sm font-body">{action}</p>
                    </div>
                  ))}
                </div>

                <div className="rounded-xl border border-emerald-300/20 bg-emerald-500/10 px-3 py-2">
                  <p className="text-emerald-100 text-sm font-body">
                    <span className="font-medium">Done when:</span> {step.doneWhen}
                  </p>
                </div>

                <Link to={step.to} className="inline-block">
                  <Button variant="outline" className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10">
                    {step.cta}
                  </Button>
                </Link>
              </div>
            );
          })}
        </div>
      </section>

      <section className="liquid-glass-strong rounded-3xl border border-white/10 p-4 md:p-6 space-y-4">
        <div className="flex items-start gap-2">
          <Target className="w-4 h-4 mt-1 text-[hsl(var(--palette-light-green))]" />
          <div>
            <p className="text-white text-xl font-heading italic">Scenario Playbooks (Real Use Cases)</p>
            <p className="text-white/60 text-sm font-body mt-1">
              Use these playbooks when specific situations happen. Each case tells you exactly what to do next.
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 xl:grid-cols-2 gap-3">
          {scenarioPlaybooks.map((playbook) => (
            <div key={playbook.title} className="rounded-2xl border border-white/10 bg-white/[0.03] p-4 space-y-3">
              <p className="text-white font-body font-medium">{playbook.title}</p>

              <div className="rounded-xl border border-white/10 bg-black/20 p-3">
                <p className="text-white/60 text-xs uppercase tracking-[0.16em]">Situation</p>
                <p className="text-white/80 text-sm font-body mt-2">{playbook.signal}</p>
              </div>

              <div className="space-y-2">
                <p className="text-white/60 text-xs uppercase tracking-[0.16em]">What You Can Do</p>
                {playbook.doNow.map((action) => (
                  <div key={action} className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2 flex items-start gap-2">
                    <CheckCircle2 className="w-3.5 h-3.5 mt-0.5 text-[hsl(var(--palette-light-green))]" />
                    <p className="text-white/80 text-sm font-body">{action}</p>
                  </div>
                ))}
              </div>

              <div className="rounded-xl border border-emerald-300/20 bg-emerald-500/10 p-3">
                <p className="text-emerald-100 text-sm font-body">
                  <span className="font-medium">Expected result:</span> {playbook.expected}
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="grid grid-cols-1 xl:grid-cols-[1.1fr_0.9fr] gap-4">
        <div className="liquid-glass rounded-3xl p-5 space-y-3">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-[hsl(var(--palette-light-green))]" />
            <p className="text-white text-xl font-heading italic">Supporting Modules</p>
          </div>

          <p className="text-white/65 text-sm font-body">
            Use these modules to support the main flow, not to replace the Operations sequence.
          </p>

          <div className="space-y-2">
            {supportingModules.map((module) => {
              const Icon = module.icon;
              return (
                <div key={module.title} className="rounded-2xl border border-white/10 bg-white/[0.03] p-3 space-y-2">
                  <div className="flex items-center justify-between gap-2 flex-wrap">
                    <div className="flex items-center gap-2">
                      <Icon className="w-4 h-4 text-[hsl(var(--palette-tea-green))]" />
                      <p className="text-white font-body font-medium">{module.title}</p>
                    </div>
                    <Link to={module.to}>
                      <Button variant="outline" className="rounded-full border-white/20 bg-white/5 text-white hover:bg-white/10">
                        {module.cta}
                      </Button>
                    </Link>
                  </div>
                  <p className="text-white/70 text-sm font-body">{module.when}</p>
                  <p className="text-white/80 text-sm font-body">{module.action}</p>
                </div>
              );
            })}
          </div>

          <div className="rounded-2xl border border-white/10 bg-black/20 p-3 space-y-2">
            <div className="flex items-center gap-2">
              <Target className="w-4 h-4 text-[hsl(var(--palette-light-green))]" />
              <p className="text-white text-sm uppercase tracking-[0.16em]">First Day Checklist</p>
            </div>
            <div className="space-y-2">
              {firstDayFlow.map((item, index) => (
                <div key={item} className="rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2 flex items-start gap-3">
                  <span className="mt-0.5 text-xs text-white/70 font-body">{index + 1}.</span>
                  <p className="text-white/80 text-sm font-body">{item}</p>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-2xl border border-white/10 bg-black/20 p-3 space-y-2">
            <p className="text-white/60 text-xs uppercase tracking-[0.16em]">If You Only Remember 3 Things</p>
            <div className="space-y-2">
              {goldenRules.map((rule) => (
                <div key={rule} className="flex items-start gap-2">
                  <CheckCircle2 className="w-3.5 h-3.5 mt-0.5 text-[hsl(var(--palette-light-green))]" />
                  <p className="text-white/80 text-sm font-body">{rule}</p>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="liquid-glass rounded-3xl p-5 space-y-3">
          <div className="flex items-center gap-2">
            <Zap className="w-4 h-4 text-[hsl(var(--palette-tea-green))]" />
            <p className="text-white text-xl font-heading italic">Field Cheatsheet</p>
          </div>

          <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3 space-y-2">
            <p className="text-white/60 text-xs uppercase tracking-[0.16em]">Batch Input</p>
            <p className="text-white/80 text-sm font-body">Output Units (pcs): total finished product count (example 280).</p>
            <p className="text-white/80 text-sm font-body">Estimated Waste (kg): expected leftover material (example 14).</p>
          </div>

          <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3 space-y-2">
            <p className="text-white/60 text-xs uppercase tracking-[0.16em]">Inventory And Waste</p>
            <p className="text-white/80 text-sm font-body">Inventory OUT requires selected running batch.</p>
            <p className="text-white/80 text-sm font-body">For waste destination dispose, auto-convert to Inventory IN is disabled.</p>
          </div>

          <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-3 space-y-2">
            <p className="text-white/60 text-xs uppercase tracking-[0.16em]">Mobile Tips</p>
            <p className="text-white/80 text-sm font-body">Use horizontal scroll on wide tables to view all columns.</p>
            <p className="text-white/80 text-sm font-body">Use global search bar to jump quickly across modules.</p>
          </div>

          <div className="space-y-2">
            <p className="text-white text-lg font-heading italic">Common Mistakes</p>
            {commonMistakes.map((item) => (
              <div key={item.bad} className="rounded-2xl border border-amber-300/20 bg-amber-500/10 p-3 space-y-2">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="w-4 h-4 mt-0.5 text-amber-200" />
                  <p className="text-amber-100 text-sm font-body">{item.bad}</p>
                </div>
                <p className="text-emerald-100 text-sm font-body">Better way: {item.fix}</p>
              </div>
            ))}
          </div>

          <div className="rounded-2xl border border-emerald-300/20 bg-emerald-500/10 p-3">
            <p className="text-emerald-200 text-sm font-body">
              Data provider can switch between mock and Spring API without changing this user flow.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
};

export default GuidePage;
