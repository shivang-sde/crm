"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";

import {
  acquisitionConfigSchema,
  LeadIngestionConfigResponse,
  type AcquisitionConfigFormInput,
  type AcquisitionConfigFormOutput,
  type LeadIngestionConfigCreateRequest,
  type LeadIngestionTransportType,
} from "@/types/acquisition";

const transportLabels: Record<LeadIngestionTransportType, string> = {
  WEBHOOK: "Webhook",
  API: "API",
  FORM: "Form",
  CONNECTOR: "Connector",
  POLLING: "Polling",
  IMPORT: "Import",
};

interface AcquisitionConfigFormProps {
  initialValues?: Partial<LeadIngestionConfigResponse>;
  onSubmit: (values: LeadIngestionConfigCreateRequest) => void;
  submitLabel?: string;
  isSubmitting?: boolean;
}

export function AcquisitionConfigForm({
  initialValues,
  onSubmit,
  submitLabel = "Save",
  isSubmitting = false,
}: AcquisitionConfigFormProps) {
  const getDefaultValues = (): AcquisitionConfigFormInput => ({
    name: initialValues?.name ?? "",
    transportType: initialValues?.transportType ?? "WEBHOOK",
    active: initialValues?.active ?? true,
  });

  const form = useForm<
    AcquisitionConfigFormInput,
    unknown,
    AcquisitionConfigFormOutput
  >({
    resolver: zodResolver(acquisitionConfigSchema),
    defaultValues: getDefaultValues(),
  });

  useEffect(() => {
    form.reset(getDefaultValues());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialValues]);

  const handleSubmit = (values: AcquisitionConfigFormOutput) => {
    const payload: LeadIngestionConfigCreateRequest = {
      name: values.name.trim(),
      transportType: values.transportType,
      active: values.active,
      settings: {},
    };

    onSubmit(payload);
  };

  return (
    <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="name">Name</Label>

        <Input
          id="name"
          placeholder="Website Leads"
          {...form.register("name")}
        />

        {form.formState.errors.name && (
          <p className="text-sm text-red-500">
            {form.formState.errors.name.message}
          </p>
        )}
      </div>

      <div className="space-y-2">
        <Label>Transport</Label>

        <Select
          value={form.watch("transportType")}
          onValueChange={(value) =>
            form.setValue("transportType", value as LeadIngestionTransportType, {
              shouldDirty: true,
              shouldValidate: true,
            })
          }
        >
          <SelectTrigger>
            <SelectValue placeholder="Select transport" />
          </SelectTrigger>

          <SelectContent>
            {Object.entries(transportLabels).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {form.formState.errors.transportType && (
          <p className="text-sm text-red-500">
            {form.formState.errors.transportType.message}
          </p>
        )}
      </div>

      {initialValues?.publicKey && (
        <div className="space-y-2">
          <Label htmlFor="publicKey">Public key</Label>

          <Input id="publicKey" value={initialValues.publicKey} readOnly disabled />

          <p className="text-xs text-muted-foreground">
            Generated automatically. Used by external systems to send leads to
            this configuration.
          </p>
        </div>
      )}

      <div className="flex items-center space-x-2">
        <Switch
          id="active"
          checked={form.watch("active")}
          onCheckedChange={(value) =>
            form.setValue("active", value, {
              shouldDirty: true,
            })
          }
        />

        <Label htmlFor="active">Active</Label>
      </div>

      <div className="flex justify-end">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Saving..." : submitLabel}
        </Button>
      </div>
    </form>
  );
}
