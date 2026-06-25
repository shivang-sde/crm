"use client";

import Link from "next/link";
import { ArrowLeft, Settings } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { LeadStatusesAdmin } from "@/components/leads/settings/LeadStatusesAdmin";
import { LeadSourcesAdmin } from "@/components/leads/settings/LeadSourcesAdmin";
import { LeadCustomFieldsAdmin } from "@/components/leads/settings/LeadCustomFieldsAdmin";

function LeadSettingsContent() {
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/leads">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to leads
          </Link>
        </Button>
        <div className="flex items-center gap-2">
          <Settings className="h-6 w-6 text-muted-foreground" />
          <h1 className="text-2xl font-bold tracking-tight">Lead Settings</h1>
        </div>
      </div>

      <p className="text-sm text-muted-foreground">
        Configure pipeline statuses, lead sources, and dynamic custom fields for your tenant.
      </p>

      <Tabs defaultValue="statuses" className="w-full">
        <TabsList>
          <TabsTrigger value="statuses">Statuses</TabsTrigger>
          <TabsTrigger value="sources">Sources</TabsTrigger>
          <TabsTrigger value="fields">Custom Fields</TabsTrigger>
        </TabsList>
        <TabsContent value="statuses" className="mt-6">
          <LeadStatusesAdmin />
        </TabsContent>
        <TabsContent value="sources" className="mt-6">
          <LeadSourcesAdmin />
        </TabsContent>
        <TabsContent value="fields" className="mt-6">
          <LeadCustomFieldsAdmin />
        </TabsContent>
      </Tabs>
    </div>
  );
}

export default function LeadSettingsPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "lead", action: "write" }}>
      <LeadSettingsContent />
    </ProtectedRoute>
  );
}
