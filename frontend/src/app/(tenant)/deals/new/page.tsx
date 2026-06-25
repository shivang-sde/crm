"use client";

import { useRouter } from "next/navigation";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { DealForm } from "@/components/deals/DealForm/DealForm";

function NewDealPageContent() {
  const router = useRouter();

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Create New Deal</h1>
      <DealForm onSuccess={(deal) => router.push(`/deals/${deal.id}`)} />
    </div>
  );
}

export default function NewDealPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "deal", action: "write" }}>
      <NewDealPageContent />
    </ProtectedRoute>
  );
}
