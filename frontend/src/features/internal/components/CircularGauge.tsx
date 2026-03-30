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
            stroke="hsl(var(--palette-house-green) / 0.52)"
            strokeWidth={strokeWidth}
            fill="none"
          />
          <motion.circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke="hsl(var(--palette-tea-green))"
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
          <p className="text-xs uppercase tracking-[0.25em] text-[hsl(var(--palette-light-green))] opacity-70 font-body">Circular Score</p>
          <p className="text-6xl font-heading italic text-[hsl(var(--palette-tea-green))] leading-none mt-2">{clamped}</p>
          <p className="text-[hsl(var(--palette-light-green))] opacity-75 text-sm font-body mt-1">out of 100</p>
        </div>
      </div>
    </div>
  );
};
