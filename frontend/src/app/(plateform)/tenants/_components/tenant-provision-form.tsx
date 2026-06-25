"use client";

import React from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Save } from "lucide-react";

import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { tenantApi } from "@/lib/api/tenants";
import { TenantProvisionRequest } from "@/types/tenant";
import { useAuthStore } from "@/lib/store/authStore";

function TenantProvisionForm() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const userRole = useAuthStore((state) => state.userRole);
  
  const isReseller = userRole === "RESELLER";
  const [form, setForm] = React.useState<TenantProvisionRequest>({
    companyName: "",
    resellerId: isReseller ? user?.id || "" : "", // Auto-populated for RESELLER, hidden from UI
    admin: {
      email: "",
      password: "",
      firstName: "",
      lastName: "",
    },
  });
  const [error, setError] = React.useState<string | null>(null);

  const updateRoot = (key: keyof TenantProvisionRequest, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const updateAdmin = (key: keyof TenantProvisionRequest["admin"], value: string) => {
    setForm((current) => ({
      ...current,
      admin: {
        ...current.admin,
        [key]: value,
      },
    }));
  };

  const mutation = useMutation({
    mutationFn: (payload: TenantProvisionRequest) => tenantApi.provisionTenant(payload),
    onSuccess: () => {
      toast.success("Tenant provisioned successfully");
      router.push("/users");
    },
    onError: (requestError: unknown) => {
      const message =
        typeof requestError === "object" &&
        requestError !== null &&
        "response" in requestError
          ? (requestError as { response?: { data?: { error?: { message?: string } } } }).response?.data?.error?.message
          : undefined;
      setError(message ?? "Failed to provision tenant");
      toast.error(message ?? "Failed to provision tenant");
    },
  });

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.back()} aria-label="Go back">
          <ArrowLeft />
        </Button>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Provision Tenant</h1>
          <p className="text-sm text-muted-foreground">Create a tenant and its first admin through the unified provisioning flow.</p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Tenant + Admin</CardTitle>
          <CardDescription>Reseller users are automatically attached by the backend. Superadmins may optionally provide a reseller user ID.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="flex flex-col gap-5"
            onSubmit={(event) => {
              event.preventDefault();
              setError(null);
              mutation.mutate({
                ...form,
                resellerId: form.resellerId || null,
              });
            }}
          >
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="companyName">Company Name</FieldLabel>
                <Input
                  id="companyName"
                  value={form.companyName}
                  onChange={(event) => updateRoot("companyName", event.target.value)}
                  required
                />
              </Field>

              {/* Only show Reseller ID field for SUPERADMIN - completely hidden for RESELLER */}
              {!isReseller && (
                <Field>
                  <FieldLabel htmlFor="resellerId">Reseller User ID (Optional)</FieldLabel>
                  <Input
                    id="resellerId"
                    value={form.resellerId ?? ""}
                    onChange={(event) => updateRoot("resellerId", event.target.value)}
                    placeholder="Optional - leave blank for direct tenant"
                  />
                  <FieldDescription>Provide a reseller user ID to associate this tenant with a reseller.</FieldDescription>
                </Field>
              )}

              {/* Optional: Show a subtle indicator for RESELLER that their ID will be used */}

              <div className="grid gap-4 md:grid-cols-2">
                <Field>
                  <FieldLabel htmlFor="adminFirstName">Admin First Name</FieldLabel>
                  <Input
                    id="adminFirstName"
                    value={form.admin.firstName}
                    onChange={(event) => updateAdmin("firstName", event.target.value)}
                    required
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="adminLastName">Admin Last Name</FieldLabel>
                  <Input
                    id="adminLastName"
                    value={form.admin.lastName}
                    onChange={(event) => updateAdmin("lastName", event.target.value)}
                    required
                  />
                </Field>
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <Field>
                  <FieldLabel htmlFor="adminEmail">Admin Email</FieldLabel>
                  <Input
                    id="adminEmail"
                    type="email"
                    value={form.admin.email}
                    onChange={(event) => updateAdmin("email", event.target.value)}
                    required
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="adminPassword">Admin Password</FieldLabel>
                  <Input
                    id="adminPassword"
                    type="password"
                    minLength={8}
                    value={form.admin.password}
                    onChange={(event) => updateAdmin("password", event.target.value)}
                    required
                  />
                </Field>
              </div>

              {error && <FieldError>{error}</FieldError>}
            </FieldGroup>

            <div className="flex justify-end gap-3 border-t pt-4">
              <Button type="button" variant="outline" onClick={() => router.back()}>
                Cancel
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                <Save data-icon="inline-start" />
                {mutation.isPending ? "Provisioning..." : "Provision Tenant"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

export default function TenantProvisionPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "tenant", action: "write" }} >
      <TenantProvisionForm />
    </ProtectedRoute>
  );
}
