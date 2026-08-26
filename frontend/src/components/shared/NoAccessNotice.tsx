"use client";

import React from "react";
import { useRouter } from "next/navigation";
import { ShieldAlert } from "lucide-react";
import { useAuthStore } from "@/lib/store/authStore";

/**
 * Minimal "no authorized destination" notice.
 *
 * This is UX-only: it is shown when the user's permission map grants no CRM
 * area (or the current route is not permitted). The backend remains the
 * authoritative authorization layer.
 */
export const NoAccessNotice: React.FC = () => {
  const router = useRouter();
  const logout = useAuthStore((state) => state.logout);

  const handleSignOut = () => {
    logout();
    router.replace("/sign-in");
  };

  return (
    <div className="flex flex-col h-full items-center justify-center p-8 text-center">
      <div className="bg-red-50 p-6 rounded-lg max-w-md">
        <ShieldAlert className="h-10 w-10 text-red-500 mx-auto mb-3" />
        <h2 className="text-2xl font-bold text-red-700 mb-2">No Access</h2>
        <p className="text-red-600 mb-1">
          Your role does not include permission for any CRM area yet.
        </p>
        <p className="text-sm text-gray-600 mb-4">
          Please contact your administrator to be assigned a role with the
          permissions you need.
        </p>
        <button
          onClick={handleSignOut}
          className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
        >
          Sign Out
        </button>
      </div>
    </div>
  );
};
