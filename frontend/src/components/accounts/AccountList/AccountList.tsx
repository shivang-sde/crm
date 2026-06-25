"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAccounts } from "@/lib/hooks/accounts";
import { AccountTable } from "./AccountTable";

export function AccountList() {
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 400);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch]);

  const { data: accountsResult, isLoading } = useAccounts({
    page,
    size: 20,
    search: debouncedSearch || undefined,
  });

  const accounts = accountsResult?.data ?? [];
  const meta = accountsResult?.meta;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex-1">
          <Input
            placeholder="Search accounts..."
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        <Button asChild>
          <Link href="/accounts/new">Create account</Link>
        </Button>
      </div>

      <div className="bg-white rounded-lg border overflow-hidden">
        {isLoading ? (
          <div className="h-24 flex items-center justify-center text-muted-foreground">
            Loading accounts...
          </div>
        ) : accounts.length === 0 ? (
          <div className="h-24 flex items-center justify-center text-muted-foreground">
            No accounts found.
          </div>
        ) : (
          <AccountTable accounts={accounts} />
        )}

        {meta && meta.totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t">
            <div className="text-sm text-muted-foreground">
              Page {meta.page + 1} of {meta.totalPages} ({meta.total} accounts)
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((current) => current + 1)}
                disabled={meta ? page >= meta.totalPages - 1 : true}
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
