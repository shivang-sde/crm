"use client";

import { useRouter } from "next/navigation";
import { Plus } from "lucide-react";
import Link from "next/link";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { AccountForm } from "@/components/accounts/AccountForm/AccountForm";
import { usePermissions } from "@/lib/hooks/usePermissions";

function NewAccountPageContent() {
  const { canEditAccounts } = usePermissions();
  const router = useRouter();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">New Account</h1>
          <p className="text-sm text-muted-foreground">Create a new account record.</p>
        </div>
        <Button variant="outline" size="sm" asChild>
          <Link href="/accounts">
            <Plus className="mr-2 h-4 w-4" />
            Back to accounts
          </Link>
        </Button>
      </div>
      {canEditAccounts && (
        <AccountForm onSuccess={(account) => router.push(`/accounts/${account.id}`)} />
      )}
    </div>
  );
}

export default function NewAccountPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "account", action: "write" }}>
      <NewAccountPageContent />
    </ProtectedRoute>
  );
}
