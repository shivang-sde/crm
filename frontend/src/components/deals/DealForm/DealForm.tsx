"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Save } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FieldGroup, Field, FieldLabel, FieldError } from "@/components/ui/field";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

import { useCreateDeal, useUpdateDeal, useDealStages, useDealCustomFields } from "@/lib/hooks/deals";
import { useDealLineItems } from "@/lib/hooks/deal-line-items";
import { RecordCombobox } from "@/components/common/RecordCombobox";
import { useQuery } from "@tanstack/react-query";
import { userApi } from "@/lib/api/users";
import { useAccount } from "@/lib/hooks/accounts";
import { useContact } from "@/lib/hooks/contacts";

import { DealResponse } from "@/types/deals";
import { DynamicFieldRenderer } from "./DynamicFieldRenderer";
import { dealFormSchema, DealFormData } from "./validation";

interface DealFormProps {
  initialData?: DealResponse;
  onSuccess?: (deal: DealResponse) => void;
}

export function DealForm({ initialData, onSuccess }: DealFormProps) {
  const router = useRouter();
  const isEdit = !!initialData;

  const { data: stages, isLoading: stagesLoading } = useDealStages();
  const { data: customFields } = useDealCustomFields();
  const { data: usersData } = useQuery({ queryKey: ["users", "deal-form"], queryFn: () => userApi.getUsers({ page: 0, isActive: true }) });
  const { data: initialAccount } = useAccount(isEdit ? initialData?.accountId || undefined : undefined);
  const { data: initialContact } = useContact(isEdit ? initialData?.contactId || undefined : undefined);

  const createMutation = useCreateDeal();
  const updateMutation = useUpdateDeal();
  const { data: lineItemsData } = useDealLineItems(initialData?.id);
  const hasLineItems = Boolean(initialData?.id && (lineItemsData?.length ?? 0) > 0);
  const amountManagedByLineItems = isEdit && hasLineItems;

  const form = useForm<DealFormData>({
    resolver: zodResolver(dealFormSchema) as any,
    defaultValues: {
      name: "",
      amount: undefined,
      currency: undefined,
      stageId: "",
      accountId: undefined,
      contactId: undefined,
      ownerUserId: undefined,
      expectedCloseDate: undefined,
      probability: undefined,
      nextStep: "",
      forecastCategory: null,
      dealType: null,
      leadSource: "",
      campaignSource: "",
      closedDate: "",
      wonReason: "",
      lostReason: "",
      customData: {},
    },
  });

  useEffect(() => {
    if (initialData) {
      form.reset({
        name: initialData.name,
        amount: initialData.amount ?? undefined,
        currency: initialData.currency ?? undefined,
        stageId: initialData.stage.id,
        accountId: initialData.accountId ?? undefined,
        contactId: initialData.contactId ?? undefined,
        ownerUserId: initialData.ownerUserId ?? undefined,
        expectedCloseDate: initialData.expectedCloseDate ?? undefined,
        probability: initialData.probability ?? undefined,
        nextStep: initialData.nextStep ?? "",
        forecastCategory: initialData.forecastCategory ?? null,
        dealType: initialData.dealType ?? null,
        leadSource: initialData.leadSource ?? "",
        campaignSource: initialData.campaignSource ?? "",
        closedDate: initialData.closedDate ?? "",
        wonReason: initialData.wonReason ?? "",
        lostReason: initialData.lostReason ?? "",
        customData: (initialData.customData as Record<string, unknown>) || {},
      });
    } else if (stages?.length) {
      const defaultStage = stages.find((s) => s.isDefault) || stages[0];
      if (defaultStage && !form.getValues("stageId")) {
        form.setValue("stageId", defaultStage.id);
      }
    }
  }, [initialData, stages, form]);

  const customData = form.watch("customData") || {};
  const isPending = createMutation.isPending || updateMutation.isPending;
  const { errors } = form.formState;

  const selectedStageId = form.watch("stageId");
  const selectedStage = stages?.find((s) => s.id === selectedStageId);
  const isWonStage = selectedStage?.recordCategory === "CLOSED_WON";
  const isLostStage = selectedStage?.recordCategory === "CLOSED_LOST";

  function onSubmit(data: DealFormData) {
    if (isLostStage && (!data.lostReason || !data.lostReason.trim())) {
      form.setError("lostReason", { type: "manual", message: "Lost reason is required for closed-lost stage" });
      return;
    }

    const payload = {
      name: data.name,
      amount: data.amount ?? undefined,
      currency: data.currency ?? undefined,
      stageId: data.stageId,
      accountId: data.accountId || undefined,
      contactId: data.contactId || undefined,
      ownerUserId: data.ownerUserId || undefined,
      expectedCloseDate: data.expectedCloseDate || undefined,
      probability: data.probability ?? undefined,
      nextStep: data.nextStep || undefined,
      forecastCategory: data.forecastCategory || undefined,
      dealType: data.dealType || undefined,
      leadSource: data.leadSource || undefined,
      campaignSource: data.campaignSource || undefined,
      closedDate: data.closedDate || undefined,
      wonReason: isWonStage ? (data.wonReason || undefined) : undefined,
      lostReason: isLostStage ? (data.lostReason || undefined) : undefined,
      customData: data.customData,
    };

    if (isEdit && initialData) {
      updateMutation.mutate(
        { id: initialData.id, data: payload },
        { onSuccess: (deal) => onSuccess?.(deal) }
      );
    } else {
      createMutation.mutate(payload, { onSuccess: (deal) => onSuccess?.(deal) });
    }
  }

  if (stagesLoading) {
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
          <FieldLabel>Name *</FieldLabel>
          <Input placeholder="Deal name" {...form.register("name")} />
          {errors.name && <FieldError>{errors.name.message}</FieldError>}
        </Field>

        <Field>
          <FieldLabel>Amount</FieldLabel>
          <Input type="number" placeholder="0.00" {...form.register("amount")} disabled={amountManagedByLineItems} />
          {amountManagedByLineItems && <p className="text-sm text-muted-foreground">Amount is managed by line items.</p>}
        </Field>

        <Field>
          <FieldLabel>Currency</FieldLabel>
          <Input placeholder="USD" {...form.register("currency")} />
        </Field>

        <Field>
          <FieldLabel>Stage *</FieldLabel>
          <Controller
            control={form.control}
            name="stageId"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Select stage" />
                </SelectTrigger>
                <SelectContent>
                  {stages?.map((s) => (
                    <SelectItem key={s.id} value={s.id}>
                      {s.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
          {errors.stageId && <FieldError>{errors.stageId.message}</FieldError>}
        </Field>

        <Field>
          <FieldLabel>Account</FieldLabel>
          <Controller
            control={form.control}
            name="accountId"
            render={({ field }) => (
              <RecordCombobox
                entityType="ACCOUNT"
                value={field.value || undefined}
                onChange={(id) => field.onChange(id ?? "")}
                fallbackLabel={initialAccount?.name}
                placeholder="Search account..."
              />
            )}
          />
        </Field>

        <Field>
          <FieldLabel>Contact</FieldLabel>
          <Controller
            control={form.control}
            name="contactId"
            render={({ field }) => (
              <RecordCombobox
                entityType="CONTACT"
                value={field.value || undefined}
                onChange={(id) => field.onChange(id ?? "")}
                fallbackLabel={
                  initialContact
                    ? [initialContact.firstName, initialContact.lastName].filter(Boolean).join(" ").trim() ||
                      initialContact.email
                    : undefined
                }
                placeholder="Search contact..."
              />
            )}
          />
        </Field>

        <Field>
          <FieldLabel>Owner</FieldLabel>
          <Controller
            control={form.control}
            name="ownerUserId"
            render={({ field }) => (
              <Select value={field.value || "none"} onValueChange={(v) => field.onChange(v === "none" ? "" : v)}>
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

        <Field>
          <FieldLabel>Expected Close Date</FieldLabel>
          <Input type="date" {...form.register("expectedCloseDate")} />
        </Field>

        <Field>
          <FieldLabel>Probability (%)</FieldLabel>
          <Input type="number" min={0} max={100} {...form.register("probability")} />
        </Field>

        <Field>
          <FieldLabel>Next Step</FieldLabel>
          <Input placeholder="E.g., Follow up call" {...form.register("nextStep")} />
        </Field>

        <Field>
          <FieldLabel>Forecast Category</FieldLabel>
          <Controller
            control={form.control}
            name="forecastCategory"
            render={({ field }) => (
              <Select value={field.value ?? "none"} onValueChange={(v) => field.onChange(v === "none" ? null : v)}>
                <SelectTrigger>
                  <SelectValue placeholder="Select forecast category" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">None</SelectItem>
                  <SelectItem value="PIPELINE">Pipeline</SelectItem>
                  <SelectItem value="BEST_CASE">Best Case</SelectItem>
                  <SelectItem value="COMMIT">Commit</SelectItem>
                  <SelectItem value="CLOSED">Closed</SelectItem>
                  <SelectItem value="OMITTED">Omitted</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </Field>

        <Field>
          <FieldLabel>Deal Type</FieldLabel>
          <Controller
            control={form.control}
            name="dealType"
            render={({ field }) => (
              <Select value={field.value ?? "none"} onValueChange={(v) => field.onChange(v === "none" ? null : v)}>
                <SelectTrigger>
                  <SelectValue placeholder="Select deal type" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">None</SelectItem>
                  <SelectItem value="NEW_BUSINESS">New Business</SelectItem>
                  <SelectItem value="EXISTING_BUSINESS">Existing Business</SelectItem>
                  <SelectItem value="RENEWAL">Renewal</SelectItem>
                  <SelectItem value="UPSELL">Upsell</SelectItem>
                  <SelectItem value="CROSS_SELL">Cross Sell</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </Field>

        <Field>
          <FieldLabel>Lead Source</FieldLabel>
          <Input placeholder="E.g., Web, Referral" {...form.register("leadSource")} />
        </Field>

        <Field>
          <FieldLabel>Campaign Source</FieldLabel>
          <Input placeholder="E.g., Summer Promo" {...form.register("campaignSource")} />
        </Field>

        <Field>
          <FieldLabel>Closed Date</FieldLabel>
          <Input type="date" {...form.register("closedDate")} />
        </Field>

        {isWonStage && (
          <Field className="md:col-span-2">
            <FieldLabel>Won Reason</FieldLabel>
            <Input placeholder="Why was this deal won?" {...form.register("wonReason")} />
          </Field>
        )}

        {isLostStage && (
          <Field className="md:col-span-2">
            <FieldLabel>Lost Reason *</FieldLabel>
            <Input placeholder="Why was this deal lost?" {...form.register("lostReason")} />
            {errors.lostReason && <FieldError>{errors.lostReason.message}</FieldError>}
          </Field>
        )}
      </FieldGroup>

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
          {isPending ? (isEdit ? "Saving..." : "Creating...") : isEdit ? "Save Changes" : "Create Deal"}
        </Button>
        <Button type="button" variant="outline" onClick={() => router.back()}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
