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
  NONE: "Keep as is",
  TRIM: "Trim spaces",
  LOWERCASE: "Make lowercase",
  UPPERCASE: "Make uppercase",
};

// Business-friendly grouping for CRM fields
const getBusinessGroup = (target: LeadIngestionTargetField): string => {
  if (target.targetType === "CUSTOM_FIELD") return "Custom Fields";
  if (target.targetType === "SYSTEM_FIELD") return "Lead Details";
  return "Lead Information";
};

const friendlySourceLabel = (path: string): string => {
  const last = path.split(".").pop() ?? path;
  return last
    .replace(/[_-]+/g, " ")
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .split(" ")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(" ");
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

  // Group all CRM fields business-friendly, hide raw targetType enum from normal UX
  const groupedCrmFields = useMemo(() => {
    const groups: Record<string, LeadIngestionTargetField[]> = {};
    for (const f of targetFields) {
      const g = getBusinessGroup(f);
      if (!groups[g]) groups[g] = [];
      groups[g].push(f);
    }
    return groups;
  }, [targetFields]);

  const handleCrmFieldChange = (value: string) => {
    // value is "targetType:fieldKey"
    const [type, ...rest] = value.split(":");
    const fieldKey = rest.join(":");
    const target = targetFields.find((t) => t.targetType === type && t.fieldKey === fieldKey);
    if (target) {
      form.setValue("targetType", target.targetType, { shouldDirty: true });
      form.setValue("targetField", target.fieldKey, { shouldDirty: true, shouldValidate: true });
    }
  };

  const selectedCrmValue = (() => {
    const t = form.watch("targetType");
    const f = form.watch("targetField");
    if (!t || !f) return "";
    return `${t}:${f}`;
  })();

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
    <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-5">
      <div className="space-y-2">
        <Label htmlFor="sourcePath">Incoming information</Label>
        <p className="text-xs text-muted-foreground">Which piece of information from your lead source should we use?</p>

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
              <SelectValue placeholder="Choose incoming field" />
            </SelectTrigger>
            <SelectContent>
              {sourceFields.map((field) => (
                <SelectItem key={field.path} value={field.path}>
                  <div className="flex flex-col">
                    <span className="font-medium">{friendlySourceLabel(field.path)}</span>
                    <span className="text-xs text-muted-foreground">
                      e.g. {String(field.sampleValue ?? "").slice(0, 30) || "—"} · {field.detectedType}
                      {field.path.includes(".") ? ` · ${field.path}` : ""}
                    </span>
                  </div>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : null}

        <div className="relative">
          <Input
            id="sourcePath"
            placeholder={
              sourceFields && sourceFields.length > 0
                ? "Or choose 'Other' and type the field name"
                : "e.g. First Name"
            }
            {...form.register("sourcePath")}
          />
          {form.watch("sourcePath") && sourceFields?.some((f) => f.path === form.watch("sourcePath")) ? (
            <p className="mt-1 text-xs text-muted-foreground">
              Selected: <span className="font-medium">{friendlySourceLabel(form.watch("sourcePath"))}</span>
              <span className="text-muted-foreground"> · {form.watch("sourcePath")}</span>
            </p>
          ) : null}
        </div>

        <p className="text-xs text-muted-foreground">
          This is the field name coming from your form or app. Pick from the examples above.
        </p>

        {form.formState.errors.sourcePath && (
          <p className="text-sm text-red-500">
            {form.formState.errors.sourcePath.message}
          </p>
        )}
      </div>

      <div className="flex items-center justify-center py-1 text-muted-foreground">
        <span className="text-lg">↓</span>
        <span className="ml-2 text-xs">Map to</span>
      </div>

      <div className="space-y-2">
        <Label>Where should we save it in the CRM?</Label>
        <p className="text-xs text-muted-foreground">Choose the CRM field that should receive this information.</p>

        <Select value={selectedCrmValue} onValueChange={handleCrmFieldChange}>
          <SelectTrigger>
            <SelectValue placeholder="Choose CRM field" />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(groupedCrmFields).map(([group, fields]) => (
              <div key={group}>
                <div className="px-2 py-1.5 text-xs font-semibold text-muted-foreground">{group}</div>
                {fields.map((target) => (
                  <SelectItem key={`${target.targetType}:${target.fieldKey}`} value={`${target.targetType}:${target.fieldKey}`}>
                    {target.label}
                    {target.required ? " *" : ""}
                    <span className="ml-2 text-xs text-muted-foreground">{target.dataType}</span>
                  </SelectItem>
                ))}
              </div>
            ))}
          </SelectContent>
        </Select>

        {targetFields.length === 0 && (
          <p className="text-xs text-muted-foreground">No CRM fields are available. Check your CRM configuration.</p>
        )}

        {form.formState.errors.targetField && (
          <p className="text-sm text-red-500">
            {form.formState.errors.targetField.message}
          </p>
        )}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <Label>Clean up value</Label>

          <Select
            value={form.watch("transformType")}
            onValueChange={(value) =>
              form.setValue("transformType", value as LeadIngestionTransformType, {
                shouldDirty: true,
              })
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="How to clean up" />
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
            Applied automatically before saving.
          </p>
        </div>

        <div className="space-y-2">
          <Label htmlFor="defaultValue">Default value</Label>
          <p className="text-xs text-muted-foreground">If the source does not provide a value, use:</p>

          <Input id="defaultValue" placeholder="e.g. New" {...form.register("defaultValue")} />
        </div>
      </div>

      <details className="rounded-md border bg-muted/10 p-3">
        <summary className="cursor-pointer text-sm font-medium">Advanced cleanup</summary>
        <p className="mt-2 text-xs text-muted-foreground">
          Optional cleanup for phone numbers, extra spaces, or custom formatting. Most mappings do not need this.
        </p>
        <div className="space-y-2">
          <Label>Additional cleanup steps</Label>
          <p className="text-xs text-muted-foreground">Apply extra cleanup after the main option above.</p>
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
            <Label htmlFor="transformPrefix">Add before (prefix)</Label>
            <Input id="transformPrefix" placeholder="e.g. +1 " {...form.register("transformPrefix")} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="transformSuffix">Add after (suffix)</Label>
            <Input id="transformSuffix" placeholder="e.g.  -lead" {...form.register("transformSuffix")} />
          </div>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <div className="space-y-1">
            <Label htmlFor="regexPattern">Find pattern (advanced)</Label>
            <Input id="regexPattern" placeholder="e.g. \\s+" {...form.register("regexPattern")} />
            <p className="text-xs text-muted-foreground">Advanced: replaces text matching this pattern.</p>
            {form.formState.errors.regexPattern && (
              <p className="text-sm text-red-500">{form.formState.errors.regexPattern.message}</p>
            )}
          </div>
          <div className="space-y-1">
            <Label htmlFor="regexReplacement">Replace with</Label>
            <Input id="regexReplacement" placeholder="e.g. _" {...form.register("regexReplacement")} />
          </div>
        </div>
      </details>

      <div className="flex items-center space-x-2">
        <Switch
          id="mapping-required"
          checked={form.watch("required")}
          onCheckedChange={(value) =>
            form.setValue("required", value, { shouldDirty: true })
          }
        />
        <Label htmlFor="mapping-required">This information is required</Label>
        <span className="text-xs text-muted-foreground">If checked, the lead will not be saved when this information is missing.</span>
      </div>
      <div className="hidden">
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
