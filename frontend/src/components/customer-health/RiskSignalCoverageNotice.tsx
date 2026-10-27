"use client";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { usePermissions } from "@/lib/hooks/usePermissions";

const SOURCE_READ_PERMISSIONS = [
  { module: "feedback", label: "feedback" },
  { module: "ticket", label: "ticket" },
  { module: "deal", label: "deal" },
  { module: "entitlement", label: "entitlement" },
] as const;

export function RiskSignalCoverageNotice({ className }: { className?: string }) {
  const { hasPermission, permissionsLoaded } = usePermissions();

  if (!permissionsLoaded) {
    return null;
  }

  const missing = SOURCE_READ_PERMISSIONS.filter(
    ({ module }) => !hasPermission(module, "read"),
  );

  if (missing.length === 0) {
    return null;
  }

  const sources = missing.map(({ label }) => label).join(", ");
  const omitted =
    missing.length === 1
      ? "Signals from that source are omitted"
      : "Signals from those sources are omitted";

  return (
    <Alert
      className={className}
      data-testid="risk-signal-coverage-notice"
      variant="destructive"
    >
      <AlertTitle>Customer Health is partially evaluated</AlertTitle>
      <AlertDescription>
        Your role cannot read {sources}. {omitted}, so these results may be
        incomplete.
      </AlertDescription>
    </Alert>
  );
}
