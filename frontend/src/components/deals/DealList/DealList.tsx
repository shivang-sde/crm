"use client";

import React, { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { useDeals, useDealStages } from "@/lib/hooks/deals";
import { userApi } from "@/lib/api/users";
import { useQuery } from "@tanstack/react-query";
import { DealFilters, DealFilterState } from "./DealFilters";
import { DealTable } from "./DealTable";

const defaultFilters: DealFilterState = { stageId: "all", ownerId: "all", search: "" };

export function DealList() {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<DealFilterState>(defaultFilters);
  const [debouncedSearch, setDebouncedSearch] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(filters.search), 400);
    return () => clearTimeout(timer);
  }, [filters.search]);

  useEffect(() => setPage(0), [debouncedSearch, filters.stageId, filters.ownerId]);

  const { data: stages } = useDealStages();
  const { data: usersData } = useQuery({ queryKey: ["users", "deal-filters"], queryFn: () => userApi.getUsers({ page: 0, isActive: true }) });

  const { data: dealsResult, isLoading } = useDeals({ page, size: 20, search: debouncedSearch || undefined, stage: filters.stageId !== "all" ? filters.stageId : undefined, owner: filters.ownerId !== "all" ? filters.ownerId : undefined });

  const deals = dealsResult?.data ?? [];
  const meta = dealsResult?.meta;

  return (
    <div className="space-y-4">
      <DealFilters filters={filters} stages={stages} users={usersData?.content} onFiltersChange={setFilters} />

      <div className="bg-white rounded-lg border overflow-hidden">
        {isLoading ? (
          <div className="h-24 flex items-center justify-center text-muted-foreground">Loading deals...</div>
        ) : deals.length === 0 ? (
          <div className="h-24 flex items-center justify-center text-muted-foreground">No deals found.</div>
        ) : (
          <DealTable deals={deals} />
        )}

        {meta && meta.totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t">
            <div className="text-sm text-muted-foreground">Page {meta.page + 1} of {meta.totalPages} ({meta.total} deals)</div>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>Previous</Button>
              <Button variant="outline" size="sm" onClick={() => setPage((p) => p + 1)} disabled={page >= meta.totalPages - 1}>Next</Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
