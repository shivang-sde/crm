"use client";

import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronsUpDown, Check, Loader2, X, Database, Search } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

import { leadApi } from "@/lib/api/leads";
import { contactApi } from "@/lib/api/contacts";
import { accountApi } from "@/lib/api/accounts";
import { dealApi } from "@/lib/api/deals";
import { recordApi } from "@/lib/api/records";
import { recordTypeApi } from "@/lib/api/records";

type EntityOption = {
  value: string;
  label: string;
  secondary?: string;
};

type TargetEntity = "LEAD" | "CONTACT" | "ACCOUNT" | "DEAL" | "RECORD";

interface TargetEntityConfig {
  value: TargetEntity;
  label: string;
  placeholder: string;
  searchApi: (params: { search?: string; page: number; size: number }) => Promise<any>;
  getApi: (id: string) => Promise<any>;
  mapOption: (item: any) => EntityOption;
  supportsCurrent: boolean;
  recordTypeRequired?: boolean;
}

const TARGET_ENTITY_CONFIGS: Record<TargetEntity, TargetEntityConfig> = {
  LEAD: {
    value: "LEAD",
    label: "Lead",
    placeholder: "Search leads…",
    searchApi: (params) => leadApi.listLeads(params),
    getApi: (id) => leadApi.getLead(id),
    mapOption: (l) => ({
      value: l.id,
      label: `${l.firstName ?? ""} ${l.lastName ?? ""}`.trim() || l.company || l.email || l.phone || l.id,
      secondary: l.company ? `${l.company}${l.email ? ` • ${l.email}` : l.phone ? ` • ${l.phone}` : ""}` : l.email || l.phone,
    }),
    supportsCurrent: true,
  },
  CONTACT: {
    value: "CONTACT",
    label: "Contact",
    placeholder: "Search contacts…",
    searchApi: (params) => contactApi.listContacts(params),
    getApi: (id) => contactApi.getContact(id),
    mapOption: (c) => ({
      value: c.id,
      label: `${c.firstName ?? ""} ${c.lastName ?? ""}`.trim() || c.email || c.phone || c.id,
      secondary: c.email || c.phone,
    }),
    supportsCurrent: true,
  },
  ACCOUNT: {
    value: "ACCOUNT",
    label: "Account",
    placeholder: "Search accounts…",
    searchApi: (params) => accountApi.listAccounts(params),
    getApi: (id) => accountApi.getAccount(id),
    mapOption: (a) => ({
      value: a.id,
      label: a.name || a.website || a.id,
      secondary: a.industry ? `${a.industry}${a.phone ? ` • ${a.phone}` : ""}` : a.phone || a.website,
    }),
    supportsCurrent: true,
  },
  DEAL: {
    value: "DEAL",
    label: "Deal",
    placeholder: "Search deals…",
    searchApi: (params) => dealApi.listDeals(params as any),
    getApi: (id) => dealApi.getDeal(id),
    mapOption: (d) => ({
      value: d.id,
      label: d.name || d.id,
      secondary: d.amount != null ? `$${d.amount}${d.stage?.name ? ` • ${d.stage.name}` : ""}` : d.stage?.name,
    }),
    supportsCurrent: true,
  },
  RECORD: {
    value: "RECORD",
    label: "Record",
    placeholder: "Search records…",
    searchApi: (params) => recordApi.list(params),
    getApi: (id) => recordApi.get(id),
    mapOption: (r: any) => ({
      value: r.id,
      label: r.recordTypeName ? `${r.recordTypeName} • ${Object.values(r.data || {}).slice(0, 2).join(" ")}` : r.id,
      secondary: r.recordTypeKey,
    }),
    supportsCurrent: true,
    recordTypeRequired: true,
  },
};

const TARGET_ENTITIES: Array<{ value: TargetEntity; label: string }> = [
  { value: "LEAD", label: "Lead" },
  { value: "CONTACT", label: "Contact" },
  { value: "ACCOUNT", label: "Account" },
  { value: "DEAL", label: "Deal" },
  { value: "RECORD", label: "Record" },
];

interface EntityTargetSelectorProps {
  /** Current target entity type */
  targetEntity: TargetEntity;
  onTargetEntityChange: (entity: TargetEntity) => void;
  /** Current target record mode: "current" or "specific" */
  targetRecordMode: "current" | "specific";
  onTargetRecordModeChange: (mode: "current" | "specific") => void;
  /** Selected specific record ID */
  specificRecordId: string | null | undefined;
  onSpecificRecordIdChange: (id: string | null) => void;
  /** Current workflow trigger entity type (for "Current" mode) */
  triggerEntityType?: string;
  /** Whether the selector is disabled */
  disabled?: boolean;
  /** Optional label prefix for the record selector */
  recordLabel?: string;
  /** For Record type: selected record type ID */
  recordTypeId?: string;
  onRecordTypeIdChange?: (id: string | undefined) => void;
  /** For Record type: available record types */
  recordTypes?: Array<{ id: string; name: string; key: string }>;
}

