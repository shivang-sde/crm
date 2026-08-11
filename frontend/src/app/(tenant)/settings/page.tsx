"use client";

import { useAuthStore } from "@/lib/store/authStore";
import Link from "next/link";

export default function SettingPage() {
  const userRole = useAuthStore((state) => state.userRole);
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="text-sm text-muted-foreground">Manage your settings</p>
      </div>

      <div className="rounded-lg border bg-white p-6 shadow-sm">
        <h2 className="text-lg font-medium">Calling configuration</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Configure username, password of your calling providor
        </p>
        <Link
          href="/settings/calling"
          className="mt-4 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-white"
        >
          Open settings
        </Link>
      </div>

      {userRole === "ADMIN" && (
        <>
          <div className="rounded-lg border bg-white p-6 shadow-sm">
            <h2 className="text-lg font-medium">Demo Data</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Setup demo data for your account.
            </p>
            <Link
              href="/settings/demo-data"
              className="mt-4 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-white"
            >
              Open settings
            </Link>
          </div>

          <div className="rounded-lg border bg-white p-6 shadow-sm">
            <h2 className="text-lg font-medium">Calling configuration</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              Configure providers, webhook endpoints, and call opening behavior
              from one place.
            </p>
            <Link
              href="/settings/calling"
              className="mt-4 inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-white"
            >
              Open settings
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
