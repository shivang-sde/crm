"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { RiskSignalCoverageNotice } from "@/components/customer-health/RiskSignalCoverageNotice";
import { RiskSignalTable } from "@/components/customer-health/RiskSignalTable";
import { apiErrorMessage } from "@/lib/api/api-utils";
import { useAccountRiskSignals } from "@/lib/hooks/customer-health";

interface AccountHealthPanelProps {
  accountId: string;
}

export function AccountHealthPanel({ accountId }: AccountHealthPanelProps) {
  const { data, isLoading, isError, error, refetch } = useAccountRiskSignals(accountId);
  const signals = data?.data ?? [];
  const total = data?.total ?? signals.length;

  return (
    <div className="overflow-hidden rounded-lg border">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b bg-muted px-4 py-3">
        <div>
          <p className="text-sm font-medium text-muted-foreground">
            Customer health
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            Evidence-based signals from existing CRM records. This is not a
            composite health score.
          </p>
        </div>
        {!isLoading && !isError && (
          <Badge variant="outline">
            {total} {total === 1 ? "signal" : "signals"}
          </Badge>
        )}
      </div>

      <RiskSignalCoverageNotice className="m-4 mb-0" />

      {isLoading ? (
        <div className="space-y-2 p-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : isError ? (
        <div className="space-y-3 p-4">
          <p className="text-sm text-destructive">
            {apiErrorMessage(error, "The customer health signals could not be loaded.")}
          </p>
          <Button variant="outline" size="sm" onClick={() => void refetch()}>
            Retry
          </Button>
        </div>
      ) : signals.length > 0 ? (
        <RiskSignalTable signals={signals} />
      ) : (
        <div className="space-y-1 p-4">
          <p className="text-sm text-muted-foreground">
            No risk signals currently detected for this account.
          </p>
          <p className="text-xs text-muted-foreground">
            An empty result only means no deterministic signal rule currently
            applies.
          </p>
        </div>
      )}
    </div>
  );
}
