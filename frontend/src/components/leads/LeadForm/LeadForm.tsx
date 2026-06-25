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
import {
  useCreateLead,
  useLeadCustomFields,
  useLeadSources,
  useLeadStatuses,
  useUpdateLead,
} from "@/lib/hooks/leads";
import { userApi } from "@/lib/api/users";
import { useQuery } from "@tanstack/react-query";
import { LeadResponse } from "@/types/leads";
import { DynamicFieldRenderer } from "./DynamicFieldRenderer";
import { leadFormSchema, LeadFormData } from "./validation";

interface LeadFormProps {
  initialData?: LeadResponse;
  onSuccess?: (lead: LeadResponse) => void;
}

export function LeadForm({ initialData, onSuccess }: LeadFormProps) {
  const router = useRouter();
  const isEdit = !!initialData;

  const { data: statuses, isLoading: statusesLoading } = useLeadStatuses();
  const { data: sources } = useLeadSources();
  const { data: customFields } = useLeadCustomFields();
  const { data: usersData } = useQuery({
    queryKey: ["users", "lead-form"],
    queryFn: () => userApi.getUsers({ page: 0, isActive: true }),
  });

  const createMutation = useCreateLead();
  const updateMutation = useUpdateLead();

  const form = useForm<LeadFormData>({
    resolver: zodResolver(leadFormSchema) as any,
    defaultValues: {
      firstName: "",
      lastName: "",
      email: "",
      phone: "",
      company: "",
      statusId: "",
      sourceId: "",
      ownerUserId: "",
      score: 0,
      notes: "",
      customData: {},
    },
  });

  useEffect(() => {
    if (initialData) {
      form.reset({
        firstName: initialData.firstName,
        lastName: initialData.lastName || "",
        email: initialData.email || "",
        phone: initialData.phone || "",
        company: initialData.company || "",
        statusId: initialData.status.id,
        sourceId: initialData.source?.id || "",
        ownerUserId: initialData.ownerUserId || "",
        score: initialData.score ?? 0,
        notes: initialData.notes || "",
        customData: (initialData.customData as Record<string, unknown>) || {},
      });
    } else if (statuses?.length) {
      const defaultStatus =
        statuses.find((s) => s.isDefault) || statuses[0];
      if (defaultStatus && !form.getValues("statusId")) {
        form.setValue("statusId", defaultStatus.id);
      }
    }
  }, [initialData, statuses, form]);

  const customData = form.watch("customData") || {};
  const isPending = createMutation.isPending || updateMutation.isPending;
  const { errors } = form.formState;

  function onSubmit(data: LeadFormData) {
    const payload = {
      firstName: data.firstName,
      lastName: data.lastName || undefined,
      email: data.email || undefined,
      phone: data.phone || undefined,
      company: data.company || undefined,
      statusId: data.statusId,
      sourceId: data.sourceId || undefined,
      ownerUserId: data.ownerUserId || undefined,
      score: data.score,
      notes: data.notes || undefined,
      customData: data.customData,
    };

    if (isEdit && initialData) {
      updateMutation.mutate(
        { id: initialData.id, data: payload },
        { onSuccess: (lead) => onSuccess?.(lead) }
      );
    } else {
      createMutation.mutate(payload, {
        onSuccess: (lead) => onSuccess?.(lead),
      });
    }
  }

  if (statusesLoading) {
    return (
      <div className="flex justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
      <FieldGroup className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Field>
          <FieldLabel>First Name *</FieldLabel>
          <Input placeholder="John" {...form.register("firstName")} />
          {errors.firstName && <FieldError>{errors.firstName.message}</FieldError>}
        </Field>

        <Field>
          <FieldLabel>Last Name</FieldLabel>
          <Input placeholder="Doe" {...form.register("lastName")} />
        </Field>

        <Field>
          <FieldLabel>Email</FieldLabel>
          <Input type="email" placeholder="john@example.com" {...form.register("email")} />
          {errors.email && <FieldError>{errors.email.message}</FieldError>}
        </Field>

        <Field>
          <FieldLabel>Phone</FieldLabel>
          <Input type="tel" placeholder="+91-9876543210" {...form.register("phone")} />
        </Field>

        <Field>
          <FieldLabel>Company</FieldLabel>
          <Input placeholder="ABC Corporation" {...form.register("company")} />
        </Field>

        <Field>
          <FieldLabel>Score (0–100)</FieldLabel>
          <Input type="number" min={0} max={100} {...form.register("score")} />
        </Field>

        <Field>
          <FieldLabel>Status *</FieldLabel>
          <Controller
            control={form.control}
            name="statusId"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Select status" />
                </SelectTrigger>
                <SelectContent>
                  {statuses?.map((status) => (
                    <SelectItem key={status.id} value={status.id}>
                      {status.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
          {errors.statusId && <FieldError>{errors.statusId.message}</FieldError>}
        </Field>

        <Field>
          <FieldLabel>Source</FieldLabel>
          <Controller
            control={form.control}
            name="sourceId"
            render={({ field }) => (
              <Select
                value={field.value || "none"}
                onValueChange={(v) => field.onChange(v === "none" ? "" : v)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select source" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">None</SelectItem>
                  {sources?.map((source) => (
                    <SelectItem key={source.id} value={source.id}>
                      {source.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
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
                  <SelectValue placeholder="Assign owner" />
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

      <Field>
        <FieldLabel>Notes</FieldLabel>
        <Input placeholder="Initial notes..." {...form.register("notes")} />
      </Field>

      {customFields && customFields.length > 0 && (
        <div className="border-t pt-6">
          <h3 className="font-semibold mb-4">Additional Information</h3>
          <DynamicFieldRenderer
            fields={customFields}
            values={customData}
            onChange={(fieldKey, value) => {
              form.setValue("customData", { ...customData, [fieldKey]: value });
            }}
          />
        </div>
      )}

      <div className="flex gap-4">
        <Button type="submit" disabled={isPending}>
          {isPending ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <Save className="mr-2 h-4 w-4" />
          )}
          {isPending
            ? isEdit
              ? "Saving..."
              : "Creating..."
            : isEdit
              ? "Save Changes"
              : "Create Lead"}
        </Button>
        <Button type="button" variant="outline" onClick={() => router.back()}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