export function EntityTargetSelector({
  targetEntity,
  onTargetEntityChange,
  targetRecordMode,
  onTargetRecordModeChange,
  specificRecordId,
  onSpecificRecordIdChange,
  triggerEntityType,
  disabled = false,
  recordLabel = "Record",
  recordTypeId,
  onRecordTypeIdChange,
  recordTypes,
}: EntityTargetSelectorProps) {
  // Normalize targetEntity to handle stale/lowercase persisted values
  const normalizedTargetEntity = (targetEntity || "").toUpperCase() as TargetEntity | "";
  const config = normalizedTargetEntity ? TARGET_ENTITY_CONFIGS[normalizedTargetEntity] : undefined;
  const isRecord = normalizedTargetEntity === "RECORD";
  const showRecordTypeSelector = isRecord && config?.recordTypeRequired;

  // Fetch selected record label for display
  const selectedQuery = useQuery({
    queryKey: ["entity-target-selected", normalizedTargetEntity, recordTypeId, specificRecordId],
    queryFn: async (): Promise<EntityOption | null> => {
      if (!specificRecordId || !config) return null;
      const trimmed = String(specificRecordId).trim();
      if (!/^[0-9a-fA-F-]{36}$/.test(trimmed)) return null;
      try {
        const data = await config.getApi(trimmed);
        return config.mapOption(data);
      } catch {
        return null;
      }
    },
    enabled: !!specificRecordId && !!config && /^[0-9a-fA-F-]{36}$/.test(String(specificRecordId).trim()),
    staleTime: 30000,
  });

  // Debounced search
  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");

  useEffect(() => {
    const t = setTimeout(() => setDebounced(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  const searchQuery = useQuery({
    queryKey: ["entity-target-search", normalizedTargetEntity, recordTypeId, debounced],
    queryFn: async (): Promise<EntityOption[]> => {
      const q = debounced.trim();
      if (!config) return [];
      try {
        // For Record type, include recordTypeId in search params
        const params: any = { search: q || undefined, page: 0, size: 20 };
        if (isRecord && recordTypeId) {
          params.recordTypeId = recordTypeId;
        }
        const res = await config.searchApi(params);
        const list = (res as any).data ?? res;
        const arr = Array.isArray(list) ? list : (list as any).data ?? [];
        return arr.slice(0, 20).map(config.mapOption);
      } catch {
        return [];
      }
    },
    enabled: targetRecordMode === "specific" && !!config && (normalizedTargetEntity !== "RECORD" || !!recordTypeId),
    staleTime: 15000,
  });

  // Popover open state for record search - controlled to close after selection
  const [isSearchOpen, setIsSearchOpen] = useState(false);

  const selected = selectedQuery.data;
  const showUnresolved = !!specificRecordId && selectedQuery.data === null && !selectedQuery.isLoading;

  // Determine if Current mode is available for this trigger entity
  const currentEntityLabel = triggerEntityType ? TARGET_ENTITIES.find(e => e.value === triggerEntityType)?.label : "Entity";
  const canUseCurrent = config?.supportsCurrent === true && triggerEntityType === normalizedTargetEntity;

  return (
    <div className="space-y-3">
      {/* Target Entity Selection */}
      <div className="space-y-1">
        <Label>Target Entity</Label>
        <div className="flex flex-wrap gap-2">
          {TARGET_ENTITIES.map((entity) => (
            <Button
              key={entity.value}
              type="button"
              variant={normalizedTargetEntity === entity.value ? "default" : "outline"}
              size="sm"
              disabled={disabled}
              onClick={() => onTargetEntityChange(entity.value)}
            >
              {entity.label}
            </Button>
          ))}
        </div>
        {isRecord && recordTypes && recordTypes.length > 0 && (
          <p className="text-xs text-muted-foreground">
            Select a Record Type to enable record search.
          </p>
        )}
      </div>

      {/* Record Type Selector for Record entity */}
      {showRecordTypeSelector && (
        <div className="space-y-1">
          <Label>Record Type <span className="text-red-500">*</span></Label>
          <div className="flex flex-wrap gap-2">
            {(recordTypes ?? []).map((rt) => (
              <Button
                key={rt.id}
                type="button"
                variant={recordTypeId === rt.id ? "default" : "outline"}
                size="sm"
                disabled={disabled}
                onClick={() => onRecordTypeIdChange?.(rt.id)}
              >
                {rt.name} ({rt.key})
              </Button>
            ))}
          </div>
        </div>
      )}

      {/* Target Record Mode Selection */}
      <div className="space-y-1">
        <Label>Target {config?.label}</Label>
        <RadioGroup
          value={targetRecordMode}
          onValueChange={onTargetRecordModeChange as any}
          disabled={disabled}
          className="flex gap-4"
        >
          <div className="flex items-center gap-2">
            <RadioGroupItem value="current" id={`mode-current-${normalizedTargetEntity}`} disabled={!canUseCurrent} />
            <Label htmlFor={`mode-current-${normalizedTargetEntity}`} className={cn("cursor-pointer", !canUseCurrent && "opacity-50")}>
              Current {currentEntityLabel}
              {!canUseCurrent && <span className="text-xs text-muted-foreground ml-1">(not available for this trigger)</span>}
            </Label>
          </div>
          <div className="flex items-center gap-2">
            <RadioGroupItem value="specific" id={`mode-specific-${normalizedTargetEntity}`} />
            <Label htmlFor={`mode-specific-${normalizedTargetEntity}`} className="cursor-pointer">
              Specific {config?.label}
            </Label>
          </div>
        </RadioGroup>
      </div>

      {/* Specific Record Search */}
      {targetRecordMode === "specific" && config && (
        <div className="space-y-1">
          <Label>{recordLabel} <span className="text-red-500">*</span></Label>
          <Popover open={isSearchOpen} onOpenChange={setIsSearchOpen}>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                role="combobox"
                disabled={disabled || (isRecord && !recordTypeId)}
                className={cn("w-full justify-between font-normal", !specificRecordId && "text-muted-foreground")}
              >
                <span className="truncate text-left">
                  {specificRecordId ? (
                    showUnresolved ? (
                      <span className="text-amber-600">Referenced record unavailable</span>
                    ) : selected?.label ? (
                      selected.label
                    ) : selectedQuery.isLoading ? (
                      "Loading…"
                    ) : (
                      String(specificRecordId)
                    )
                  ) : (
                    config.placeholder
                  )}
                </span>
                <span className="ml-2 flex items-center gap-1 shrink-0">
                  {searchQuery.isLoading ? <Loader2 className="h-4 w-4 animate-spin opacity-50" /> : <ChevronsUpDown className="h-4 w-4 shrink-0 opacity-50" />}
                </span>
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
              <Command shouldFilter={false}>
                <CommandInput placeholder={config.placeholder} value={search} onValueChange={setSearch} />
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
                          onSpecificRecordIdChange(opt.value);
                          setIsSearchOpen(false);
                        }}
                      >
                        <Check className={cn("mr-2 h-4 w-4", specificRecordId === opt.value ? "opacity-100" : "opacity-0")} />
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
          {specificRecordId ? (
            <div className="flex items-center justify-between">
              <p className="text-xs text-muted-foreground truncate">
                {showUnresolved ? (
                  <span className="text-amber-600">Unavailable — stored UUID: <span className="font-mono">{String(specificRecordId)}</span></span>
                ) : selected?.secondary ? (
                  selected.secondary
                ) : selected ? (
                  <span className="font-mono text-[11px]">{String(specificRecordId)}</span>
                ) : null}
              </p>
              <Button variant="ghost" size="sm" className="h-6 px-2 text-xs" onClick={() => onSpecificRecordIdChange(null)} disabled={disabled}>
                <X className="h-3 w-3 mr-1" /> Clear
              </Button>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">Tenant-scoped search; cross-tenant access is blocked.</p>
          )}
        </div>
      )}

      {/* Current Mode Display */}
      {targetRecordMode === "current" && canUseCurrent && (
        <div className="rounded-md border bg-green-50 p-2 text-xs dark:bg-green-950/20">
          <p className="font-medium text-green-900 dark:text-green-100">Using Current {currentEntityLabel}</p>
          <p className="text-green-700 dark:text-green-300">
            The {currentEntityLabel} that triggered this workflow will be used.
            Expression: <code className="font-mono">{'{{entity.id}}'}</code>
          </p>
        </div>
      )}

      {/* Current Mode Unavailable */}
      {targetRecordMode === "current" && !canUseCurrent && (
        <div className="rounded-md border bg-amber-50 p-2 text-xs dark:bg-amber-950/20">
          <p className="font-medium text-amber-900 dark:text-amber-100">Current {currentEntityLabel} not available</p>
          <p className="text-amber-700 dark:text-amber-300">
            This workflow is triggered by a {triggerEntityType || "different entity"}. Select "Specific {config?.label}" to choose one manually.
          </p>
        </div>
      )}
    </div>
  );
}