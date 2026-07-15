"use client";

import { useParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { DealDetail } from "@/components/deals/DealDetail/DealDetail";
import { useDeal } from "@/lib/hooks/deals";

function DealDetailPageContent() {
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
    return <div className="text-center py-24 text-muted-foreground">Deal not found.</div>;
  }

  return <DealDetail deal={deal} />;
}

export default function DealDetailPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "deal", action: "read" }}>
      <DealDetailPageContent />
    </ProtectedRoute>
  );
}
