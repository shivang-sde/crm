"use client";

import React from "react";
import { useRouter } from "next/navigation";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Loader2, Save } from "lucide-react";

import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { roleApi } from "@/lib/api/roles";
import { FieldGroup, Field, FieldLabel, FieldError, FieldDescription } from "@/components/ui/field";

const roleSchema = z.object({
  name: z.string().min(1, 'Role name is required'),
  description: z.string().optional(),
  parentRoleId: z.string().optional(),
});

type RoleFormValues = z.infer<typeof roleSchema>;

function CreateRoleForm() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const { data: roles, isLoading: rolesLoading } = useQuery({
    queryKey: ["roles"],
    queryFn: () => roleApi.getRoles(),
  });

  const form = useForm<RoleFormValues>({
    resolver: zodResolver(roleSchema) as any,
    defaultValues: {
      name: "",
      description: "",
      parentRoleId: "",
    },
  });

  const createMutation = useMutation({
    mutationFn: (data: RoleFormValues) => roleApi.createRole({
      name: data.name,
      description: data.description,
      parentRoleId: data.parentRoleId || undefined,
    }),
    onSuccess: (newRole) => {
      toast.success("Role created successfully");
      queryClient.invalidateQueries({ queryKey: ["roles"] });
      // Redirect to the role detail page to assign permissions
      router.push(`/roles/${newRole.id}`);
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.error?.message || "Failed to create role");
    },
  });

  function onSubmit(data: RoleFormValues) {
    createMutation.mutate(data);
  }

  const { errors } = form.formState;

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Create Role</h1>
          <p className="text-sm text-gray-500">Add a new role and configure its basic details.</p>
        </div>
      </div>

      <div className="bg-white p-6 rounded-lg border shadow-sm">
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
          <FieldGroup>
            <Field data-invalid={!!errors.name}>
              <FieldLabel htmlFor="name">Role Name</FieldLabel>
              <Input 
                id="name" 
                placeholder="e.g. Regional Manager" 
                aria-invalid={!!errors.name}
                {...form.register("name")} 
              />
              <FieldError>{errors.name?.message}</FieldError>
            </Field>

            <Field data-invalid={!!errors.description}>
              <FieldLabel htmlFor="description">Description (Optional)</FieldLabel>
              <Textarea 
                id="description" 
                placeholder="Brief description of what this role entails..." 
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
                  <FieldLabel htmlFor="parentRoleId">Parent Role (Optional)</FieldLabel>
                  <Select onValueChange={field.onChange} value={field.value}>
                    <SelectTrigger id="parentRoleId" aria-invalid={!!errors.parentRoleId}>
                      <SelectValue placeholder="Select a parent role (for hierarchy)" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="">None (Top Level)</SelectItem>
                      {rolesLoading ? (
                        <SelectItem value="loading" disabled>Loading roles...</SelectItem>
                      ) : (
                        roles?.map((role) => (
                          <SelectItem key={role.id} value={role.id}>
                            {role.name}
                          </SelectItem>
                        ))
                      )}
                    </SelectContent>
                  </Select>
                  <FieldDescription>
                    Used to build organizational hierarchies for data access.
                  </FieldDescription>
                  <FieldError>{errors.parentRoleId?.message}</FieldError>
                </Field>
              )}
            />
          </FieldGroup>

          <div className="flex justify-end pt-4 border-t gap-4">
            <Button type="button" variant="outline" onClick={() => router.back()}>
              Cancel
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="mr-2 h-4 w-4" />
                  Create Role & Next
                </>
              )}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function CreateRolePage() {
  return (
    <ProtectedRoute requiredPermission={{ module: 'admin', action: 'role_manage' }}>
      <CreateRoleForm />
    </ProtectedRoute>
  );
}
