"use client";

import Link from "next/link";
import { ChevronRight } from "lucide-react";
import type { LucideIcon } from "lucide-react";

interface SettingsNavItemProps {
  href: string;
  label: string;
  description: string;
  icon: LucideIcon;
  isActive: boolean;
}

export function SettingsNavItem({ href, label, description, icon: Icon, isActive }: SettingsNavItemProps) {
  return (
    <Link
      href={href}
      className={`group flex items-start gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors ${
        isActive
          ? "bg-primary/10 text-primary"
          : "text-muted-foreground hover:bg-muted hover:text-foreground"
      }`}
      aria-current={isActive ? "page" : undefined}
    >
      <Icon className={`h-5 w-5 shrink-0 mt-0.5 transition-colors ${isActive ? "text-primary" : "text-muted-foreground"}`} />
      <div className="min-w-0 flex-1">
        <p className={`font-medium truncate ${isActive ? "text-primary" : ""}`}>{label}</p>
        <p className="text-xs truncate text-muted-foreground">{description}</p>
      </div>
      <ChevronRight className={`h-4 w-4 shrink-0 mt-0.5 opacity-0 group-hover:opacity-100 transition-opacity ${isActive ? "opacity-100 text-primary" : ""}`} />
    </Link>
  );
}