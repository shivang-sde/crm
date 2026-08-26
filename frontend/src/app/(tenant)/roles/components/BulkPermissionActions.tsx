"use client";

import React, { useState } from "react";
import { ListFilter } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Permission, AccessScope } from "@/types/rbac";

const SCOPES: AccessScope[] = ["ALL", "TEAM", "OWN", "NONE"];
const ACTION_ORDER = ["read", "write", "delete", "assign", "export"];

interface BulkPermissionActionsProps {
  catalog: Permission[];
  /** Called with every catalog permission matching the filters. */
  onApply: (permissions: Permission[], scope: AccessScope) => void;
}

/** Bulk scope editing over catalog-backed permissions only. */
export function BulkPermissionActions({ catalog, onApply }: BulkPermissionActionsProps) {
  const [moduleFilter, setModuleFilter] = useState<string>("__all");
  const [action, setAction] = useState<string>("");
  const [scope, setScope] = useState<AccessScope>("OWN");

  const modules = Array.from(new Set(catalog.map((p) => p.module))).sort();
  const actions = Array.from(new Set(catalog.map((p) => p.action))).sort(
    (a, b) =>
      (ACTION_ORDER.indexOf(a) === -1 ? ACTION_ORDER.length : ACTION_ORDER.indexOf(a)) -
        (ACTION_ORDER.indexOf(b) === -1 ? ACTION_ORDER.length : ACTION_ORDER.indexOf(b)) ||
      a.localeCompare(b)
  );

  const matched =
    action === ""
      ? []
      : catalog.filter((p) => p.action === action && (moduleFilter === "__all" || p.module === moduleFilter));

  const apply = () => {
    if (matched.length > 0) {
      onApply(matched, scope);
      setAction("");
    }
  };

  return (
    <div className="flex flex-wrap items-end gap-3 p-3 bg-gray-50 border rounded-lg">
      <div className="space-y-1">
        <label className="text-xs font-medium text-gray-600">Module</label>
        <Select value={moduleFilter} onValueChange={setModuleFilter}>
          <SelectTrigger className="w-[150px] h-8 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all">All modules</SelectItem>
            {modules.map((m) => (
              <SelectItem key={m} value={m} className="capitalize">
                {m}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-1">
        <label className="text-xs font-medium text-gray-600">Action</label>
        <Select value={action} onValueChange={setAction}>
          <SelectTrigger className="w-[120px] h-8 text-xs">
            <SelectValue placeholder="Select" />
          </SelectTrigger>
          <SelectContent>
            {actions.map((a) => (
              <SelectItem key={a} value={a} className="capitalize">
                {a}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-1">
        <label className="text-xs font-medium text-gray-600">Scope</label>
        <Select value={scope} onValueChange={(val) => setScope(val as AccessScope)}>
          <SelectTrigger className="w-[90px] h-8 text-xs font-semibold">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {SCOPES.map((s) => (
              <SelectItem key={s} value={s}>
                {s}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Button size="sm" onClick={apply} disabled={!action || matched.length === 0} className="h-8">
        <ListFilter className="mr-1.5 h-3.5 w-3.5" />
        Apply{matched.length > 0 ? ` (${matched.length})` : ""}
      </Button>
      <p className="text-xs text-gray-400 w-full sm:w-auto sm:ml-auto">
        Applies to permissions defined in the permission catalog.
      </p>
    </div>
  );
}
