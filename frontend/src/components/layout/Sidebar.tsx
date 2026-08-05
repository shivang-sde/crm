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
      {/* Mobile overlay */}
      <div
        className={`fixed inset-0 z-20 bg-black/40 transition-opacity md:hidden ${
          isOpen
            ? "pointer-events-auto opacity-100"
            : "pointer-events-none opacity-0"
        }`}
        onClick={onClose}
      />

      <aside
        className={`
          fixed inset-y-0 left-0 z-30
          flex h-screen w-64 shrink-0 flex-col
          transform border-r bg-white
          transition-transform duration-200
          md:sticky md:top-0 md:translate-x-0
          ${
            isOpen
              ? "translate-x-0"
              : "-translate-x-full"
          }
        `}
      >
        {/* Mobile heading */}
        <div className="flex shrink-0 items-center justify-between border-b p-6 md:hidden">
          <div>
            <h2 className="text-xl font-bold text-gray-800">
              {tenantName || "CRM"}
            </h2>

            {roleLabel && (
              <p className="text-xs capitalize text-gray-500">
                {roleLabel}
              </p>
            )}
          </div>

          <button
            type="button"
            aria-label="Close sidebar"
            className="rounded-md p-2 text-gray-600 transition-colors hover:bg-gray-100"
            onClick={onClose}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Desktop heading */}
        <div className="hidden shrink-0 border-b p-6 md:block">
          <h2 className="text-2xl font-bold text-gray-800">
            {tenantName || "CRM"}
          </h2>

          {roleLabel && (
            <span className="mt-1 block text-xs capitalize text-gray-500">
              {roleLabel}
            </span>
          )}
        </div>

        {/* Only sidebar navigation scrolls */}
        <nav className="min-h-0 flex-1 space-y-1 overflow-y-auto p-4">
          {navigationItems.map((item) => {
            const Icon = item.icon;

            const isActive =
              activePathname === item.href ||
              activePathname.startsWith(`${item.href}/`);

            return (
              <Link
                key={item.name}
                href={item.href}
                className={`flex items-center gap-3 rounded-md px-4 py-2.5 text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-blue-50 text-blue-700"
                    : "text-gray-600 hover:bg-gray-100 hover:text-gray-900"
                }`}
                onClick={onClose}
              >
                <Icon className="h-5 w-5 shrink-0" />
                <span>{item.name}</span>
              </Link>
            );
          })}
        </nav>
      </aside>
    </>
  );
}