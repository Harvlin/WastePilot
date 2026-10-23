import { motion, useMotionValueEvent, useReducedMotion, useScroll, useTransform, type MotionValue } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { CountUp, Reveal, Tilt } from "./motion";
import { EASE, useEnter } from "./motion-utils";

function Gauge({ value, active, size = 200, stroke = 14 }: { value: number; active: boolean; size?: number; stroke?: number }) {
  const reduce = useReducedMotion();
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const target = c - (value / 100) * c;
  return (
    <div className="l-score-wrap" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={`Circular score ${value} out of 100`}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#E5E7EB" strokeWidth={stroke} />
        <motion.circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="#141414"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          initial={{ strokeDashoffset: reduce ? target : c }}
          animate={{ strokeDashoffset: active || reduce ? target : c }}
          transition={{ duration: 1.6, ease: EASE }}
        />
      </svg>
      <div className="l-score-center" aria-hidden="true">
        <span className="l-kpi-label">Circular Score</span>
        <span className="l-score-num">
          <CountUp to={value} active={active} />
        </span>
        <span className="l-small">out of 100</span>
      </div>
    </div>
  );
}

function MirrorCopy({ eyebrow, title, text, id }: { eyebrow: string; title: string; text: string; id?: string }) {
  return (
    <Reveal className="l-mirror-copy">
      <span className="l-eyebrow" id={id}>{eyebrow}</span>
      <h2 className="l-h2">{title}</h2>
      <p className="l-lead">{text}</p>
    </Reveal>
  );
}

/* 1 — Dashboard */
const KPIS = [
  { l: "Waste diverted", v: "78.4", u: "%", d: "+3.1 pts" },
  { l: "Material efficiency", v: "92.7", u: "%", d: "+1.2 pts" },
  { l: "Landfill share", v: "6.8", u: "%", d: "−0.9 pts" },
  { l: "Open red flags", v: "2", u: "", d: "1 new today" },
];

