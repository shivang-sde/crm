"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Bell, CheckCheck, Loader2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Separator } from "@/components/ui/separator";
import { useAuthStore } from "@/lib/store/authStore";
import {
  useMarkAllNotificationsAsRead,
  useMarkNotificationAsRead,
  useNotifications,
  useUnreadNotificationCount,
} from "@/lib/hooks/notifications";
import type { NotificationResponse } from "@/types/notifications";

function getNotificationRoute(notification: NotificationResponse) {
  switch (notification.referenceType) {
    case "TASK":
      return notification.referenceId ? `/tasks/${notification.referenceId}` : "/tasks";
    case "CALL":
      return notification.referenceId ? `/calls/${notification.referenceId}` : "/calls";
    case "MEETING":
      return notification.referenceId ? `/meetings/${notification.referenceId}` : "/meetings";
    default:
      return null;
  }
}

function formatTime(value?: string | null) {
  if (!value) {
    return "just now";
  }

  try {
    const diffInSeconds = Math.floor((Date.now() - new Date(value).getTime()) / 1000);
    if (diffInSeconds < 60) {
      return "just now";
    }

    const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
    if (diffInSeconds < 3600) {
      return rtf.format(-Math.floor(diffInSeconds / 60), "minute");
    }
    if (diffInSeconds < 86_400) {
      return rtf.format(-Math.floor(diffInSeconds / 3600), "hour");
    }

    return rtf.format(-Math.floor(diffInSeconds / 86_400), "day");
  } catch {
    return "just now";
  }
}

export function NotificationBell() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const hydrated = useAuthStore((state) => state.hydrated);
  const { data: unreadCountData, isLoading: isUnreadLoading } = useUnreadNotificationCount();
  const { data: notificationsData, isLoading: isNotificationsLoading, isError: isNotificationsError, refetch } = useNotifications({ page: 0, size: 10, read: false }, open);
  const markOneAsRead = useMarkNotificationAsRead();
  const markAllAsRead = useMarkAllNotificationsAsRead();

  const unreadCount = unreadCountData?.count ?? 0;
  const notifications = useMemo(() => notificationsData?.content ?? [], [notificationsData]);
  const hasUnreadItems = notifications.some((item) => !item.read);

  if (!isAuthenticated || !hydrated) {
    return null;
  }

  const handleSelect = async (notification: NotificationResponse) => {
    if (!notification.read) {
      markOneAsRead.mutate(notification.id);
    }

    const route = getNotificationRoute(notification);
    if (route) {
      router.push(route);
    }
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" className="relative rounded-full" type="button">
          <Bell className="h-4 w-4" />
          {unreadCount > 0 ? (
            <Badge className="absolute -right-1 -top-1 min-w-5 rounded-full px-1.5 py-0.5 text-[10px]">
              {unreadCount > 99 ? "99+" : unreadCount}
            </Badge>
          ) : null}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[360px] p-0">
        <div className="flex items-center justify-between px-4 py-3">
          <div>
            <p className="text-sm font-semibold">Notifications</p>
            <p className="text-xs text-muted-foreground">{unreadCount > 0 ? `${unreadCount} unread` : "All caught up"}</p>
          </div>
          {hasUnreadItems ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => markAllAsRead.mutate()}
              disabled={markAllAsRead.isPending}
              className="h-7 px-2"
              type="button"
            >
              {markAllAsRead.isPending ? <Loader2 className="mr-1 h-3 w-3 animate-spin" /> : <CheckCheck className="mr-1 h-3 w-3" />}
              Mark all read
            </Button>
          ) : null}
        </div>
        <Separator />
        <div className="max-h-[360px] overflow-y-auto px-2 py-2">
          {isUnreadLoading || isNotificationsLoading ? (
            <div className="flex items-center justify-center py-8 text-sm text-muted-foreground">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Loading notifications...
            </div>
          ) : isNotificationsError ? (
            <div className="px-3 py-6 text-center text-sm text-destructive">
              Unable to load notifications right now.
            </div>
          ) : notifications.length === 0 ? (
            <div className="px-3 py-6 text-center text-sm text-muted-foreground">
              No notifications yet.
            </div>
          ) : (
            <div className="space-y-1">
              {notifications.map((notification) => (
                <button
                  key={notification.id}
                  type="button"
                  onClick={() => handleSelect(notification)}
                  className={`w-full rounded-lg border px-3 py-2 text-left transition-colors ${notification.read ? "border-transparent bg-background/70" : "border-primary/20 bg-primary/5"}`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <p className="truncate text-sm font-medium">{notification.title}</p>
                        {!notification.read ? <span className="h-2 w-2 rounded-full bg-primary" /> : null}
                      </div>
                      {notification.message ? (
                        <p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{notification.message}</p>
                      ) : null}
                    </div>
                    <span className="shrink-0 text-[11px] text-muted-foreground">{formatTime(notification.createdAt)}</span>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
        <Separator />
        <div className="flex items-center justify-between px-4 py-2 text-xs text-muted-foreground">
          <span>Updates every 30 seconds</span>
          <Button variant="link" size="sm" className="h-auto px-0 text-xs" type="button" onClick={() => refetch()}>
            Refresh
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
