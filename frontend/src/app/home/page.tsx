"use client";

import React, { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";

import { useAuthStore } from "@/lib/store/authStore";
import { getDefaultRoute } from "@/lib/constants/navigation";

/**
 * Entry resolver.
 *
 * "/" is redirected here by the proxy; this page then performs the ONE
 * permission-driven redirect to the user's default landing area once the
 * permission map has loaded. It never redirects to itself:
 * getDefaultRoute() returns "/no-access" when nothing is granted, which is a
 * real rendered page — so no redirect loops are possible for custom roles,
 * empty roles, or platform roles.
 */
export default function HomeEntryPage() {
  const router = useRouter();

  const hydrated = useAuthStore((state) => state.hydrated);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);
  const userRole = useAuthStore((state) => state.userRole);
  const permissions = useAuthStore((state) => state.permissions);
  const permissionsLoaded = useAuthStore((state) => state.permissionsLoaded);

  useEffect(() => {
    if (!hydrated) return;

    if (!isAuthenticated || !user) {
      router.replace("/sign-in");
      return;
    }

    if (!permissionsLoaded) return;

    const target = getDefaultRoute(permissions, userRole ?? user.roleName ?? null);
    if (target !== "/home") {
      router.replace(target);
    }
  }, [hydrated, isAuthenticated, user, userRole, permissions, permissionsLoaded, router]);

  return (
    <div className="flex h-screen items-center justify-center">
      <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
    </div>
  );
}
