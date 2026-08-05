import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { notificationApi } from "@/lib/api/notifications";
import type { NotificationListParams } from "@/types/notifications";
import { useAuthStore } from "@/lib/store/authStore";

const NOTIFICATION_COUNT_REFETCH_MS = 30_000;
const notificationQueryKeys = {
  all: ["notifications"] as const,
  lists: () => [...notificationQueryKeys.all, "list"] as const,
  list: (params?: NotificationListParams) => [...notificationQueryKeys.lists(), params] as const,
  unreadCount: () => [...notificationQueryKeys.all, "unread-count"] as const,
};

export function useNotifications(params?: NotificationListParams, enabled = true) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  return useQuery({
    queryKey: notificationQueryKeys.list(params),
    queryFn: () => notificationApi.listNotifications(params),
    enabled: isAuthenticated && enabled,
    staleTime: 15_000,
    gcTime: 5 * 60_000,
  });
}

export function useUnreadNotificationCount() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const hydrated = useAuthStore((state) => state.hydrated);

  return useQuery({
    queryKey: notificationQueryKeys.unreadCount(),
    queryFn: () => notificationApi.getUnreadNotificationCount(),
    enabled: isAuthenticated && hydrated,
    refetchInterval: isAuthenticated && hydrated ? NOTIFICATION_COUNT_REFETCH_MS : false,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    retry: 1,
    retryDelay: 1_000,
  });
}

export function useMarkNotificationAsRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => notificationApi.markNotificationAsRead(id),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: notificationQueryKeys.lists() }),
        queryClient.invalidateQueries({ queryKey: notificationQueryKeys.unreadCount() }),
      ]);
    },
    onError: () => {
      toast.error("Unable to update notification status.");
    },
  });
}

export function useMarkAllNotificationsAsRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => notificationApi.markAllNotificationsAsRead(),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: notificationQueryKeys.lists() }),
        queryClient.setQueryData(notificationQueryKeys.unreadCount(), { count: 0 }),
      ]);
      toast.success("All notifications marked as read.");
    },
    onError: () => {
      toast.error("Unable to mark all notifications as read.");
    },
  });
}
