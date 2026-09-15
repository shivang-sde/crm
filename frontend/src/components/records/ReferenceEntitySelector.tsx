"use client";

import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronsUpDown, Check, Loader2, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command";
import { cn } from "@/lib/utils";

import { leadApi } from "@/lib/api/leads";
import { contactApi } from "@/lib/api/contacts";
import { accountApi } from "@/lib/api/accounts";
import { dealApi } from "@/lib/api/deals";

type EntityOption = {
  value: string;
  label: string;
  secondary?: string;
};

function getLeadOption(l: { id: string; firstName: string; lastName?: string; company?: string; email?: string; phone?: string }): EntityOption {
  const full = `${l.firstName ?? ""} ${l.lastName ?? ""}`.trim();
  const label = full || l.company || l.email || l.phone || l.id;
  const secondary = l.company ? `${l.company}${l.email ? ` • ${l.email}` : l.phone ? ` • ${l.phone}` : ""}` : l.email || l.phone || undefined;
  return { value: l.id, label, secondary };
}
function getContactOption(c: { id: string; firstName?: string; lastName?: string; email?: string; phone?: string; accountId?: string }): EntityOption {
  const full = `${c.firstName ?? ""} ${c.lastName ?? ""}`.trim();
  const label = full || c.email || c.phone || c.id;
  const secondary = c.email || c.phone || undefined;
  return { value: c.id, label, secondary };
}
function getAccountOption(a: { id: string; name: string; industry?: string; phone?: string; website?: string }): EntityOption {
  const label = a.name || a.website || a.id;
  const secondary = a.industry ? `${a.industry}${a.phone ? ` • ${a.phone}` : ""}` : a.phone || a.website || undefined;
  return { value: a.id, label, secondary };
}
function getDealOption(d: { id: string; name: string; amount?: number | null; stage?: { name?: string } }): EntityOption {
  const label = d.name || d.id;
  const secondary = d.amount != null ? `$${d.amount}${d.stage?.name ? ` • ${d.stage.name}` : ""}` : d.stage?.name;
  return { value: d.id, label, secondary };
}

interface Props {
  value: string | null | undefined;
  onChange: (value: string | null) => void;
  referenceEntityType: string;
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
}

const ENTITY_PLACEHOLDERS: Record<string, string> = {
  LEAD: "Search leads…",
  CONTACT: "Search contacts…",
  ACCOUNT: "Search accounts…",
  DEAL: "Search deals…",
};

const ENTITY_LABELS: Record<string, string> = {
  LEAD: "Lead",
  CONTACT: "Contact",
  ACCOUNT: "Account",
  DEAL: "Deal",
};

