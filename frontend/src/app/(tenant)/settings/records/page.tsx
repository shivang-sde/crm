"use client";

import Link from "next/link";
import { ArrowLeft, Database } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SettingsLayout } from "@/components/settings/SettingsLayout";
import { RecordTypesAdmin } from "@/components/records/RecordTypesAdmin";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";

function RecordsSettingsContent() {
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
            <Database className="h-6 w-6 text-muted-foreground" />
            <h1 className="text-2xl font-bold tracking-tight">Record Types</h1>
          </div>
        </div>
        <p className="text-sm text-muted-foreground max-w-2xl">
          Define your canonical business schemas. Each Record Type is a tenant-owned collection of fields – for example, CDR, Payment, or Shipment – that powers future records, mappings and workflows. You control the fields; the platform stays generic.
        </p>
        <RecordTypesAdmin />
      </div>
    </SettingsLayout>
  );
}

export default function RecordsSettingsPage() {
  return (
    <ProtectedRoute>
      <RecordsSettingsContent />
    </ProtectedRoute>
  );
}
