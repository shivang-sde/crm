"use client";

import Link from "next/link";
import { Phone, Globe, Database, Settings, ChevronRight, Key } from "lucide-react";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { SettingsLayout } from "@/components/settings/SettingsLayout";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export default function SettingsPage() {
  const { hasPermission } = usePermissions();
  const canInstallDemoData = hasPermission("tenant", "write");
  const canManageCallingAdminSettings = hasPermission("admin", "settings");

  return (
    <SettingsLayout>
      <div className="space-y-8">
        <section aria-labelledby="my-settings-heading">
          <h2 id="my-settings-heading" className="sr-only">My Settings</h2>
          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Phone className="h-5 w-5" />
                  Profile
                </CardTitle>
                <CardDescription>Manage your name, profile information and personal settings.</CardDescription>
              </CardHeader>
              <CardContent>
                <Link
                  href="/settings/profile"
                  className="inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                >
                  Open <ChevronRight className="h-4 w-4" />
                </Link>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Settings className="h-5 w-5" />
                  Preferences
                </CardTitle>
                <CardDescription>Manage your personal CRM preferences.</CardDescription>
              </CardHeader>
              <CardContent>
                <Link
                  href="/settings/preferences"
                  className="inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                >
                  Open <ChevronRight className="h-4 w-4" />
                </Link>
              </CardContent>
            </Card>
          </div>
        </section>

        <section aria-labelledby="workspace-heading">
          <h2 id="workspace-heading" className="sr-only">Workspace</h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Phone className="h-5 w-5" />
                  Calling
                </CardTitle>
                <CardDescription>Configure calling behavior and provider settings for your workspace.</CardDescription>
              </CardHeader>
              <CardContent>
                <Link
                  href="/settings/calling"
                  className="inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                >
                  Open <ChevronRight className="h-4 w-4" />
                </Link>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Globe className="h-5 w-5" />
                  HTTP Connections
                </CardTitle>
                <CardDescription>Manage outbound HTTP connections used by integrations and workflows.</CardDescription>
              </CardHeader>
              <CardContent>
                <Link
                  href="/settings/http-connections"
                  className="inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                >
                  Open <ChevronRight className="h-4 w-4" />
                </Link>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Key className="h-5 w-5" />
                  HTTP Credentials
                </CardTitle>
                <CardDescription>Manage encrypted credentials for generic HTTP workflows.</CardDescription>
              </CardHeader>
              <CardContent>
                <Link
                  href="/settings/http-credentials"
                  className="inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                >
                  Open <ChevronRight className="h-4 w-4" />
                </Link>
              </CardContent>
            </Card>

            {canInstallDemoData && (
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Database className="h-5 w-5" />
                    Demo Workspace
                  </CardTitle>
                  <CardDescription>Populate this tenant with realistic sample CRM data.</CardDescription>
                </CardHeader>
                <CardContent>
                  <Link
                    href="/settings/demo-data"
                    className="inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                  >
                    Open <ChevronRight className="h-4 w-4" />
                  </Link>
                </CardContent>
              </Card>
            )}

            {canManageCallingAdminSettings && (
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Settings className="h-5 w-5" />
                    Calling (Admin)
                  </CardTitle>
                  <CardDescription>Configure providers, webhook endpoints, and call opening behavior.</CardDescription>
                </CardHeader>
                <CardContent>
                  <Link
                    href="/admin/settings"
                    className="inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                  >
                    Open <ChevronRight className="h-4 w-4" />
                  </Link>
                </CardContent>
              </Card>
            )}
          </div>
        </section>

        <section aria-labelledby="admin-heading">
          <h2 id="admin-heading" className="sr-only">Administration</h2>
          <p className="text-sm text-muted-foreground">
            Additional administrative settings are available in the sidebar under Administration.
          </p>
        </section>
      </div>
    </SettingsLayout>
  );
}