"use client";

import React from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/store/authStore";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { getDashboardRoute } from "@/lib/constants/navigation";
import { Loader2 } from "lucide-react";

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredPermission?: {
    module: string;
    action: string;
  };
  allowedRoles?: string[];
  fallback?: React.ReactNode;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  children,
  requiredPermission,
  allowedRoles,
  fallback,
}) => {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const hydrated = useAuthStore((state) => state.hydrated);
  const userRole = useAuthStore((state) => state.userRole);
  const { hasPermission, permissions  } = usePermissions();
  const router = useRouter();

  if (!hydrated) {
    return (
      <div className="flex h-screen items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
      </div>
    );
  }

  if (!isAuthenticated) {
    router.replace("/sign-in");
    return null;
  }

  if (allowedRoles && allowedRoles.length > 0) {
    if (!userRole || !allowedRoles.includes(userRole)) {
      if (fallback) return <>{fallback}</>;
      return (
        <div className="flex flex-col h-full items-center justify-center p-8 text-center">
          <div className="bg-red-50 p-6 rounded-lg max-w-md">
            <h2 className="text-2xl font-bold text-red-700 mb-2">Access Denied</h2>
            <p className="text-red-600">
              This area is restricted to specific roles. Your current role does not have access.
            </p>
            <button
              onClick={() => router.push(getDashboardRoute(userRole))}
              className="mt-4 px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
            >
              Return Home
            </button>
          </div>
        </div>
      );
    }
  }

  if (requiredPermission && !hasPermission(requiredPermission.module, requiredPermission.action)) {
    
      console.log('Permission check failed:', {
    required: `${requiredPermission.module}:${requiredPermission.action}`,
    hasPermission: hasPermission(requiredPermission.module, requiredPermission.action),
    allPermissions: Array.from(permissions.entries())
  });

    if (fallback) {
      return <>{fallback}</>;
    }

    return (
      <div className="flex flex-col h-full items-center justify-center p-8 text-center">
        <div className="bg-red-50 p-6 rounded-lg max-w-md">
          <h2 className="text-2xl font-bold text-red-700 mb-2">Access Denied</h2>
          <p className="text-red-600">
            You do not have permission to view this page. You need {requiredPermission.action} access to {requiredPermission.module}.
          </p>
          <button
            onClick={() => router.push(getDashboardRoute(userRole))}
            className="mt-4 px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
          >
            Return Home
          </button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
};
