import * as React from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";

export type StepTab = {
  value: string;
  title: string;
  description?: string;
};

type StepTabsProps = React.ComponentPropsWithoutRef<typeof Tabs> & {
  steps: StepTab[];
  listLabel?: string;
};

export function StepTabs({ steps, listLabel = "Workflow steps", className, children, ...props }: StepTabsProps) {
  return (
    <Tabs {...props} className={cn("space-y-4", className)}>
      <TabsList
        aria-label={listLabel}
        className="grid h-auto w-full grid-cols-2 gap-2 bg-transparent p-0 text-foreground sm:grid-cols-3 xl:grid-cols-5"
      >
        {steps.map((step, index) => (
          <TabsTrigger
            key={step.value}
            value={step.value}
            className="group h-full min-h-16 justify-start gap-3 whitespace-normal rounded-md border border-border bg-white px-3 py-3 text-left text-foreground shadow-card data-[state=active]:border-foreground data-[state=active]:bg-white data-[state=active]:text-foreground data-[state=active]:shadow-card"
          >
            <span
              aria-hidden="true"
              className="flex h-7 w-7 shrink-0 items-center justify-center rounded-sm bg-muted text-xs font-semibold text-muted-foreground group-data-[state=active]:bg-primary group-data-[state=active]:text-primary-foreground"
            >
              {String(index + 1).padStart(2, "0")}
            </span>
            <span className="flex min-w-0 flex-col items-start gap-1">
              <span className="text-sm font-medium leading-tight">{step.title}</span>
              {step.description && (
                <span className="text-xs font-normal leading-snug text-muted-foreground">{step.description}</span>
              )}
            </span>
          </TabsTrigger>
        ))}
      </TabsList>
      {children}
    </Tabs>
  );
}

export { TabsContent };
