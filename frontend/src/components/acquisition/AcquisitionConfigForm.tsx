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

const transportAvailability: Record<LeadIngestionTransportType, { available: boolean; note: string }> = {
  WEBHOOK: { available: true, note: "Available — custom webhook" },
  IMPORT: { available: true, note: "Available — CSV import" },
  FORM: { available: true, note: "Available — public form" },
  API: { available: true, note: "Available — direct API" },
  POLLING: { available: true, note: "Available — API polling" },
  CONNECTOR: { available: false, note: "Coming soon" },
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

  const selectedTransport = form.watch("transportType") as LeadIngestionTransportType;
  const selectedAvailable = transportAvailability[selectedTransport]?.available ?? false;

  const handleSubmit = (values: AcquisitionConfigFormOutput) => {
    const avail = transportAvailability[values.transportType as LeadIngestionTransportType]?.available;
    if (!avail) {
      form.setError("transportType", { message: "This transport is not yet available — only Webhook can be created." });
      return;
    }
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
        <Label>How leads arrive — Transport</Label>

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
            {Object.entries(transportLabels).map(([value, label]) => {
              const avail = transportAvailability[value as LeadIngestionTransportType]?.available;
              return (
                <SelectItem key={value} value={value} disabled={!avail}>
                  <span className="flex items-center gap-2">
                    {label}
                    {!avail && <span className="text-[10px] text-muted-foreground">(Coming soon)</span>}
                    {avail && <span className="text-[10px] text-green-600">✓ Available</span>}
                  </span>
                </SelectItem>
              );
            })}
          </SelectContent>
        </Select>

        {!selectedAvailable && (
          <p className="text-xs text-amber-600 flex items-center gap-1">
            This transport is not yet available — only Webhook can be created and activated. Others are shown for roadmap clarity.
          </p>
        )}
        {selectedAvailable && (
          <p className="text-xs text-muted-foreground">
            Webhook: external system POSTs JSON to a unique public endpoint. Different sources can have different payload shapes — map them to the same CRM lead.
          </p>
        )}

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
          disabled={!selectedAvailable}
          onCheckedChange={(value) =>
            form.setValue("active", value, {
              shouldDirty: true,
            })
          }
        />

        <Label htmlFor="active" className={!selectedAvailable ? "text-muted-foreground" : ""}>
          Active { !selectedAvailable && "(disabled for Coming soon transports)"}
        </Label>
      </div>
      {!initialValues && selectedAvailable && (
        <p className="text-xs text-muted-foreground">
          Next: discover a sample payload → map fields → test preview → activate.
        </p>
      )}

      <div className="flex justify-end">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Saving..." : submitLabel}
        </Button>
      </div>
    </form>
  );
}
