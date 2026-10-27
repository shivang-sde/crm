"use client";

import { useMemo, useState } from "react";

import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { RiskSignalCoverageNotice } from "@/components/customer-health/RiskSignalCoverageNotice";
import { RiskSignalTable } from "@/components/customer-health/RiskSignalTable";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { apiErrorMessage } from "@/lib/api/api-utils";
import { useRiskSignals } from "@/lib/hooks/customer-health";
import {
  RISK_SIGNAL_TYPE_LABELS,
  type RiskSignalSeverity,
  type RiskSignalType,
} from "@/types/customer-health";

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

interface Filters {
  page: number;
  size: number;
  type: RiskSignalType | "";
  severity: RiskSignalSeverity | "";
}

const INITIAL_FILTERS: Filters = {
  page: 0,
  size: 20,
  type: "",
  severity: "",
};

function CustomerHealthPageContent() {
  const [filters, setFilters] = useState<Filters>(INITIAL_FILTERS);

  const requestParams = useMemo(
    () => ({
      page: filters.page,
      size: filters.size,
      type: filters.type || undefined,
      severity: filters.severity || undefined,
    }),
    [filters.page, filters.size, filters.type, filters.severity],
  );

  const { data, isLoading, isError, error, refetch } = useRiskSignals(requestParams);

  const signals = data?.data ?? [];
  const meta = data?.meta;
  const hasFilters = Boolean(filters.type || filters.severity);

  const updateFilters = (changes: Partial<Filters>) => {
    setFilters((current) => ({ ...current, ...changes }));
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Customer Health</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Evidence-based risk signals from existing CRM records. These are
          individual deterministic signals, never a composite health score.
        </p>
      </div>

      <RiskSignalCoverageNotice />

      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
        <Select
          value={filters.type || "ALL"}
          onValueChange={(value) =>
            updateFilters({
              type: value === "ALL" ? "" : (value as RiskSignalType),
              page: 0,
            })
          }
        >
          <SelectTrigger className="w-full sm:w-64" aria-label="Signal type">
            <SelectValue placeholder="All signal types" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All signal types</SelectItem>
            {(Object.entries(RISK_SIGNAL_TYPE_LABELS) as [RiskSignalType, string][]).map(
              ([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ),
            )}
          </SelectContent>
        </Select>

        <Select
          value={filters.severity || "ALL"}
          onValueChange={(value) =>
            updateFilters({
              severity: value === "ALL" ? "" : (value as RiskSignalSeverity),
              page: 0,
            })
          }
        >
          <SelectTrigger className="w-full sm:w-44" aria-label="Severity">
            <SelectValue placeholder="All severities" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All severities</SelectItem>
            <SelectItem value="HIGH">High</SelectItem>
            <SelectItem value="MEDIUM">Medium</SelectItem>
          </SelectContent>
        </Select>

        <Select
          value={String(filters.size)}
          onValueChange={(value) =>
            updateFilters({ size: Number(value), page: 0 })
          }
        >
          <SelectTrigger className="w-full sm:w-36" aria-label="Page size">
            <SelectValue placeholder="Page size" />
          </SelectTrigger>
          <SelectContent>
            {PAGE_SIZE_OPTIONS.map((size) => (
              <SelectItem key={size} value={String(size)}>
                {size} per page
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {hasFilters && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => updateFilters({ type: "", severity: "", page: 0 })}
          >
            Clear filters
          </Button>
        )}
      </div>

      <div className="overflow-hidden rounded-lg border">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b bg-muted px-4 py-3">
          <p className="text-sm font-medium text-muted-foreground">Risk signals</p>
          {!isLoading && !isError && meta && (
            <p className="text-xs text-muted-foreground">
              {meta.total} {meta.total === 1 ? "record" : "records"}
            </p>
          )}
        </div>

        {isLoading ? (
          <div className="space-y-2 p-4">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : isError ? (
          <div className="space-y-3 p-4">
            <p className="text-sm text-destructive">
              {apiErrorMessage(error, "The risk signals could not be loaded.")}
            </p>
            <Button variant="outline" size="sm" onClick={() => void refetch()}>
              Retry
            </Button>
          </div>
        ) : signals.length > 0 ? (
          <RiskSignalTable signals={signals} showAccount />
        ) : (
          <div className="space-y-1 p-4">
            <p className="text-sm text-muted-foreground">
              {hasFilters
                ? "No risk signals match the current filters."
                : "No risk signals detected."}
            </p>
            <p className="text-xs text-muted-foreground">
              An empty result only means no deterministic signal rule currently
              applies. It is not a health assessment.
            </p>
          </div>
        )}

        {!isLoading && !isError && meta && (
          <div className="flex flex-wrap items-center justify-between gap-2 border-t px-4 py-3">
            <Button
              variant="outline"
              size="sm"
              disabled={meta.page <= 0}
              onClick={() => updateFilters({ page: Math.max(0, meta.page - 1) })}
            >
              Previous
            </Button>
            <span className="text-sm text-muted-foreground">
              Page {meta.page + 1} of {Math.max(meta.totalPages, 1)}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={meta.page + 1 >= meta.totalPages}
              onClick={() => updateFilters({ page: meta.page + 1 })}
            >
              Next
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

export default function CustomerHealthPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "account", action: "read" }}>
      <CustomerHealthPageContent />
    </ProtectedRoute>
  );
}
