"use client";

import { useParams, useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { DealForm } from "@/components/deals/DealForm/DealForm";
import { useDeal } from "@/lib/hooks/deals";

function EditDealPageContent() {
  const router = useRouter();
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === "string" ? rawId : rawId?.[0];
  const { data: deal, isLoading, isError } = useDeal(id);

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (isError || !deal) {
    return (
      <div className="text-center py-24 text-muted-foreground">
        Deal not found.
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Edit Deal</h1>
      <DealForm
        initialData={deal}
        onSuccess={(updated) => router.push(`/deals/${updated.id}`)}
      />
    </div>
  );
}

export default function EditDealPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "deal", action: "write" }}>
      <EditDealPageContent />
    </ProtectedRoute>
  );
}
