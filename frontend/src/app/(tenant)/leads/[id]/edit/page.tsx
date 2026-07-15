"use client";

import { useParams, useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { LeadForm } from "@/components/leads/LeadForm/LeadForm";
import { useLead } from "@/lib/hooks/leads";

function EditLeadPageContent() {
  const router = useRouter();
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === "string" ? rawId : rawId?.[0];
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

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Edit Lead</h1>
      <LeadForm
        initialData={lead}
        onSuccess={(updated) => router.push(`/leads/${updated.id}`)}
      />
    </div>
  );
}

export default function EditLeadPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "lead", action: "write" }}>
      <EditLeadPageContent />
    </ProtectedRoute>
  );
}
