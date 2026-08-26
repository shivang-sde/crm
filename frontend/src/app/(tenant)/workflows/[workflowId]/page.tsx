"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Plus } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  useActivateWorkflowVersion,
  useCreateWorkflowVersion,
  useWorkflow,
  useWorkflowVersions,
} from "@/lib/hooks/workflow";
import { CreateVersionDialog } from "@/components/workflow/CreateVersionDialog";
import { usePermissions } from "@/lib/hooks/usePermissions";

export default function WorkflowDetailPage() {
  const params = useParams<{ workflowId: string }>();
  const router = useRouter();
  const workflowId = params?.workflowId ?? "";

  const { canViewWorkflows, canEditWorkflows } = usePermissions();
  const [createOpen, setCreateOpen] = useState(false);

  const workflowQuery = useWorkflow(workflowId);
  const versionsQuery = useWorkflowVersions(workflowId);
  const createVersion = useCreateWorkflowVersion(workflowId);
  const activate = useActivateWorkflowVersion(workflowId);

  if (!canViewWorkflows) {
    return (
      <div className="space-y-6 p-6">
        <h1 className="text-2xl font-semibold">Workflow</h1>
        <p className="text-sm text-muted-foreground">
          You do not have permission to view workflows.
        </p>
      </div>
    );
  }

  if (workflowQuery.isLoading) {
    return (
      <div className="space-y-6 p-6">
        <p className="text-sm text-muted-foreground">Loading workflow…</p>
      </div>
    );
  }

  if (workflowQuery.isError || !workflowQuery.data) {
    return (
      <div className="space-y-6 p-6">
        <p className="text-sm text-muted-foreground">This workflow could not be found.</p>
        <Link href="/workflows" className="text-sm underline">
          ← Back to Workflows
        </Link>
      </div>
    );
  }

  const workflow = workflowQuery.data;
  const versions = versionsQuery.data?.data ?? [];

  return (
    <div className="space-y-6 p-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <Link
            href="/workflows"
            className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to Workflows
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold">{workflow.name}</h1>
            <Badge variant={workflow.status === "ACTIVE" ? "default" : "secondary"}>
              {workflow.status}
            </Badge>
          </div>
        </div>

        {canEditWorkflows && (
          <div className="flex items-center gap-2">
            <Link href={`/workflows/${workflow.id}/executions`}>
              <Button variant="outline">Executions</Button>
            </Link>
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="mr-2 h-4 w-4" /> Create Draft Version
            </Button>
          </div>
        )}
        {!canEditWorkflows && (
          <Link href={`/workflows/${workflow.id}/executions`}>
            <Button variant="outline">Executions</Button>
          </Link>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Versions</CardTitle>
        </CardHeader>
        <CardContent>
          {versionsQuery.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading versions…</p>
          ) : versions.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No versions yet. Create a draft version to start building.
            </p>
          ) : (
            <div className="space-y-3">
              {versions.map((version) => (
                <div
                  key={version.id}
                  className="flex flex-col gap-3 rounded-lg border p-4 md:flex-row md:items-center md:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-medium">Version {version.versionNumber}</span>
                      <Badge variant={version.status === "DRAFT" ? "outline" : "default"}>
                        {version.status}
                      </Badge>
                      <Badge variant="outline">
                        WHEN {version.triggerEntityType} IS {version.triggerEventType}
                      </Badge>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Created {new Date(version.createdAt).toLocaleString()} · Updated{" "}
                      {new Date(version.updatedAt).toLocaleString()}
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    <Link href={`/workflows/${workflow.id}/builder?versionId=${version.id}`}>
                      <Button variant="outline" size="sm">
                        Open Builder
                      </Button>
                    </Link>
                    {canEditWorkflows && version.status === "DRAFT" && (
                      <Button
                        size="sm"
                        disabled={activate.isPending}
                        onClick={async () => {
                          try {
                            await activate.mutateAsync(version.id);
                            toast.success("Workflow version activated");
                          } catch {
                            toast.error("Failed to activate — run Validate first");
                          }
                        }}
                      >
                        Activate
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <CreateVersionDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        isSubmitting={createVersion.isPending}
        onSubmit={async (values) => {
          try {
            const versionId = await createVersion.mutateAsync(values);
            toast.success("Draft version created");
            setCreateOpen(false);
            router.push(`/workflows/${workflowId}/builder?versionId=${versionId}`);
          } catch {
            toast.error("Failed to create draft version");
          }
        }}
      />
    </div>
  );
}
