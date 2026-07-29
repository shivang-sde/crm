"use client";

import React from "react";
import {
  useFieldArray,
  useForm,
} from "react-hook-form";
import { Plus, X } from "lucide-react";

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
  useCreateMeeting,
  useUpdateMeeting,
} from "@/hooks/tasks/useMeetings";

import type {
  MeetingCreateRequest,
  MeetingResponse,
  MeetingStatus,
  MeetingType,
  MeetingUpdateRequest,
} from "@/types/meetings";
import type {
  EntityType,
} from "@/types/tasks";
import type {
  Recurrence,
} from "@/types/recurrence";
import { toInstant } from "@/lib/utils";

interface MeetingFormProps {
  meeting?: MeetingResponse;
  entityType?: EntityType;
  entityId?: string;
  onSuccess?: () => void;
  onCancel?: () => void;
}

/**
 * UI-only attendee structure.
 *
 * The form needs an object array so useFieldArray can safely use:
 * attendees.${index}.email
 */
interface MeetingAttendeeFormValue {
  email: string;
}

/**
 * Keep one stable shape for React Hook Form.
 *
 * This shape is independent of the backend request naming convention.
 */
interface MeetingFormValues {
  subject: string;
  description: string;
  location: string;
  agenda: string;

  meetingType: MeetingType;
  startTime: string;
  endTime: string;
  status: MeetingStatus;

  attendees: MeetingAttendeeFormValue[];

  entityType?: EntityType;
  entityId: string;

  remindAt: string;
  recurrence?: Recurrence;

  assignedToId: string;
}

/**
 * Converts an ISO date to the value expected by datetime-local.
 *
 * Example:
 * 2026-07-29T10:30:00Z
 * becomes:
 * 2026-07-29T10:30
 */
function toDateTimeLocal(
  value?: string | null
): string {
  if (!value) {
    return "";
  }

  return value.slice(0, 16);
}

function getAttendeeEmail(
  attendee: unknown
): string {
  if (typeof attendee === "string") {
    return attendee;
  }

  if (
    attendee &&
    typeof attendee === "object" &&
    "email" in attendee &&
    typeof attendee.email === "string"
  ) {
    return attendee.email;
  }

  return "";
}