export function ReferenceEntitySelector({ value, onChange, referenceEntityType, placeholder, disabled, required }: Props) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");

  useEffect(() => {
    const t = setTimeout(() => setDebounced(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  const entityType = (referenceEntityType || "").toUpperCase();

  // Fetch selected entity label
  const selectedQuery = useQuery({
    queryKey: ["reference-entity", entityType, value],
    queryFn: async (): Promise<EntityOption | null> => {
      if (!value) return null;
      const trimmed = String(value).trim();
      if (!/^[0-9a-fA-F-]{36}$/.test(trimmed)) return null;
      try {
        if (entityType === "LEAD") {
          const l = await leadApi.getLead(trimmed);
          return getLeadOption(l as any);
        }
        if (entityType === "CONTACT") {
          const c = await contactApi.getContact(trimmed);
          return getContactOption(c as any);
        }
        if (entityType === "ACCOUNT") {
          const a = await accountApi.getAccount(trimmed);
          return getAccountOption(a as any);
        }
        if (entityType === "DEAL") {
          const d = await dealApi.getDeal(trimmed);
          return getDealOption(d as any);
        }
      } catch {
        return null;
      }
      return null;
    },
    enabled: !!value && /^[0-9a-fA-F-]{36}$/.test(String(value).trim()) && ["LEAD", "CONTACT", "ACCOUNT", "DEAL"].includes(entityType),
  });

  const searchQuery = useQuery({
    queryKey: ["reference-search", entityType, debounced],
    queryFn: async (): Promise<EntityOption[]> => {
      const q = debounced.trim();
      try {
        if (entityType === "LEAD") {
          const res = await leadApi.listLeads({ search: q || undefined, page: 0, size: 20 });
          const list = (res as any).data ?? res;
          const arr = Array.isArray(list) ? list : (list as any).data ?? [];
          return arr.slice(0, 20).map((l: any) => getLeadOption(l));
        }
        if (entityType === "CONTACT") {
          const res = await contactApi.listContacts({ search: q || undefined, page: 0, size: 20 });
          const list = (res as any).data ?? res;
          const arr = Array.isArray(list) ? list : (list as any).data ?? [];
          return arr.slice(0, 20).map((c: any) => getContactOption(c));
        }
        if (entityType === "ACCOUNT") {
          const res = await accountApi.listAccounts({ search: q || undefined, page: 0, size: 20 });
          const list = (res as any).data ?? res;
          const arr = Array.isArray(list) ? list : (list as any).data ?? [];
          return arr.slice(0, 20).map((a: any) => getAccountOption(a));
        }
        if (entityType === "DEAL") {
          const res = await dealApi.listDeals({ search: q || undefined, page: 0, size: 20 } as any);
          const list = (res as any).data ?? res;
          const arr = Array.isArray(list) ? list : (list as any).data ?? [];
          return arr.slice(0, 20).map((d: any) => getDealOption(d));
        }
      } catch {
        return [];
      }
      return [];
    },
    enabled: open && ["LEAD", "CONTACT", "ACCOUNT", "DEAL"].includes(entityType),
  });

  const isSupported = ["LEAD", "CONTACT", "ACCOUNT", "DEAL"].includes(entityType);

  if (!isSupported) {
    return (
      <div className="space-y-1">
        <p className="text-xs text-muted-foreground">
          Reference to {entityType || "unknown"} is not yet searchable. Please enter UUID.
        </p>
        <div className="flex gap-2">
          <input
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value.trim() === "" ? null : e.target.value.trim())}
            placeholder={`UUID for ${entityType || "reference"}`}
            disabled={disabled}
            className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm font-mono shadow-sm"
          />
        </div>
      </div>
    );
  }

  const selected = selectedQuery.data;
  const displayLabel = selected?.label || (value ? String(value) : "");
  const isUnresolved = !!value && !selectedQuery.isLoading && !selected && !selectedQuery.isError ? true : false;
  // Actually selectedQuery returns null on 404; treat as unresolved
  const showUnresolved = !!value && selectedQuery.data === null && !selectedQuery.isLoading;

  return (
    <div className="space-y-1">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            role="combobox"
            aria-expanded={open}
            disabled={disabled}
            className={cn("w-full justify-between font-normal", !value && "text-muted-foreground")}
          >
            <span className="truncate text-left">
              {value ? (showUnresolved ? "Referenced record unavailable" : selected?.label ?? (selectedQuery.isLoading ? "Loading…" : String(value))) : placeholder || ENTITY_PLACEHOLDERS[entityType] || `Search ${ENTITY_LABELS[entityType] || entityType}…`}
            </span>
            <span className="ml-2 flex items-center gap-1 shrink-0">
              {selectedQuery.isLoading ? <Loader2 className="h-4 w-4 animate-spin opacity-50" /> : <ChevronsUpDown className="h-4 w-4 shrink-0 opacity-50" />}
            </span>
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
          <Command shouldFilter={false}>
            <CommandInput placeholder={placeholder || ENTITY_PLACEHOLDERS[entityType] || "Search…"} value={search} onValueChange={setSearch} />
            <CommandList>
              {searchQuery.isLoading ? (
                <div className="py-6 text-center text-sm text-muted-foreground flex items-center justify-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" /> Searching…
                </div>
              ) : null}
              {!searchQuery.isLoading && (searchQuery.data?.length ?? 0) === 0 ? <CommandEmpty>No results found.</CommandEmpty> : null}
              <CommandGroup>
                {searchQuery.data?.map((opt) => (
                  <CommandItem
                    key={opt.value}
                    value={opt.value}
                    onSelect={() => {
                      onChange(opt.value);
                      setOpen(false);
                    }}
                  >
                    <Check className={cn("mr-2 h-4 w-4", value === opt.value ? "opacity-100" : "opacity-0")} />
                    <div className="flex flex-col overflow-hidden">
                      <span className="truncate font-medium">{opt.label}</span>
                      {opt.secondary ? <span className="truncate text-xs text-muted-foreground">{opt.secondary}</span> : null}
                    </div>
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
      {value ? (
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground truncate">
            {showUnresolved ? (
              <span className="text-amber-600">Unavailable — stored UUID: <span className="font-mono">{String(value)}</span></span>
            ) : selected?.secondary ? (
              selected.secondary
            ) : selected ? (
              <span className="font-mono text-[11px]">{String(value)}</span>
            ) : null}
          </p>
          {!required ? (
            <Button variant="ghost" size="sm" className="h-6 px-2 text-xs" onClick={() => onChange(null)} disabled={disabled}>
              <X className="h-3 w-3 mr-1" /> Clear
            </Button>
          ) : null}
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">Tenant-scoped search; cross-tenant access is blocked.</p>
      )}
    </div>
  );
}
