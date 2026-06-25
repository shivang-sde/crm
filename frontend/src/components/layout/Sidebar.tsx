"use client";

import React from "react";
import Link from "next/link";
import { X } from "lucide-react";
import type { NavItem } from "@/lib/constants/navigation";

export interface SidebarProps {
  tenantName?: string;
  roleLabel?: string;
  navigationItems: NavItem[];
  activePathname: string;
  isOpen: boolean;
  onClose: () => void;
}

export function Sidebar({
  tenantName,
  roleLabel,
  navigationItems,
  activePathname,
  isOpen,
  onClose,
}: SidebarProps) {
  return (
    <>
      <div
        className={`fixed inset-0 z-20 bg-black/40 transition-opacity md:hidden ${
          isOpen ? "opacity-100 pointer-events-auto" : "opacity-0 pointer-events-none"
        }`}
        onClick={onClose}
      />

      <aside
        className={`fixed inset-y-0 left-0 z-30 w-64 transform overflow-y-auto border-r bg-white transition-transform duration-200 md:relative md:translate-x-0 md:h-screen ${
          isOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="flex items-center justify-between border-b p-6 md:hidden">
          <div>
            <h2 className="text-xl font-bold text-gray-800">{tenantName || "CRM"}</h2>
            {roleLabel && (
              <p className="text-xs text-gray-500 capitalize">{roleLabel}</p>
            )}
          </div>
          <button
            type="button"
            className="rounded-md p-2 text-gray-600 hover:bg-gray-100"
            onClick={onClose}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="hidden border-b p-6 md:block">
          <h2 className="text-2xl font-bold text-gray-800">{tenantName || "CRM"}</h2>
          {roleLabel && (
            <span className="mt-1 block text-xs text-gray-500 capitalize">
              {roleLabel}
            </span>
          )}
        </div>

        <nav className="flex-1 p-4 space-y-1">
          {navigationItems.map((item) => {
            const Icon = item.icon;
            const isActive =
              activePathname === item.href || activePathname.startsWith(item.href + "/");

            return (
              <Link
                key={item.name}
                href={item.href}
                className={`flex items-center gap-3 px-4 py-2.5 rounded-md text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-blue-50 text-blue-700"
                    : "text-gray-600 hover:bg-gray-100 hover:text-gray-900"
                }`}
                onClick={onClose}
              >
                <Icon className="w-5 h-5" />
                {item.name}
              </Link>
            );
          })}
        </nav>
      </aside>
    </>
  );
}
