"use client";

import Link from "next/link";
import { ArrowLeft, Rabbit } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { DeadLetterQueueTable } from "@/components/operations/DeadLetterQueueTable";
import { usePermissions } from "@/lib/hooks/usePermissions";

function DeadLettersContent() {
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/admin/operations/outbox">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to operations
          </Link>
        </Button>
        <div className="flex items-center gap-2">
          <Rabbit className="h-6 w-6 text-muted-foreground" />
          <h1 className="text-2xl font-bold tracking-tight">RabbitMQ Dead Letters</h1>
        </div>
      </div>

      <p className="max-w-3xl text-sm text-muted-foreground">
        Messages that a bounded consumer retry could not deliver. Reading this list does not consume,
        re-publish or remove anything. A message is republished only when you re-drive it, and the
        original stays in the queue so a failed re-drive loses nothing.
      </p>

      <DeadLetterQueueTable />
    </div>
  );
}

export default function OperatorDeadLettersPage() {
  const { hasPermission } = usePermissions();
  return (
    <ProtectedRoute requiredPermission={{ module: "admin", action: "operations_read" }}>
      {hasPermission("admin", "operations_read") ? <DeadLettersContent /> : null}
    </ProtectedRoute>
  );
}
