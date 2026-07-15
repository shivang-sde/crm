"use client";

import React, { useEffect, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Loader2, Save, Trash2, Users } from "lucide-react";

import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { roleApi } from "@/lib/api/roles";
import { FieldGroup, Field, FieldLabel, FieldError } from "@/components/ui/field";
import { PermissionMatrix } from "../components/PermissionMatrix";
import { AssignPermissionModal } from "../components/AssignPermissionModal";
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
import { Badge } from "@/components/ui/badge";

const updateRoleSchema = z.object({
  name: z.string().min(1, 'Role name is required'),
  description: z.string().optional(),
  parentRoleId: z.string().optional(),
});

type UpdateRoleFormValues = z.infer<typeof updateRoleSchema>;

function RoleDetailContent() {
  const router = useRouter();
  const params = useParams<{ roleId?: string | string[] }>();
  const rawRoleId = params?.roleId;
  const roleId = typeof rawRoleId === 'string' ? rawRoleId : rawRoleId?.[0] ?? '';
  const queryClient = useQueryClient();

  const [showDeleteDialog, setShowDeleteDialog] = useState(false);

  const { data: role, isLoading: roleLoading } = useQuery({
    queryKey: ["role", roleId],
    queryFn: () => roleApi.getRole(roleId),
    enabled: !!roleId,
  });

  const { data: roles } = useQuery({
    queryKey: ["roles"],
    queryFn: () => roleApi.getRoles(),
  });

  const form = useForm<UpdateRoleFormValues>({
    resolver: zodResolver(updateRoleSchema) as any,
    defaultValues: {
      name: "",
      description: "",
      parentRoleId: "",
    },
  });

  useEffect(() => {
    if (role) {
      form.reset({
        name: role.name,
        description: role.description || "",
        parentRoleId: role.parentRoleId || "",
      });
    }
  }, [role, form]);

  const updateMutation = useMutation({
    mutationFn: (data: UpdateRoleFormValues) => roleApi.updateRole(roleId, {
      name: data.name,
      description: data.description,
      parentRoleId: data.parentRoleId || undefined,
    }),
    onSuccess: () => {
      toast.success("Role updated successfully");
      queryClient.invalidateQueries({ queryKey: ["roles"] });
      queryClient.invalidateQueries({ queryKey: ["role", roleId] });
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.error?.message || "Failed to update role");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => roleApi.deleteRole(roleId),
    onSuccess: () => {
      toast.success("Role deleted successfully");
      queryClient.invalidateQueries({ queryKey: ["roles"] });
      router.push("/roles");
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.error?.message || "Failed to delete role");
    },
  });

  function onSubmit(data: UpdateRoleFormValues) {
    updateMutation.mutate(data);
  }

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

  const { errors } = form.formState;

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
              {role.isDefault && (
                <Badge variant="secondary">Default</Badge>
              )}
            </div>
            <p className="text-sm text-gray-500">Manage role details and its permissions matrix.</p>
          </div>
        </div>
        
        {!role.isDefault && (
          <Button 
            variant="destructive" 
            onClick={() => setShowDeleteDialog(true)}
            className="flex items-center gap-2"
          >
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

            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
              <FieldGroup>
                <Field data-invalid={!!errors.name}>
                  <FieldLabel htmlFor="name">Role Name</FieldLabel>
                  <Input 
                    id="name"
                    disabled={role.isDefault} 
                    placeholder="Role Name" 
                    aria-invalid={!!errors.name}
                    {...form.register("name")} 
                  />
                  {role.isDefault && (
                    <p className="text-xs text-amber-600">Default roles cannot be renamed.</p>
                  )}
                  <FieldError>{errors.name?.message}</FieldError>
                </Field>

                <Field data-invalid={!!errors.description}>
                  <FieldLabel htmlFor="description">Description</FieldLabel>
                  <Textarea 
                    id="description"
                    placeholder="Brief description..." 
                    className="resize-none" 
                    aria-invalid={!!errors.description}
                    {...form.register("description")} 
                  />
                  <FieldError>{errors.description?.message}</FieldError>
                </Field>

                <Controller
                  control={form.control}
                  name="parentRoleId"
                  render={({ field }) => (
                    <Field data-invalid={!!errors.parentRoleId}>
                      <FieldLabel htmlFor="parentRoleId">Parent Role</FieldLabel>
                      <Select onValueChange={field.onChange} value={field.value}>
                        <SelectTrigger id="parentRoleId" aria-invalid={!!errors.parentRoleId}>
                          <SelectValue placeholder="Select a parent role" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="">None (Top Level)</SelectItem>
                          {roles?.filter(r => r.id !== roleId).map((r) => (
                            <SelectItem key={r.id} value={r.id}>
                              {r.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FieldError>{errors.parentRoleId?.message}</FieldError>
                    </Field>
                  )}
                />
              </FieldGroup>

              <Button type="submit" disabled={updateMutation.isPending || (role.isDefault && !form.formState.isDirty)} className="w-full">
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
            </form>
          </div>
        </div>

        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white p-6 rounded-lg border shadow-sm">
            <div className="flex justify-between items-center mb-6">
              <div>
                <h3 className="text-lg font-bold">Permissions Matrix</h3>
                <p className="text-sm text-gray-500 mt-1">Configure data access scopes across different modules.</p>
              </div>
              <AssignPermissionModal roleId={roleId} />
            </div>

            <PermissionMatrix roleId={roleId} />
          </div>
        </div>
      </div>

      <AlertDialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Role</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete the role "{role?.name}"? 
              This action cannot be undone and will affect {role?.userCount || 0} user(s) currently assigned to this role.
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
    <ProtectedRoute requiredPermission={{ module: 'admin', action: 'role_manage' }}>
      <RoleDetailContent />
    </ProtectedRoute>
  );
}
