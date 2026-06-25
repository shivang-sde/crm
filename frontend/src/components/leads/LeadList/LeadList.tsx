"use client";

import React, { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { useLeads, useLeadSources, useLeadStatuses } from "@/lib/hooks/leads";
import { userApi } from "@/lib/api/users";
import { useQuery } from "@tanstack/react-query";
import { LeadFilters, LeadFilterState } from "./LeadFilters";
import { LeadTable } from "./LeadTable";

const defaultFilters: LeadFilterState = {
  statusId: "all",
  sourceId: "all",
  ownerId: "all",
  converted: "all",
  search: "",
};

export function LeadList() {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<LeadFilterState>(defaultFilters);
  const [debouncedSearch, setDebouncedSearch] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(filters.search), 400);
    return () => clearTimeout(timer);
  }, [filters.search]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, filters.statusId, filters.sourceId, filters.ownerId, filters.converted]);

  const { data: statuses } = useLeadStatuses();
  const { data: sources } = useLeadSources();
  const { data: usersData } = useQuery({
    queryKey: ["users", "lead-filters"],
    queryFn: () => userApi.getUsers({ page: 0, isActive: true }),
  });

  const { data: leadsResult, isLoading } = useLeads({
    page,
    size: 20,
    search: debouncedSearch || undefined,
    status: filters.statusId !== "all" ? filters.statusId : undefined,
    source: filters.sourceId !== "all" ? filters.sourceId : undefined,
    owner: filters.ownerId !== "all" ? filters.ownerId : undefined,
    converted:
      filters.converted !== "all" ? filters.converted === "true" : undefined,
  });

  const leads = leadsResult?.data ?? [];
  const meta = leadsResult?.meta;

  return (
    <div className="space-y-4">
      <LeadFilters
        filters={filters}
        statuses={statuses}
        sources={sources}
        users={usersData?.content}
        onFiltersChange={setFilters}
      />

      <div className="bg-white rounded-lg border overflow-hidden">
        {isLoading ? (
          <div className="h-24 flex items-center justify-center text-muted-foreground">
            Loading leads...
          </div>
        ) : leads.length === 0 ? (
          <div className="h-24 flex items-center justify-center text-muted-foreground">
            No leads found.
          </div>
        ) : (
          <LeadTable leads={leads} />
        )}

        {meta && meta.totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t">
            <div className="text-sm text-muted-foreground">
              Page {meta.page + 1} of {meta.totalPages} ({meta.total} leads)
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => p + 1)}
                disabled={page >= meta.totalPages - 1}
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
