"use client";

import React from "react";
import { usePathname } from "next/navigation";

interface SettingsContentProps {
  children: React.ReactNode;
  pathname: string;
}

const SECTION_LABELS: Record<string, string> = {
  "/settings": "Settings",
  "/settings/profile": "Settings / Profile",
  "/settings/preferences": "Settings / Preferences",
  "/settings/calling": "Settings / Calling",
  "/settings/http-connections": "Settings / HTTP Connections",
  "/settings/http-credentials": "Settings / HTTP Credentials",
  "/settings/demo-data": "Settings / Demo Workspace",
  "/admin/settings": "Settings / Administration / Organization",
  "/leads/settings": "Settings / Administration / Lead Settings",
  "/deals/settings": "Settings / Administration / Deal Settings",
};

export function SettingsContent({ children, pathname }: SettingsContentProps) {
  const breadcrumbLabel = SECTION_LABELS[pathname] || "Settings";

  return (
    <div className="p-4 md:p-6 lg:p-8">
      <div className="mb-6">
        <nav className="flex items-center gap-2 text-sm text-muted-foreground" aria-label="Breadcrumb">
          <span>Settings</span>
          {pathname !== "/settings" && (
            <>
              <span>/</span>
              <span className="text-foreground font-medium">{breadcrumbLabel.replace("Settings / ", "")}</span>
            </>
          )}
        </nav>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">
          {breadcrumbLabel.replace("Settings / ", "")}
        </h1>
      </div>
      {children}
    </div>
  );
}