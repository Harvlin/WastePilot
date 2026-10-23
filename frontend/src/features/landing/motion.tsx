import { motion, useReducedMotion, animate, useScroll, useSpring } from "motion/react";
import { useEffect, useRef, useState, type CSSProperties, type MouseEvent, type ReactNode } from "react";
import { EASE } from "./motion-utils";

export function Reveal({
  children,
  delay = 0,
  y = 24,
  className,
  style,
}: {
  children: ReactNode;
  delay?: number;
  y?: number;
  className?: string;
  style?: CSSProperties;
}) {
  const reduce = useReducedMotion();
  if (reduce) return <div className={className} style={style}>{children}</div>;
  return (
    <motion.div
      className={className}
      style={style}
      initial={{ opacity: 0, y }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-80px" }}
      transition={{ duration: 0.8, delay, ease: EASE }}
    >
      {children}
    </motion.div>
  );
}

export function CountUp({
  to,
  active,
  decimals = 0,
  duration = 1.6,
  from = 0,
}: {
  to: number;
  active: boolean;
  decimals?: number;
  duration?: number;
  from?: number;
}) {
  const reduce = useReducedMotion();
  const [val, setVal] = useState(reduce ? to : from);
  useEffect(() => {
    if (reduce) {
      setVal(to);
      return;
    }
    if (!active) return;
    const controls = animate(from, to, {
      duration,
      ease: EASE,
      onUpdate: (v) => setVal(v),
    });
    return () => controls.stop();
  }, [active, to, from, duration, reduce]);
  return <span className="l-num">{val.toFixed(decimals)}</span>;
}

/** Subtle cursor-aware tilt; transform only. */
export function Tilt({ children, className, max = 2 }: { children: ReactNode; className?: string; max?: number }) {
  const ref = useRef<HTMLDivElement>(null);
  const reduce = useReducedMotion();
  const onMove = (e: MouseEvent<HTMLDivElement>) => {
    const el = ref.current;
    if (!el || reduce) return;
    const r = el.getBoundingClientRect();
    const px = (e.clientX - r.left) / r.width - 0.5;
    const py = (e.clientY - r.top) / r.height - 0.5;
    el.style.transform = `perspective(900px) rotateX(${(-py * max).toFixed(2)}deg) rotateY(${(px * max).toFixed(2)}deg) translateY(-1px)`;
  };
  const onLeave = () => {
    if (ref.current) ref.current.style.transform = "";
  };
  return (
    <div ref={ref} className={`l-hover ${className ?? ""}`} onMouseMove={onMove} onMouseLeave={onLeave}>
      {children}
    </div>
  );
}

export function ScrollBar() {
  const { scrollYProgress } = useScroll();
  const scaleX = useSpring(scrollYProgress, { stiffness: 120, damping: 28, mass: 0.4 });
  return <motion.div className="l-scrollbar" style={{ scaleX }} aria-hidden="true" />;
}
