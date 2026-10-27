import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDateTime } from "@/lib/utils";
import {
  riskSignalSeverityLabel,
  riskSignalSourceHref,
  riskSignalSourceLabel,
  riskSignalTypeLabel,
  type RiskSignalResponse,
} from "@/types/customer-health";

function shortId(value: string): string {
  return value.length > 8 ? value.slice(0, 8) : value;
}

function SeverityBadge({ severity }: { severity: string }) {
  if (severity === "HIGH") {
    return <Badge variant="destructive">{riskSignalSeverityLabel(severity)}</Badge>;
  }

  if (severity === "MEDIUM") {
    return (
      <Badge
        variant="secondary"
        className="border-transparent bg-amber-100 text-amber-900"
      >
        {riskSignalSeverityLabel(severity)}
      </Badge>
    );
  }

  return <Badge variant="outline">{riskSignalSeverityLabel(severity)}</Badge>;
}

function SourceCell({ signal }: { signal: RiskSignalResponse }) {
  const label = riskSignalSourceLabel(signal.sourceType);
  const href = riskSignalSourceHref(signal.sourceType, signal.sourceId);

  return (
    <div className="flex flex-col gap-0.5">
      {href ? (
        <Link
          href={href}
          className="text-primary hover:underline"
          title={signal.sourceId}
        >
          {label}
        </Link>
      ) : (
        <span title={signal.sourceId}>{label}</span>
      )}
      <span className="font-mono text-[11px] text-muted-foreground">
        {shortId(signal.sourceId)}
      </span>
    </div>
  );
}

interface RiskSignalTableProps {
  signals: RiskSignalResponse[];
  showAccount?: boolean;
}

export function RiskSignalTable({
  signals,
  showAccount = false,
}: RiskSignalTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Signal</TableHead>
          <TableHead>Severity</TableHead>
          <TableHead>Reason</TableHead>
          <TableHead className="hidden md:table-cell">Detected</TableHead>
          <TableHead>Source</TableHead>
          {showAccount && <TableHead>Account</TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {signals.map((signal) => (
          <TableRow
            key={`${signal.accountId ?? "unknown"}:${signal.type}:${signal.sourceType}:${signal.sourceId}`}
          >
            <TableCell className="font-medium whitespace-normal">
              {riskSignalTypeLabel(signal.type)}
            </TableCell>
            <TableCell>
              <SeverityBadge severity={signal.severity} />
            </TableCell>
            <TableCell className="max-w-md whitespace-normal">
              {signal.reason || "No explanation was recorded."}
            </TableCell>
            <TableCell className="hidden whitespace-nowrap text-sm text-muted-foreground md:table-cell">
              {formatDateTime(signal.detectedAt)}
            </TableCell>
            <TableCell>
              <SourceCell signal={signal} />
            </TableCell>
            {showAccount && (
              <TableCell>
                {signal.accountId ? (
                  <Link
                    href={`/accounts/${signal.accountId}`}
                    className="font-mono text-xs text-primary hover:underline"
                    title={signal.accountId}
                  >
                    {shortId(signal.accountId)}
                  </Link>
                ) : (
                  <span className="text-muted-foreground">Unavailable</span>
                )}
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
