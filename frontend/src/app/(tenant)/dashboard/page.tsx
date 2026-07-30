import Link from "next/link";
import React from "react";

export default function TenantDashboard() {
  return (
    <div className="p-6 space-y-4">
      <h1 className="text-2xl font-bold tracking-tight">Tenant Dashboard</h1>
      <p className="text-sm text-gray-500">Welcome to the tenant dashboard.</p>

 <div>
                <h1 className="text-2xl font-semibold">Dashboard</h1>
                <p className="text-sm text-muted-foreground">Manage calling integrations and tenant-specific user settings.</p>
            </div>

            <div className="rounded-lg border bg-white p-6 shadow-sm">
                <h2 className="text-lg font-medium">Calling configuration</h2>
                <p className="mt-2 text-sm text-muted-foreground">Configure providers, webhook endpoints, and call opening behavior from one place.</p>
                <Link href="/settings/calling" className="mt-4 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-white">
                    Open settings
                </Link>
            </div>

    </div>
  );
}
