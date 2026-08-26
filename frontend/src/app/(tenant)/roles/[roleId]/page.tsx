"use client";

import React, { useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Loader2, Save, Trash2, Users } from "lucide-react";

import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { roleApi } from "@/lib/api/roles";
import { apiErrorMessage } from "@/lib/api/api-utils";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Badge } from "@/components/ui/badge";
import { Role, RolePermission } from "@/types/rbac";
import { RolePermissionEditor } from "../components/RolePermissionEditor";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

function RoleDetailContent() {
  const router = useRouter();
  const params = useParams<{ roleId?: string | string[] }>();
  const rawRoleId = params?.roleId;
  const roleId = typeof rawRoleId === "string" ? rawRoleId : rawRoleId?.[0] ?? "";

  const { data: role, isLoading: roleLoading } = useQuery({
    queryKey: ["role", roleId],
    queryFn: () => roleApi.getRole(roleId),
    enabled: !!roleId,
  });

  if (roleLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
      </div>
    );
  }

  if (!role) {
    return (
      <div className="text-center p-12">
        <h2 className="text-xl font-semibold mb-2">Role not found</h2>
        <Button onClick={() => router.push("/roles")}>Back to Roles</Button>
      </div>
    );
  }

  // key={role.id}: the editor's local state re-seeds when navigating between
  // roles without effects.
  return <RoleDetailView key={role.id} role={role} />;
}

function RoleDetailView({ role }: { role: Role }) {
  const router = useRouter();
  const queryClient = useQueryClient();

  // Local draft: edited freely, submitted as one complete set on save.
  const [name, setName] = useState(role.name);
  const [description, setDescription] = useState(role.description || "");
  const [baseline, setBaseline] = useState<RolePermission[]>(() =>
    (role.permissions ?? []).map((p) => ({ ...p }))
  );
  const [draft, setDraft] = useState<RolePermission[]>(() =>
    (role.permissions ?? []).map((p) => ({ ...p }))
  );

  const [showDeleteDialog, setShowDeleteDialog] = useState(false);

  const updateMutation = useMutation({
    mutationFn: () =>
      roleApi.updateRole(role.id, {
        name,
        description: description || undefined,
        // Complete explicit set — the backend replaces all rows atomically
        // (PUT /roles/{roleId}) and validates delegation on the result.
        permissions: draft.map((p) => ({ permissionId: p.id, accessScope: p.accessScope })),
      }),
    onSuccess: () => {
      toast.success("Role updated successfully");
      setBaseline(draft.map((p) => ({ ...p })));
      queryClient.invalidateQueries({ queryKey: ["roles"] });
      queryClient.invalidateQueries({ queryKey: ["role", role.id] });
      queryClient.invalidateQueries({ queryKey: ["role-permissions", role.id] });
    },
    onError: (error: unknown) => {
      // Backend stays authoritative (RBAC-6 delegation etc.); surface its message.
      toast.error(apiErrorMessage(error, "Failed to update role"));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => roleApi.deleteRole(role.id),
    onSuccess: () => {
      toast.success("Role deleted successfully");
      queryClient.invalidateQueries({ queryKey: ["roles"] });
      router.push("/roles");
    },
    onError: (error: unknown) => {
      toast.error(apiErrorMessage(error, "Failed to delete role"));
    },
  });

  const handleSave = () => {
    if (!name.trim()) {
      toast.error("Role name is required");
      return;
    }
    if (draft.length === 0) {
      // Backend UpdateRoleRequest requires @NotEmpty permissions; fail fast client-side.
      toast.error("A role must keep at least one permission.");
      return;
    }
    updateMutation.mutate();
  };

  const isDirty =
    name !== role.name ||
    (description || "") !== (role.description || "") ||
    draft.length !== baseline.length ||
    draft.some((p) => baseline.find((b) => b.id === p.id)?.accessScope !== p.accessScope);

  return (
    <div className="max-w-5xl mx-auto space-y-8">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={() => router.back()}>
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold tracking-tight">{role.name}</h1>
              {role.isDefault && <Badge variant="secondary">Default</Badge>}
            </div>
            <p className="text-sm text-gray-500">Manage role details and its permissions matrix.</p>
          </div>
        </div>

        {!role.isDefault && (
          <Button variant="destructive" onClick={() => setShowDeleteDialog(true)} className="flex items-center gap-2">
            <Trash2 className="w-4 h-4" />
            Delete Role
          </Button>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-1 space-y-6">
          <div className="bg-white p-6 rounded-lg border shadow-sm">
            <div className="flex items-center gap-2 text-gray-600 mb-6 pb-6 border-b">
              <Users className="w-5 h-5" />
              <span className="font-medium">{role.userCount || 0} Users</span>
              <span className="text-sm">assigned to this role</span>
            </div>

            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="name">Role Name</FieldLabel>
                <Input id="name" placeholder="Role Name" value={name} onChange={(e) => setName(e.target.value)} />
                {role.isDefault && <p className="text-xs text-amber-600">Default roles cannot be renamed.</p>}
              </Field>

              <Field>
                <FieldLabel htmlFor="description">Description</FieldLabel>
                <Textarea
                  id="description"
                  placeholder="Brief description..."
                  className="resize-none"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </Field>
            </FieldGroup>

            <Button
              onClick={handleSave}
              disabled={!isDirty || updateMutation.isPending || !name.trim()}
              className="w-full mt-6"
            >
              {updateMutation.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="mr-2 h-4 w-4" />
                  Save Changes
                </>
              )}
            </Button>
            <p className="text-xs text-gray-400 mt-2">
              Saves role info and the complete permission set together.
            </p>
          </div>
        </div>

        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white p-6 rounded-lg border shadow-sm">
            <div className="mb-6">
              <h3 className="text-lg font-bold">Permissions Matrix</h3>
              <p className="text-sm text-gray-500 mt-1">Configure data access scopes across different modules.</p>
            </div>

            <RolePermissionEditor draft={draft} baseline={baseline} onChange={setDraft} />
          </div>
        </div>
      </div>

      <AlertDialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Role</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete the role &quot;{role.name}&quot;?
              This action cannot be undone and will affect {role.userCount || 0} user(s) currently assigned to this
              role.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-red-600 hover:bg-red-700"
              onClick={() => deleteMutation.mutate()}
            >
              {deleteMutation.isPending ? "Deleting..." : "Delete Role"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

export default function RoleDetailPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "admin", action: "role_manage" }}>
      <RoleDetailContent />
    </ProtectedRoute>
  );
}
