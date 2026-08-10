"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";

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
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";



import {
    schema,
  type BillingInterval,
  type BillingType,
  type OfferingCreateRequest,
  type OfferingFormInput,
  type OfferingFormOutput,
  type OfferingType,
  type OfferingUpdateRequest,
  
} from "@/types/offerings";



interface OfferingFormProps {
  initialValues?: Partial<OfferingCreateRequest & OfferingUpdateRequest>;
  onSubmit: (
    values: OfferingCreateRequest | OfferingUpdateRequest,
  ) => void;
  submitLabel?: string;
  isSubmitting?: boolean;
}

export function OfferingForm({
  initialValues,
  onSubmit,
  submitLabel = "Save",
  isSubmitting = false,
}: OfferingFormProps) {
  const getDefaultValues = (): OfferingFormInput => ({
    name: initialValues?.name ?? "",
    code: initialValues?.code ?? "",
    description: initialValues?.description ?? "",
    offeringType: initialValues?.offeringType ?? "PRODUCT",
    billingType: initialValues?.billingType ?? "ONE_TIME",
    billingInterval: initialValues?.billingInterval ?? "MONTHLY",
    defaultPrice: initialValues?.defaultPrice ?? 0,
    currencyCode: initialValues?.currencyCode ?? "USD",
    defaultTermDays: initialValues?.defaultTermDays ?? 30,
    renewable: initialValues?.renewable ?? false,
    active: initialValues?.active ?? true,
  });

  const form = useForm<
    OfferingFormInput,
    unknown,
    OfferingFormOutput
  >({
    resolver: zodResolver(schema),
    defaultValues: getDefaultValues(),
  });

  useEffect(() => {
    form.reset(getDefaultValues());
  }, [initialValues, form]);

  const handleSubmit = (values: OfferingFormOutput) => {
    const payload: OfferingCreateRequest = {
      name: values.name,
      code: values.code,
      description: values.description || null,
      offeringType: values.offeringType,
      billingType: values.billingType,
      billingInterval:
        values.billingType === "RECURRING"
          ? values.billingInterval ?? null
          : null,
      defaultPrice: values.defaultPrice ?? null,
      currencyCode: values.currencyCode || null,
      defaultTermDays: values.defaultTermDays ?? null,
      renewable: values.renewable,
      active: values.active,
    };

    onSubmit(payload);
  };

  return (
    <form
      onSubmit={form.handleSubmit(handleSubmit)}
      className="space-y-4"
    >
      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="name">Name</Label>

          <Input
            id="name"
            {...form.register("name")}
          />

          {form.formState.errors.name && (
            <p className="text-sm text-red-500">
              {form.formState.errors.name.message}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="code">Code</Label>

          <Input
            id="code"
            {...form.register("code")}
          />

          {form.formState.errors.code && (
            <p className="text-sm text-red-500">
              {form.formState.errors.code.message}
            </p>
          )}
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">Description</Label>

        <Textarea
          id="description"
          {...form.register("description")}
        />
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="space-y-2">
          <Label>Type</Label>

          <Select
            value={form.watch("offeringType")}
            onValueChange={(value) =>
              form.setValue(
                "offeringType",
                value as OfferingType,
                { shouldDirty: true, shouldValidate: true },
              )
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select type" />
            </SelectTrigger>

            <SelectContent>
              <SelectItem value="PRODUCT">Product</SelectItem>
              <SelectItem value="SERVICE">Service</SelectItem>
              <SelectItem value="SUBSCRIPTION">
                Subscription
              </SelectItem>
              <SelectItem value="LICENSE">License</SelectItem>
              <SelectItem value="MEMBERSHIP">
                Membership
              </SelectItem>
              <SelectItem value="WARRANTY">Warranty</SelectItem>
              <SelectItem value="MAINTENANCE">
                Maintenance
              </SelectItem>
              <SelectItem value="RENTAL">Rental</SelectItem>
              <SelectItem value="OTHER">Other</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label>Billing</Label>

          <Select
            value={form.watch("billingType")}
            onValueChange={(value) =>
              form.setValue(
                "billingType",
                value as BillingType,
                { shouldDirty: true, shouldValidate: true },
              )
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select billing" />
            </SelectTrigger>

            <SelectContent>
              <SelectItem value="ONE_TIME">One time</SelectItem>
              <SelectItem value="RECURRING">Recurring</SelectItem>
              <SelectItem value="USAGE_BASED">
                Usage based
              </SelectItem>
              <SelectItem value="FREE">Free</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label>Interval</Label>

          <Select
            value={form.watch("billingInterval")}
            disabled={form.watch("billingType") !== "RECURRING"}
            onValueChange={(value) =>
              form.setValue(
                "billingInterval",
                value as BillingInterval,
                { shouldDirty: true, shouldValidate: true },
              )
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select interval" />
            </SelectTrigger>

            <SelectContent>
              <SelectItem value="DAILY">Daily</SelectItem>
              <SelectItem value="WEEKLY">Weekly</SelectItem>
              <SelectItem value="MONTHLY">Monthly</SelectItem>
              <SelectItem value="QUARTERLY">Quarterly</SelectItem>
              <SelectItem value="HALF_YEARLY">
                Half-yearly
              </SelectItem>
              <SelectItem value="YEARLY">Yearly</SelectItem>
              <SelectItem value="CUSTOM">Custom</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="space-y-2">
          <Label htmlFor="defaultPrice">Default price</Label>

          <Input
            id="defaultPrice"
            type="number"
            min="0"
            step="0.01"
            {...form.register("defaultPrice")}
          />

          {form.formState.errors.defaultPrice && (
            <p className="text-sm text-red-500">
              {form.formState.errors.defaultPrice.message}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="currencyCode">Currency</Label>

          <Input
            id="currencyCode"
            maxLength={3}
            {...form.register("currencyCode")}
          />

          {form.formState.errors.currencyCode && (
            <p className="text-sm text-red-500">
              {form.formState.errors.currencyCode.message}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="defaultTermDays">Term days</Label>

          <Input
            id="defaultTermDays"
            type="number"
            min="0"
            step="1"
            {...form.register("defaultTermDays")}
          />

          {form.formState.errors.defaultTermDays && (
            <p className="text-sm text-red-500">
              {form.formState.errors.defaultTermDays.message}
            </p>
          )}
        </div>
      </div>

      <div className="flex items-center space-x-2">
        <Switch
          id="renewable"
          checked={form.watch("renewable")}
          onCheckedChange={(value) =>
            form.setValue("renewable", value, {
              shouldDirty: true,
            })
          }
        />

        <Label htmlFor="renewable">Renewable</Label>
      </div>

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
        <Button
          type="submit"
          disabled={isSubmitting}
        >
          {isSubmitting ? "Saving..." : submitLabel}
        </Button>
      </div>
    </form>
  );
}