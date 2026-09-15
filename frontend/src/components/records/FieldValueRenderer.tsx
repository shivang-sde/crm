"use client";

import { Badge } from "@/components/ui/badge";
import { RecordFieldResponse } from "@/types/records";

interface Props {
  field: RecordFieldResponse;
  value: unknown;
  variant?: "list" | "detail";
}

function formatDate(value: string): string {
  try {
    const d = new Date(value);
    if (isNaN(d.getTime())) return value;
    return d.toLocaleDateString();
  } catch {
    return value;
  }
}
function formatDateTime(value: string): string {
  try {
    const d = new Date(value);
    if (isNaN(d.getTime())) return value;
    return d.toLocaleString();
  } catch {
    return value;
  }
}

export function FieldValueRenderer({ field, value, variant = "list" }: Props) {
  if (value === null || value === undefined || value === "") {
    return <span className="text-muted-foreground/60">—</span>;
  }

  const type = field.fieldType;

  // REFERENCE fields store UUID strings; show truncated UUID with type badge, detail variant will be enhanced by RelatedRecordsPanel
  if (type === "REFERENCE") {
    const uuid = String(value);
    const isUuid = /^[0-9a-fA-F-]{36}$/.test(uuid.trim());
    if (!isUuid) return <span className="font-mono text-xs truncate max-w-[240px] block">{uuid}</span>;
    const shortId = uuid.slice(0, 8);
    return (
      <span className="inline-flex items-center gap-1.5 font-mono text-xs">
        <Badge variant="outline" className="text-[10px] px-1 py-0">{field.referenceEntityType || "REF"}</Badge>
        <span className="truncate max-w-[160px]" title={uuid}>{shortId}…</span>
      </span>
    );
  }

  switch (type) {
    case "TEXT":
      return <span className="truncate">{String(value)}</span>;
    case "LONG_TEXT":
      return variant === "list" ? (
        <span className="truncate max-w-[240px] block">{String(value).slice(0, 80)}{String(value).length > 80 ? "…" : ""}</span>
      ) : (
        <span className="whitespace-pre-wrap">{String(value)}</span>
      );
    case "INTEGER":
    case "DECIMAL":
      return <span className="font-mono text-sm">{String(value)}</span>;
    case "BOOLEAN": {
      const b = value === true || value === "true" || value === "TRUE";
      return <Badge variant={b ? "secondary" : "outline"} className={b ? "bg-emerald-50 text-emerald-700 border-emerald-200" : ""}>{b ? "Yes" : "No"}</Badge>;
    }
    case "DATE":
      return <span>{formatDate(String(value))}</span>;
    case "DATETIME":
      return <span>{formatDateTime(String(value))}</span>;
    case "ENUM":
      return <Badge variant="outline">{String(value)}</Badge>;
    case "URL": {
      const url = String(value);
      const safe = url.startsWith("http://") || url.startsWith("https://");
      if (!safe) return <span className="truncate">{url}</span>;
      return (
        <a href={url} target="_blank" rel="noopener noreferrer" className="text-indigo-600 hover:underline truncate block max-w-[240px]">
          {url}
        </a>
      );
    }
    case "PHONE":
      return <span className="font-mono text-sm">{String(value)}</span>;
    case "EMAIL": {
      const email = String(value);
      return (
        <a href={`mailto:${email}`} className="text-indigo-600 hover:underline truncate block max-w-[240px]">
          {email}
        </a>
      );
    }
    case "JSON":
      try {
        const pretty = typeof value === "string" ? JSON.stringify(JSON.parse(value as string), null, 2) : JSON.stringify(value, null, 2);
        return variant === "list" ? (
          <span className="font-mono text-xs truncate max-w-[240px] block">{pretty.slice(0, 80)}{pretty.length > 80 ? "…" : ""}</span>
        ) : (
          <pre className="text-xs bg-muted/50 rounded p-2 overflow-auto max-h-64 font-mono">{pretty}</pre>
        );
      } catch {
        return <span className="font-mono text-xs">{String(value)}</span>;
      }
    default:
      return <span>{String(value)}</span>;
  }
}
