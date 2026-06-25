"use client";

import Link from "next/link";
import { Plus } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { AccountList } from "@/components/accounts/AccountList/AccountList";
import { usePermissions } from "@/lib/hooks/usePermissions";

function AccountsPageContent() {
  const { canEditAccounts } = usePermissions();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <h1 className="text-2xl font-bold tracking-tight">Accounts</h1>
        <div>
          {canEditAccounts && (
            <Button asChild>
              <Link href="/accounts/new">
                <Plus className="mr-2 h-4 w-4" />
                New Account
              </Link>
            </Button>
          )}
        </div>
      </div>
      <AccountList />
    </div>
  );
}

export default function AccountsPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "account", action: "read" }}>
      <AccountsPageContent />
    </ProtectedRoute>
  );
}
