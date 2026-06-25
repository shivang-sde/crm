"use client";

import React, { useEffect } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { userApi } from "@/lib/api/users";
import { roleApi } from "@/lib/api/roles";
import { FieldGroup, Field, FieldLabel, FieldError, FieldDescription } from "@/components/ui/field";
import { User } from "@/types/rbac";

// Create schema (includes email and password)
const createUserSchema = z.object({
  firstName: z.string().min(1, "First name is required"),
  lastName: z.string().min(1, "Last name is required"),
  email: z.string().email("Invalid email address"),
  roleId: z.string().min(1, "Role is required"),
  managerId: z.string().optional(),
  password: z.string().min(8, "Password must be at least 8 characters"),
  isActive: z.boolean().default(true),
});

// Edit schema (no email or password)
const editUserSchema = z.object({
  firstName: z.string().min(1, "First name is required"),
  lastName: z.string().min(1, "Last name is required"),
  roleId: z.string().min(1, "Role is required"),
  managerId: z.string().optional(),
  isActive: z.boolean().default(true),
});

type CreateUserFormValues = z.infer<typeof createUserSchema>;
type EditUserFormValues = z.infer<typeof editUserSchema>;
type UserFormValues = Omit<CreateUserFormValues, "email" | "password"> &
  Partial<Pick<CreateUserFormValues, "email" | "password">>;

interface UserFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  mode: "create" | "edit";
  userId?: string;
  onSuccess?: () => void;
}

