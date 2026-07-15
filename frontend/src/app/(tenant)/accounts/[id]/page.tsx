"use client";

import { useParams } from "next/navigation";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { useAccount } from "@/lib/hooks/accounts";
import { AccountDetail } from "@/components/accounts/AccountDetail/AccountDetail";

function AccountDetailPageContent() {
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === "string" ? rawId : rawId?.[0];
  const { data: account, isLoading } = useAccount(id);

  if (isLoading) {
    return <div className="py-12 text-center text-muted-foreground">Loading account...</div>;
  }

  if (!account) {
    return <div className="py-12 text-center text-muted-foreground">Account not found.</div>;
  }

  return <AccountDetail account={account} />;
}

export default function AccountDetailPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "account", action: "read" }}>
      <AccountDetailPageContent />
    </ProtectedRoute>
  );
}
