import { z } from "zod";

import type {
  Recurrence,
} from "./recurrence";

export type TaskStatus =
  | "NOT_STARTED"
  | "IN_PROGRESS"
  | "WAITING_ON_SOMEONE"
  | "DEFERRED"
  | "COMPLETED";

export type TaskPriority =
  | "LOW"
  | "MEDIUM"
  | "HIGH"
  | "URGENT";

export type EntityType =
  | "LEAD"
  | "CONTACT"
  | "ACCOUNT"
  | "DEAL";

export interface AssignedUserResponse {
  id: string;
  name: string;
  email: string;
}

export interface TaskResponse {
  id: string;
  tenantId?: string;

  subject: string;
  description?: string;

  dueDate?: string;
  status: TaskStatus;
  priority: TaskPriority;

  entityType?: EntityType;
  entityId?: string;
  entityName?: string;

  remindAt?: string;
  recurrence?: Recurrence;

  completedAt?: string;

  isClosed: boolean;
  isOverdue?: boolean;

  customData?: Record<
    string,
    unknown
  >;

  ownerUserId?: string;

  /**
   * Your provided backend response does not currently
   * return an AssignedUserResponse object.
   *
   * Keep this only if another mapper enriches the API response.
   */
  assignedTo?: AssignedUserResponse;

  /**
   * Backend currently returns UUID values for these fields.
   */
  createdBy: string;
  updatedBy?: string;

  createdAt: string;
  updatedAt: string;
}

export interface TaskCreateRequest {
  subject: string;
  description?: string;

  due_date?: string;

  status?: TaskStatus;
  priority?: TaskPriority;

  entity_type?: EntityType;
  entity_id?: string;

  remind_at?: string;
  recurrence?: Recurrence;

  owner_user_id?: string;

  /**
   * Enable this only after uncommenting assignedTo
   * in the backend DTO.
   */
  // assigned_to?: string;

  custom_data?: Record<
    string,
    unknown
  >;
}

export interface TaskUpdateRequest {
  subject?: string;
  description?: string;

  due_date?: string;

  status?: TaskStatus;
  priority?: TaskPriority;

  entity_type?: EntityType;
  entity_id?: string;

  remind_at?: string;
  recurrence?: Recurrence;

  owner_user_id?: string;

  // assigned_to?: string;

  custom_data?: Record<
    string,
    unknown
  >;
}

export const taskStatusSchema =
  z.enum([
    "NOT_STARTED",
    "IN_PROGRESS",
    "WAITING_ON_SOMEONE",
    "DEFERRED",
    "COMPLETED",
  ]);

export const taskPrioritySchema =
  z.enum([
    "LOW",
    "MEDIUM",
    "HIGH",
    "URGENT",
  ]);

export const entityTypeSchema =
  z.enum([
    "LEAD",
    "CONTACT",
    "ACCOUNT",
    "DEAL",
  ]);

export const taskSchema = z
  .object({
    subject: z
      .string()
      .trim()
      .min(
        1,
        "Subject is required"
      )
      .max(
        255,
        "Subject must not exceed 255 characters"
      ),

    description:
      z.string().optional(),

    dueDate:
      z.string().optional(),

    status:
      taskStatusSchema,

    priority:
      taskPrioritySchema,

    entityType:
      entityTypeSchema.optional(),

    entityId: z
      .string()
      .uuid(
        "Invalid entity ID"
      )
      .optional()
      .or(z.literal("")),

    remindAt:
      z.string().optional(),

    recurrence:
      z
        .custom<Recurrence>()
        .optional(),

    ownerUserId: z
      .string()
      .uuid(
        "Invalid owner user ID"
      )
      .optional()
      .or(z.literal("")),

    assignedToId: z
      .string()
      .uuid(
        "Invalid assigned user ID"
      )
      .optional()
      .or(z.literal("")),
  })
  .superRefine(
    (data, ctx) => {
      const hasEntityType =
        Boolean(data.entityType);

      const hasEntityId =
        Boolean(data.entityId);

      if (
        hasEntityType !==
        hasEntityId
      ) {
        ctx.addIssue({
          code:
            z.ZodIssueCode
              .custom,

          path: hasEntityType
            ? ["entityId"]
            : ["entityType"],

          message:
            "Both entity type and entity ID are required",
        });
      }

      if (
        data.dueDate &&
        data.remindAt
      ) {
        const dueDate =
          new Date(
            `${data.dueDate}T23:59:59`
          );

        const remindAt =
          new Date(
            data.remindAt
          );

        if (
          !Number.isNaN(
            dueDate.getTime()
          ) &&
          !Number.isNaN(
            remindAt.getTime()
          ) &&
          remindAt >= dueDate
        ) {
          ctx.addIssue({
            code:
              z.ZodIssueCode
                .custom,

            path: ["remindAt"],

            message:
              "Reminder must be before the due date",
          });
        }
      }
    }
  );

export type TaskFormValues =
  z.infer<typeof taskSchema>;