export function MeetingForm({
  meeting,
  entityType,
  entityId,
  onSuccess,
  onCancel,
}: MeetingFormProps) {
  const createMutation = useCreateMeeting();
  const updateMutation = useUpdateMeeting();

  const form = useForm<MeetingFormValues>({
    defaultValues: {
      subject: meeting?.subject ?? "",
      description: meeting?.description ?? "",
      location: meeting?.location ?? "",
      agenda: meeting?.agenda ?? "",

      meetingType:
        meeting?.meetingType ??
        "IN_PERSON",

      startTime: toDateTimeLocal(
        meeting?.startTime
      ),

      endTime: toDateTimeLocal(
        meeting?.endTime
      ),

      status:
        meeting?.status ??
        "PLANNED",

      attendees:
        meeting?.attendees?.map(
          (attendee) => ({
            email:
              getAttendeeEmail(attendee),
          })
        ) ?? [],

      entityType:
        meeting?.entityType ??
        entityType,

      entityId:
        meeting?.entityId ??
        entityId ??
        "",

      remindAt: toDateTimeLocal(
        meeting?.remindAt
      ),

      recurrence:
        meeting?.recurrence ??
        undefined,

      /**
       * Your error says assignedTo is a string,
       * so do not use meeting.assignedTo.id.
       */
      assignedToId:
        meeting?.assignedTo ??
        "",
    },
  });

  const {
    fields,
    append,
    remove,
  } = useFieldArray({
    control: form.control,
    name: "attendees",
  });

  const onSubmit = (
    values: MeetingFormValues
  ) => {
    const attendeeEmails = values.attendees
      .map((attendee) =>
        attendee.email.trim()
      )
      .filter(Boolean);

    const commonRequest = {
      subject: values.subject.trim(),

      description:
        values.description.trim() ||
        undefined,

      location:
        values.location.trim() ||
        undefined,

      agenda:
        values.agenda.trim() ||
        undefined,

      meeting_type:
        values.meetingType,

      start_time: toInstant(values.startTime),

      end_time: values.endTime
        ? toInstant(values.endTime)
        : undefined,
        
      remind_at: values.remindAt
        ? toInstant(values.remindAt)
        : undefined,

      status:
        values.status,

      attendees:
        attendeeEmails,

      entity_type:
        values.entityType,

      entity_id:
        values.entityId ||
        undefined,

      recurrence:
        values.recurrence,

      assigned_to:
        values.assignedToId ||
        undefined,
    };

    if (meeting) {
      const request: MeetingUpdateRequest = {
        ...commonRequest,
      };

      updateMutation.mutate(
        {
          id: meeting.id,
          request,
        },
        {
          onSuccess,
        }
      );

      return;
    }

    const request: MeetingCreateRequest = {
      ...commonRequest,
      subject: values.subject.trim(),
      start_time: values.startTime,
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
        className="space-y-6"
      >
        <FormField
          control={form.control}
          name="subject"
          rules={{
            required:
              "Subject is required",
          }}
          render={({ field }) => (
            <FormItem>
              <FormLabel>
                Subject
              </FormLabel>

              <FormControl>
                <Input
                  {...field}
                  placeholder="Enter meeting subject"
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
                  rows={3}
                  placeholder="Enter meeting description"
                />
              </FormControl>

              <FormMessage />
            </FormItem>
          )}
        />

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <FormField
            control={form.control}
            name="location"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Location
                </FormLabel>

                <FormControl>
                  <Input
                    {...field}
                    placeholder="Address or video link"
                  />
                </FormControl>

                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="agenda"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Agenda
                </FormLabel>

                <FormControl>
                  <Input
                    {...field}
                    placeholder="Meeting agenda"
                  />
                </FormControl>

                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <FormField
            control={form.control}
            name="meetingType"
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Meeting type
                </FormLabel>

                <Select
                  value={field.value}
                  onValueChange={
                    field.onChange
                  }
                >
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select meeting type" />
                    </SelectTrigger>
                  </FormControl>

                  <SelectContent>
                    <SelectItem value="IN_PERSON">
                      In person
                    </SelectItem>

                    <SelectItem value="VIDEO">
                      Video
                    </SelectItem>

                    <SelectItem value="PHONE">
                      Phone
                    </SelectItem>
                  </SelectContent>
                </Select>

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
                    <SelectItem value="PLANNED">
                      Planned
                    </SelectItem>

                    <SelectItem value="HELD">
                      Held
                    </SelectItem>

                    <SelectItem value="NOT_HELD">
                      Not held
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

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <FormField
            control={form.control}
            name="startTime"
            rules={{
              required:
                "Start time is required",
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  Start time
                </FormLabel>

                <FormControl>
                  <Input
                    type="datetime-local"
                    {...field}
                  />
                </FormControl>

                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="endTime"
            rules={{
              required:
                "End time is required",
            }}
            render={({ field }) => (
              <FormItem>
                <FormLabel>
                  End time
                </FormLabel>

                <FormControl>
                  <Input
                    type="datetime-local"
                    {...field}
                  />
                </FormControl>

                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        <div className="space-y-3">
          <div>
            <FormLabel>
              Attendees
            </FormLabel>

            <p className="mt-1 text-sm text-muted-foreground">
              Add attendee email addresses.
            </p>
          </div>

          {fields.length === 0 && (
            <div className="rounded-md border border-dashed p-4 text-sm text-muted-foreground">
              No attendees added.
            </div>
          )}

          {fields.map(
            (attendeeField, index) => (
              <div
                key={attendeeField.id}
                className="flex items-start gap-2"
              >
                <FormField
                  control={form.control}
                  name={`attendees.${index}.email`}
                  rules={{
                    required:
                      "Email is required",
                    pattern: {
                      value:
                        /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                      message:
                        "Enter a valid email address",
                    },
                  }}
                  render={({ field }) => (
                    <FormItem className="flex-1">
                      <FormControl>
                        <Input
                          {...field}
                          type="email"
                          placeholder="attendee@example.com"
                        />
                      </FormControl>

                      <FormMessage />
                    </FormItem>
                  )}
                />

                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  aria-label="Remove attendee"
                  onClick={() =>
                    remove(index)
                  }
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            )
          )}

          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() =>
              append({
                email: "",
              })
            }
          >
            <Plus className="mr-2 h-4 w-4" />
            Add attendee
          </Button>
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
              ? meeting
                ? "Updating..."
                : "Creating..."
              : meeting
                ? "Update Meeting"
                : "Create Meeting"}
          </Button>
        </div>
      </form>
    </Form>
  );
}