"use client";

import Link from "next/link";
import { useQueries } from "@tanstack/react-query";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Loader2, ExternalLink, Users, Building2, Handshake, CheckSquare } from "lucide-react";
import { RecordFieldResponse } from "@/types/records";
import { leadApi } from "@/lib/api/leads";
import { contactApi } from "@/lib/api/contacts";
import { accountApi } from "@/lib/api/accounts";
import { dealApi } from "@/lib/api/deals";

interface Props {
  fields: RecordFieldResponse[];
  data: Record<string, unknown>;
}

const ENTITY_CONFIG: Record<string, { label: string; icon: React.ElementType; href: (id: string) => string; fields: string[] }> = {
  LEAD: { label: "Lead", icon: Users, href: (id) => `/leads/${id}`, fields: ["name", "status", "email"] },
  CONTACT: { label: "Contact", icon: Users, href: (id) => `/contacts/${id}`, fields: ["name", "email"] },
  ACCOUNT: { label: "Account", icon: Building2, href: (id) => `/accounts/${id}`, fields: ["name", "industry"] },
  DEAL: { label: "Deal", icon: Handshake, href: (id) => `/deals/${id}`, fields: ["name", "stage"] },
  TASK: { label: "Task", icon: CheckSquare, href: (id) => `/tasks/${id}`, fields: ["name"] },
  MEETING: { label: "Meeting", icon: CheckSquare, href: (id) => `/meetings/${id}`, fields: ["name"] },
  CALL: { label: "Call", icon: CheckSquare, href: (id) => `/calls/${id}`, fields: ["name"] },
};

function isUuid(v: unknown): boolean {
  return typeof v === "string" && /^[0-9a-fA-F-]{36}$/.test(v.trim());
}

export function RelatedRecordsPanel({ fields, data }: Props) {
  const referenceFields = fields.filter((f) => f.fieldType === "REFERENCE" && f.referenceEntityType && f.isActive);

  const refs = referenceFields
    .map((f) => {
      const raw = data[f.fieldKey];
      const id = typeof raw === "string" ? raw.trim() : raw ? String(raw).trim() : "";
      const refType = f.referenceEntityType!.trim().toUpperCase();
      const config = ENTITY_CONFIG[refType];
      // Only show supported types that have actual resolvable backend (LEAD/CONTACT/ACCOUNT/DEAL supported)
      const isSupported = !!config && ["LEAD", "CONTACT", "ACCOUNT", "DEAL"].includes(refType);
      return { field: f, id, refType, config, isSupported };
    })
    .filter((r) => r.id && isUuid(r.id));

  // If no reference fields configured, don't render section
  if (referenceFields.length === 0) return null;

  // If no values set, show empty state but still indicate no related records
  const hasValues = refs.length > 0;

  const queries = useQueries({
    queries: refs.map((r) => ({
      queryKey: ["related", r.refType, r.id],
      queryFn: async () => {
        try {
          if (r.refType === "LEAD") return await leadApi.getLead(r.id);
          if (r.refType === "CONTACT") return await contactApi.getContact(r.id);
          if (r.refType === "ACCOUNT") return await accountApi.getAccount(r.id);
          if (r.refType === "DEAL") return await dealApi.getDeal(r.id);
          // TASK/MEETING/CALL deferred
          return null;
        } catch {
          return null;
        }
      },
      enabled: !!r.id && r.isSupported,
      staleTime: 60_000,
    })),
  });

  return (
    <Card className="shadow-sm border-indigo-100">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-semibold text-indigo-700 flex items-center gap-2">
          Related Records
          <Badge variant="secondary" className="text-[10px]">One-hop explicit</Badge>
        </CardTitle>
        <p className="text-xs text-muted-foreground">Declared via Record Type → Reference fields. Tenant-scoped, one-hop only.</p>
      </CardHeader>
      <CardContent>
        {!hasValues ? (
          <p className="text-sm text-muted-foreground">No related records linked for this record. Fill the Reference fields to link a Lead, Contact, Account or Deal.</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {refs.map((r, idx) => {
              const q = queries[idx];
              const isLoading = q.isLoading;
              const data = q.data as any;
              const Icon = r.config?.icon ?? Users;
              return (
                <div key={r.field.id} className="rounded-lg border bg-muted/10 p-3 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-muted-foreground flex items-center gap-1.5">
                      <Icon className="h-3.5 w-3.5" /> {r.field.fieldLabel} <Badge variant="outline" className="text-[10px] px-1 py-0">{r.refType}</Badge>
                    </span>
                    {!r.isSupported && <Badge variant="outline" className="text-[10px] border-amber-200 text-amber-700">Deferred</Badge>}
                  </div>
                  {!r.isSupported ? (
                    <p className="text-xs text-muted-foreground font-mono truncate" title={r.id}>
                      {r.id.slice(0, 8)}… (unsupported type – backend will ignore)
                    </p>
                  ) : isLoading ? (
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <Loader2 className="h-3 w-3 animate-spin" /> Loading {r.config.label}…
                    </div>
                  ) : !data ? (
                    <div className="space-y-1">
                      <p className="text-xs font-mono truncate" title={r.id}>{r.id.slice(0, 8)}…</p>
                      <p className="text-xs text-amber-700">Related record unavailable (deleted or no access).</p>
                    </div>
                  ) : (
                    <div className="space-y-1">
                      <Link href={r.config.href(r.id)} className="text-sm font-medium text-indigo-600 hover:underline flex items-center gap-1">
                        {(data as any).name || (data as any).fullName || (data as any).title || r.id.slice(0, 8)}
                        <ExternalLink className="h-3 w-3" />
                      </Link>
                      <div className="text-xs text-muted-foreground space-y-0.5">
                        {(data as any).status && <div>Status: {(data as any).status}</div>}
                        {(data as any).stage && <div>Stage: {(data as any).stage}</div>}
                        {(data as any).email && <div>Email: {(data as any).email}</div>}
                        {(data as any).industry && <div>Industry: {(data as any).industry}</div>}
                        <div className="font-mono text-[11px]">ID: {r.id.slice(0, 8)}…</div>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
