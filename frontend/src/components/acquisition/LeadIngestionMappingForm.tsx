"use client";

import { useEffect, useMemo } from "react";
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
  acquisitionMappingSchema,
  LeadIngestionFieldMappingRequest,
  LeadIngestionFieldMappingResponse,
  LeadIngestionSourceField,
  LeadIngestionTargetField,
  type AcquisitionMappingFormInput,
  type AcquisitionMappingFormOutput,
  type LeadIngestionTargetType,
  type LeadIngestionTransformType,
} from "@/types/acquisition";

const transformLabels: Record<LeadIngestionTransformType, string> = {
  NONE: "None",
  TRIM: "Trim",
  LOWERCASE: "Lowercase",
  UPPERCASE: "Uppercase",
};

const targetTypeLabels: Record<LeadIngestionTargetType, string> = {
  STANDARD_FIELD: "Standard Field",
  SYSTEM_FIELD: "System Field",
  CUSTOM_FIELD: "Custom Field",
};

interface LeadIngestionMappingFormProps {
  configId: string;
  targetFields: LeadIngestionTargetField[];
  sourceFields?: LeadIngestionSourceField[];
  initialValues?:
    | (Partial<LeadIngestionFieldMappingResponse> & { id?: string })
    | null;
  onSubmit: (values: LeadIngestionFieldMappingRequest) => void;
  submitLabel?: string;
  isSubmitting?: boolean;
}

