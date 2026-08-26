"use client";

import React, { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";

import { useAuthStore } from "@/lib/store/authStore";
import {
  canAccessRoute,
  getDefaultRoute,
} from "@/lib/constants/navigation";
import { NoAccessNotice } from "@/components/shared/NoAccessNotice";

function CenteredSpinner() {
  return (
    <div className="flex h-screen items-center justify-center">
      <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
    </div>
  );
}

/**
 * Permission-driven client route guard (UX layer only).
 *
 * Evaluation order:
 *   1. wait for auth hydration + authentication
 *   2. wait for the permission map to load (never route on partial data)
 *   3. canAccessRoute(pathname, permissions, role)
 *
 * Loop safety: when access is denied we redirect to getDefaultRoute(...);
 * if that destination equals the current pathname (i.e. there is nowhere
 * better to go) we render the inline no-access notice instead of
 * redirecting again.
 *
 * The backend remains the authoritative authorization layer; this guard only
 * prevents users from navigating into areas their permissions do not cover.
 */
export const RouteGuard: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const router = useRouter();
  const pathname = usePathname() ?? "/";

  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const hydrated = useAuthStore((state) => state.hydrated);
  const user = useAuthStore((state) => state.user);
  const userRole = useAuthStore((state) => state.userRole);
  const permissions = useAuthStore((state) => state.permissions);
  const permissionsLoaded = useAuthStore((state) => state.permissionsLoaded);

  const allowed = canAccessRoute(pathname, permissions, userRole);

  useEffect(() => {
    if (!hydrated || !isAuthenticated || !permissionsLoaded || allowed) {
      return;
    }

    const fallback = getDefaultRoute(permissions, userRole ?? user?.roleName ?? null);
    if (fallback !== pathname) {
      router.replace(fallback);
    }
  }, [
    hydrated,
    isAuthenticated,
    permissionsLoaded,
    allowed,
    pathname,
    permissions,
    userRole,
    user?.roleName,
    router,
  ]);

  if (!hydrated || !isAuthenticated) {
    return <CenteredSpinner />;
  }

  // Never evaluate routes against a partially loaded permission map.
  if (!permissionsLoaded) {
    return <CenteredSpinner />;
  }

  if (allowed) {
    return <>{children}</>;
  }

  // Denied with no alternative destination: deterministic terminal state.
  return <NoAccessNotice />;
};
