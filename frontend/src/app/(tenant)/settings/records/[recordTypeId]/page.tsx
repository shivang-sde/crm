"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Database, Pencil, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { SettingsLayout } from "@/components/settings/SettingsLayout";
import { RecordFieldsAdmin } from "@/components/records/RecordFieldsAdmin";
import { DisplayConfigEditor } from "@/components/records/DisplayConfigEditor";
import { RecordTypeDialog } from "@/components/records/RecordTypeDialog";
import { useRecordType, useUpdateRecordType } from "@/lib/hooks/records";
import { useState } from "react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";

function RecordTypeDetailContent() {
  const params = useParams<{ recordTypeId: string }>();
  const recordTypeId = params.recordTypeId;
  const { data: recordType, isLoading, isError, error } = useRecordType(recordTypeId);
  const updateMutation = useUpdateRecordType();
  const [editOpen, setEditOpen] = useState(false);

  if (isLoading) {
    return (
      <SettingsLayout>
        <div className="flex justify-center py-16">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      </SettingsLayout>
    );
  }
  if (isError || !recordType) {
    return (
      <SettingsLayout>
        <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-6 text-center">
          <p className="text-sm text-destructive">Failed to load record type.</p>
          <p className="text-xs text-muted-foreground mt-1">{(error as any)?.response?.data?.error?.message || (error as Error)?.message || "Not found"}</p>
          <Button asChild variant="outline" size="sm" className="mt-4">
            <Link href="/settings/records">Back to Record Types</Link>
          </Button>
        </div>
      </SettingsLayout>
    );
  }

  return (
    <SettingsLayout>
      <div className="space-y-6">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/settings/records">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Record Types
          </Link>
        </Button>

        <div className="rounded-xl border bg-white p-6 shadow-sm">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="space-y-1">
              <div className="flex items-center gap-3">
                <div className="h-10 w-10 rounded-xl bg-indigo-50 flex items-center justify-center">
                  <Database className="h-5 w-5 text-indigo-600" />
                </div>
                <div>
                  <h1 className="text-xl font-bold tracking-tight flex items-center gap-2">
                    {recordType.name}
                    {recordType.isActive ? (
                      <Badge className="bg-emerald-50 text-emerald-700 border-emerald-200">Active</Badge>
                    ) : (
                      <Badge variant="outline">Inactive</Badge>
                    )}
                  </h1>
                  <p className="font-mono text-xs text-muted-foreground">{recordType.key}</p>
                </div>
              </div>
              {recordType.description && <p className="text-sm text-muted-foreground max-w-xl">{recordType.description}</p>}
              <p className="text-xs text-muted-foreground">Updated {new Date(recordType.updatedAt).toLocaleString()}</p>
            </div>
            <Button variant="outline" size="sm" onClick={() => setEditOpen(true)}>
              <Pencil className="h-4 w-4 mr-2" /> Edit Type
            </Button>
          </div>
        </div>

        <Tabs defaultValue="fields" className="w-full">
          <TabsList>
            <TabsTrigger value="fields">Fields</TabsTrigger>
            <TabsTrigger value="display">Display</TabsTrigger>
          </TabsList>
          <TabsContent value="fields" className="mt-6">
            <RecordFieldsAdmin recordTypeId={recordType.id} />
          </TabsContent>
          <TabsContent value="display" className="mt-6">
            <DisplayConfigEditor recordTypeId={recordType.id} />
          </TabsContent>
        </Tabs>

        <RecordTypeDialog
          open={editOpen}
          onOpenChange={setEditOpen}
          editing={recordType}
          isPending={updateMutation.isPending}
          onSubmit={(d) =>
            updateMutation.mutate(
              { id: recordType.id, data: { name: d.name, description: d.description, isActive: d.isActive } },
              { onSuccess: () => setEditOpen(false) }
            )
          }
        />
      </div>
    </SettingsLayout>
  );
}

export default function RecordTypeDetailPage() {
  return (
    <ProtectedRoute>
      <RecordTypeDetailContent />
    </ProtectedRoute>
  );
}
