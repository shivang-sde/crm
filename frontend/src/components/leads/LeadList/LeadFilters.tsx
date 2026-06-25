"use client";

import { Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { LeadSourceSummary, LeadStatusSummary } from "@/types/leads";
import { User } from "@/types/rbac";

export interface LeadFilterState {
  statusId: string;
  sourceId: string;
  ownerId: string;
  converted: string;
  search: string;
}

interface LeadFiltersProps {
  filters: LeadFilterState;
  statuses?: LeadStatusSummary[];
  sources?: LeadSourceSummary[];
  users?: User[];
  onFiltersChange: (filters: LeadFilterState) => void;
}

export function LeadFilters({
  filters,
  statuses,
  sources,
  users,
  onFiltersChange,
}: LeadFiltersProps) {
  const update = (partial: Partial<LeadFilterState>) => {
    onFiltersChange({ ...filters, ...partial });
  };

  return (
    <div className="flex flex-col sm:flex-row gap-4 items-center justify-between bg-white p-4 rounded-lg border">
      <div className="relative w-full sm:w-96">
        <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Search by name, email, phone..."
          className="pl-9"
          value={filters.search}
          onChange={(e) => update({ search: e.target.value })}
        />
      </div>

      <div className="flex flex-wrap gap-3 w-full sm:w-auto">
        <Select
          value={filters.statusId}
          onValueChange={(v) => update({ statusId: v })}
        >
          <SelectTrigger className="w-full sm:w-[160px]">
            <SelectValue placeholder="All statuses" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            {statuses?.map((s) => (
              <SelectItem key={s.id} value={s.id}>
                {s.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={filters.sourceId}
          onValueChange={(v) => update({ sourceId: v })}
        >
          <SelectTrigger className="w-full sm:w-[160px]">
            <SelectValue placeholder="All sources" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All sources</SelectItem>
            {sources?.map((s) => (
              <SelectItem key={s.id} value={s.id}>
                {s.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={filters.ownerId}
          onValueChange={(v) => update({ ownerId: v })}
        >
          <SelectTrigger className="w-full sm:w-[160px]">
            <SelectValue placeholder="All owners" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All owners</SelectItem>
            {users?.map((u) => (
              <SelectItem key={u.id} value={u.id}>
                {u.firstName} {u.lastName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={filters.converted}
          onValueChange={(v) => update({ converted: v })}
        >
          <SelectTrigger className="w-full sm:w-[140px]">
            <SelectValue placeholder="Conversion" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All leads</SelectItem>
            <SelectItem value="false">Open</SelectItem>
            <SelectItem value="true">Converted</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
