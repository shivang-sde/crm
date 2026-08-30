"use client";

import { Building2, Loader2 } from "lucide-react";
import { useState, useRef, useEffect } from "react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { TenantResponse } from "@/types/tenant";

interface TenantSelectorProps {
  tenants: TenantResponse[];
  selectedTenantId: string | null;
  onSelect: (tenantId: string | null) => void;
  placeholder?: string;
  disabled?: boolean;
  isLoading?: boolean;
}

export function TenantSelector({
  tenants,
  selectedTenantId,
  onSelect,
  placeholder = "All Tenants",
  disabled = false,
  isLoading = false,
}: TenantSelectorProps) {
  const [isOpen, setIsOpen] = useState(false);
  const selectRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (selectRef.current && !selectRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleSelect = (value: string) => {
    onSelect(value === "all" ? null : value);
    setIsOpen(false);
  };

  const getTenantLabel = (tenant: TenantResponse) => {
    return tenant.name || tenant.slug || tenant.id;
  };

  if (isLoading || tenants.length === 0) {
    return (
      <div className="relative w-full max-w-xs">
        <Select disabled>
          <SelectTrigger className="w-full" aria-label="Tenant selector">
            <SelectValue placeholder={isLoading ? "Loading tenants..." : "No tenants available"} />
            {isLoading && (
              <Loader2 className="ml-2 h-4 w-4 animate-spin text-muted-foreground" />
            )}
          </SelectTrigger>
        </Select>
      </div>
    );
  }

  return (
    <div className="relative w-full max-w-xs" ref={selectRef}>
      <Select
        value={selectedTenantId ?? "all"}
        onValueChange={handleSelect}
        disabled={disabled}
        open={isOpen}
        onOpenChange={setIsOpen}
      >
        <SelectTrigger className="w-full" aria-label="Tenant selector">
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent position="popper" sideOffset={5}>
          <SelectItem value="all">
            <div className="flex items-center gap-2">
              <Building2 className="h-4 w-4 text-muted-foreground" />
              <span>All Tenants</span>
            </div>
          </SelectItem>
          {tenants.map((tenant) => (
            <SelectItem key={tenant.id} value={tenant.id}>
              <div className="flex items-center gap-2">
                <Building2 className="h-4 w-4 text-muted-foreground" />
                <span>{getTenantLabel(tenant)}</span>
              </div>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}