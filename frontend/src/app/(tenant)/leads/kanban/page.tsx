"use client";

import Link from "next/link";
import { ArrowLeft, List, Plus, Settings } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { LeadKanban } from "@/components/leads/LeadKanban/LeadKanban";
import { usePermissions } from "@/lib/hooks/usePermissions";

function LeadKanbanPageContent() {
  const { canEditLeads } = usePermissions();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" asChild>
            <Link href="/leads">
              <ArrowLeft className="mr-2 h-4 w-4" />
              List view
            </Link>
          </Button>
          <h1 className="text-2xl font-bold tracking-tight">Lead Pipeline</h1>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link href="/leads">
              <List className="mr-2 h-4 w-4" />
              Table
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
      <LeadKanban />
    </div>
  );
}

export default function LeadKanbanPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "lead", action: "read" }}>
      <LeadKanbanPageContent />
    </ProtectedRoute>
  );
}
