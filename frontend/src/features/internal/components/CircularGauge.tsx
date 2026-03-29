import { motion } from "motion/react";

export const CircularGauge = ({ value }: { value: number }) => {
  const clamped = Math.max(0, Math.min(100, value));
  const size = 230;
  const strokeWidth = 16;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const dashOffset = circumference - (clamped / 100) * circumference;

  return (
    <div className="liquid-glass rounded-3xl p-6 flex flex-col items-center justify-center text-center">
      <div className="relative" style={{ width: size, height: size }}>
        <svg width={size} height={size} className="-rotate-90">
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke="rgba(255,255,255,0.12)"
            strokeWidth={strokeWidth}
            fill="none"
          />
          <motion.circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke="rgba(255,255,255,0.92)"
            strokeWidth={strokeWidth}
            fill="none"
            strokeLinecap="round"
            strokeDasharray={circumference}
            initial={{ strokeDashoffset: circumference }}
            animate={{ strokeDashoffset: dashOffset }}
            transition={{ duration: 1.2, ease: "easeOut" }}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <p className="text-xs uppercase tracking-[0.25em] text-white/50 font-body">Circular Score</p>
          <p className="text-6xl font-heading italic text-white leading-none mt-2">{clamped}</p>
          <p className="text-white/60 text-sm font-body mt-1">out of 100</p>
        </div>
      </div>
    </div>
  );
};
