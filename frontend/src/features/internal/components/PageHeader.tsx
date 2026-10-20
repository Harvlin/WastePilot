import { motion } from "motion/react";
import { ReactNode } from "react";

export const PageHeader = ({
  title,
  description,
  actions,
  appearance = "legacy",
}: {
  title: string;
  description: string;
  actions?: ReactNode;
  appearance?: "legacy" | "editorial";
}) => {
  const titleClassName = appearance === "editorial"
    ? "text-3xl md:text-4xl font-heading font-semibold text-foreground tracking-tight leading-tight"
    : "text-3xl md:text-4xl font-heading italic text-white tracking-tight leading-[0.9]";
  const descriptionClassName = appearance === "editorial"
    ? "text-muted-foreground font-body mt-3 max-w-2xl"
    : "text-[hsl(var(--palette-light-green))] opacity-80 font-body mt-3 max-w-2xl";

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35 }}
      className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between"
    >
      <div>
        <h1 className={titleClassName}>{title}</h1>
        <p className={descriptionClassName}>{description}</p>
      </div>
      {actions}
    </motion.div>
  );
};
