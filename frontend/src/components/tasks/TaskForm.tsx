"use client";

import React from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";

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
  useCreateTask,
  useUpdateTask,
} from "@/hooks/tasks/useTasks";

import {
  taskSchema,
  type EntityType,
  type TaskCreateRequest,
  type TaskFormValues,
  type TaskResponse,
  type TaskUpdateRequest,
} from "@/types/tasks";

import { RecurrencePicker } from "./RecurrencePicker";
import { ReminderPicker } from "./ReminderPicker";

interface TaskFormProps {
  task?: TaskResponse;
  entityType?: EntityType;
  entityId?: string;
  onSuccess?: () => void;
  onCancel?: () => void;
}

function toDateInputValue(
  value?: string | null
): string {
  if (!value) {
    return "";
  }

  return value.slice(0, 10);
}

function toDateTimeLocal(
  value?: string | null
): string {
  if (!value) {
    return "";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const timezoneOffset =
    date.getTimezoneOffset() * 60_000;

  return new Date(
    date.getTime() - timezoneOffset
  )
    .toISOString()
    .slice(0, 16);
}

function toIsoString(
  value?: string
): string | undefined {
  if (!value) {
    return undefined;
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return undefined;
  }

  return date.toISOString();
}

/**
 * A date input gives YYYY-MM-DD.
 *
 * Since the backend expects Instant, this converts the selected
 * date to an ISO instant at the end of the local day.
 */
function dueDateToIso(
  value?: string
): string | undefined {
  if (!value) {
    return undefined;
  }

  const date = new Date(
    `${value}T23:59:59`
  );

  if (Number.isNaN(date.getTime())) {
    return undefined;
  }

  return date.toISOString();
}

function emptyToUndefined(
  value?: string
): string | undefined {
  const normalized = value?.trim();

  return normalized || undefined;
}

export function TaskForm({
  task,
  entityType,
  entityId,
  onSuccess,
  onCancel,
}: TaskFormProps) {
  const createMutation = useCreateTask();
  const updateMutation = useUpdateTask();

  const form = useForm<TaskFormValues>({
    resolver: zodResolver(taskSchema),

    defaultValues: {
      subject: task?.subject ?? "",
      description:
        task?.description ?? "",

      dueDate: toDateInputValue(
        task?.dueDate
      ),

      status:
        task?.status ??
        "NOT_STARTED",

      priority:
        task?.priority ??
        "MEDIUM",

      entityType:
        task?.entityType ??
        entityType,

      entityId:
        task?.entityId ??
        entityId ??
        "",

      remindAt: toDateTimeLocal(
        task?.remindAt
      ),

      recurrence:
        task?.recurrence ??
        undefined,

      ownerUserId:
        task?.ownerUserId ??
        "",

      assignedToId:
        task?.assignedTo?.id ??
        "",
    },
  });

  const onSubmit = (
    values: TaskFormValues
  ) => {
    const commonRequest = {
      subject: values.subject.trim(),

      description:
        emptyToUndefined(
          values.description
        ),

      due_date:
        dueDateToIso(
          values.dueDate
        ),

      status:
        values.status,

      priority:
        values.priority,

      entity_type:
        values.entityType,

      entity_id:
        emptyToUndefined(
          values.entityId
        ),

      remind_at:
        toIsoString(
          values.remindAt
        ),

      recurrence:
        values.recurrence,

      owner_user_id:
        emptyToUndefined(
          values.ownerUserId
        ),

      /**
       * Do not include assigned_to yet because it is
       * commented out in your backend TaskCreateRequest.
       *
       * Add it after backend support is enabled.
       */
    };

    if (task) {
      const request: TaskUpdateRequest = {
        ...commonRequest,
      };

      updateMutation.mutate(
        {
          id: task.id,
          request,
        },
        {
          onSuccess,
        }
      );

      return;
    }

    const request: TaskCreateRequest = {
      ...commonRequest,
      subject: values.subject.trim(),
    };

    createMutation.mutate(request, {
      onSuccess,
    });
  };

  const isSubmitting =
    createMutation.isPending ||
    updateMutation.isPending;

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(
          onSubmit
        )}
        className="space-y-4"
      >
        <FormField
          control={form.control}
          name="subject"
          render={({ field }) => (
            <FormItem>
              <FormLabel>
                Subject
              </FormLabel>

              <FormControl>
                <Input
                  {...field}
                  placeholder="Enter task subject"
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
              <FormLabel>
                Description
              </FormLabel>

              <FormControl>
                <Textarea
                  {...field}
                  value={
                    field.value ?? ""
                  }
                  rows={4}
                  placeholder="Enter task description"
                />
              </FormControl>

              <FormMessage />
            </FormItem>
          )}
        />

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <FormField
            control={form.control}
            name="dueDate"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Due date
                </FormLabel>

                <FormControl>
                  <Input
                    type="date"
                    {...field}
                    value={
                      field.value ?? ""
                    }
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
                <FormLabel>
                  Status
                </FormLabel>

                <Select
                  value={field.value}
                  onValueChange={
                    field.onChange
                  }
                >
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select status" />
                    </SelectTrigger>
                  </FormControl>

                  <SelectContent>
                    <SelectItem value="NOT_STARTED">
                      Not started
                    </SelectItem>

                    <SelectItem value="IN_PROGRESS">
                      In progress
                    </SelectItem>

                    <SelectItem value="WAITING_ON_SOMEONE">
                      Waiting on someone
                    </SelectItem>

                    <SelectItem value="DEFERRED">
                      Deferred
                    </SelectItem>

                    <SelectItem value="COMPLETED">
                      Completed
                    </SelectItem>
                  </SelectContent>
                </Select>

                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <FormField
            control={form.control}
            name="priority"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Priority
                </FormLabel>

                <Select
                  value={field.value}
                  onValueChange={
                    field.onChange
                  }
                >
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select priority" />
                    </SelectTrigger>
                  </FormControl>

                  <SelectContent>
                    <SelectItem value="LOW">
                      Low
                    </SelectItem>

                    <SelectItem value="MEDIUM">
                      Medium
                    </SelectItem>

                    <SelectItem value="HIGH">
                      High
                    </SelectItem>

                    <SelectItem value="URGENT">
                      Urgent
                    </SelectItem>
                  </SelectContent>
                </Select>

                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="assignedToId"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Assign to
                </FormLabel>

                <Select
                  value={
                    field.value ||
                    undefined
                  }
                  onValueChange={
                    field.onChange
                  }
                  disabled
                >
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Assignment is not supported by backend yet" />
                    </SelectTrigger>
                  </FormControl>

                  <SelectContent>
                    <SelectItem value="current">
                      Current user
                    </SelectItem>
                  </SelectContent>
                </Select>

                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <ReminderPicker
          value={form.watch(
            "remindAt"
          )}
          onChange={(value) => {
            form.setValue(
              "remindAt",
              value ?? "",
              {
                shouldDirty: true,
                shouldValidate: true,
              }
            );
          }}
        />

        <RecurrencePicker
          value={form.watch(
            "recurrence"
          )}
          onChange={(value) => {
            form.setValue(
              "recurrence",
              value,
              {
                shouldDirty: true,
                shouldValidate: true,
              }
            );
          }}
        />

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
              ? task
                ? "Updating..."
                : "Creating..."
              : task
                ? "Update Task"
                : "Create Task"}
          </Button>
        </div>
      </form>
    </Form>
  );
}