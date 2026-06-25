"use client";

import React from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";

import { roleApi } from "@/lib/api/roles";
import { Badge } from "@/components/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { RolePermission } from "@/types/rbac";

interface PermissionMatrixProps {
  roleId: string;
}

const SCOPE_COLORS: Record<string, string> = {
  ALL: "bg-green-100 text-green-800 hover:bg-green-200 border-green-200",
  TEAM: "bg-blue-100 text-blue-800 hover:bg-blue-200 border-blue-200",
  OWN: "bg-yellow-100 text-yellow-800 hover:bg-yellow-200 border-yellow-200",
  NONE: "bg-gray-100 text-gray-800 hover:bg-gray-200 border-gray-200",
};

export function PermissionMatrix({ roleId }: PermissionMatrixProps) {
  const queryClient = useQueryClient();

  const { data: rolePermissions, isLoading } = useQuery({
    queryKey: ["role-permissions", roleId],
    queryFn: () => roleApi.getRolePermissions(roleId),
  });

  const updateScopeMutation = useMutation({
    mutationFn: ({ permissionId, scope }: { permissionId: string; scope: string }) =>
      roleApi.updatePermissionScope(roleId, permissionId, scope),
    onSuccess: () => {
      toast.success("Permission scope updated");
      queryClient.invalidateQueries({ queryKey: ["role-permissions", roleId] });
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.error?.message || "Failed to update scope");
    },
  });

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center border rounded-md bg-white">
        <Loader2 className="h-8 w-8 animate-spin text-gray-400" />
      </div>
    );
  }

  // Transform permissions array into matrix format: Record<Module, Record<Action, RolePermission>>
  const matrix: Record<string, Record<string, RolePermission>> = {};
  
  if (rolePermissions) {
    rolePermissions.forEach(permission => {
      // TypeScript safety: as any here because we are treating PermissionResponse somewhat loosely 
      // where accessScope might be returned or we fall back if missing
      const p = permission as any; 
      if (!matrix[p.module]) {
        matrix[p.module] = {};
      }
      matrix[p.module][p.action] = {
        id: p.id,
        module: p.module,
        action: p.action,
        accessScope: p.accessScope || "NONE",
        description: p.description
      };
    });
  }

  const modules = Object.keys(matrix).sort();
  const allActions = ["read", "write", "delete", "assign", "export"];

  const handleScopeChange = (permissionId: string, newScope: string) => {
    updateScopeMutation.mutate({ permissionId, scope: newScope });
  };

  return (
    <div className="border rounded-lg bg-white overflow-hidden shadow-sm">
      <div className="overflow-x-auto">
        <Table>
          <TableHeader className="bg-gray-50">
            <TableRow>
              <TableHead className="font-semibold text-gray-700 w-48">Module</TableHead>
              {allActions.map(action => (
                <TableHead key={action} className="font-semibold text-gray-700 capitalize text-center">
                  {action}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {modules.length === 0 ? (
              <TableRow>
                <TableCell colSpan={allActions.length + 1} className="h-32 text-center text-gray-500">
                  No permissions assigned to this role yet.
                </TableCell>
              </TableRow>
            ) : (
              modules.map(module => (
                <TableRow key={module} className="hover:bg-gray-50">
                  <TableCell className="font-medium capitalize text-gray-800">
                    {module}
                  </TableCell>
                  {allActions.map(action => {
                    const permission = matrix[module][action];
                    
                    return (
                      <TableCell key={`${module}-${action}`} className="text-center">
                        {permission ? (
                          <div className="flex justify-center">
                            <Select 
                              defaultValue={permission.accessScope}
                              onValueChange={(val) => handleScopeChange(permission.id, val)}
                              disabled={updateScopeMutation.isPending}
                            >
                              <SelectTrigger className={`w-[100px] h-8 text-xs font-semibold ${SCOPE_COLORS[permission.accessScope] || SCOPE_COLORS['NONE']} focus:ring-0 border-0`}>
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="ALL">ALL</SelectItem>
                                <SelectItem value="TEAM">TEAM</SelectItem>
                                <SelectItem value="OWN">OWN</SelectItem>
                                <SelectItem value="NONE">NONE</SelectItem>
                              </SelectContent>
                            </Select>
                          </div>
                        ) : (
                          <div className="text-gray-300 text-sm">-</div>
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
