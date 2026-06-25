"use client";

import { useParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { LeadDetail } from "@/components/leads/LeadDetail/LeadDetail";
import { useLead } from "@/lib/hooks/leads";

function LeadDetailPageContent() {
  const params = useParams();
  const id = typeof params.id === "string" ? params.id : params.id?.[0];
  const { data: lead, isLoading, isError } = useLead(id);

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (isError || !lead) {
    return (
      <div className="text-center py-24 text-muted-foreground">
        Lead not found.
      </div>
    );
  }

  return <LeadDetail lead={lead} />;
}

export default function LeadDetailPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "lead", action: "read" }}>
      <LeadDetailPageContent />
    </ProtectedRoute>
  );
}
