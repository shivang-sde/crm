"use client";

import { Building2, Loader2, RefreshCw } from "lucide-react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { TenantResponse } from "@/types/tenant";

interface TenantSelectorProps {
  tenants: TenantResponse[];
  selectedTenantId: string | null;
  onSelect: (tenantId: string | null) => void;
  placeholder?: string;
  disabled?: boolean;
  isLoading?: boolean;
  isError?: boolean;
  onRetry?: () => void;
}

const ALL_VALUE = "__all_tenants__";

export function TenantSelector({
  tenants,
  selectedTenantId,
  onSelect,
  placeholder = "All Tenants",
  disabled = false,
  isLoading = false,
  isError = false,
  onRetry,
}: TenantSelectorProps) {
  const handleSelect = (value: string) => {
    onSelect(value === ALL_VALUE ? null : value);
  };

  if (isError) {
    return (
      <div className="flex w-full items-center justify-between gap-2 rounded-lg border border-dashed px-3 py-1.5 sm:w-auto sm:max-w-xs">
        <span className="text-sm text-destructive">Couldn&apos;t load tenants.</span>
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RefreshCw className="mr-1.5 h-3 w-3" />
          Retry
        </Button>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="flex w-full items-center gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-sm text-muted-foreground sm:w-auto sm:max-w-xs">
        <Loader2 className="h-4 w-4 animate-spin" />
        Loading tenants...
      </div>
    );
  }

  if (tenants.length === 0) {
    return (
      <div className="flex w-full items-center gap-2 rounded-lg border border-dashed px-3 py-2 text-sm text-muted-foreground sm:w-auto sm:max-w-xs">
        <Building2 className="h-4 w-4" />
        No tenants available
      </div>
    );
  }

  return (
    <div className="w-full sm:w-auto sm:max-w-xs">
      <Select value={selectedTenantId ?? ALL_VALUE} onValueChange={handleSelect} disabled={disabled}>
        <SelectTrigger className="w-full" aria-label={placeholder}>
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent position="popper" sideOffset={5}>
          <SelectItem value={ALL_VALUE}>
            <div className="flex items-center gap-2">
              <Building2 className="h-4 w-4 text-muted-foreground" />
              <span>{placeholder}</span>
            </div>
          </SelectItem>
          {tenants.map((tenant) => (
            <SelectItem key={tenant.id} value={tenant.id}>
              <div className="flex items-center gap-2">
                <Building2 className="h-4 w-4 text-muted-foreground" />
                <div className="flex flex-col leading-tight">
                  <span>{tenant.name || tenant.slug || "Unnamed tenant"}</span>
                  {tenant.slug && tenant.name && <span className="text-[11px] text-muted-foreground">{tenant.slug}</span>}
                </div>
              </div>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}