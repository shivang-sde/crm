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
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { userApi } from "@/lib/api/users";
import { roleApi } from "@/lib/api/roles";
import { FieldGroup, Field, FieldLabel, FieldError, FieldDescription } from "@/components/ui/field";

const userSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  email: z.string().email('Invalid email address'),
  roleId: z.string().min(1, 'Role is required'),
  managerId: z.string().optional(),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  isActive: z.boolean(),
});

type UserFormValues = z.infer<typeof userSchema>;

function CreateUserForm() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const { data: roles, isLoading: rolesLoading } = useQuery({
    queryKey: ["roles"],
    queryFn: () => roleApi.getRoles(),
  });

  const { data: managers } = useQuery({
    queryKey: ["managers"],
    queryFn: () => userApi.getManagers(),
  });

  console.log("managers", managers);

  const form = useForm<UserFormValues>({
    resolver: zodResolver(userSchema) as any,
    defaultValues: {
      firstName: "",
      lastName: "",
      email: "",
      password: "",
      roleId: "",
      managerId: "",
      isActive: true,
    },
  });

  const selectedRoleId = form.watch("roleId");

  const selectedRole = roles?.find(
    (role) => role.id === selectedRoleId
  );

  const showManagerField = selectedRole &&
    !["ADMIN", "MANAGER", "RESELLER", "SUPERADMIN"].includes(selectedRole.name?.toUpperCase());



  const createMutation = useMutation({
    mutationFn: (data: UserFormValues) => userApi.createUser({
      firstName: data.firstName,
      lastName: data.lastName,
      email: data.email,
      password: data.password,
      roleId: data.roleId,
      managerId: data.managerId || null,
      isActive: data.isActive
    }),
    onSuccess: () => {
      toast.success("User created successfully");
      queryClient.invalidateQueries({ queryKey: ["users"] });
      router.push("/users");
    },
    onError: (error: any) => {
      toast.error(error?.response?.data?.error?.message || "Failed to create user");
    },
  });

  function onSubmit(data: UserFormValues) {
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
          <h1 className="text-2xl font-bold tracking-tight">Create User</h1>
          <p className="text-sm text-gray-500">Add a new user to the system and assign a role.</p>
        </div>
      </div>

      <div className="bg-white p-6 rounded-lg border shadow-sm">
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

            <Field data-invalid={!!errors.email}>
              <FieldLabel htmlFor="email">Email Address</FieldLabel>
              <Input
                id="email"
                type="email"
                placeholder="john.doe@example.com"
                aria-invalid={!!errors.email}
                {...form.register("email")}
              />
              <FieldError>{errors.email?.message}</FieldError>
            </Field>

            <Field data-invalid={!!errors.password}>
              <FieldLabel htmlFor="password">Initial Password</FieldLabel>
              <Input
                id="password"
                type="password"
                placeholder="Min 8 characters"
                aria-invalid={!!errors.password}
                {...form.register("password")}
              />
              <FieldError>{errors.password?.message}</FieldError>
            </Field>

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

            {showManagerField && (
              <Controller
                control={form.control}
                name="managerId"
                render={({ field }) => (
                  <Field data-invalid={!!errors.managerId}>
                    <FieldLabel>Manager</FieldLabel>

                    <Select
                      onValueChange={field.onChange}
                      value={field.value}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Select manager" />
                      </SelectTrigger>

                      <SelectContent>
                        {managers?.map((manager) => (
                          <SelectItem
                            key={manager.id}
                            value={manager.id}
                          >
                            {manager.firstName} {manager.lastName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>

                    <FieldDescription>
                      Select the manager this user reports to.
                    </FieldDescription>

                    <FieldError>
                      {errors.managerId?.message}
                    </FieldError>
                  </Field>
                )}
              />
            )}


            <Controller
              control={form.control}
              name="isActive"
              render={({ field }) => (
                <Field orientation="horizontal" className="justify-between rounded-lg border p-4">
                  <div className="flex flex-col gap-0.5">
                    <FieldLabel htmlFor="isActive" className="text-base font-medium">Active Account</FieldLabel>
                    <FieldDescription>
                      Determine if this user can immediately log in to the system.
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
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="mr-2 h-4 w-4" />
                  Create User
                </>
              )}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function CreateUserPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: 'admin', action: 'user_manage' }}>
      <CreateUserForm />
    </ProtectedRoute>
  );
}
