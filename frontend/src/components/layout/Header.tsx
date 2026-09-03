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
    <header className="w-full bg-white px-4 py-3 md:px-6">
      <div className="mx-auto flex flex-row items-center justify-between gap-4">
        {/* Left Side: Mobile Menu Button & Breadcrumbs */}
        <div className="flex items-center gap-4 min-w-0">
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden shrink-0"
            onClick={onToggleSidebar}
            aria-label="Toggle navigation menu"
          >
            <Menu className="h-5 w-5" />
          </Button>

          {breadcrumbs.length > 0 && (
            <nav className="hidden sm:flex items-center gap-2 overflow-x-auto text-sm text-slate-500 whitespace-nowrap">
              {breadcrumbs.map((crumb, index) => (
                <React.Fragment key={crumb.href}>
                  {index < breadcrumbs.length - 1 ? (
                    <Link href={crumb.href} className="hover:text-slate-900 transition-colors">
                      {crumb.label}
                    </Link>
                  ) : (
                    <span className="font-medium text-slate-900 truncate max-w-[200px]">
                      {crumb.label}
                    </span>
                  )}
                  {index < breadcrumbs.length - 1 && (
                    <ChevronRight className="h-4 w-4 text-slate-400 shrink-0" />
                  )}
                </React.Fragment>
              ))}
            </nav>
          )}
        </div>

        {/* Right Side: Notification Bell & Profile Settings Dropdown */}
        <div className="flex items-center gap-4 shrink-0">
          <NotificationBell />

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="relative h-9 w-9 rounded-full bg-slate-100 p-0 hover:bg-slate-200">
                <UserIcon className="h-5 w-5 text-slate-600" />
                <span className="sr-only">Toggle user menu</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-48">
              <div className="px-2 py-1.5 text-xs text-slate-400">
                Logged in as <span className="font-semibold text-slate-700 block truncate">{userName}</span>
                {roleName && <span className="text-[10px] text-indigo-600 font-medium tracking-wider uppercase block mt-0.5">{roleName}</span>}
              </div>
              <DropdownMenuSeparator />
              <DropdownMenuItem asChild>
                <Link href="/settings" className="w-full cursor-pointer">Settings</Link>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem 
                onClick={onLogout} 
                disabled={logoutPending}
                className="text-rose-600 focus:text-rose-600 cursor-pointer focus:bg-rose-50"
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
