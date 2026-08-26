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
import { AccountResponse } from "@/types/accounts";
import { ContactResponse } from "@/types/contacts";

export interface DealFilterState {
  stageId: string;
  ownerId: string;
  accountId: string;
  contactId: string;
  closeDateFrom: string;
  closeDateTo: string;
  search: string;
}

interface DealFiltersProps {
  filters: DealFilterState;
  stages?: DealStageSummary[];
  users?: User[];
  accounts?: AccountResponse[];
  contacts?: ContactResponse[];
  onFiltersChange: (filters: DealFilterState) => void;
}

export function DealFilters({
  filters,
  stages,
  users,
  accounts,
  contacts,
  onFiltersChange,
}: DealFiltersProps) {
  const update = (partial: Partial<DealFilterState>) => {
    onFiltersChange({ ...filters, ...partial });
  };

  return (
    <div className="flex flex-col gap-4 bg-white p-4 rounded-lg border">
      <div className="flex flex-col sm:flex-row gap-4 items-center justify-between">
        <div className="relative w-full sm:w-96">
          <Search className="absolute left-2.5 top-2.5 h-4 text-muted-foreground" />
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

      <div className="flex flex-wrap gap-3 items-center">
        <Select
          value={filters.accountId}
          onValueChange={(v) => update({ accountId: v })}
        >
          <SelectTrigger className="w-full sm:w-[180px]">
            <SelectValue placeholder="All accounts" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All accounts</SelectItem>
            {accounts?.map((account) => (
              <SelectItem key={account.id} value={account.id}>
                {account.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={filters.contactId}
          onValueChange={(v) => update({ contactId: v })}
        >
          <SelectTrigger className="w-full sm:w-[180px]">
            <SelectValue placeholder="All contacts" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All contacts</SelectItem>
            {contacts?.map((contact) => (
              <SelectItem key={contact.id} value={contact.id}>
                {[contact.firstName, contact.lastName].filter(Boolean).join(" ")}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="flex items-center gap-2">
          <Input
            type="date"
            aria-label="Expected close from"
            className="w-[150px]"
            value={filters.closeDateFrom}
            onChange={(e) => update({ closeDateFrom: e.target.value })}
          />
          <span className="text-xs text-muted-foreground">to</span>
          <Input
            type="date"
            aria-label="Expected close to"
            className="w-[150px]"
            value={filters.closeDateTo}
            onChange={(e) => update({ closeDateTo: e.target.value })}
          />
        </div>
      </div>
    </div>
  );
}
