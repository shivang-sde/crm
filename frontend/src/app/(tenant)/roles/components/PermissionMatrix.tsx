"use client";

import React from "react";
import { X } from "lucide-react";

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Permission, RolePermission, AccessScope } from "@/types/rbac";

const SCOPES: AccessScope[] = ["ALL", "TEAM", "OWN", "NONE"];

const SCOPE_COLORS: Record<string, string> = {
  ALL: "bg-green-100 text-green-800 hover:bg-green-200 border-green-200",
  TEAM: "bg-blue-100 text-blue-800 hover:bg-blue-200 border-blue-200",
  OWN: "bg-yellow-100 text-yellow-800 hover:bg-yellow-200 border-yellow-200",
  NONE: "bg-gray-100 text-gray-500 hover:bg-gray-200 border-gray-200",
};

const ACTION_ORDER = ["read", "write", "delete", "assign", "export"];

const SCOPE_RANK: Record<AccessScope, number> = { NONE: 0, OWN: 1, TEAM: 2, ALL: 3 };

interface PermissionMatrixProps {
  catalog: Permission[];
  draft: RolePermission[];
  /** Permissions the draft started from; drives the change indicators. */
  baseline?: RolePermission[];
  onScopeChange: (permissionId: string, scope: AccessScope) => void;
  onRemove: (permissionId: string) => void;
}

/**
 * Catalog-driven module × action matrix over a local permission draft.
 * A cell renders only when the backend permission catalog defines that
 * module+action pair; "-" means the permission is not assigned to the role.
 */
export function PermissionMatrix({ catalog, draft, baseline = [], onScopeChange, onRemove }: PermissionMatrixProps) {
  const draftById = new Map(draft.map((p) => [p.id, p]));
  const baselineById = new Map(baseline.map((p) => [p.id, p]));

  const catalogByModuleAction = new Map<string, Permission>();
  catalog.forEach((p) => catalogByModuleAction.set(`${p.module}:${p.action}`, p));

  const modules = Array.from(new Set(catalog.map((p) => p.module))).sort();
  const actions = Array.from(new Set(catalog.map((p) => p.action))).sort(
    (a, b) =>
      (ACTION_ORDER.indexOf(a) === -1 ? ACTION_ORDER.length : ACTION_ORDER.indexOf(a)) -
        (ACTION_ORDER.indexOf(b) === -1 ? ACTION_ORDER.length : ACTION_ORDER.indexOf(b)) ||
      a.localeCompare(b)
  );

  // Change indicator relative to the baseline: + added, ↑ raised, ↓ lowered.
  const diffIndicator = (permission: RolePermission): string | null => {
    const before = baselineById.get(permission.id);
    if (!before) return "+";
    if (before.accessScope !== permission.accessScope) {
      return SCOPE_RANK[permission.accessScope] > SCOPE_RANK[before.accessScope] ? "↑" : "↓";
    }
    return null;
  };

  return (
    <div className="border rounded-lg overflow-hidden">
      <div className="overflow-x-auto">
        <Table>
          <TableHeader className="bg-gray-50">
            <TableRow>
              <TableHead className="font-semibold text-gray-700 w-48">Module</TableHead>
              {actions.map((action) => (
                <TableHead key={action} className="font-semibold text-gray-700 capitalize text-center">
                  {action}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {modules.length === 0 ? (
              <TableRow>
                <TableCell colSpan={actions.length + 1} className="h-32 text-center text-gray-500">
                  No permissions assigned to this role yet.
                </TableCell>
              </TableRow>
            ) : (
              modules.map((module) => (
                <TableRow key={module} className="hover:bg-gray-50">
                  <TableCell className="font-medium capitalize text-gray-800">{module}</TableCell>
                  {actions.map((action) => {
                    const catalogPermission = catalogByModuleAction.get(`${module}:${action}`);
                    if (!catalogPermission) {
                      return (
                        <TableCell key={`${module}-${action}`} className="text-center">
                          <span className="text-gray-300" aria-hidden>
                            –
                          </span>
                        </TableCell>
                      );
                    }

                    const assigned = draftById.get(catalogPermission.id);
                    return (
                      <TableCell key={`${module}-${action}`} className="text-center">
                        {assigned ? (
                          <div className="flex justify-center items-center gap-1">
                            <Select
                              value={assigned.accessScope}
                              onValueChange={(val) => onScopeChange(assigned.id, val as AccessScope)}
                            >
                              <SelectTrigger
                                title={
                                  diffIndicator(assigned)
                                    ? `${module}:${action} ${diffIndicator(assigned)} was ${
                                        baselineById.get(assigned.id)?.accessScope ?? "not set"
                                      }`
                                    : undefined
                                }
                                className={`w-[92px] h-8 text-xs font-semibold ${SCOPE_COLORS[assigned.accessScope]} focus:ring-0 border-0`}
                              >
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                {SCOPES.map((scope) => (
                                  <SelectItem key={scope} value={scope}>
                                    {scope}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                            <button
                              type="button"
                              onClick={() => onRemove(assigned.id)}
                              aria-label={`Remove ${module} ${action} permission`}
                              title={`Remove ${module}:${action}`}
                              className="text-gray-400 hover:text-red-600 transition-colors"
                            >
                              <X className="h-3.5 w-3.5" />
                            </button>
                            {diffIndicator(assigned) && (
                              <span
                                className="text-xs font-semibold text-gray-500 w-2"
                                aria-label={`Changed ${diffIndicator(assigned) === "+" ? "(added)" : diffIndicator(assigned) === "↑" ? "(scope increased)" : "(scope reduced)"}`}
                                title={
                                  diffIndicator(assigned) === "+"
                                    ? "Added"
                                    : `Was ${baselineById.get(assigned.id)?.accessScope}`
                                }
                              >
                                {diffIndicator(assigned)}
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-gray-300 text-sm">-</span>
                        )}
                      </TableCell>
                    );
                  })}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
