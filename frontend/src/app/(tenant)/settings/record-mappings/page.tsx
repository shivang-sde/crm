"use client";

import Link from "next/link";
import { ArrowLeft, Settings2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SettingsLayout } from "@/components/settings/SettingsLayout";
import { MappingProfilesAdmin } from "@/components/records/MappingProfilesAdmin";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";

function RecordMappingsContent() {
  return (
    <SettingsLayout>
      <div className="space-y-6">
        <div className="flex flex-wrap items-center gap-4">
          <Button variant="ghost" size="sm" asChild>
            <Link href="/settings">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back to settings
            </Link>
          </Button>
          <div className="flex items-center gap-2">
            <Settings2 className="h-6 w-6 text-muted-foreground" />
            <h1 className="text-2xl font-bold tracking-tight">Mapping Profiles</h1>
          </div>
        </div>
        <p className="text-sm text-muted-foreground max-w-2xl">
          Define how external payloads map to your canonical Record Types. Each profile targets one Record Type and can be reused by future webhooks. DIRECT = payload already uses canonical keys; CUSTOM = explicit source path → field mappings. Mapping is independent from transport and workflows.
        </p>
        <MappingProfilesAdmin />
      </div>
    </SettingsLayout>
  );
}

export default function RecordMappingsPage() {
  return (
    <ProtectedRoute>
      <RecordMappingsContent />
    </ProtectedRoute>
  );
}
