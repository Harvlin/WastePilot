import { lazy, Suspense, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { motion, useReducedMotion } from "motion/react";
import "./landing.css";
import { CountUp, Reveal, ScrollBar } from "./motion";
import { EASE, pointerFill } from "./motion-utils";

const Mirrors = lazy(() => import("./mirrors"));

const FEED = [
  { t: "Material logged", d: "Organic cotton jersey · 420 kg", k: "in" },
  { t: "Waste classified", d: "Cutting offcuts · 38 kg · Recycled", k: "out" },
  { t: "Anomaly check", d: "Within expected range", k: "in" },
  { t: "Score updated", d: "Circular Score recalculated", k: "out" },
];
const SCORES = [78, 79, 81, 84];

function HeroDemo() {
  const reduce = useReducedMotion();
  const [stage, setStage] = useState(reduce ? 3 : 0);
  useEffect(() => {
    if (reduce) return;
    const t = setInterval(() => setStage((s) => (s + 1) % 5), 1700);
    return () => clearInterval(t);
  }, [reduce]);
  const shown = Math.min(stage, 4);
  const idx = Math.max(0, Math.min(shown - 1, 3));
  const prev = idx === 0 ? 76 : SCORES[idx - 1];
  return (
    <div className="l-frame">
      <div className="l-widget">
        <div className="l-widget-bar">
          <span>Shift 2 · Batch WP-1042</span>
          <span className="l-live"><span className="l-dot" /> Live demo</span>
        </div>
        <div className="l-widget-body">
          <div className="l-feed" aria-live="off">
            {FEED.map((f, n) => (
              <motion.div
                key={f.t}
                className="l-feed-row"
                animate={{ opacity: n < shown ? 1 : 0.18, y: n < shown ? 0 : 6 }}
                transition={{ duration: 0.6, ease: EASE }}
              >
                <span className="l-tag" data-k={f.k === "out" ? "out" : undefined}>{f.t}</span>
                <span className="l-small">{f.d}</span>
              </motion.div>
            ))}
          </div>
          <div className="l-gauge-row">
            <span className="l-kpi-label">Circular Score</span>
            <span className="l-score-num">
              <CountUp key={idx} from={prev} to={SCORES[idx]} active duration={1.1} />
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}

const FEATURES = [
  ["Four layers of data integrity", "A hash-chained audit trail, z-score anomaly detection, pattern review and cross-validation check every entry, so the numbers hold up to scrutiny."],
  ["AI Vision Scan", "Photograph an invoice and review the extracted materials in an editable table. Nothing is saved until a person confirms it."],
  ["Honest AI labeling", "Every insight says how it was produced: AI, statistics, or a rule-based recommendation. No black boxes."],
  ["Role-based access", "Operators run the shift. Supervisors review integrity, insights and sign-offs. Roles are enforced on the server, not just hidden in the UI."],
];

const ROWS = [
  ["Where data is captured", "On the floor, during the shift", "After the fact, from paper or spreadsheets"],
  ["Tamper evidence", "Hash-chained audit trail", "Editable cells, no history"],
  ["Spotting odd numbers", "Statistical anomaly detection and cross-validation", "Manual review, if any"],
  ["Invoice entry", "Photo scan with human confirmation", "Retyping line by line"],
  ["How AI output is labeled", "AI, statistics or rule-based, always stated", "Not applicable"],
];

export default function LandingPage() {
  const [stuck, setStuck] = useState(false);
  useEffect(() => {
    const on = () => setStuck(window.scrollY > 8);
    on();
    window.addEventListener("scroll", on, { passive: true });
    return () => window.removeEventListener("scroll", on);
  }, []);

  return (
    <div className="landing-root">
      <ScrollBar />
      <nav className="l-nav" data-stuck={stuck} aria-label="Primary">
        <div className="l-nav-inner">
          <a className="l-brand" href="#home">
            <img
              src="/logo-wordmark-dark.png"
              alt="WastePilot"
              className="l-brand-logo"
            />
          </a>
          <div className="l-nav-links">
            <a className="l-hide-sm" href="#how-it-works">How it works</a>
            <a className="l-hide-sm" href="#features">Features</a>
            <a className="l-hide-sm" href="#get-started">Get started</a>
            <Link to="/auth" className="l-btn l-btn-nav l-btn-primary" onMouseMove={pointerFill}>Sign in</Link>
          </div>
        </div>
      </nav>

      <main>
        <header className="l-hero" id="home">
          <div className="l-hero-grid-bg" aria-hidden="true" />
          <div className="l-container l-hero-stack">
            <Reveal><span className="l-eyebrow">Circularity, on the record</span></Reveal>
            <Reveal delay={0.08}><h1 className="l-display">Waste you can prove.</h1></Reveal>
            <Reveal delay={0.16}>
              <p className="l-lead">
                WastePilot turns factory-floor waste and material data into a trustworthy, auditable circularity record, replacing paper and spreadsheets with one guided workflow.
              </p>
            </Reveal>
            <Reveal delay={0.24}>
              <div className="l-cta-row">
                <Link to="/auth" className="l-btn l-btn-primary" onMouseMove={pointerFill}>Get started</Link>
                <a href="#how-it-works" className="l-btn l-btn-secondary">See how it works</a>
              </div>
            </Reveal>
            <Reveal delay={0.34} y={40} className="l-hero-demo"><HeroDemo /></Reveal>
          </div>
        </header>

        <section className="l-section">
          <div className="l-container">
            <Reveal className="l-center">
              <h2 className="l-h1">You can't improve what you can't measure credibly.</h2>
              <p className="l-lead">Most factories log waste late, on paper, with no way to verify the numbers.</p>
            </Reveal>
            <div className="l-problem">
              {[["Late", "Waste is written down at the end of the week, if at all."], ["On paper", "Spreadsheets and clipboards that anyone can edit."], ["Unverifiable", "No trail showing who changed what, or when."]].map(([t, d], i) => (
                <Reveal key={t} delay={i * 0.1}>
                  <div className="l-card l-pad"><h3 className="l-h3">{t}</h3><p className="l-body">{d}</p></div>
                </Reveal>
              ))}
            </div>
          </div>
        </section>

        <Suspense fallback={<div style={{ minHeight: 600 }} aria-hidden="true" />}>
          <Mirrors />
        </Suspense>

        <section className="l-section" id="features">
          <div className="l-container">
            <Reveal className="l-center">
              <h2 className="l-h1">Built so the numbers hold up.</h2>
            </Reveal>
            <div className="l-grid-2" style={{ marginTop: 40 }}>
              {FEATURES.map(([t, d], i) => (
                <Reveal key={t} delay={i * 0.08}>
                  <div className="l-card l-pad l-hover"><h3 className="l-h3">{t}</h3><p className="l-body">{d}</p></div>
                </Reveal>
              ))}
            </div>
          </div>
        </section>

        <section className="l-section">
          <div className="l-container">
            <Reveal className="l-center">
              <h2 className="l-h1">Record at the source, not after the fact.</h2>
            </Reveal>
            <Reveal delay={0.1}>
              <div className="l-card" style={{ overflowX: "auto", marginTop: 40 }}>
                <table className="l-compare">
                  <thead><tr><th scope="col" /><th scope="col">WastePilot</th><th scope="col">Paper and spreadsheets</th></tr></thead>
                  <tbody>
                    {ROWS.map(([a, b, c]) => (
                      <tr key={a}><th scope="row">{a}</th><td>{b}</td><td>{c}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Reveal>
          </div>
        </section>

        <section className="l-section">
          <div className="l-container l-center">
            <Reveal>
              <span className="l-eyebrow">Where we start</span>
              <h2 className="l-h1">Textile and garment exporters.</h2>
              <p className="l-lead">
                Export SMEs increasingly need credible, operational-level circularity and waste data for buyer-side sustainability reporting, such as Higg FEM and Cascale-style requirements. That pressure is why we begin here. WastePilot does not integrate with Higg FEM today.
              </p>
              <p className="l-lead">The platform is built to extend to other manufacturing sectors afterward.</p>
            </Reveal>
            <div className="l-grid-3" style={{ marginTop: 40 }}>
              <Reveal><div className="l-stat"><CountUp to={5} active /> <span className="l-small">guided steps per shift</span></div></Reveal>
              <Reveal delay={0.08}><div className="l-stat"><CountUp to={4} active /> <span className="l-small">integrity layers</span></div></Reveal>
              <Reveal delay={0.16}><div className="l-stat"><CountUp to={2} active /> <span className="l-small">roles: Operator, Supervisor</span></div></Reveal>
            </div>
          </div>
        </section>

        <section className="l-section" id="get-started">
          <div className="l-container">
            <Reveal>
              <div className="l-cta-block">
                <h2 className="l-h1">Make your circularity data credible.</h2>
                <div className="l-cta-row" style={{ justifyContent: "center" }}>
                  <Link to="/auth" className="l-btn l-btn-primary" onMouseMove={pointerFill}>Get started</Link>
                </div>
              </div>
            </Reveal>
          </div>
        </section>
      </main>

      <footer className="l-footer">
        <div className="l-container l-footer-inner">
          <span>WastePilot</span>
          <span className="l-small">Demo data shown on this page is fictional.</span>
        </div>
      </footer>
    </div>
  );
}
