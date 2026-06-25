"use client";

import Link from "next/link";
import { ArrowLeft, Settings } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { DealStagesAdmin } from "@/components/deals/settings/DealStagesAdmin";
import { DealCustomFieldsAdmin } from "@/components/deals/settings/DealCustomFieldsAdmin";

function DealSettingsContent() {
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/deals">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to deals
          </Link>
        </Button>
        <div className="flex items-center gap-2">
          <Settings className="h-6 w-6 text-muted-foreground" />
          <h1 className="text-2xl font-bold tracking-tight">Deal Settings</h1>
        </div>
      </div>

      <p className="text-sm text-muted-foreground">
        Configure pipeline stages and dynamic custom fields for your tenant deals.
      </p>

      <Tabs defaultValue="stages" className="w-full">
        <TabsList>
          <TabsTrigger value="stages">Stages</TabsTrigger>
          <TabsTrigger value="fields">Custom Fields</TabsTrigger>
        </TabsList>
        <TabsContent value="stages" className="mt-6">
          <DealStagesAdmin />
        </TabsContent>
        <TabsContent value="fields" className="mt-6">
          <DealCustomFieldsAdmin />
        </TabsContent>
      </Tabs>
    </div>
  );
}

export default function DealSettingsPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "deal", action: "write" }}>
      <DealSettingsContent />
    </ProtectedRoute>
  );
}
