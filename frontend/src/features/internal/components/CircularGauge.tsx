import { motion } from "motion/react";
import { Card, CardContent } from "@/components/ui/card";

export const CircularGauge = ({ value }: { value: number }) => {
  const clamped = Math.max(0, Math.min(100, value));
  const size = 230;
  const strokeWidth = 16;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const dashOffset = circumference - (clamped / 100) * circumference;

  return (
    <Card>
      <CardContent className="flex flex-col items-center justify-center p-6 text-center">
        <div
          className="relative"
          role="img"
          aria-label={`Circular Score ${clamped} out of 100`}
          style={{ width: size, height: size }}
        >
          <svg width={size} height={size} className="-rotate-90" aria-hidden="true">
            <circle
              cx={size / 2}
              cy={size / 2}
              r={radius}
              stroke="hsl(var(--border))"
              strokeWidth={strokeWidth}
              fill="none"
            />
            <motion.circle
              cx={size / 2}
              cy={size / 2}
              r={radius}
              stroke="hsl(var(--brand))"
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
            <p className="font-body text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">Circular Score</p>
            <p className="mt-2 font-heading text-6xl font-semibold leading-none tracking-tight text-brand">{clamped}</p>
            <p className="mt-1 font-body text-sm text-muted-foreground">out of 100</p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};
