"use client";

import { usePermissions } from "@/lib/hooks/usePermissions";
import Link from "next/link";

export default function SettingPage() {
  const { hasPermission } = usePermissions();
  // Capability gates use existing catalog permissions, mirroring what each
  // destination page itself requires — never role names.
  const canInstallDemoData = hasPermission("tenant", "write");
  const canManageCallingAdminSettings = hasPermission("admin", "settings");
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="text-sm text-muted-foreground">Manage your settings</p>
      </div>

      <div className="rounded-lg border bg-white p-6 shadow-sm">
        <h2 className="text-lg font-medium">Calling configuration</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Configure username, password of your calling providor
        </p>
        <Link
          href="/settings/calling"
          className="mt-4 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-white"
        >
          Open settings
        </Link>
      </div>

      <div className="rounded-lg border bg-white p-6 shadow-sm">
        <h2 className="text-lg font-medium">Outbound HTTP connections</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Manage credential references used by HTTP API workflow actions.
        </p>
        <Link
          href="/settings/http-connections"
          className="mt-4 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-white"
        >
          Open settings
        </Link>
      </div>

      {canInstallDemoData && (
        <>
          <div className="rounded-lg border bg-white p-6 shadow-sm">
            <h2 className="text-lg font-medium">Demo Data</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Setup demo data for your account.
            </p>
            <Link
              href="/settings/demo-data"
              className="mt-4 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-white"
            >
              Open settings
            </Link>
          </div>

          <div className="rounded-lg border bg-white p-6 shadow-sm">
            <h2 className="text-lg font-medium">Calling configuration (admin)</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Configure providers, webhook endpoints, and call opening behavior
              from one place.
            </p>
            {canManageCallingAdminSettings && (
              <Link
                href="/admin/settings"
                className="mt-4 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-white"
              >
                Open settings
              </Link>
            )}
          </div>
        </>
      )}
    </div>
  );
}
