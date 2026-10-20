import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";

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
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-3">
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          <Badge className={positive
            ? "gap-1 border-status-success/30 bg-white text-status-success"
            : "gap-1 border-status-critical/30 bg-white text-status-critical"}
          >
          {positive ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
          {Math.abs(delta).toFixed(1)}%
          </Badge>
        </div>
        <p className="mt-5 font-heading text-3xl font-semibold tracking-tight text-foreground">
          {value.toLocaleString()}
          <span className="ml-1 font-body text-base font-normal text-muted-foreground">{unit}</span>
        </p>
      </CardContent>
    </Card>
  );
};