export function UserFormModal({
  isOpen,
  onClose,
  mode,
  userId,
  onSuccess,
}: UserFormModalProps) {
  const queryClient = useQueryClient();
  const isEditMode = mode === "edit";

  // Fetch user data when in edit mode
  const { data: user, isLoading: userLoading } = useQuery({
    queryKey: ["user", userId],
    queryFn: () => userApi.getUser(userId!),
    enabled: isEditMode && !!userId,
  });

  // Fetch roles
  const { data: roles, isLoading: rolesLoading } = useQuery({
    queryKey: ["roles"],
    queryFn: () => roleApi.getRoles(),
  });

  // Fetch managers
  const { data: managers } = useQuery({
    queryKey: ["managers"],
    queryFn: () => userApi.getManagers(),
  });

  // Select appropriate schema and form values based on mode
  const schema = isEditMode ? editUserSchema : createUserSchema;
  const defaultValues = isEditMode
    ? {
        firstName: "",
        lastName: "",
        roleId: "",
        managerId: "",
        isActive: true,
      }
    : {
        firstName: "",
        lastName: "",
        email: "",
        password: "",
        roleId: "",
        managerId: "",
        isActive: true,
      };

  const form = useForm<UserFormValues>({
    resolver: zodResolver(schema) as any,
    defaultValues: defaultValues as any,
  });

  // Reset form when user data loads
  useEffect(() => {
    if (user && isEditMode) {
      form.reset({
        firstName: user.firstName,
        lastName: user.lastName,
        roleId: user.roleId || "",
        managerId: user.managerId || "",
        isActive: user.isActive,
      } as any);
    }
  }, [user, form, isEditMode]);

  const selectedRoleId = form.watch("roleId");
  const selectedRole = roles?.find((role) => role.id === selectedRoleId);
  const showManagerField =
    selectedRole &&
    !["ADMIN", "MANAGER", "RESELLER", "SUPERADMIN"].includes(
      selectedRole.name?.toUpperCase() || ""
    );

  // Create mutation
  const createMutation = useMutation({
    mutationFn: (data: CreateUserFormValues) =>
      userApi.createUser({
        firstName: data.firstName,
        lastName: data.lastName,
        email: data.email,
        password: data.password,
        roleId: data.roleId,
        managerId: data.managerId || null,
        isActive: data.isActive,
      }),
    onSuccess: () => {
      toast.success("User created successfully");
      queryClient.invalidateQueries({ queryKey: ["users"] });
      form.reset();
      onClose();
      onSuccess?.();
    },
    onError: (error: any) => {
      toast.error(
        error?.response?.data?.error?.message || "Failed to create user"
      );
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: (data: EditUserFormValues) =>
      userApi.updateUser(userId!, {
        firstName: data.firstName,
        lastName: data.lastName,
        roleId: data.roleId,
        managerId: data.managerId || null,
        isActive: data.isActive,
      }),
    onSuccess: () => {
      toast.success("User updated successfully");
      queryClient.invalidateQueries({ queryKey: ["users"] });
      queryClient.invalidateQueries({ queryKey: ["user", userId] });
      form.reset();
      onClose();
      onSuccess?.();
    },
    onError: (error: any) => {
      toast.error(
        error?.response?.data?.error?.message || "Failed to update user"
      );
    },
  });

  function onSubmit(data: UserFormValues) {
    if (isEditMode) {
      updateMutation.mutate(data as EditUserFormValues);
    } else {
      createMutation.mutate(data as CreateUserFormValues);
    }
  }

  const { errors } = form.formState;
  const isLoading = isEditMode ? userLoading : false;
  const isPending = createMutation.isPending || updateMutation.isPending;

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>
            {isEditMode ? "Edit User" : "Create User"}
          </DialogTitle>
          <DialogDescription>
            {isEditMode
              ? "Update user details and access level."
              : "Add a new user to the system and assign a role."}
          </DialogDescription>
        </DialogHeader>

        {isLoading ? (
          <div className="flex h-64 items-center justify-center">
            <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
          </div>
        ) : (
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
            <FieldGroup>
              {/* Email field - only visible in create mode and read-only in edit mode */}
              {!isEditMode && (
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
              )}

              {/* Show email as read-only in edit mode */}
              {isEditMode && (
                <Field>
                  <FieldLabel htmlFor="emailDisplay">Email Address</FieldLabel>
                  <Input
                    id="emailDisplay"
                    type="email"
                    value={user?.email || ""}
                    disabled
                    className="bg-gray-50"
                  />
                  <FieldDescription>
                    Email address cannot be changed.
                  </FieldDescription>
                </Field>
              )}

              {/* Name fields */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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

              {/* Password field - only in create mode */}
              {!isEditMode && (
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
              )}

              {/* Role field */}
              <Controller
                control={form.control}
                name="roleId"
                render={({ field }) => (
                  <Field data-invalid={!!errors.roleId}>
                    <FieldLabel htmlFor="roleId">Role</FieldLabel>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <SelectTrigger
                        id="roleId"
                        aria-invalid={!!errors.roleId}
                      >
                        <SelectValue placeholder="Select a role" />
                      </SelectTrigger>
                      <SelectContent>
                        {rolesLoading ? (
                          <SelectItem value="loading" disabled>
                            Loading roles...
                          </SelectItem>
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

              {/* Manager field - conditional */}
              {showManagerField && (
                <Controller
                  control={form.control}
                  name="managerId"
                  render={({ field }) => (
                    <Field data-invalid={!!errors.managerId}>
                      <FieldLabel htmlFor="managerId">Manager</FieldLabel>
                      <Select
                        onValueChange={field.onChange}
                        value={field.value || ""}
                      >
                        <SelectTrigger id="managerId">
                          <SelectValue placeholder="Select manager" />
                        </SelectTrigger>
                        <SelectContent>
                          {managers?.map((manager) => (
                            <SelectItem key={manager.id} value={manager.id}>
                              {manager.firstName} {manager.lastName}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FieldDescription>
                        Select the manager this user reports to.
                      </FieldDescription>
                      <FieldError>{errors.managerId?.message}</FieldError>
                    </Field>
                  )}
                />
              )}

              {/* Active status field */}
              <Controller
                control={form.control}
                name="isActive"
                render={({ field }) => (
                  <Field
                    orientation="horizontal"
                    className="justify-between rounded-lg border p-4"
                  >
                    <div className="flex flex-col gap-0.5">
                      <FieldLabel
                        htmlFor="isActive"
                        className="text-base font-medium"
                      >
                        Active Account
                      </FieldLabel>
                      <FieldDescription>
                        {isEditMode
                          ? "Determine if this user can currently log in to the system."
                          : "Determine if this user can immediately log in to the system."}
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

            {/* Form actions */}
            <div className="flex justify-end gap-3 pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={onClose}
                disabled={isPending}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    {isEditMode ? "Updating..." : "Creating..."}
                  </>
                ) : isEditMode ? (
                  "Save Changes"
                ) : (
                  "Create User"
                )}
              </Button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
