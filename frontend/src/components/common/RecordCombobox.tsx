"use client";

import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Check, ChevronsUpDown, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import { leadApi } from "@/lib/api/leads";
import { contactApi } from "@/lib/api/contacts";
import { accountApi } from "@/lib/api/accounts";
import { dealApi } from "@/lib/api/deals";

export type RecordEntity = "LEAD" | "CONTACT" | "ACCOUNT" | "DEAL";

interface RecordOption {
  id: string;
  label: string;
  context?: string;
}

const MIN_SEARCH_LENGTH = 2;
const RESULT_PAGE_SIZE = 25;

function toOption(entityType: RecordEntity, row: any): RecordOption {
  switch (entityType) {
    case "LEAD": {
      const name = [row.firstName, row.lastName].filter(Boolean).join(" ").trim();
      const context = [row.company, row.email].filter(Boolean).join(" • ");
      return { id: row.id, label: name || row.company || row.email || "Lead", context };
    }
    case "CONTACT": {
      const name = [row.firstName, row.lastName].filter(Boolean).join(" ").trim();
      const context = [row.email, row.phone].filter(Boolean).join(" • ");
      return { id: row.id, label: name || row.email || "Contact", context };
    }
    case "ACCOUNT": {
      const context = [row.industry, row.website].filter(Boolean).join(" • ");
      return { id: row.id, label: row.name, context };
    }
    default: {
      const amount =
        row.amount !== undefined && row.amount !== null
          ? `${Number(row.amount).toLocaleString()}${row.currency ? " " + row.currency : ""}`
          : undefined;
      const context = [row.stage?.name, amount ? `Value ${amount}` : undefined]
        .filter(Boolean)
        .join(" • ");
      return { id: row.id, label: row.name, context };
    }
  }
}

export function RecordCombobox({
  entityType,
  value,
  onChange,
  placeholder,
  disabled,
  fallbackLabel,
}: {
  entityType: RecordEntity;
  value?: string;
  onChange: (id: string | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
  /** Meaningful label for an externally initialised value (e.g. edit mode). */
  fallbackLabel?: string;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [selectedLabel, setSelectedLabel] = useState<string | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Reset local state when the entity type changes.
  useEffect(() => {
    setSelectedLabel(null);
    setSearch("");
  }, [entityType]);

  const enabled = open && debouncedSearch.length >= MIN_SEARCH_LENGTH;

  const { data: result, isLoading, isError } = useQuery({
    queryKey: ["record-combobox", entityType, debouncedSearch],
    queryFn: async () => {
      const params = { search: debouncedSearch, page: 0, size: RESULT_PAGE_SIZE };
      switch (entityType) {
        case "LEAD":
          return leadApi.listLeads(params);
        case "CONTACT":
          return contactApi.listContacts(params);
        case "ACCOUNT":
          return accountApi.listAccounts(params);
        default:
          return dealApi.listDeals(params);
      }
    },
    enabled,
  });

  const options = useMemo<RecordOption[]>(
    () => ((result as any)?.data ?? []).map((row: any) => toOption(entityType, row)),
    [result, entityType]
  );

  const selectedFromResults = useMemo(
    () => options.find((option: RecordOption) => option.id === value) ?? null,
    [options, value]
  );

  const displayLabel =
    selectedLabel ??
    selectedFromResults?.label ??
    (value ? fallbackLabel ?? "Selected record" : null);

  const tooShort = debouncedSearch.length > 0 && debouncedSearch.length < MIN_SEARCH_LENGTH;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={disabled}
          className={cn("w-full justify-between font-normal", !displayLabel && "text-muted-foreground")}
        >
          <span className="truncate">{displayLabel ?? placeholder ?? "Search and select..."}</span>
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
        <Command shouldFilter={false}>
          <div className="flex items-center gap-2 border-b px-3">
            <Loader2
              className={cn(
                "h-4 w-4 shrink-0 text-muted-foreground",
                isLoading ? "animate-spin" : "opacity-0"
              )}
            />
            <CommandInput
              placeholder={`Search ${entityType.toLowerCase()}...`}
              value={search}
              onValueChange={setSearch}
            />
          </div>
          <CommandList>
            {tooShort ? (
              <div className="px-3 py-4 text-sm text-muted-foreground">
                Type at least {MIN_SEARCH_LENGTH} characters to search.
              </div>
            ) : isError ? (
              <div className="px-3 py-4 text-sm text-destructive">Unable to load records.</div>
            ) : (
              <CommandEmpty>
                {isLoading ? "Searching..." : "No matching records."}
              </CommandEmpty>
            )}
            <CommandGroup>
              {options.map((option: RecordOption) => (
                <CommandItem
                  key={option.id}
                  value={option.id}
                  onSelect={() => {
                    setSelectedLabel(option.label);
                    onChange(option.id);
                    setOpen(false);
                  }}
                >
                  <Check
                    className={cn("mr-2 h-4 w-4", value === option.id ? "opacity-100" : "opacity-0")}
                  />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{option.label}</p>
                    {option.context && (
                      <p className="truncate text-xs text-muted-foreground">{option.context}</p>
                    )}
                  </div>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
          {value && (
            <div className="border-t p-1">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="w-full justify-start"
                onClick={() => {
                  setSelectedLabel(null);
                  onChange(undefined);
                  setOpen(false);
                }}
              >
                Clear selection
              </Button>
            </div>
          )}
        </Command>
      </PopoverContent>
    </Popover>
  );
}
