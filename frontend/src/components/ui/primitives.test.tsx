import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StatusBadge } from "@/components/ui/status-badge";
import { StepTabs, TabsContent } from "@/components/ui/step-tabs";

describe("StatusBadge", () => {
  it("shows the state label and exposes the state as data", () => {
    render(<StatusBadge status="running">Running</StatusBadge>);

    expect(screen.getByText("Running").closest("[data-status]")).toHaveAttribute("data-status", "running");
  });
});

describe("StepTabs", () => {
  it("renders numbered steps and switches the selected panel accessibly", () => {
    render(
      <StepTabs
        defaultValue="batch"
        steps={[
          { value: "batch", title: "Start batch", description: "Create a production run" },
          { value: "material", title: "Log material" },
        ]}
      >
        <TabsContent value="batch">Batch form</TabsContent>
        <TabsContent value="material">Material form</TabsContent>
      </StepTabs>,
    );

    const materialTab = screen.getByRole("tab", { name: "Log material" });
    expect(screen.getByRole("tab", { name: /start batch/i })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Batch form")).toBeVisible();

    fireEvent.mouseDown(materialTab, { button: 0, ctrlKey: false });

    expect(materialTab).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Material form")).toBeVisible();
  });
});
