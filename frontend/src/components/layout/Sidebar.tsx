"use client";

import React from "react";
import Link from "next/link";
import { X, ChevronLeft, ChevronRight, LogOut, Building2, ShieldCheck } from "lucide-react";
import type { NavItem } from "@/lib/constants/navigation";

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export interface SidebarProps {
  tenantName?: string;
  roleLabel?: string;
  navigationItems: NavItem[];
  activePathname: string;
  isOpen: boolean;         
  onClose: () => void;     
  isCollapsed: boolean;    
  setIsCollapsed: (collapsed: boolean) => void;
  user?: { name: string; email: string } | null;
  onLogout: () => void;
  isLoggingOut?: boolean;
}

export function Sidebar({
  tenantName,
  roleLabel,
  navigationItems,
  activePathname,
  isOpen,
  onClose,
  isCollapsed,
  setIsCollapsed,
  user,
  onLogout,
  isLoggingOut = false,
}: SidebarProps) {
  const asideRef = React.useRef<HTMLElement>(null);
  const closeButtonRef = React.useRef<HTMLButtonElement>(null);

  React.useEffect(() => {
    if (!isOpen) return;

    const previouslyFocused = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    const mobileQuery = window.matchMedia("(max-width: 767px)");

    document.body.style.overflow = "hidden";
    closeButtonRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
      }
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      if (mobileQuery.matches) {
        previouslyFocused?.focus();
      }
    };
  }, [isOpen, onClose]);

  const handleTabTrap = (event: React.KeyboardEvent<HTMLElement>) => {
    if (event.key !== "Tab" || !isOpen) return;

    const focusable = asideRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
    if (!focusable?.length) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  return (
    <>
      {/* 1. Mobile Backdrop Overlay */}
      <div
        className={`fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-sm transition-opacity duration-300 md:hidden ${
          isOpen ? "pointer-events-auto opacity-100" : "pointer-events-none opacity-0"
        }`}
        onClick={onClose}
        aria-hidden="true"
      />

      {/* 2. Primary Structural Sidebar Container */}
      <aside
        ref={asideRef}
        id="mobile-navigation"
        onKeyDown={handleTabTrap}
        className={`
          fixed inset-y-0 left-0 z-50 flex h-dvh w-[min(18rem,85vw)] flex-col border-r border-slate-200 bg-slate-900 pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)] text-slate-400
          transition-all duration-300 ease-in-out md:sticky md:top-0 md:z-30 md:h-screen md:pt-0
          ${isOpen ? "visible translate-x-0" : "invisible -translate-x-full md:visible md:translate-x-0"}
          ${isCollapsed ? "md:w-20" : "md:w-64"}
        `}
        role={isOpen ? "dialog" : undefined}
        aria-modal={isOpen ? true : undefined}
        aria-label="Main Navigation"
      >
        {/* 3. Header Section (Context & Identity) */}
        <div className="flex h-16 shrink-0 items-center justify-between border-b border-slate-800 px-4">
          <div className={`flex items-center gap-3 overflow-hidden transition-all ${isCollapsed ? "justify-center w-full" : ""}`}>
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-600 text-white">
              <Building2 className="h-5 w-5" />
            </div>
            {!isCollapsed && (
              <div className="flex flex-col truncate">
                <span className="text-sm font-semibold text-white truncate">
                  {tenantName || "Enterprise CRM"}
                </span>
                {roleLabel && (
                  <span className="flex items-center gap-1 text-[10px] font-medium tracking-wider uppercase text-indigo-400">
                    <ShieldCheck className="h-3 w-3" />
                    {roleLabel.toLowerCase()}
                  </span>
                )}
              </div>
            )}
          </div>

          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-slate-400 hover:bg-slate-800 hover:text-white md:hidden"
            aria-label="Close Mobile Menu"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* 4. Actionable Desktop Column Toggle */}
        <button
          type="button"
          onClick={() => setIsCollapsed(!isCollapsed)}
          className="absolute -right-3 top-20 hidden h-6 w-6 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-600 shadow-sm transition-transform hover:bg-slate-50 md:flex z-50"
          aria-label={isCollapsed ? "Expand navigation sidebar" : "Collapse navigation sidebar"}
        >
          {isCollapsed ? <ChevronRight className="h-3 w-3" /> : <ChevronLeft className="h-3 w-3" />}
        </button>

        {/* 5. Gated Scrollable Navigation Element Container */}
        <nav className="flex-1 space-y-1 overflow-y-auto overscroll-contain px-3 py-4 custom-scrollbar">
          {navigationItems
            .filter((item) => item.show) // FIX: Safeguard against hidden navigation states
            .map((item) => {
              const Icon = item.icon;
              const isActive = activePathname === item.href || activePathname.startsWith(`${item.href}/`);

              return (
                <Link
                  key={item.name}
                  href={item.href}
                  onClick={onClose}
                  className={`group relative flex min-h-11 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-200 ${
                    isActive
                      ? "bg-indigo-600 text-white shadow-sm shadow-indigo-600/10"
                      : "hover:bg-slate-800 hover:text-slate-200"
                  }`}
                  title={isCollapsed ? item.name : undefined}
                >
                  <Icon className={`h-5 w-5 shrink-0 transition-transform group-hover:scale-105 ${isActive ? "text-white" : "text-slate-400 group-hover:text-slate-200"}`} />
                  <span className={`whitespace-nowrap transition-opacity duration-200 ${isCollapsed ? "opacity-0 w-0 pointer-events-none hidden" : "opacity-100"}`}>
                    {item.name}
                  </span>
                </Link>
              );
            })}
        </nav>

        {/* 6. User Account Context Footer Panel */}
        <div className="shrink-0 border-t border-slate-800 bg-slate-950/40 p-4">
          <div className={`flex items-center justify-between gap-3 ${isCollapsed ? "flex-col" : ""}`}>
            {!isCollapsed && user && (
              <div className="flex flex-col min-w-0 flex-1">
                <span className="text-sm font-medium text-slate-200 truncate">{user.name}</span>
                <span className="text-xs text-slate-500 truncate">{user.email}</span>
              </div>
            )}
            <button
              type="button"
              onClick={onLogout}
              disabled={isLoggingOut}
              className="flex h-11 w-11 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-rose-500/10 hover:text-rose-400 disabled:opacity-50"
              aria-label="Log Out"
              title="Log Out"
            >
              <LogOut className="h-5 w-5" />
            </button>
          </div>
        </div>
      </aside>
    </>
  );
}
