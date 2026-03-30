import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import { motion } from "motion/react";

export const StatCard = ({
  label,
  value,
  unit,
  delta,
}: {
  label: string;
  value: number;
  unit: string;
  delta: number;
}) => {
  const positive = delta >= 0;

  return (
    <motion.div
      whileHover={{ y: -4 }}
      transition={{ duration: 0.2 }}
      className="liquid-glass rounded-3xl p-5"
    >
      <div className="flex items-start justify-between gap-4">
        <p className="text-[hsl(var(--palette-light-green))] opacity-75 text-sm font-body">{label}</p>
        <div
          className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-body ${
            positive
              ? "bg-[hsl(var(--palette-primary-green))]/30 text-[hsl(var(--palette-tea-green))]"
              : "bg-rose-500/15 text-rose-300"
          }`}
        >
          {positive ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
          {Math.abs(delta).toFixed(1)}%
        </div>
      </div>
      <p className="mt-4 text-3xl font-heading italic text-[hsl(var(--palette-tea-green))]">
        {value.toLocaleString()}
        <span className="ml-1 text-base text-[hsl(var(--palette-light-green))] opacity-80 font-body not-italic">{unit}</span>
      </p>
    </motion.div>
  );
};
