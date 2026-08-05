"use client";

import React from "react";
import { Button } from "@/components/ui/button";
import { ChevronRight, Menu } from "lucide-react";
import { NotificationBell } from "./NotificationBell";

export interface BreadcrumbItem {
  label: string;
  href: string;
}

export interface HeaderProps {
  userName: string;
  roleName?: string;
  onLogout: () => void;
  logoutPending: boolean;
  onToggleSidebar: () => void;
  breadcrumbs?: BreadcrumbItem[];
}

export function Header({
  userName,
  roleName,
  onLogout,
  logoutPending,
  onToggleSidebar,
  breadcrumbs = [],
}: HeaderProps) {
  return (
    <header className="sticky top-0 z-20 bg-white border-b px-4 py-4 shadow-sm md:px-6">
      <div className="mx-auto flex max-w-screen-2xl flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-4">
          <Button
            variant="ghost"
            className="md:hidden p-2"
            onClick={onToggleSidebar}
          >
            <Menu className="h-5 w-5" />
          </Button>

          {/* <div className="flex flex-row">
            <p className="text-sm text-gray-500">Welcome back,</p>
            <p className="text-lg font-semibold text-gray-800">{userName}</p>
            {roleName && (
              <span className="mt-1 inline-flex rounded-full bg-gray-100 px-2 py-1 text-xs font-medium text-gray-600">
                {roleName}
              </span>
            )}
          </div> */}
        </div>

        <div className="flex flex-col gap-3 md:flex-row md:items-center md:gap-4">
          <NotificationBell />
          {breadcrumbs.length > 0 && (
            <nav className="flex max-w-full flex-wrap items-center gap-2 overflow-x-auto text-xs text-gray-500 md:flex-nowrap">
              {breadcrumbs.map((crumb, index) => (
                <React.Fragment key={crumb.href}>
                  <a href={crumb.href} className="hover:text-gray-700">
                    {crumb.label}
                  </a>
                  {index < breadcrumbs.length - 1 && (
                    <ChevronRight className="h-3 w-3" />
                  )}
                </React.Fragment>
              ))}
            </nav>
          )}

          <Button variant="outline" onClick={onLogout} disabled={logoutPending}>
            {logoutPending ? "Logging out..." : "Log out"}
          </Button>
        </div>
      </div>
    </header>
  );
}
