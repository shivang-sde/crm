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
import { useCreateContact, useUpdateContact } from "@/lib/hooks/contacts";
import { accountApi } from "@/lib/api/accounts";
import { userApi } from "@/lib/api/users";
import { useQuery } from "@tanstack/react-query";
import { ContactResponse } from "@/types/contacts";
import { contactFormSchema, ContactFormData } from "./validation";

interface ContactFormProps {
  initialData?: ContactResponse;
  onSuccess?: (contact: ContactResponse) => void;
}

export function ContactForm({ initialData, onSuccess }: ContactFormProps) {
  const router = useRouter();
  const isEdit = !!initialData;

  const { data: accountsData } = useQuery({
    queryKey: ["accounts", "contact-form"],
    queryFn: () => accountApi.listAccounts({ page: 0, size: 100 }),
  });

  const { data: usersData } = useQuery({
    queryKey: ["users", "contact-form"],
    queryFn: () => userApi.getUsers({ page: 0, isActive: true }),
  });

  const createMutation = useCreateContact();
  const updateMutation = useUpdateContact();

  const form = useForm<ContactFormData>({
    resolver: zodResolver(contactFormSchema) as any,
    defaultValues: {
      accountId: "",
      firstName: "",
      lastName: "",
      email: "",
      phone: "",
      title: "",
      department: "",
      ownerUserId: "",
    },
  });

  useEffect(() => {
    if (initialData) {
      form.reset({
        accountId: initialData.accountId,
        firstName: initialData.firstName || "",
        lastName: initialData.lastName || "",
        email: initialData.email || "",
        phone: initialData.phone || "",
        title: initialData.title || "",
        department: initialData.department || "",
        ownerUserId: initialData.ownerUserId || "",
      });
    }
  }, [initialData, form]);

  const isPending = createMutation.isPending || updateMutation.isPending;
  const { errors } = form.formState;

  function onSubmit(data: ContactFormData) {
    const payload = {
      accountId: data.accountId,
      firstName: data.firstName,
      lastName: data.lastName || undefined,
      email: data.email || undefined,
      phone: data.phone || undefined,
      title: data.title || undefined,
      department: data.department || undefined,
      ownerUserId: data.ownerUserId || undefined,
    };

    if (isEdit && initialData) {
      updateMutation.mutate(
        { id: initialData.id, data: payload },
        { onSuccess: (contact) => onSuccess?.(contact) }
      );
    } else {
      createMutation.mutate(payload, {
        onSuccess: (contact) => onSuccess?.(contact),
      });
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
      <FieldGroup className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Field>
          <FieldLabel>Account *</FieldLabel>
          <Controller
            control={form.control}
            name="accountId"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Select account" />
                </SelectTrigger>
                <SelectContent>
                  {accountsData?.data.map((account) => (
                    <SelectItem key={account.id} value={account.id}>
                      {account.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
          {errors.accountId && <FieldError>{errors.accountId.message}</FieldError>}
        </Field>

        <Field>
          <FieldLabel>First Name *</FieldLabel>
          <Input placeholder="Jane" {...form.register("firstName")} />
          {errors.firstName && <FieldError>{errors.firstName.message}</FieldError>}
        </Field>

        <Field>
          <FieldLabel>Last Name</FieldLabel>
          <Input placeholder="Doe" {...form.register("lastName")} />
        </Field>

        <Field>
          <FieldLabel>Email</FieldLabel>
          <Input type="email" placeholder="jane@example.com" {...form.register("email")} />
          {errors.email && <FieldError>{errors.email.message}</FieldError>}
        </Field>

        <Field>
          <FieldLabel>Phone</FieldLabel>
          <Input type="tel" placeholder="+1 555 123 4567" {...form.register("phone")} />
        </Field>

        <Field>
          <FieldLabel>Title</FieldLabel>
          <Input placeholder="Sales Manager" {...form.register("title")} />
        </Field>

        <Field>
          <FieldLabel>Department</FieldLabel>
          <Input placeholder="Marketing" {...form.register("department")} />
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
      </FieldGroup>

      <div className="flex gap-4">
        <Button type="submit" disabled={isPending}>
          {isPending ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <Save className="mr-2 h-4 w-4" />
          )}
          {isPending ? (isEdit ? "Saving..." : "Creating...") : isEdit ? "Save Changes" : "Create Contact"}
        </Button>
        <Button type="button" variant="outline" onClick={() => router.back()}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
