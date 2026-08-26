"use client";

import React, { useState } from "react";

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
import { Permission, AccessScope } from "@/types/rbac";

const SCOPES: AccessScope[] = ["ALL", "TEAM", "OWN", "NONE"];

interface AssignPermissionModalProps {
  catalog: Permission[];
  /** Permissions already in the draft; duplicates are disabled. */
  existingIds: Set<string>;
  onAdd: (permissionId: string, accessScope: AccessScope) => void;
  trigger?: React.ReactNode;
}

/** Adds a single catalog permission to the local draft. No API calls. */
export function AssignPermissionModal({ catalog, existingIds, onAdd, trigger }: AssignPermissionModalProps) {
  const [open, setOpen] = useState(false);
  const [selectedModule, setSelectedModule] = useState<string>("");
  const [selectedAction, setSelectedAction] = useState<string>("");
  const [selectedScope, setSelectedScope] = useState<AccessScope>("NONE");

  const resetForm = () => {
    setSelectedModule("");
    setSelectedAction("");
    setSelectedScope("NONE");
  };

  const modules = Array.from(new Set(catalog.map((p) => p.module))).sort();
  const actionsForModule = catalog.filter((p) => p.module === selectedModule);
  const selectedPermission = catalog.find(
    (p) => p.module === selectedModule && p.action === selectedAction
  );

  const handleAdd = () => {
    if (selectedPermission && !existingIds.has(selectedPermission.id)) {
      onAdd(selectedPermission.id, selectedScope);
      setOpen(false);
      resetForm();
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(val) => {
        setOpen(val);
        if (!val) resetForm();
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Add Permission</DialogTitle>
          <DialogDescription>Add a permission to the role being configured.</DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <Label>Module</Label>
            <Select
              value={selectedModule}
              onValueChange={(val) => {
                setSelectedModule(val);
                setSelectedAction("");
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select a module" />
              </SelectTrigger>
              <SelectContent>
                {modules.map((module) => (
                  <SelectItem key={module} value={module} className="capitalize">
                    {module.charAt(0).toUpperCase() + module.slice(1)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-2">
            <Label>Action</Label>
            <Select value={selectedAction} onValueChange={setSelectedAction} disabled={!selectedModule}>
              <SelectTrigger>
                <SelectValue placeholder="Select an action" />
              </SelectTrigger>
              <SelectContent>
                {actionsForModule.map((permission) => {
                  const isAssigned = existingIds.has(permission.id);
                  return (
                    <SelectItem key={permission.id} value={permission.action} disabled={isAssigned}>
                      {permission.action.charAt(0).toUpperCase() + permission.action.slice(1)}
                      {isAssigned && " (Already added)"}
                    </SelectItem>
                  );
                })}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-2">
            <Label>Access Scope</Label>
            <Select value={selectedScope} onValueChange={(val) => setSelectedScope(val as AccessScope)}>
              <SelectTrigger>
                <SelectValue placeholder="Select access scope" />
              </SelectTrigger>
              <SelectContent>
                {SCOPES.map((scope) => (
                  <SelectItem key={scope} value={scope}>
                    {scope}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleAdd} disabled={!selectedPermission || existingIds.has(selectedPermission?.id ?? "")}>
            Add
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
