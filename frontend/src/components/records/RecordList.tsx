"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Loader2, Database, Search, ArrowUpDown } from "lucide-react";
import { useQueries } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { useRecords, useRecordFields, useDisplayConfig } from "@/lib/hooks/records";
import { RecordFieldResponse } from "@/types/records";
import { FieldValueRenderer } from "./FieldValueRenderer";
import { leadApi } from "@/lib/api/leads";
import { contactApi } from "@/lib/api/contacts";
import { accountApi } from "@/lib/api/accounts";
import { dealApi } from "@/lib/api/deals";

interface Props {
  recordTypeId: string;
}

function getLeadLabel(l: any): string {
  const full = `${l.firstName ?? ""} ${l.lastName ?? ""}`.trim();
  return full || l.company || l.email || l.phone || l.id;
}
function getContactLabel(c: any): string {
  const full = `${c.firstName ?? ""} ${c.lastName ?? ""}`.trim();
  return full || c.email || c.phone || c.id;
}
function getAccountLabel(a: any): string {
  return a.name || a.website || a.id;
}
function getDealLabel(d: any): string {
  return d.name || d.id;
}

export function RecordList({ recordTypeId }: Props) {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [sort, setSort] = useState("createdAt");
  const [direction, setDirection] = useState("desc");
  const size = 20;

  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);

  // Reset page when search/sort changes
  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, sort, direction, recordTypeId]);

  const { data: fields } = useRecordFields(recordTypeId);
  const { data: display, isLoading: displayLoading } = useDisplayConfig(recordTypeId);
  const { data: result, isLoading, isError, error } = useRecords({
    recordTypeId,
    page,
    size,
    search: debouncedSearch || undefined,
    sort,
    direction,
  });

  const records = result?.data ?? [];
  const meta = result?.meta;

  const fieldById = new Map<string, RecordFieldResponse>((fields || []).map((f) => [f.id, f]));

  let columns: RecordFieldResponse[];
  const visibleFieldIds: string[] = display?.list?.columns?.map((c) => c.fieldId).filter((id) => fieldById.has(id)) ?? [];
  if (visibleFieldIds.length > 0) {
    columns = visibleFieldIds.map((id) => fieldById.get(id)!).filter(Boolean);
  } else if (fields) {
    columns = [...fields].filter((f) => f.isActive).sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0));
  } else {
    columns = [];
  }
  const displayColumns = columns.slice(0, 6);

  // Collect unique reference IDs per entity type for current page
  const referenceIds = useMemo(() => {
    const map: Record<string, Set<string>> = {};
    for (const col of displayColumns) {
      if (col.fieldType !== "REFERENCE") continue;
      const type = (col.referenceEntityType || "").toUpperCase();
      if (!["LEAD", "CONTACT", "ACCOUNT", "DEAL"].includes(type)) continue;
      if (!map[type]) map[type] = new Set();
      for (const rec of records) {
        const v = rec.data?.[col.fieldKey];
        if (typeof v === "string" && /^[0-9a-fA-F-]{36}$/.test(v.trim())) {
          map[type].add(v.trim());
        }
      }
    }
    return map;
  }, [records, displayColumns]);

  const leadIds = useMemo(() => Array.from(referenceIds["LEAD"] ?? []), [referenceIds]);
  const contactIds = useMemo(() => Array.from(referenceIds["CONTACT"] ?? []), [referenceIds]);
  const accountIds = useMemo(() => Array.from(referenceIds["ACCOUNT"] ?? []), [referenceIds]);
  const dealIds = useMemo(() => Array.from(referenceIds["DEAL"] ?? []), [referenceIds]);

  const leadQueries = useQueries({
    queries: leadIds.map((id) => ({
      queryKey: ["reference-label", "LEAD", id],
      queryFn: async () => {
        try {
          const l = await leadApi.getLead(id);
          return { id, label: getLeadLabel(l) };
        } catch {
          return { id, label: null };
        }
      },
      staleTime: 60_000,
    })),
  });
  const contactQueries = useQueries({
    queries: contactIds.map((id) => ({
      queryKey: ["reference-label", "CONTACT", id],
      queryFn: async () => {
        try {
          const c = await contactApi.getContact(id);
          return { id, label: getContactLabel(c) };
        } catch {
          return { id, label: null };
        }
      },
      staleTime: 60_000,
    })),
  });
  const accountQueries = useQueries({
    queries: accountIds.map((id) => ({
      queryKey: ["reference-label", "ACCOUNT", id],
      queryFn: async () => {
        try {
          const a = await accountApi.getAccount(id);
          return { id, label: getAccountLabel(a) };
        } catch {
          return { id, label: null };
        }
      },
      staleTime: 60_000,
    })),
  });
  const dealQueries = useQueries({
    queries: dealIds.map((id) => ({
      queryKey: ["reference-label", "DEAL", id],
      queryFn: async () => {
        try {
          const d = await dealApi.getDeal(id);
          return { id, label: getDealLabel(d) };
        } catch {
          return { id, label: null };
        }
      },
      staleTime: 60_000,
    })),
  });

  const labelMap = useMemo(() => {
    const m = new Map<string, string | null>();
    for (const q of [...leadQueries, ...contactQueries, ...accountQueries, ...dealQueries]) {
      const d = (q as any).data as { id: string; label: string | null } | undefined;
      if (d) m.set(d.id, d.label);
    }
    return m;
  }, [leadQueries, contactQueries, accountQueries, dealQueries]);

  if (isLoading || displayLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (isError) {
    return <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive">Failed to load records: {(error as any)?.response?.data?.error?.message || (error as Error).message}</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input placeholder="Search records…" value={search} onChange={(e) => setSearch(e.target.value)} className="pl-9" />
        </div>
        <div className="flex items-center gap-2">
          <Select value={sort} onValueChange={setSort}>
            <SelectTrigger className="w-[160px]">
              <SelectValue placeholder="Sort by" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="createdAt">Created</SelectItem>
              <SelectItem value="updatedAt">Updated</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" onClick={() => setDirection((d) => (d === "asc" ? "desc" : "asc"))} className="gap-1">
            <ArrowUpDown className="h-4 w-4" /> {direction === "asc" ? "Oldest" : "Newest"}
          </Button>
        </div>
      </div>

      <div className="rounded-xl border bg-white overflow-hidden shadow-sm">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40">
              {displayColumns.map((f) => (
                <TableHead key={f.id}>{f.fieldLabel}</TableHead>
              ))}
              <TableHead>Created</TableHead>
              <TableHead className="w-[160px] text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {records.length === 0 ? (
              <TableRow>
                <TableCell colSpan={displayColumns.length + 2} className="h-48">
                  <div className="flex flex-col items-center justify-center gap-3 py-6 text-center">
                    <div className="rounded-full bg-muted p-4">
                      <Database className="h-6 w-6 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="font-medium">{debouncedSearch ? "No matching records" : "No records yet"}</p>
                      <p className="text-sm text-muted-foreground">{debouncedSearch ? `No records match "${debouncedSearch}"` : "Create the first record for this type."}</p>
                    </div>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              records.map((rec) => (
                <TableRow key={rec.id} className="hover:bg-muted/20">
                  {displayColumns.map((f) => {
                    const raw = rec.data?.[f.fieldKey];
                    if (f.fieldType === "REFERENCE") {
                      const isUuid = typeof raw === "string" && /^[0-9a-fA-F-]{36}$/.test(String(raw).trim());
                      if (!isUuid) return <TableCell key={f.id} className="max-w-[200px]"><FieldValueRenderer field={f} value={raw} variant="list" /></TableCell>;
                      const id = String(raw).trim();
                      const label = labelMap.get(id);
                      // label === null means not found/deleted, undefined means still loading
                      if (label === undefined) {
                        return <TableCell key={f.id} className="max-w-[200px]"><span className="inline-flex items-center gap-1.5 font-mono text-xs text-muted-foreground"><Loader2 className="h-3 w-3 animate-spin" />{id.slice(0, 8)}…</span></TableCell>;
                      }
                      if (label === null) {
                        return <TableCell key={f.id} className="max-w-[200px]"><span className="inline-flex items-center gap-1.5 font-mono text-xs"><Badge variant="outline" className="text-[10px] px-1 py-0">{f.referenceEntityType || "REF"}</Badge><span className="truncate max-w-[120px]" title={id}>{id.slice(0, 8)}…</span></span></TableCell>;
                      }
                      return <TableCell key={f.id} className="max-w-[200px]"><span className="inline-flex items-center gap-1.5 text-sm"><Badge variant="outline" className="text-[10px] px-1 py-0">{f.referenceEntityType || "REF"}</Badge><span className="truncate max-w-[160px]" title={id}>{label}</span></span></TableCell>;
                    }
                    return <TableCell key={f.id} className="max-w-[200px]"><FieldValueRenderer field={f} value={raw} variant="list" /></TableCell>;
                  })}
                  <TableCell className="text-xs text-muted-foreground">{new Date(rec.createdAt).toLocaleDateString()}</TableCell>
                  <TableCell className="text-right">
                    <Button asChild variant="ghost" size="sm">
                      <Link href={`/records/${rec.id}`}>View</Link>
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {meta && meta.totalPages > 1 && (
        <div className="flex items-center justify-between px-2">
          <div className="text-sm text-muted-foreground">
            Page {meta.page + 1} of {meta.totalPages} ({meta.total} records)
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
              Previous
            </Button>
            <Button variant="outline" size="sm" onClick={() => setPage((p) => p + 1)} disabled={page >= meta.totalPages - 1}>
              Next
            </Button>
          </div>
        </div>
      )}

      {records.length > 0 && <p className="text-xs text-muted-foreground px-2">Showing {displayColumns.length} of {columns.length} configured columns. Configure in Settings → Record Types → Display.</p>}
    </div>
  );
}
