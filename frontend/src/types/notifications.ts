export type NotificationType = "REMINDER";

export type NotificationReferenceType = "TASK" | "CALL" | "MEETING";

export interface NotificationResponse {
  id: string;
  notificationType: NotificationType;
  title: string;
  message?: string | null;
  referenceType?: NotificationReferenceType | null;
  referenceId?: string | null;
  read: boolean;
  readAt?: string | null;
  createdAt: string;
  metadata?: Record<string, unknown>;
}

export interface NotificationListParams {
  page?: number;
  size?: number;
  read?: boolean;
}
