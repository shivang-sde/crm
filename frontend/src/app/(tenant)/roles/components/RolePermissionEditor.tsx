"use client";

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Loader2, RotateCcw, Settings2, Trash2 } from "lucide-react";

import { roleApi } from "@/lib/api/roles";
import { Button } from "@/components/ui/button";
import { AccessScope, Permission, RolePermission } from "@/types/rbac";
import { PermissionMatrix } from "./PermissionMatrix";
import { AssignPermissionModal } from "./AssignPermissionModal";
import { BulkPermissionActions } from "./BulkPermissionActions";

interface RolePermissionEditorProps {
  /** Local permission draft (id = catalog permission id). */
  draft: RolePermission[];
  /** What the draft started from; enables Reset + change indicators. */
  baseline?: RolePermission[];
  onChange: (draft: RolePermission[]) => void;
}

/**
 * Shared permission editing surface for role creation and role details.
 * Holds no persistence logic: callers own the draft and submit it.
 */
export function RolePermissionEditor({ draft, baseline = [], onChange }: RolePermissionEditorProps) {
  const [showBulk, setShowBulk] = useState(false);

  const {
    data: catalog,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ["all-permissions"],
    queryFn: () => roleApi.getAllPermissions(),
  });

  const setScope = (permissionId: string, scope: AccessScope) =>
    onChange(draft.map((p) => (p.id === permissionId ? { ...p, accessScope: scope } : p)));

  const removePermission = (permissionId: string) => onChange(draft.filter((p) => p.id !== permissionId));

  const addPermission = (permissionId: string, scope: AccessScope) => {
    const permission = catalog?.find((p) => p.id === permissionId);
    if (!permission || draft.some((p) => p.id === permissionId)) return;
    onChange([
      ...draft,
      { id: permission.id, module: permission.module, action: permission.action, accessScope: scope, description: permission.description },
    ]);
  };

  // Bulk applies to catalog-backed permissions only: upsert each match.
  const applyBulk = (permissions: Permission[], scope: AccessScope) => {
    const next = new Map(draft.map((p) => [p.id, p]));
    permissions.forEach((permission) =>
      next.set(permission.id, {
        id: permission.id,
        module: permission.module,
        action: permission.action,
        accessScope: scope,
        description: permission.description,
      })
    );
    onChange(Array.from(next.values()));
  };

  const quickSet = (action: string, scope: AccessScope) =>
    onChange(
      draft.map((p) => (catalog?.some((c) => c.id === p.id && c.action === action) ? { ...p, accessScope: scope } : p))
    );

  const reset = () => onChange(baseline.map((p) => ({ ...p })));
  const isDirty =
    draft.length !== baseline.length ||
    draft.some((p) => baseline.find((b) => b.id === p.id)?.accessScope !== p.accessScope);

  if (isLoading) {
    return (
      <div className="flex h-48 flex-col items-center justify-center gap-2 border rounded-lg bg-white text-gray-500">
        <Loader2 className="h-7 w-7 animate-spin" />
        <span className="text-sm">Loading permissions...</span>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex h-48 flex-col items-center justify-center gap-3 border rounded-lg bg-white">
        <p className="text-sm font-medium text-red-600">Failed to load the permission catalog.</p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          Retry
        </Button>
      </div>
    );
  }

  if (!catalog || catalog.length === 0) {
    return (
      <div className="flex h-48 flex-col items-center justify-center gap-2 border rounded-lg bg-white">
        <p className="text-sm font-medium text-gray-700">Permission catalog unavailable</p>
        <p className="text-sm text-gray-500">No permissions are defined for this workspace.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <AssignPermissionModal
          catalog={catalog}
          existingIds={new Set(draft.map((p) => p.id))}
          onAdd={addPermission}
          trigger={
            <Button variant="outline" size="sm">
              Add Permission
            </Button>
          }
        />
        <Button variant="outline" size="sm" onClick={() => setShowBulk((v) => !v)}>
          <Settings2 className="mr-1.5 h-3.5 w-3.5" />
          Bulk Edit
        </Button>

        <div className="flex flex-wrap items-center gap-1.5 ml-auto">
          <span className="text-xs text-gray-400 mr-1">Quick actions:</span>
          <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => quickSet("read", "ALL")}>
            All READ → ALL
          </Button>
          <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => quickSet("write", "OWN")}>
            All WRITE → OWN
          </Button>
          <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => quickSet("delete", "NONE")}>
            All DELETE → NONE
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="h-7 text-xs text-red-600 hover:text-red-700"
            onClick={() => onChange([])}
          >
            <Trash2 className="mr-1 h-3 w-3" />
            Clear all
          </Button>
        </div>
      </div>

      {showBulk && <BulkPermissionActions catalog={catalog} onApply={applyBulk} />}

      <div className="flex items-center justify-between">
        <p className="text-xs text-gray-500">
          {draft.length} permission{draft.length === 1 ? "" : "s"} configured
          {isDirty && " · unsaved changes"}
        </p>
        {isDirty && (
          <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={reset}>
            <RotateCcw className="mr-1 h-3 w-3" />
            Reset changes
          </Button>
        )}
      </div>

      <PermissionMatrix
        catalog={catalog}
        draft={draft}
        baseline={baseline}
        onScopeChange={setScope}
        onRemove={removePermission}
      />
    </div>
  );
}
