"use client";

import React from "react";
import { usePathname } from "next/navigation";
import { ChevronRight } from "lucide-react";
import { SettingsSidebar } from "./SettingsSidebar";
import { SettingsContent } from "./SettingsContent";

interface SettingsLayoutProps {
  children: React.ReactNode;
}

export function SettingsLayout({ children }: SettingsLayoutProps) {
  const pathname = usePathname();

  return (
    <div className="flex min-h-[calc(100vh-4rem)]">
      <SettingsSidebar pathname={pathname} />
      <div className="flex-1 min-w-0 lg:max-w-5xl">
        <SettingsContent pathname={pathname}>
          {children}
        </SettingsContent>
      </div>
    </div>
  );
}