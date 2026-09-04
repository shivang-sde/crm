"use client";

import { HttpConnectionsManager } from "@/components/workflow/HttpConnectionsManager";
import { SettingsLayout } from "@/components/settings/SettingsLayout";

export default function HttpConnectionsSettingsPage() {
  return (
    <SettingsLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold">Outbound HTTP connections</h1>
          <p className="text-sm text-muted-foreground">
            Manage credential references for the HTTP API workflow action.
          </p>
        </div>
        <HttpConnectionsManager />
      </div>
    </SettingsLayout>
  );
}