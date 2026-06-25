"use client";

import React from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Save } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { tenantApi } from "@/lib/api/tenants";
import { TenantProvisionRequest, TenantProvisionResponse, TenantResponse, TenantUpdateRequest } from "@/types/tenant";
import { useAuthStore } from "@/lib/store/authStore";
import { Tenant } from "@/types/auth";

interface TenantProvisionModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tenant?: TenantResponse;
  mode: "create" | "edit";
  onSuccess?: () => void;
}

export function TenantProvisionModal({ open, mode, tenant, onOpenChange, onSuccess }: TenantProvisionModalProps) {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const userRole = useAuthStore((state) => state.userRole);
  
  const isReseller = userRole === "RESELLER";
  
  const [form, setForm] = React.useState<TenantProvisionRequest>({
    companyName: "",
    maxUsers: 10,
    companyEmail: "",
    companyPhone: "",
    website: "",
    subscriptionEndDate: new Date(Date.now() + 30*24*60*60*1000).toISOString().split("T")[0],
    resellerId: isReseller ? user?.id || "" : "",
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

  const mutation = useMutation<
  TenantResponse | TenantProvisionResponse,
  Error,
  TenantUpdateRequest | TenantProvisionRequest
>({
  mutationFn: (payload) => {
    if (mode === "edit" && tenant) {
      return tenantApi.updateTenant(tenant.id, payload as TenantUpdateRequest);
    }
    return tenantApi.provisionTenant(payload as TenantProvisionRequest);
  },
    onSuccess: () => {
      toast.success(mode === "edit" ? "Tenant updated successfully" : "Tenant provisioned successfully");
      queryClient.invalidateQueries({ queryKey: ["tenants"] });
      resetForm();
      onOpenChange(false);
      onSuccess?.();
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

  const resetForm = () => {
    setForm({
      companyName: "",
      maxUsers: 10,
    companyEmail: "",
    companyPhone: "",
    website: "",
    subscriptionEndDate: new Date(Date.now() + 30*24*60*60*1000).toISOString().split("T")[0],
      resellerId: isReseller ? user?.id || "" : "",
      admin: {
        email: "",
        password: "",
        firstName: "",
        lastName: "",
      },
    });
    setError(null);
  };

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    
    const payload = {
      ...form,
      resellerId: isReseller ? user?.id || null : form.resellerId || null,
    };
    
    mutation.mutate(payload);
  };


  React.useEffect(() => {
  if (mode === "edit" && tenant) {
    console.log("tenant edit set", tenant)
    setForm((current) => ({
      ...current,

      companyName: tenant.name,
      companyEmail: tenant.companyEmail ?? "",
      companyPhone: tenant.companyPhone ?? "",
      website: tenant.website ?? "",

      maxUsers: tenant.maxUsers ?? 10,

      subscriptionEndDate:
        tenant.subscriptionEndDate?.split("T")[0] ?? "",

      addressLine1: tenant.addressLine1 ?? "",
      city: tenant.city ?? "",
      state: tenant.state ?? "",
      postalCode: tenant.postalCode ?? "",
      country: tenant.country ?? "",
    }));
  }
}, [tenant, mode]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange} >
      <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{mode === "edit" ? "Update Tenant" : "Provision New Tenant"}</DialogTitle>
          <DialogDescription>
            { mode === "edit" ?  "Update the tenant details." : isReseller 
              ? `Provision a new tenant under your reseller account.` 
              : "Create a tenant and its first admin user."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
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

            <Field>
  <FieldLabel>Maximum Users</FieldLabel>
  <Input
    type="number"
    min={1}
    value={form.maxUsers ?? ""}
    onChange={(e) =>
      setForm((current) => ({
        ...current,
        maxUsers: Number(e.target.value),
      }))
    }
  />
</Field>

            <div className="grid gap-4 md:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="companyEmail">Company Email</FieldLabel>
                <Input
                  id="companyEmail"
                  type="email"
                  value={form.companyEmail}
                  onChange={(event) => updateRoot("companyEmail", event.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="companyPhone">Company Phone</FieldLabel>
                <Input
                  id="companyPhone"
                  value={form.companyPhone}
                  onChange={(event) => updateRoot("companyPhone", event.target.value)}
                />
              </Field>
            </div>

            <Field>
              <FieldLabel htmlFor="website">Website</FieldLabel>
              <Input
                id="website"
                value={form.website}
                onChange={(event) => updateRoot("website", event.target.value)}
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="subscriptionEndDate">Subscription End Date</FieldLabel>
              <Input
                id="subscriptionEndDate"
                type="date"
                value={form.subscriptionEndDate}
                onChange={(event) => updateRoot("subscriptionEndDate", event.target.value)}
              />
            </Field>

            {!isReseller && (
              <Field>
                <FieldLabel htmlFor="resellerId">Reseller User ID (Optional)</FieldLabel>
                <Input
                  id="resellerId"
                  value={form.resellerId ?? ""}
                  onChange={(event) => updateRoot("resellerId", event.target.value)}
                  placeholder="Optional - leave blank for direct tenant"
                />
                <FieldDescription>Associate this tenant with a reseller</FieldDescription>
              </Field>
            )}

            {mode === "create" && (
                <>
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
                </>
            )}

            {error && <FieldError>{error}</FieldError>}
          </FieldGroup>

          <div className="flex justify-end gap-3 pt-4">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              <Save data-icon="inline-start" />
              {mutation.isPending ? "Provisioning..." : "Provision Tenant"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}