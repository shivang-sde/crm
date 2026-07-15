"use client";

import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { AccountForm } from "@/components/accounts/AccountForm/AccountForm";
import { useAccount } from "@/lib/hooks/accounts";
import { usePermissions } from "@/lib/hooks/usePermissions";

function EditAccountPageContent() {
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === "string" ? rawId : rawId?.[0];
  const router = useRouter();
  const { data: account, isLoading } = useAccount(id);
  const { canEditAccounts } = usePermissions();

  if (isLoading) {
    return <div className="py-12 text-center text-muted-foreground">Loading account...</div>;
  }

  if (!account) {
    return <div className="py-12 text-center text-muted-foreground">Account not found.</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Edit Account</h1>
          <p className="text-sm text-muted-foreground">Update account details.</p>
        </div>
        <Button variant="outline" size="sm" asChild>
          <Link href={`/accounts/${account.id}`}>
            Back to account
          </Link>
        </Button>
      </div>
      {canEditAccounts && (
        <AccountForm initialData={account} onSuccess={(updated) => router.push(`/accounts/${updated.id}`)} />
      )}
    </div>
  );
}

export default function EditAccountPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "account", action: "write" }}>
      <EditAccountPageContent />
    </ProtectedRoute>
  );
}
