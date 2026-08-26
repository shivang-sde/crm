"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Plus } from "lucide-react";

import { useAccounts } from "@/lib/hooks/accounts";
import { usePermissions } from "@/lib/hooks/usePermissions";
import type { AccountResponse } from "@/types/accounts";

import { Button } from "@/components/ui/button";
import {
  AccountDataTable,
  type AccountFilters,
} from "./AccountDataTable";

const INITIAL_FILTERS: AccountFilters = {
  page: 0,
  size: 20,
  sort: "createdAt,desc",
  search: "",
};

export function AccountList() {
  const router = useRouter();
  const { canEditAccounts } = usePermissions();

  const [filters, setFilters] =
    useState<AccountFilters>(INITIAL_FILTERS);

  const [debouncedSearch, setDebouncedSearch] = useState("");

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedSearch(filters.search.trim());
    }, 400);

    return () => window.clearTimeout(timer);
  }, [filters.search]);

  const requestParams = useMemo(
    () => ({
      page: filters.page,
      size: filters.size,
      sort: filters.sort,
      search: debouncedSearch || undefined,
    }),
    [
      debouncedSearch,
      filters.page,
      filters.size,
      filters.sort,
    ],
  );

  const {
    data: accountsResult,
    isLoading,
    isFetching,
  } = useAccounts(requestParams);

  const accounts = accountsResult?.data ?? [];
  const meta = accountsResult?.meta;

  const totalElements = meta?.total ?? 0;
  const totalPages = meta?.totalPages ?? 0;

  const handleFiltersChange = (
    nextFilters: AccountFilters,
  ) => {
    setFilters(nextFilters);
  };

  const handleViewAccount = (
    account: AccountResponse,
  ) => {
    router.push(`/accounts/${account.id}`);
  };

  const handleEditAccount = (
    account: AccountResponse,
  ) => {
    router.push(`/accounts/${account.id}/edit`);
  };

  const handleDeleteAccount = (
    account: AccountResponse,
  ) => {
    /*
     * Connect this to your delete confirmation dialog and
     * useDeleteAccount mutation when available.
     */
    console.log("Delete account:", account.id);
  };

  return (
    <div className="space-y-6">

      <AccountDataTable
        data={accounts}
        isLoading={isLoading || isFetching}
        filters={filters}
        onFiltersChange={handleFiltersChange}
        totalElements={totalElements}
        totalPages={totalPages}
        canEdit={canEditAccounts}
        canDelete={false}
        onView={handleViewAccount}
        onEdit={handleEditAccount}
        onDelete={handleDeleteAccount}
      />
    </div>
  );
}