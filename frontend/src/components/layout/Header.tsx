"use client";

import React from "react";
import { NotificationBell } from "./NotificationBell";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import {
  ChevronRight,
  Menu,
  User as UserIcon,
} from "lucide-react";
import Link from "next/link";

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
  sidebarOpen?: boolean;
  breadcrumbs?: BreadcrumbItem[];
}

export function Header({
  userName,
  roleName,
  onLogout,
  logoutPending,
  onToggleSidebar,
  sidebarOpen = false,
  breadcrumbs = [],
}: HeaderProps) {
  const currentLabel = breadcrumbs[breadcrumbs.length - 1]?.label ?? "Sellspark CRM";

  return (
    <header className="w-full bg-white px-3 sm:px-4 md:px-6">
      <div className="mx-auto flex h-full flex-row items-center justify-between gap-2 sm:gap-4">
        {/* Left Side: Mobile Menu Button, Current Page & Breadcrumbs */}
        <div className="flex min-w-0 flex-1 items-center gap-1.5 sm:gap-3">
          <Button
            variant="ghost"
            size="icon"
            className="h-11 w-11 shrink-0 md:hidden"
            onClick={onToggleSidebar}
            aria-label="Open navigation menu"
            aria-controls="mobile-navigation"
            aria-expanded={sidebarOpen}
          >
            <Menu className="h-5 w-5" />
          </Button>

          <span className="truncate text-sm font-semibold text-slate-900 md:hidden">
            {currentLabel}
          </span>

          {breadcrumbs.length > 0 && (
            <nav
              aria-label="Breadcrumb"
              className="hidden min-w-0 items-center gap-1.5 overflow-x-auto text-sm whitespace-nowrap text-slate-500 md:flex"
            >
              {breadcrumbs.map((crumb, index) => (
                <React.Fragment key={crumb.href}>
                  {index < breadcrumbs.length - 1 ? (
                    <Link
                      href={crumb.href}
                      className="rounded-sm transition-colors hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      {crumb.label}
                    </Link>
                  ) : (
                    <span
                      aria-current="page"
                      className="max-w-[36vw] truncate font-medium text-slate-900 lg:max-w-[240px]"
                    >
                      {crumb.label}
                    </span>
                  )}
                  {index < breadcrumbs.length - 1 && (
                    <ChevronRight className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                  )}
                </React.Fragment>
              ))}
            </nav>
          )}
        </div>

        {/* Right Side: Notification Bell & Profile Settings Dropdown */}
        <div className="flex shrink-0 items-center gap-1 sm:gap-2">
          <NotificationBell />

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                className="relative h-11 w-11 rounded-full bg-slate-100 p-0 hover:bg-slate-200"
              >
                <UserIcon className="h-5 w-5 text-slate-600" />
                <span className="sr-only">Toggle user menu</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56 max-w-[calc(100vw-2rem)]">
              <div className="px-2 py-1.5 text-xs text-slate-400">
                Logged in as{" "}
                <span className="block truncate font-semibold text-slate-700">{userName}</span>
                {roleName && (
                  <span className="mt-0.5 block text-[10px] font-medium tracking-wider text-indigo-600 uppercase">
                    {roleName}
                  </span>
                )}
              </div>
              <DropdownMenuSeparator />
              <DropdownMenuItem asChild>
                <Link href="/settings" className="w-full cursor-pointer">
                  Settings
                </Link>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={onLogout}
                disabled={logoutPending}
                className="cursor-pointer text-rose-600 focus:bg-rose-50 focus:text-rose-600"
              >
                {logoutPending ? "Logging out..." : "Log out"}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  );
}
