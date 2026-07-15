"use client";

import React, { useEffect } from "react";
import { useRouter, useParams } from "next/navigation";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Loader2, Save } from "lucide-react";

import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { userApi } from "@/lib/api/users";
import { roleApi } from "@/lib/api/roles";
import { FieldGroup, Field, FieldLabel, FieldError, FieldDescription } from "@/components/ui/field";

const updateUserSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  roleId: z.string().min(1, 'Role is required'),
  isActive: z.boolean()
});

type UpdateUserFormValues = z.infer<typeof updateUserSchema>;

function EditUserForm() {
  const router = useRouter();
  const params = useParams<{ userId?: string | string[] }>();
  const rawUserId = params?.userId;
  const userId = typeof rawUserId === 'string' ? rawUserId : rawUserId?.[0] ?? '';
  const queryClient = useQueryClient();

  const { data: user, isLoading: userLoading } = useQuery({
    queryKey: ["user", userId],
    queryFn: () => userApi.getUser(userId),
    enabled: !!userId,
  });

  const { data: roles, isLoading: rolesLoading } = useQuery({
    queryKey: ["roles"],
    queryFn: () => roleApi.getRoles(),
  });

  const form = useForm<UpdateUserFormValues>({
    resolver: zodResolver(updateUserSchema) as any,
    defaultValues: {
      firstName: "",
      lastName: "",
      roleId: "",
      isActive: true,
    },
  });

  useEffect(() => {
    if (user) {
      form.reset({
        firstName: user.firstName,
        lastName: user.lastName,
        roleId: user.roleId || "",
        isActive: user.isActive,
      });
    }
  }, [user, form]);

  const updateMutation = useMutation({
    mutationFn: (data: UpdateUserFormValues) => userApi.updateUser(userId, data),
    onSuccess: () => {
      toast.success("User updated successfully");
      queryClient.invalidateQueries({ queryKey: ["users"] });
      queryClient.invalidateQueries({ queryKey: ["user", userId] });
      router.push("/users");
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.error?.message || "Failed to update user");
    },
  });

  function onSubmit(data: UpdateUserFormValues) {
    updateMutation.mutate(data);
  }

  if (userLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
      </div>
    );
  }

  const { errors } = form.formState;

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Edit User</h1>
          <p className="text-sm text-gray-500">Update user details and access level.</p>
        </div>
      </div>

      <div className="bg-white p-6 rounded-lg border shadow-sm">
        <div className="mb-6 pb-6 border-b">
          <h3 className="text-lg font-medium">Account Details</h3>
          <p className="text-sm text-gray-500 mt-1">Email address cannot be changed.</p>
          <div className="mt-4">
            <FieldLabel>Email Address</FieldLabel>
            <Input disabled value={user?.email || ""} className="mt-1 bg-gray-50" />
          </div>
        </div>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
          <FieldGroup>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <Field data-invalid={!!errors.firstName}>
                <FieldLabel htmlFor="firstName">First Name</FieldLabel>
                <Input 
                  id="firstName" 
                  placeholder="John" 
                  aria-invalid={!!errors.firstName}
                  {...form.register("firstName")} 
                />
                <FieldError>{errors.firstName?.message}</FieldError>
              </Field>

              <Field data-invalid={!!errors.lastName}>
                <FieldLabel htmlFor="lastName">Last Name</FieldLabel>
                <Input 
                  id="lastName" 
                  placeholder="Doe" 
                  aria-invalid={!!errors.lastName}
                  {...form.register("lastName")} 
                />
                <FieldError>{errors.lastName?.message}</FieldError>
              </Field>
            </div>

            <Controller
              control={form.control}
              name="roleId"
              render={({ field }) => (
                <Field data-invalid={!!errors.roleId}>
                  <FieldLabel htmlFor="roleId">Role</FieldLabel>
                  <Select onValueChange={field.onChange} value={field.value}>
                    <SelectTrigger id="roleId" aria-invalid={!!errors.roleId}>
                      <SelectValue placeholder="Select a role" />
                    </SelectTrigger>
                    <SelectContent>
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
                  <FieldError>{errors.roleId?.message}</FieldError>
                </Field>
              )}
            />

            <Controller
              control={form.control}
              name="isActive"
              render={({ field }) => (
                <Field orientation="horizontal" className="justify-between rounded-lg border p-4">
                  <div className="flex flex-col gap-0.5">
                    <FieldLabel htmlFor="isActive" className="text-base font-medium">Active Account</FieldLabel>
                    <FieldDescription>
                      Determine if this user can currently log in to the system.
                    </FieldDescription>
                  </div>
                  <Switch
                    id="isActive"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                </Field>
              )}
            />
          </FieldGroup>

          <div className="flex justify-end pt-4 border-t gap-4">
            <Button type="button" variant="outline" onClick={() => router.back()}>
              Cancel
            </Button>
            <Button type="submit" disabled={updateMutation.isPending}>
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
          </div>
        </form>
      </div>
    </div>
  );
}

export default function EditUserPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: 'admin', action: 'user_manage' }}>
      <EditUserForm />
    </ProtectedRoute>
  );
}
