import {
  BarChart3,
  Boxes,
  ClipboardList,
  LayoutDashboard,
  Lightbulb,
  QrCode,
  Settings,
  Shapes,
} from "lucide-react";

export const internalNav = [
  { label: "Dashboard", to: "/dashboard", icon: LayoutDashboard },
  { label: "Operations", to: "/operations", icon: ClipboardList },
  { label: "Scan", to: "/scan", icon: QrCode },
  { label: "Materials", to: "/materials", icon: Boxes },
  { label: "Templates", to: "/templates", icon: Shapes },
  { label: "Insights", to: "/insights", icon: Lightbulb },
  { label: "Analytics", to: "/analytics", icon: BarChart3 },
  { label: "Settings", to: "/settings", icon: Settings },
];
