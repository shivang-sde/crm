"use client";

import Link from "next/link";
import { Plus, LayoutGrid, Settings } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { DealList } from "@/components/deals/DealList/DealList";
import { usePermissions } from "@/lib/hooks/usePermissions";

function DealsPageContent() {
  const { canEditDeals } = usePermissions();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <h1 className="text-2xl font-bold tracking-tight">Deals</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link href="/deals/kanban">
              <LayoutGrid className="mr-2 h-4 w-4" />
              Kanban
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
      <DealList />
    </div>
  );
}

export default function DealsPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "deal", action: "read" }}>
      <DealsPageContent />
    </ProtectedRoute>
  );
}
