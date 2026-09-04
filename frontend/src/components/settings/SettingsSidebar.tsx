"use client";

import React from "react";
import Link from "next/link";
import { User, Briefcase, Settings, ChevronRight, Phone, Globe, Database, Target, Package, Key } from "lucide-react";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { SettingsNavItem } from "./SettingsNavItem";
import { SettingsSection } from "./SettingsSection";

interface SettingsSidebarProps {
  pathname: string;
}

interface SettingsNavItemConfig {
  href: string;
  label: string;
  description: string;
  permission?: { module: string; action: string };
}

const MY_SETTINGS_ITEMS: SettingsNavItemConfig[] = [
  { href: "/settings/profile", label: "Profile", description: "Manage your name, profile information and personal settings." },
  { href: "/settings/preferences", label: "Preferences", description: "Manage your personal CRM preferences." },
  { href: "/settings/calling", label: "Calling", description: "Configure your personal calling credentials and provider agent identity.", permission: { module: "call", action: "read" } },
];

const WORKSPACE_ITEMS: SettingsNavItemConfig[] = [
  { href: "/settings/http-connections", label: "HTTP Connections", description: "Manage outbound HTTP connections used by integrations and workflows.", permission: { module: "workflow", action: "read" } },
  { href: "/settings/http-credentials", label: "HTTP Credentials", description: "Manage encrypted credentials for generic HTTP workflows (workspace and per-user).", permission: { module: "workflow", action: "read" } },
  { href: "/settings/demo-data", label: "Demo Workspace", description: "Populate this tenant with realistic sample CRM data.", permission: { module: "tenant", action: "write" } },
];

const ADMIN_ITEMS: SettingsNavItemConfig[] = [
  { href: "/admin/settings", label: "Organization", description: "Manage organization-level calling configuration.", permission: { module: "admin", action: "settings" } },
  { href: "/leads/settings", label: "Lead Settings", description: "Configure lead statuses, sources, and custom fields.", permission: { module: "lead", action: "write" } },
  { href: "/deals/settings", label: "Deal Settings", description: "Configure deal stages and custom fields.", permission: { module: "deal", action: "write" } },
  { href: "/users", label: "Users & Roles", description: "Manage users, roles and access.", permission: { module: "admin", action: "user_manage" } },
  { href: "/roles", label: "Permissions", description: "Configure role permissions and access control.", permission: { module: "admin", action: "role_manage" } },
];

export function SettingsSidebar({ pathname }: SettingsSidebarProps) {
  const { hasPermission } = usePermissions();

  const isActive = (href: string) => pathname === href || pathname.startsWith(`${href}/`);

  const mySettingsItems = MY_SETTINGS_ITEMS
    .filter((item) => !item.permission || hasPermission(item.permission.module, item.permission.action))
    .map((item) => (
      <SettingsNavItem
        key={item.href}
        href={item.href}
        label={item.label}
        description={item.description}
        icon={User}
        isActive={isActive(item.href)}
      />
    ));

  const workspaceItems = WORKSPACE_ITEMS
    .filter((item) => !item.permission || hasPermission(item.permission.module, item.permission.action))
    .map((item) => {
      let icon = Briefcase;
      if (item.href === "/settings/http-connections") icon = Globe;
      if (item.href === "/settings/http-credentials") icon = Key;
      if (item.href === "/settings/demo-data") icon = Database;
      return (
        <SettingsNavItem
          key={item.href}
          href={item.href}
          label={item.label}
          description={item.description}
          icon={icon}
          isActive={isActive(item.href)}
        />
      );
    });

  const adminItems = ADMIN_ITEMS
    .filter((item) => !item.permission || hasPermission(item.permission.module, item.permission.action))
    .map((item) => {
      let icon = Settings;
      if (item.href === "/leads/settings") icon = Target;
      if (item.href === "/deals/settings") icon = Package;
      if (item.href === "/users") icon = Settings;
      if (item.href === "/roles") icon = Settings;
      return (
        <SettingsNavItem
          key={item.href}
          href={item.href}
          label={item.label}
          description={item.description}
          icon={icon}
          isActive={isActive(item.href)}
        />
      );
    });

  const hasWorkspaceItems = workspaceItems.length > 0;
  const hasAdminItems = adminItems.length > 0;

  return (
    <aside className="hidden lg:block w-64 shrink-0 border-r bg-white">
      <nav className="flex h-full flex-col p-4" aria-label="Settings navigation">
        <div className="space-y-1">
          <SettingsSection title="My Settings" />
          {mySettingsItems}
        </div>

        {hasWorkspaceItems && (
          <div className="mt-6 space-y-1">
            <SettingsSection title="Workspace" />
            {workspaceItems}
          </div>
        )}

        {hasAdminItems && (
          <div className="mt-6 space-y-1">
            <SettingsSection title="Administration" />
            {adminItems}
          </div>
        )}

        {!hasWorkspaceItems && !hasAdminItems && (
          <div className="mt-6 space-y-1">
            <SettingsSection title="Administration" />
            <p className="px-3 py-2 text-xs text-muted-foreground">
              No administrative settings available for your role.
            </p>
          </div>
        )}
      </nav>
    </aside>
  );
}