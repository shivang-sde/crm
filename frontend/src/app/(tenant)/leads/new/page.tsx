"use client";

import { useRouter } from "next/navigation";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { LeadForm } from "@/components/leads/LeadForm/LeadForm";

function NewLeadPageContent() {
  const router = useRouter();

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Create New Lead</h1>
      <LeadForm onSuccess={(lead) => router.push(`/leads/${lead.id}`)} />
    </div>
  );
}

export default function NewLeadPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "lead", action: "write" }}>
      <NewLeadPageContent />
    </ProtectedRoute>
  );
}
