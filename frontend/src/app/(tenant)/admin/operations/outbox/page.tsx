"use client";

import Link from "next/link";
import { ArrowLeft, PackageX } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { OutboxDeadEventTable } from "@/components/operations/OutboxDeadEventTable";

function DeadOutboxContent() {
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/admin/operations/rabbitmq">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to operations
          </Link>
        </Button>
        <div className="flex items-center gap-2">
          <PackageX className="h-6 w-6 text-muted-foreground" />
          <h1 className="text-2xl font-bold tracking-tight">Terminal Outbox Events</h1>
        </div>
      </div>

      <p className="max-w-3xl text-sm text-muted-foreground">
        CRM events whose publish attempt budget was exhausted. Nothing replays them automatically: the
        outbox publisher only selects events in the retryable state, so these stay parked until you
        requeue one. Attempt counts, the last error and the failed-at timestamp are preserved across a
        requeue.
      </p>

      <OutboxDeadEventTable />
    </div>
  );
}

export default function OperatorDeadOutboxPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "admin", action: "operations_read" }}>
      <DeadOutboxContent />
    </ProtectedRoute>
  );
}
