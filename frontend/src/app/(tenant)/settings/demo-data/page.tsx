"use client";

import React, { useState } from "react";
import { Loader2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { useDemoDataStatus, useInstallDemoData } from "@/lib/hooks/useDemoData";
import { SettingsLayout } from "@/components/settings/SettingsLayout";

export default function DemoDataSettingsPage() {
  const { hasPermission } = usePermissions();

  const { data: status, isLoading: isStatusLoading, isError: isStatusError, refetch: refetchStatus } = useDemoDataStatus();
  const installMutation = useInstallDemoData();

  const [isConfirmOpen, setIsConfirmOpen] = useState(false);

  const handleInstallClick = () => {
    setIsConfirmOpen(true);
  };

  const confirmInstallation = () => {
    installMutation.mutate(undefined, {
      onSuccess: () => {
        setIsConfirmOpen(false);
      },
      onError: () => {
        setIsConfirmOpen(false);
      }
    });
  };

  const getLabelForModule = (key: string) => {
    const labels: Record<string, string> = {
      leads: "Leads",
      accounts: "Accounts",
      contacts: "Contacts",
      deals: "Deals",
      offerings: "Offerings",
      tasks: "Tasks",
      calls: "Calls",
      meetings: "Meetings",
      dealLineItems: "Deal Line Items",
      entitlements: "Entitlements",
      leadStatuses: "Lead Statuses",
      leadSources: "Lead Sources",
      dealStages: "Deal Stages",
    };
    return labels[key] || key;
  };

  const renderContent = () => {
    if (isStatusLoading) {
      return (
        <div className="flex h-64 items-center justify-center">
          <Loader2 className="h-8 w-8 animate-spin text-gray-500" />
        </div>
      );
    }

    if (isStatusError) {
      return (
        <div className="flex h-64 flex-col items-center justify-center space-y-4">
          <p className="text-sm text-destructive">Unable to load demo workspace status.</p>
          <Button variant="outline" onClick={() => refetchStatus()}>
            Retry
          </Button>
        </div>
      );
    }

    if (status?.installed) {
      return (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <div className="flex items-center space-x-3">
                <CardTitle className="text-2xl">Demo Workspace Installed</CardTitle>
                <Badge variant="secondary">Ready</Badge>
              </div>
              <CardDescription>
                This demo template has already been installed for this tenant.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="flex flex-col space-y-1 text-sm text-muted-foreground">
                <p>Template version: {status.templateVersion}</p>
                {status.installedAt && (
                  <p>Installed at: {new Date(status.installedAt).toLocaleString()}</p>
                )}
              </div>

              {status.counts && Object.keys(status.counts).length > 0 && (
                <div className="space-y-4">
                  <h3 className="font-medium">Installation Summary</h3>
                  <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
                    {Object.entries(status.counts).map(([key, count]) => (
                      <div key={key} className="flex flex-col rounded-md border p-4 text-sm">
                        <span className="text-muted-foreground">{getLabelForModule(key)}</span>
                        <span className="text-2xl font-bold">{count}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      );
    }

    return (
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <div className="flex items-center space-x-3">
              <CardTitle className="text-2xl">Demo Workspace</CardTitle>
              <Badge variant="outline">Not installed</Badge>
            </div>
            <CardDescription>
              Load a realistic sample workspace so you can explore the CRM without configuring everything manually.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-2 text-sm text-muted-foreground">
              <p>The generic sales demo includes:</p>
              <ul className="list-inside list-disc space-y-1 pl-2">
                <li>Lead statuses & sources</li>
                <li>Leads</li>
                <li>Accounts</li>
                <li>Contacts</li>
                <li>Deal pipeline</li>
                <li>Deals</li>
                <li>Offerings</li>
                <li>Tasks</li>
                <li>Calls</li>
                <li>Meetings</li>
                <li>Customer Entitlements</li>
              </ul>
            </div>

            <Button onClick={handleInstallClick} disabled={installMutation.isPending}>
              {installMutation.isPending ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Setting up demo workspace...
                </>
              ) : (
                "Load Demo Workspace"
              )}
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  };

  return (
    <SettingsLayout>
      <div className="mx-auto max-w-4xl space-y-8">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Demo Workspace</h1>
          <p className="text-muted-foreground">
            Populate this tenant with realistic sample CRM data for testing and product exploration.
          </p>
        </div>

        {renderContent()}

        <AlertDialog open={isConfirmOpen} onOpenChange={setIsConfirmOpen}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Load demo workspace?</AlertDialogTitle>
              <AlertDialogDescription>
                This will add realistic sample CRM records and any required demo configuration to the current tenant. Existing tenant data will not be deleted or overwritten.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel disabled={installMutation.isPending}>Cancel</AlertDialogCancel>
              <AlertDialogAction
                onClick={(e) => {
                  e.preventDefault();
                  confirmInstallation();
                }}
                disabled={installMutation.isPending}
              >
                {installMutation.isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Loading...
                  </>
                ) : (
                  "Load Demo Data"
                )}
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>
    </SettingsLayout>
  );
}