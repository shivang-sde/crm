"use client";

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { roleApi } from "@/lib/api/roles";

interface AssignPermissionModalProps {
  roleId: string;
  trigger?: React.ReactNode;
}

export function AssignPermissionModal({ roleId, trigger }: AssignPermissionModalProps) {
  const [open, setOpen] = useState(false);
  const [selectedModule, setSelectedModule] = useState<string>("");
  const [selectedAction, setSelectedAction] = useState<string>("");
  const [selectedScope, setSelectedScope] = useState<string>("NONE");

  const queryClient = useQueryClient();

  const { data: allPermissions, isLoading: permissionsLoading } = useQuery({
    queryKey: ["all-permissions"],
    queryFn: () => roleApi.getAllPermissions(),
  });

  const { data: rolePermissions } = useQuery({
    queryKey: ["role-permissions", roleId],
    queryFn: () => roleApi.getRolePermissions(roleId),
  });

  const assignMutation = useMutation({
    mutationFn: (data: { permissionId: string; accessScope: string }) =>
      roleApi.assignPermission(roleId, {
        permissionId: data.permissionId,
        accessScope: data.accessScope as any,
      }),
    onSuccess: () => {
      toast.success("Permission assigned successfully");
      queryClient.invalidateQueries({ queryKey: ["role-permissions", roleId] });
      setOpen(false);
      resetForm();
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.error?.message || "Failed to assign permission");
    },
  });

  const resetForm = () => {
    setSelectedModule("");
    setSelectedAction("");
    setSelectedScope("NONE");
  };

  // Get unique modules
  const modules = Array.from(new Set(allPermissions?.map((p) => p.module) || []));

  // Get available actions for selected module
  const actionsForModule = allPermissions?.filter((p) => p.module === selectedModule) || [];

  // Find the exact permission ID
  const selectedPermissionId = allPermissions?.find(
    (p) => p.module === selectedModule && p.action === selectedAction
  )?.id;

  const handleAssign = () => {
    if (selectedPermissionId && selectedScope) {
      assignMutation.mutate({
        permissionId: selectedPermissionId,
        accessScope: selectedScope,
      });
    }
  };

  return (
    <Dialog open={open} onOpenChange={(val) => {
      setOpen(val);
      if (!val) resetForm();
    }}>
      <DialogTrigger asChild>
        {trigger || <Button variant="outline">Add Permission</Button>}
      </DialogTrigger>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Assign Permission</DialogTitle>
          <DialogDescription>
            Grant a new permission to this role.
          </DialogDescription>
        </DialogHeader>
        
        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <Label>Module</Label>
            <Select 
              value={selectedModule} 
              onValueChange={(val) => {
                setSelectedModule(val);
                setSelectedAction(""); // Reset action when module changes
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select a module" />
              </SelectTrigger>
              <SelectContent>
                {permissionsLoading ? (
                  <SelectItem value="loading" disabled>Loading modules...</SelectItem>
                ) : (
                  modules.map((module) => (
                    <SelectItem key={module} value={module}>
                      {module.charAt(0).toUpperCase() + module.slice(1)}
                    </SelectItem>
                  ))
                )}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-2">
            <Label>Action</Label>
            <Select 
              value={selectedAction} 
              onValueChange={setSelectedAction}
              disabled={!selectedModule}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select an action" />
              </SelectTrigger>
              <SelectContent>
                {actionsForModule.map((permission) => {
                  // Don't show if already assigned
                  const isAssigned = rolePermissions?.some(rp => rp.id === permission.id);
                  return (
                    <SelectItem key={permission.id} value={permission.action} disabled={isAssigned}>
                      {permission.action.charAt(0).toUpperCase() + permission.action.slice(1)}
                      {isAssigned && " (Already assigned)"}
                    </SelectItem>
                  );
                })}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-2">
            <Label>Access Scope</Label>
            <Select 
              value={selectedScope} 
              onValueChange={setSelectedScope}
              disabled={!selectedAction}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select access scope" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="NONE">None</SelectItem>
                <SelectItem value="OWN">Own</SelectItem>
                <SelectItem value="TEAM">Team</SelectItem>
                <SelectItem value="ALL">All</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button 
            onClick={handleAssign} 
            disabled={!selectedPermissionId || !selectedScope || assignMutation.isPending}
          >
            {assignMutation.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin mr-2" />
            ) : null}
            Assign Permission
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
