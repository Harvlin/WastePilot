import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export type StatusBadgeState = "running" | "completed" | "pending" | "warning" | "critical" | "info";

const statusClasses: Record<StatusBadgeState, string> = {
  running: "border-status-info/30 text-status-info",
  completed: "border-status-success/30 text-status-success",
  pending: "border-status-warning/30 text-status-warning",
  warning: "border-status-warning/30 text-status-warning",
  critical: "border-status-critical/30 text-status-critical",
  info: "border-status-info/30 text-status-info",
};

export function StatusBadge({
  status,
  children,
  className,
}: {
  status: StatusBadgeState;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border bg-white px-2.5 py-1 text-xs font-medium leading-none",
        statusClasses[status],
        className,
      )}
      data-status={status}
    >
      <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
      {children}
    </span>
  );
}
