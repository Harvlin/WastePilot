import {
  BarChart3,
  BookOpenText,
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
  { label: "Scan", to: "/scan", icon: QrCode },
  { label: "Materials", to: "/materials", icon: Boxes },
  { label: "Templates", to: "/templates", icon: Shapes },
  { label: "Operations", to: "/operations", icon: ClipboardList },
  { label: "Insights", to: "/insights", icon: Lightbulb },
  { label: "Analytics", to: "/analytics", icon: BarChart3 },
  { label: "How To Use", to: "/how-to-use", icon: BookOpenText },
  { label: "Settings", to: "/settings", icon: Settings },
];
