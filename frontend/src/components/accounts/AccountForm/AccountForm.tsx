"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Save } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FieldGroup, Field, FieldLabel, FieldError } from "@/components/ui/field";
import { useCreateAccount, useUpdateAccount } from "@/lib/hooks/accounts";
import { userApi } from "@/lib/api/users";
import { useQuery } from "@tanstack/react-query";
import { AccountResponse } from "@/types/accounts";
import { accountFormSchema, AccountFormData } from "./validation";

interface AccountFormProps {
  initialData?: AccountResponse;
  onSuccess?: (account: AccountResponse) => void;
}

export function AccountForm({ initialData, onSuccess }: AccountFormProps) {
  const router = useRouter();
  const isEdit = !!initialData;

  const { data: usersData } = useQuery({
    queryKey: ["users", "account-form"],
    queryFn: () => userApi.getUsers({ page: 0, isActive: true }),
  });

  const createMutation = useCreateAccount();
  const updateMutation = useUpdateAccount();

  const form = useForm<AccountFormData>({
    resolver: zodResolver(accountFormSchema) as any,
    defaultValues: {
      name: "",
      website: "",
      industry: "",
      phone: "",
      email: "",
      annualRevenue: undefined,
      employeeCount: undefined,
      description: "",
      country: "",
      state: "",
      city: "",
      addressLine1: "",
      postalCode: "",
      ownerUserId: "",
    },
  });

  useEffect(() => {
    if (initialData) {
      form.reset({
        name: initialData.name,
        website: initialData.website || "",
        industry: initialData.industry || "",
        phone: initialData.phone || "",
        email: initialData.email || "",
        annualRevenue: initialData.annualRevenue,
        employeeCount: initialData.employeeCount,
        description: initialData.description || "",
        country: initialData.country || "",
        state: initialData.state || "",
        city: initialData.city || "",
        addressLine1: initialData.addressLine1 || "",
        postalCode: initialData.postalCode || "",
        ownerUserId: initialData.ownerUserId || "",
      });
    }
  }, [initialData, form]);

  const isPending = createMutation.isPending || updateMutation.isPending;
  const { errors } = form.formState;

  function onSubmit(data: AccountFormData) {
    const payload = {
      name: data.name,
      website: data.website || undefined,
      industry: data.industry || undefined,
      phone: data.phone || undefined,
      email: data.email || undefined,
      annualRevenue: data.annualRevenue,
      employeeCount: data.employeeCount,
      description: data.description || undefined,
      country: data.country || undefined,
      state: data.state || undefined,
      city: data.city || undefined,
      addressLine1: data.addressLine1 || undefined,
      postalCode: data.postalCode || undefined,
      ownerUserId: data.ownerUserId || undefined,
    };

    if (isEdit && initialData) {
      updateMutation.mutate(
        { id: initialData.id, data: payload },
        { onSuccess: (account) => onSuccess?.(account) }
      );
    } else {
      createMutation.mutate(payload, {
        onSuccess: (account) => onSuccess?.(account),
      });
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
      <FieldGroup className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Field>
          <FieldLabel>Name *</FieldLabel>
          <Input placeholder="Acme Corporation" {...form.register("name")} />
          {errors.name && <FieldError>{errors.name.message}</FieldError>}
        </Field>

        <Field>
          <FieldLabel>Owner</FieldLabel>
          <Controller
            control={form.control}
            name="ownerUserId"
            render={({ field }) => (
              <Select
                value={field.value || "none"}
                onValueChange={(v) => field.onChange(v === "none" ? "" : v)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Unassigned" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">Unassigned</SelectItem>
                  {usersData?.content.map((user) => (
                    <SelectItem key={user.id} value={user.id}>
                      {user.firstName} {user.lastName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </Field>

        <Field>
          <FieldLabel>Email</FieldLabel>
          <Input type="email" placeholder="contact@acme.com" {...form.register("email")} />
          {errors.email && <FieldError>{errors.email.message}</FieldError>}
        </Field>

        <Field>
          <FieldLabel>Phone</FieldLabel>
          <Input type="tel" placeholder="+1 555 123 4567" {...form.register("phone")} />
        </Field>

        <Field>
          <FieldLabel>Website</FieldLabel>
          <Input placeholder="https://acme.com" {...form.register("website")} />
        </Field>

        <Field>
          <FieldLabel>Industry</FieldLabel>
          <Input placeholder="Manufacturing" {...form.register("industry")} />
        </Field>

        <Field>
          <FieldLabel>Annual Revenue</FieldLabel>
          <Input type="number" min={0} {...form.register("annualRevenue", { valueAsNumber: true })} />
        </Field>

        <Field>
          <FieldLabel>Employee Count</FieldLabel>
          <Input type="number" min={0} {...form.register("employeeCount", { valueAsNumber: true })} />
        </Field>

        <Field>
          <FieldLabel>Address</FieldLabel>
          <Input placeholder="123 Main St" {...form.register("addressLine1")} />
        </Field>

        <Field>
          <FieldLabel>City</FieldLabel>
          <Input {...form.register("city")} />
        </Field>

        <Field>
          <FieldLabel>State</FieldLabel>
          <Input {...form.register("state")} />
        </Field>

        <Field>
          <FieldLabel>Country</FieldLabel>
          <Input {...form.register("country")} />
        </Field>

        <Field>
          <FieldLabel>Postal Code</FieldLabel>
          <Input {...form.register("postalCode")} />
        </Field>
      </FieldGroup>

      <Field>
        <FieldLabel>Description</FieldLabel>
        <Input {...form.register("description")} />
      </Field>

      <div className="flex gap-4">
        <Button type="submit" disabled={isPending}>
          {isPending ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <Save className="mr-2 h-4 w-4" />
          )}
          {isPending ? (isEdit ? "Saving..." : "Creating...") : isEdit ? "Save Changes" : "Create Account"}
        </Button>
        <Button type="button" variant="outline" onClick={() => router.back()}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