export function LeadIngestionMappingForm({
  configId,
  targetFields,
  sourceFields,
  initialValues,
  onSubmit,
  submitLabel = "Save",
  isSubmitting = false,
}: LeadIngestionMappingFormProps) {
  const parseTransformConfig = (
    cfg?: Record<string, unknown> | null
  ): {
    chain: ("TRIM" | "LOWERCASE" | "UPPERCASE")[];
    prefix: string;
    suffix: string;
    regexPattern: string;
    regexReplacement: string;
  } => {
    if (!cfg) return { chain: [], prefix: "", suffix: "", regexPattern: "", regexReplacement: "" };
    const chainRaw = cfg["chain"];
    const chain: ("TRIM" | "LOWERCASE" | "UPPERCASE")[] = Array.isArray(chainRaw)
      ? (chainRaw.filter((v) => typeof v === "string" && ["TRIM", "LOWERCASE", "UPPERCASE"].includes(v.toUpperCase())) as ("TRIM" | "LOWERCASE" | "UPPERCASE")[])
          .map((v) => v.toUpperCase() as "TRIM" | "LOWERCASE" | "UPPERCASE")
      : [];
    const prefix = typeof cfg["prefix"] === "string" ? (cfg["prefix"] as string) : "";
    const suffix = typeof cfg["suffix"] === "string" ? (cfg["suffix"] as string) : "";
    let regexPattern = "";
    let regexReplacement = "";
    const regexObj = cfg["regex"];
    if (regexObj && typeof regexObj === "object") {
      const r = regexObj as Record<string, unknown>;
      if (typeof r["pattern"] === "string") regexPattern = r["pattern"] as string;
      if (typeof r["replacement"] === "string") regexReplacement = r["replacement"] as string;
    }
    if (!regexPattern && typeof cfg["pattern"] === "string") {
      regexPattern = cfg["pattern"] as string;
      if (typeof cfg["replacement"] === "string") regexReplacement = cfg["replacement"] as string;
    }
    return { chain, prefix, suffix, regexPattern, regexReplacement };
  };

  const getDefaultValues = (): AcquisitionMappingFormInput => {
    const tc = parseTransformConfig(initialValues?.transformConfig as Record<string, unknown> | null);
    return {
      sourcePath: initialValues?.sourcePath ?? "",
      targetType: initialValues?.targetType ?? "STANDARD_FIELD",
      targetField: initialValues?.targetField ?? "",
      transformType: initialValues?.transformType ?? "NONE",
      transformChain: tc.chain,
      transformPrefix: tc.prefix,
      transformSuffix: tc.suffix,
      regexPattern: tc.regexPattern,
      regexReplacement: tc.regexReplacement,
      defaultValue: initialValues?.defaultValue ?? "",
      required: initialValues?.required ?? false,
      active: initialValues?.active ?? true,
      displayOrder: initialValues?.displayOrder ?? 0,
    };
  };

  const form = useForm<
    AcquisitionMappingFormInput,
    unknown,
    AcquisitionMappingFormOutput
  >({
    resolver: zodResolver(acquisitionMappingSchema),
    defaultValues: getDefaultValues(),
  });

  useEffect(() => {
    form.reset(getDefaultValues());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialValues]);

  const selectedTargetType = form.watch("targetType");

  const targetsForType = useMemo(
    () => targetFields.filter((t) => t.targetType === selectedTargetType),
    [targetFields, selectedTargetType]
  );

  const handleTargetTypeChange = (value: string) => {
    const nextType = value as LeadIngestionTargetType;
    const currentTarget = form.getValues("targetField");
    const stillValid = targetFields.some(
      (t) => t.targetType === nextType && t.fieldKey === currentTarget
    );
    form.setValue("targetType", nextType, { shouldDirty: true });
    if (!stillValid) {
      form.setValue("targetField", "", { shouldDirty: true, shouldValidate: true });
    }
  };

  const handleSubmit = (values: AcquisitionMappingFormOutput) => {
    const tc: Record<string, unknown> = {};
    const chain = (values.transformChain ?? []).filter(Boolean);
    if (chain.length > 0) tc["chain"] = chain;
    const prefix = values.transformPrefix?.trim();
    if (prefix) tc["prefix"] = prefix;
    const suffix = values.transformSuffix?.trim();
    if (suffix) tc["suffix"] = suffix;
    const pattern = values.regexPattern?.trim();
    const replacement = values.regexReplacement ?? "";
    if (pattern) {
      tc["regex"] = { pattern, replacement };
    }
    const hasConfig = Object.keys(tc).length > 0;

    const payload: LeadIngestionFieldMappingRequest = {
      sourcePath: values.sourcePath.trim(),
      targetType: values.targetType,
      targetField: values.targetField,
      transformType: values.transformType,
      transformConfig: hasConfig ? tc : null,
      defaultValue: values.defaultValue?.trim() ? values.defaultValue.trim() : null,
      required: values.required,
      active: values.active,
      displayOrder: values.displayOrder ?? 0,
    };

    onSubmit(payload);
  };

  return (
    <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="sourcePath">Source field</Label>

        {sourceFields && sourceFields.length > 0 ? (
          <Select
            value={form.watch("sourcePath")}
            onValueChange={(value) =>
              form.setValue("sourcePath", value, {
                shouldDirty: true,
                shouldValidate: true,
              })
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select discovered source field" />
            </SelectTrigger>
            <SelectContent>
              {sourceFields.map((field) => (
                <SelectItem key={field.path} value={field.path}>
                  {field.path}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : null}

        <Input
          id="sourcePath"
          placeholder={
            sourceFields && sourceFields.length > 0
              ? "Or enter a payload path manually"
              : "Payload path e.g. customer.name"
          }
          {...form.register("sourcePath")}
        />

        <p className="text-xs text-muted-foreground">
          Path within the incoming lead payload — not a CRM field.
          {sourceFields && sourceFields.length > 0
            ? " Choose a discovered field above or type a path."
            : " Load an ingestion event above to discover available fields."}
        </p>

        {form.formState.errors.sourcePath && (
          <p className="text-sm text-red-500">
            {form.formState.errors.sourcePath.message}
          </p>
        )}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <Label>Target type</Label>

          <Select
            value={form.watch("targetType")}
            onValueChange={handleTargetTypeChange}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select target type" />
            </SelectTrigger>
            <SelectContent>
              {(Object.keys(targetTypeLabels) as LeadIngestionTargetType[]).map(
                (type) => (
                  <SelectItem key={type} value={type}>
                    {targetTypeLabels[type]}
                  </SelectItem>
                )
              )}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label>Target field</Label>

          <Select
            value={form.watch("targetField")}
            onValueChange={(value) =>
              form.setValue("targetField", value, {
                shouldDirty: true,
                shouldValidate: true,
              })
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select target field" />
            </SelectTrigger>
            <SelectContent>
              {targetsForType.map((target) => (
                <SelectItem key={`${target.targetType}:${target.fieldKey}`} value={target.fieldKey}>
                  {target.label}
                  {target.required ? " *" : ""}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {targetsForType.length === 0 && (
            <p className="text-xs text-muted-foreground">
              No {targetTypeLabels[selectedTargetType]?.toLowerCase()}s are
              registered for this configuration.
            </p>
          )}

          {form.formState.errors.targetField && (
            <p className="text-sm text-red-500">
              {form.formState.errors.targetField.message}
            </p>
          )}
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="space-y-2">
          <Label>Transform</Label>

          <Select
            value={form.watch("transformType")}
            onValueChange={(value) =>
              form.setValue("transformType", value as LeadIngestionTransformType, {
                shouldDirty: true,
              })
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select transform" />
            </SelectTrigger>
            <SelectContent>
              {(Object.keys(transformLabels) as LeadIngestionTransformType[]).map(
                (transform) => (
                  <SelectItem key={transform} value={transform}>
                    {transformLabels[transform]}
                  </SelectItem>
                )
              )}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            Applied first. Chain/prefix/suffix/regex follow deterministically.
          </p>
        </div>

        <div className="space-y-2">
          <Label htmlFor="defaultValue">Default value</Label>

          <Input id="defaultValue" {...form.register("defaultValue")} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="displayOrder">Display order</Label>

          <Input id="displayOrder" type="number" min="0" step="1" {...form.register("displayOrder")} />

          {form.formState.errors.displayOrder && (
            <p className="text-sm text-red-500">
              {form.formState.errors.displayOrder.message}
            </p>
          )}
        </div>
      </div>

      <div className="rounded-md border bg-muted/10 p-3 space-y-3">
        <p className="text-sm font-medium">Advanced transform (transformConfig)</p>
        <p className="text-xs text-muted-foreground">
          Deterministic order: primary transform → chain → prefix/suffix → regex → default fallback → normalization → validation. Stored in JSONB.
        </p>
        <div className="space-y-2">
          <Label>Chain (additional transforms after primary)</Label>
          <div className="flex flex-wrap gap-4">
            {(["TRIM", "LOWERCASE", "UPPERCASE"] as const).map((t) => {
              const chain = (form.watch("transformChain") ?? []) as string[];
              const checked = chain.includes(t);
              return (
                <label key={t} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={(e) => {
                      const cur = (form.getValues("transformChain") ?? []) as string[];
                      if (e.target.checked) {
                        form.setValue("transformChain", [...cur, t] as any, { shouldDirty: true });
                      } else {
                        form.setValue(
                          "transformChain",
                          cur.filter((x) => x !== t) as any,
                          { shouldDirty: true }
                        );
                      }
                    }}
                    className="h-4 w-4"
                  />
                  {transformLabels[t]}
                </label>
              );
            })}
          </div>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <div className="space-y-1">
            <Label htmlFor="transformPrefix">Prefix</Label>
            <Input id="transformPrefix" placeholder="e.g. +1 " {...form.register("transformPrefix")} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="transformSuffix">Suffix</Label>
            <Input id="transformSuffix" placeholder="e.g.  -lead" {...form.register("transformSuffix")} />
          </div>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <div className="space-y-1">
            <Label htmlFor="regexPattern">Regex pattern</Label>
            <Input id="regexPattern" placeholder="e.g. \\s+" {...form.register("regexPattern")} />
            <p className="text-xs text-muted-foreground">Java regex, replaceAll</p>
            {form.formState.errors.regexPattern && (
              <p className="text-sm text-red-500">{form.formState.errors.regexPattern.message}</p>
            )}
          </div>
          <div className="space-y-1">
            <Label htmlFor="regexReplacement">Regex replacement</Label>
            <Input id="regexReplacement" placeholder="e.g. _" {...form.register("regexReplacement")} />
          </div>
        </div>
      </div>

      <div className="flex items-center space-x-6">
        <div className="flex items-center space-x-2">
          <Switch
            id="mapping-required"
            checked={form.watch("required")}
            onCheckedChange={(value) =>
              form.setValue("required", value, { shouldDirty: true })
            }
          />
          <Label htmlFor="mapping-required">Required</Label>
        </div>

        <div className="flex items-center space-x-2">
          <Switch
            id="mapping-active"
            checked={form.watch("active")}
            onCheckedChange={(value) =>
              form.setValue("active", value, { shouldDirty: true })
            }
          />
          <Label htmlFor="mapping-active">Active</Label>
        </div>
      </div>

      <div className="flex justify-end">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Saving..." : submitLabel}
        </Button>
      </div>
    </form>
  );
}
