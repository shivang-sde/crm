"use client";

import React from "react";
import { usePathname } from "next/navigation";
import { ChevronDown, SlidersHorizontal } from "lucide-react";
import { SettingsSidebar } from "./SettingsSidebar";
import { SettingsContent } from "./SettingsContent";

interface SettingsLayoutProps {
  children: React.ReactNode;
}

export function SettingsLayout({ children }: SettingsLayoutProps) {
  const pathname = usePathname();
  const [mobileNavOpen, setMobileNavOpen] = React.useState(false);

  return (
    <div className="flex min-h-[calc(100dvh-4rem)] flex-col lg:flex-row">
      <SettingsSidebar pathname={pathname} />

      <div className="border-b bg-white lg:hidden">
        <button
          type="button"
          className="flex min-h-11 w-full items-center gap-2 px-4 py-2 text-left text-sm font-medium text-slate-700 hover:bg-slate-50"
          onClick={() => setMobileNavOpen((open) => !open)}
          aria-expanded={mobileNavOpen}
          aria-controls="mobile-settings-navigation"
        >
          <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
          Settings menu
          <ChevronDown
            className={`ml-auto h-4 w-4 transition-transform ${mobileNavOpen ? "rotate-180" : ""}`}
            aria-hidden="true"
          />
        </button>
        {mobileNavOpen && <SettingsSidebar pathname={pathname} variant="mobile" />}
      </div>

      <div className="min-w-0 flex-1 lg:max-w-5xl">
        <SettingsContent pathname={pathname}>
          {children}
        </SettingsContent>
      </div>
    </div>
  );
}