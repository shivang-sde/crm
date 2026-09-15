"use client";

import Link from "next/link";
import { ArrowLeft, Webhook } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SettingsLayout } from "@/components/settings/SettingsLayout";
import { WebhooksAdmin } from "@/components/records/WebhooksAdmin";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";

function RecordWebhooksContent() {
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
            <Webhook className="h-6 w-6 text-muted-foreground" />
            <h1 className="text-2xl font-bold tracking-tight">Incoming Webhooks</h1>
          </div>
        </div>
        <p className="text-sm text-muted-foreground max-w-2xl">
          Control plane for incoming Record webhooks. Each webhook defines which Record Type receives data and which Mapping Profile translates the external payload. Incoming HTTP ingestion is not yet active – this is configuration only. Endpoint preview: <span className="font-mono">/api/v1/records/webhooks/&#123;webhookKey&#125;</span>
        </p>
        <WebhooksAdmin />
      </div>
    </SettingsLayout>
  );
}

export default function RecordWebhooksPage() {
  return (
    <ProtectedRoute>
      <RecordWebhooksContent />
    </ProtectedRoute>
  );
}
