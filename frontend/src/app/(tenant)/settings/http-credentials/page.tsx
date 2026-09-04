"use client";

import { GenericCredentialsManager } from "@/components/workflow/GenericCredentialsManager";
import { SettingsLayout } from "@/components/settings/SettingsLayout";

export default function HttpCredentialsSettingsPage() {
  return (
    <SettingsLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold">HTTP credentials</h1>
          <p className="text-sm text-muted-foreground">
            Manage encrypted credentials for generic HTTP API workflows. Use workspace or per-user credentials and reference them as {"{{credential.*}}"} in HTTP nodes.
          </p>
        </div>
        <GenericCredentialsManager />
      </div>
    </SettingsLayout>
  );
}
