import { AlertTriangle, Inbox, LoaderCircle } from "lucide-react";
import { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

export const DataLoading = ({ rows = 4 }: { rows?: number }) => {
  return (
    <div className="liquid-glass rounded-3xl p-6 space-y-4">
      {Array.from({ length: rows }).map((_, index) => (
        <Skeleton key={index} className="h-12 w-full rounded-2xl bg-white/10" />
      ))}
    </div>
  );
};

export const DataEmpty = ({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) => {
  return (
    <div className="liquid-glass rounded-3xl p-8 flex flex-col items-center text-center gap-3">
      <div className="w-12 h-12 rounded-full liquid-glass-strong flex items-center justify-center">
        <Inbox className="w-5 h-5 text-white/70" />
      </div>
      <h3 className="text-white font-body font-semibold">{title}</h3>
      <p className="text-white/60 font-body text-sm max-w-md">{description}</p>
      {action}
    </div>
  );
};

export const DataError = ({ message, onRetry }: { message: string; onRetry: () => void }) => {
  return (
    <div className="liquid-glass rounded-3xl p-8 flex flex-col items-center text-center gap-3">
      <div className="w-12 h-12 rounded-full liquid-glass-strong flex items-center justify-center">
        <AlertTriangle className="w-5 h-5 text-white/80" />
      </div>
      <h3 className="text-white font-body font-semibold">Could not load this section</h3>
      <p className="text-white/60 font-body text-sm max-w-md">{message}</p>
      <Button onClick={onRetry} className="bg-white text-black hover:bg-white/90 rounded-full px-5">
        <LoaderCircle className="w-4 h-4" />
        Retry
      </Button>
    </div>
  );
};
