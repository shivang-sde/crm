import { api } from "./client";
import { unwrapResponse } from "./api-utils";
import type { ApiResponse } from "@/types/auth";
import type {
  NotificationListParams,
  NotificationResponse,
} from "@/types/notifications";
import type { ListResponse } from "@/types/common";

interface UnreadNotificationCountResponse {
  count: number;
}

export const notificationApi = {
  listNotifications: async (params?: NotificationListParams) => {
    const response = await api.get<ApiResponse<ListResponse<NotificationResponse>>>('/notifications', {
      params,
    });

    return unwrapResponse<ListResponse<NotificationResponse>>(response);
  },

  getUnreadNotificationCount: async () => {
    const response = await api.get<ApiResponse<UnreadNotificationCountResponse>>('/notifications/unread-count');
    return unwrapResponse(response);
  },

  markNotificationAsRead: async (id: string) => {
    const response = await api.patch<ApiResponse<NotificationResponse>>(`/notifications/${id}/read`);
    return unwrapResponse(response);
  },

  markAllNotificationsAsRead: async () => {
    const response = await api.patch<ApiResponse<unknown>>('/notifications/read-all');
    return unwrapResponse(response);
  },
};
