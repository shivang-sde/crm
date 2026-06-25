"use client";

import Link from "next/link";
import { Plus, LayoutGrid, Settings } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { LeadList } from "@/components/leads/LeadList/LeadList";
import { usePermissions } from "@/lib/hooks/usePermissions";

function LeadsPageContent() {
  const { canEditLeads } = usePermissions();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <h1 className="text-2xl font-bold tracking-tight">Leads</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link href="/leads/kanban">
              <LayoutGrid className="mr-2 h-4 w-4" />
              Kanban
            </Link>
          </Button>
          {canEditLeads && (
            <Button variant="outline" size="sm" asChild>
              <Link href="/leads/settings">
                <Settings className="mr-2 h-4 w-4" />
                Settings
              </Link>
            </Button>
          )}
          {canEditLeads && (
            <Button asChild>
              <Link href="/leads/new">
                <Plus className="mr-2 h-4 w-4" />
                New Lead
              </Link>
            </Button>
          )}
        </div>
      </div>
      <LeadList />
    </div>
  );
}

export default function LeadsPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "lead", action: "read" }}>
      <LeadsPageContent />
    </ProtectedRoute>
  );
}
