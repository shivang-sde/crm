"use client";

import React from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import {
  useCreateCall,
  useUpdateCall,
} from "@/hooks/tasks/useCalls";

import type {
  CallCreateRequest,
  CallResponse,
  CallUpdateRequest,
} from "@/types/calls";
import { EntityType } from "@/types";

interface CallFormProps {
  call?: CallResponse;
  entityType?: EntityType;
  entityId?: string;
  onSuccess?: () => void;
  onCancel?: () => void;
}

function toDateInputValue(value?: string | null): string {
  if (!value) {
    return "";
  }

  return value.split("T")[0];
}

export function CallForm({
  call,
  entityType,
  entityId,
  onSuccess,
  onCancel,
}: CallFormProps) {
  const createMutation = useCreateCall();
  const updateMutation = useUpdateCall();

  const form = useForm<CallCreateRequest>({
    defaultValues: {
      subject: call?.subject ?? "",
      description: call?.description ?? "",
      callType: call?.callType ?? "OUTGOING",
      phoneNumber: call?.phoneNumber ?? "",
      startTime: toDateInputValue(call?.startTime),
      status: call?.status ?? "PLANNED",

      entityType:
        call?.entityType ??
        entityType ??
        "LEAD",

      entityId:
        call?.entityId ??
        entityId ??
        "",

      remindAt:
        call?.remindAt ??
        undefined,

      recurrence:
        call?.recurrence ??
        undefined,

      assignedToId:
        call?.assignedTo ??
        undefined,
    },
  });

  const onSubmit = (
    data: CallCreateRequest
  ) => {
    if (call) {
      const updateRequest: CallUpdateRequest = {
        ...data,
      };

      updateMutation.mutate(
        {
          id: call.id,
          request: updateRequest,
        },
        {
          onSuccess,
        }
      );

      return;
    }

    createMutation.mutate(data, {
      onSuccess,
    });
  };

  const isSubmitting =
    createMutation.isPending ||
    updateMutation.isPending;

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-4"
      >
        <FormField
          control={form.control}
          name="subject"
          rules={{
            required: "Subject is required",
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>Subject</FormLabel>

              <FormControl>
                <Input
                  {...field}
                  placeholder="Enter call subject"
                />
              </FormControl>

              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="description"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Description</FormLabel>

              <FormControl>
                <Textarea
                  {...field}
                  value={field.value ?? ""}
                  rows={4}
                  placeholder="Enter call description"
                />
              </FormControl>

              <FormMessage />
            </FormItem>
          )}
        />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField
            control={form.control}
            name="callType"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Call Type</FormLabel>

                <Select
                  value={field.value}
                  onValueChange={field.onChange}
                >
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select type" />
                    </SelectTrigger>
                  </FormControl>

                  <SelectContent>
                    <SelectItem value="INCOMING">
                      Incoming
                    </SelectItem>

                    <SelectItem value="OUTGOING">
                      Outgoing
                    </SelectItem>
                  </SelectContent>
                </Select>

                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="phoneNumber"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Phone Number</FormLabel>

                <FormControl>
                  <Input
                    {...field}
                    value={field.value ?? ""}
                    placeholder="Enter phone number"
                  />
                </FormControl>

                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField
            control={form.control}
            name="startTime"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Date</FormLabel>

                <FormControl>
                  <Input
                    type="date"
                    {...field}
                    value={field.value ?? ""}
                  />
                </FormControl>

                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="status"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Status</FormLabel>

                <Select
                  value={field.value ?? "PLANNED"}
                  onValueChange={field.onChange}
                >
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select status" />
                    </SelectTrigger>
                  </FormControl>

                  <SelectContent>
                    <SelectItem value="PLANNED">
                      Planned
                    </SelectItem>

                    <SelectItem value="HELD">
                      Held
                    </SelectItem>

                    <SelectItem value="NOT_HELD">
                      Not Held
                    </SelectItem>

                    <SelectItem value="CANCELLED">
                      Cancelled
                    </SelectItem>
                  </SelectContent>
                </Select>

                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <div className="flex justify-end gap-2 pt-4">
          <Button
            type="button"
            variant="outline"
            onClick={onCancel}
            disabled={isSubmitting}
          >
            Cancel
          </Button>

          <Button
            type="submit"
            disabled={isSubmitting}
          >
            {isSubmitting
              ? call
                ? "Updating..."
                : "Creating..."
              : call
                ? "Update Call"
                : "Create Call"}
          </Button>
        </div>
      </form>
    </Form>
  );
}