function DashboardMirror() {
  const [ref, inView] = useEnter<HTMLDivElement>();
  return (
    <div className="l-container l-mirror">
      <MirrorCopy
        eyebrow="Dashboard"
        title="One score your buyers can trust."
        text="Every kilogram logged on the floor rolls up into a single Circular Score, with the red flags that would undermine it surfaced right beside it."
      />
      <Reveal delay={0.1}>
        <div className="l-frame">
          <div className="l-widget" ref={ref}>
            <div className="l-widget-bar">
              <span>Plant overview</span>
              <span className="l-live"><span className="l-dot" /> Demo data</span>
            </div>
            <div className="l-widget-body">
              <div style={{ display: "flex", justifyContent: "center", padding: "8px 0 20px" }}>
                <Gauge value={84} active={inView} />
              </div>
              <div className="l-grid-2">
                {KPIS.map((k, i) => (
                  <motion.div
                    key={k.l}
                    className="l-kpi"
                    initial={{ opacity: 0, y: 14 }}
                    animate={inView ? { opacity: 1, y: 0 } : undefined}
                    whileInView={undefined}
                    transition={{ duration: 0.7, delay: 0.5 + i * 0.12, ease: EASE }}
                    style={inView ? undefined : { opacity: 0 }}
                  >
                    <div className="l-kpi-label">{k.l}</div>
                    <div className="l-kpi-val">{k.v}<small>{k.u}</small></div>
                    <span className="l-delta">{k.d}</span>
                  </motion.div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </Reveal>
    </div>
  );
}

/* 2 — Operations stepper */
const STEPS = [
  ["Start Batch", "Create a running batch for the current shift."],
  ["Log Material", "Record stock IN/OUT while production is active."],
  ["Log Waste", "Classify waste destination and recovery status."],
  ["Close Batch", "Review summary and confirm final output."],
  ["Integrity Check", "Review activity and audit trail before handover."],
];

function StepperMirror() {
  const reduce = useReducedMotion();
  const [wide, setWide] = useState(() => (typeof window === "undefined" ? true : window.matchMedia("(min-width: 961px)").matches));
  useEffect(() => {
    const mq = window.matchMedia("(min-width: 961px)");
    const on = () => setWide(mq.matches);
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, []);
  const pinned = wide && !reduce;
  const track = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({ target: track, offset: ["start start", "end end"] });
  const [i, setI] = useState(reduce ? 2 : 0);
  useMotionValueEvent(scrollYProgress, "change", (p) => {
    if (!pinned) return;
    const n = Math.min(STEPS.length - 1, Math.max(0, Math.floor(p * (STEPS.length + 0.4))));
    setI((prev) => (prev === n ? prev : n));
  });
  const progress = useTransform(scrollYProgress, [0, 1], [0.1, 1]);
  const lift = useTransform(scrollYProgress, [0, 0.5, 1], [24, 0, -24]);
  const copyShift = useTransform(scrollYProgress, [0, 1], [16, -16]);
  return (
    <div ref={track} className={pinned ? "l-pin" : undefined}>
      <div className={pinned ? "l-pin-stick" : "l-section"}>
        <div className="l-container l-mirror flip">
          <motion.div style={pinned ? { y: copyShift } : undefined}>
            <MirrorCopy
              id="how-it-works"
              eyebrow="Daily shift workflow"
              title="Five steps. No paper trail to chase."
              text="Operators are guided from batch start to integrity check, so every shift ends with a record that is complete by construction."
            />
          </motion.div>
          <motion.div style={pinned ? { y: lift } : undefined}>
            <StepperFrame i={i} progress={pinned ? progress : undefined} />
          </motion.div>
        </div>
      </div>
    </div>
  );
}

function StepperFrame({ i, progress }: { i: number; progress?: MotionValue<number> }) {
  return (
    <div className="l-container-free">
      <Reveal delay={0.1}>
        <div className="l-frame">
          <div className="l-widget">
            <div className="l-widget-bar">
              <span>Daily Shift Workflow</span>
              <span className="l-tag">Step {i + 1} of 5</span>
            </div>
            <div className="l-widget-body">
              <div className="l-steps">
                {STEPS.map(([t, h], n) => (
                  <div key={t} className="l-step" data-s={n === i ? "active" : n < i ? "done" : "todo"}>
                    <div className="l-step-badge">{n < i ? "✓" : n + 1}</div>
                    <div>
                      <div className="l-step-title">{n + 1}. {t}</div>
                      <div className="l-step-help">{h}</div>
                    </div>
                  </div>
                ))}
              </div>
              <div className="l-progress" aria-hidden="true">
                {progress ? (
                  <motion.i style={{ scaleX: progress }} />
                ) : (
                  <i style={{ transform: `scaleX(${(i + 1) / STEPS.length})` }} />
                )}
              </div>
            </div>
          </div>
        </div>
      </Reveal>
    </div>
  );
}

/* 3 — Floor View */
const BATCHES = [
  { n: "Cotton Jersey Knit", a: "42 min", h: "green", l: "On track", rt: "03:12", v: "+0.8%" },
  { n: "Denim Wash Line", a: "1 h 05 min", h: "amber", l: "Watch", rt: "04:40", v: "+6.4%" },
  { n: "Poly-Blend Cut & Sew", a: "18 min", h: "green", l: "On track", rt: "01:05", v: "−0.3%" },
  { n: "Dye House Lot 7", a: "2 h 10 min", h: "red", l: "Attention", rt: "05:58", v: "+14.9%" },
];

function FloorMirror() {
  const [ref, inView] = useEnter<HTMLDivElement>();
  return (
    <div className="l-container l-mirror">
      <MirrorCopy
        eyebrow="Floor View"
        title="See the whole floor at a glance."
        text="Running batches report their runtime and variance live, and the ones drifting from plan are flagged before the shift ends."
      />
      <Reveal delay={0.1}>
        <div className="l-frame">
          <div className="l-widget" ref={ref}>
            <div className="l-widget-bar">
              <span>Live Factory Floor</span>
              <span className="l-live"><span className="l-dot" /> Real-time</span>
            </div>
            <div className="l-widget-body l-grid-2">
              {BATCHES.map((b, n) => (
                <motion.div
                  key={b.n}
                  initial={{ opacity: 0, y: 18 }}
                  animate={inView ? { opacity: 1, y: 0 } : undefined}
                  style={inView ? undefined : { opacity: 0 }}
                  transition={{ duration: 0.7, delay: n * 0.1, ease: EASE }}
                >
                  <Tilt className="l-batch">
                    <div className="l-batch-head">
                      <div>
                        <div className="l-step-title">{b.n}</div>
                        <div className="l-small">Started {b.a} ago</div>
                      </div>
                      <span className="l-health">
                        <span className="l-dot" data-h={b.h === "green" ? undefined : b.h} />
                        {b.l}
                      </span>
                    </div>
                    <div className="l-tiles">
                      <div className="l-tile"><div className="l-kpi-label">Runtime</div><div className="l-step-title l-num">{b.rt}</div></div>
                      <div className="l-tile"><div className="l-kpi-label">Variance</div><div className="l-step-title l-num">{b.v}</div></div>
                    </div>
                  </Tilt>
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      </Reveal>
    </div>
  );
}

/* 4 — Analytics */
const SERIES = [14.2, 12.8, 12.9, 10.4, 9.1, 8.3, 7.6, 6.8];

function AnalyticsMirror() {
  const [ref, inView] = useEnter<HTMLDivElement>();
  const reduce = useReducedMotion();
  const w = 440, h = 200, padX = 28, padY = 20;
  const pts = SERIES.map((v, i) => [padX + (i * (w - padX * 2)) / (SERIES.length - 1), padY + ((16 - v) / 16) * (h - padY * 2)]);
  const d = pts.map((p, i) => `${i ? "L" : "M"}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(" ");
  const show = inView || reduce;
  return (
    <div className="l-container l-mirror flip">
      <MirrorCopy
        eyebrow="Analytics"
        title="Watch landfill share fall, week by week."
        text="Trends, waste breakdowns and per-batch circularity turn a month of shifts into evidence you can hand to a buyer."
      />
      <Reveal delay={0.1}>
        <div className="l-frame">
          <div className="l-widget" ref={ref}>
            <div className="l-widget-bar">
              <span>Landfill share trend</span>
              <span className="l-tag">Last 8 weeks</span>
            </div>
            <div className="l-widget-body">
              <svg viewBox={`0 0 ${w} ${h}`} width="100%" role="img" aria-label="Landfill share falling from 14.2 percent to 6.8 percent over eight weeks">
                {[0, 1, 2, 3].map((g) => (
                  <line key={g} className="l-axis" x1={padX} x2={w - padX} y1={padY + (g * (h - padY * 2)) / 3} y2={padY + (g * (h - padY * 2)) / 3} />
                ))}
                <motion.path
                  d={d}
                  fill="none"
                  stroke="#141414"
                  strokeWidth={2.5}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  initial={{ pathLength: reduce ? 1 : 0 }}
                  animate={{ pathLength: show ? 1 : 0 }}
                  transition={{ duration: 1.8, ease: EASE }}
                />
                {pts.map((p, i) => (
                  <motion.circle
                    key={i}
                    cx={p[0]}
                    cy={p[1]}
                    r={4}
                    fill="#fff"
                    stroke="#141414"
                    strokeWidth={2}
                    initial={{ opacity: reduce ? 1 : 0 }}
                    animate={{ opacity: show ? 1 : 0 }}
                    transition={{ duration: 0.4, delay: 0.2 + i * 0.2, ease: EASE }}
                  />
                ))}
                {["W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8"].map((t, i) => (
                  <text key={t} className="l-axis-t" x={pts[i][0]} y={h - 2} textAnchor="middle">{t}</text>
                ))}
              </svg>
              <div className="l-grid-2" style={{ marginTop: 12 }}>
                <div className="l-kpi"><div className="l-kpi-label">Landfill share</div><div className="l-kpi-val">6.8<small>%</small></div></div>
                <div className="l-kpi"><div className="l-kpi-label">Change</div><div className="l-kpi-val">−7.4<small>pts</small></div></div>
              </div>
            </div>
          </div>
        </div>
      </Reveal>
    </div>
  );
}

export default function Mirrors() {
  return (
    <>
      <section className="l-section"><DashboardMirror /></section>
      <StepperMirror />
      <section className="l-section"><FloorMirror /></section>
      <section className="l-section"><AnalyticsMirror /></section>
    </>
  );
}
