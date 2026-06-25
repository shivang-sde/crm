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
import { DealStageSummary } from "@/types/deal-stages";
import { User } from "@/types/rbac";

export interface DealFilterState {
  stageId: string;
  ownerId: string;
  search: string;
}

interface DealFiltersProps {
  filters: DealFilterState;
  stages?: DealStageSummary[];
  users?: User[];
  onFiltersChange: (filters: DealFilterState) => void;
}

export function DealFilters({
  filters,
  stages,
  users,
  onFiltersChange,
}: DealFiltersProps) {
  const update = (partial: Partial<DealFilterState>) => {
    onFiltersChange({ ...filters, ...partial });
  };

  return (
    <div className="flex flex-col sm:flex-row gap-4 items-center justify-between bg-white p-4 rounded-lg border">
      <div className="relative w-full sm:w-96">
        <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Search deals by name, account, contact..."
          className="pl-9"
          value={filters.search}
          onChange={(e) => update({ search: e.target.value })}
        />
      </div>

      <div className="flex flex-wrap gap-3 w-full sm:w-auto">
        <Select
          value={filters.stageId}
          onValueChange={(v) => update({ stageId: v })}
        >
          <SelectTrigger className="w-full sm:w-[160px]">
            <SelectValue placeholder="All stages" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All stages</SelectItem>
            {stages?.map((stage) => (
              <SelectItem key={stage.id} value={stage.id}>
                {stage.name}
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
            {users?.map((user) => (
              <SelectItem key={user.id} value={user.id}>
                {user.firstName} {user.lastName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}