"use client";

import Link from "next/link";
import { ArrowLeft, List, Plus, Settings } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { DealKanban } from "@/components/deals/DealKanban/DealKanban";
import { usePermissions } from "@/lib/hooks/usePermissions";

function DealKanbanPageContent() {
  const { canEditDeals } = usePermissions();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" asChild>
            <Link href="/deals">
              <ArrowLeft className="mr-2 h-4 w-4" />
              List view
            </Link>
          </Button>
          <h1 className="text-2xl font-bold tracking-tight">Deal Pipeline</h1>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link href="/deals">
              <List className="mr-2 h-4 w-4" />
              Table
            </Link>
          </Button>
          {canEditDeals && (
            <Button variant="outline" size="sm" asChild>
              <Link href="/deals/settings">
                <Settings className="mr-2 h-4 w-4" />
                Settings
              </Link>
            </Button>
          )}
          {canEditDeals && (
            <Button asChild>
              <Link href="/deals/new">
                <Plus className="mr-2 h-4 w-4" />
                New Deal
              </Link>
            </Button>
          )}
        </div>
      </div>
      <DealKanban />
    </div>
  );
}

export default function DealKanbanPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "deal", action: "read" }}>
      <DealKanbanPageContent />
    </ProtectedRoute>
  );
}
