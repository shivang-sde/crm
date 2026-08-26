"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  useCreateWorkflow,
  useDeactivateWorkflow,
  useWorkflows,
} from "@/lib/hooks/workflow";
import { WorkflowFormDialog } from "@/components/workflow/WorkflowFormDialog";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { WorkflowResponse } from "@/types/workflow";

export default function WorkflowsPage() {
  const router = useRouter();
  const [createOpen, setCreateOpen] = useState(false);

  const { canViewWorkflows, canEditWorkflows } = usePermissions();
  const workflowsQuery = useWorkflows({ page: 0, size: 50 });
  const deactivate = useDeactivateWorkflow("");
  const createWorkflow = useCreateWorkflow();

  if (!canViewWorkflows) {
    return (
      <div className="space-y-6 p-6">
        <h1 className="text-2xl font-semibold">Workflows</h1>
        <p className="text-sm text-muted-foreground">
          You do not have permission to view workflows.
        </p>
      </div>
    );
  }

  const workflows: WorkflowResponse[] = workflowsQuery.data?.data ?? [];

  const handleCreated = async (workflowId: string) => {
    setCreateOpen(false);
    toast.success("Workflow created");
    router.push(`/workflows/${workflowId}`);
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Workflows</h1>
          <p className="text-sm text-muted-foreground">
            Automate CRM behaviour when entities are created or changed.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => workflowsQuery.refetch()}>
            <RefreshCw className="mr-2 h-4 w-4" /> Refresh
          </Button>
          {canEditWorkflows && (
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="mr-2 h-4 w-4" /> Create Workflow
            </Button>
          )}
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All workflows</CardTitle>
        </CardHeader>
        <CardContent>
          {workflowsQuery.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading workflows…</p>
          ) : workflowsQuery.isError ? (
            <p className="text-sm text-muted-foreground">Failed to load workflows.</p>
          ) : workflows.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No workflows yet. Create your first workflow to get started.
            </p>
          ) : (
            <div className="space-y-3">
              {workflows.map((workflow) => (
                <div
                  key={workflow.id}
                  className="flex flex-col gap-3 rounded-lg border p-4 md:flex-row md:items-center md:justify-between"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <Link
                        href={`/workflows/${workflow.id}`}
                        className="truncate text-sm font-medium hover:underline"
                      >
                        {workflow.name}
                      </Link>
                      <Badge variant={workflow.status === "ACTIVE" ? "default" : "secondary"}>
                        {workflow.status}
                      </Badge>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Created {new Date(workflow.createdAt).toLocaleString()} · Updated{" "}
                      {new Date(workflow.updatedAt).toLocaleString()}
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    <Link href={`/workflows/${workflow.id}`}>
                      <Button variant="outline" size="sm">
                        Open
                      </Button>
                    </Link>
                    {canEditWorkflows && workflow.status === "ACTIVE" && (
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={deactivate.isPending}
                        onClick={async () => {
                          try {
                            await deactivate.mutateAsync(workflow.id);
                            toast.success("Workflow deactivated");
                          } catch {
                            toast.error("Failed to deactivate workflow");
                          }
                        }}
                      >
                        Deactivate
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <WorkflowFormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        isSubmitting={createWorkflow.isPending}
        onSubmit={async (values) => {
          try {
            const workflowId = await createWorkflow.mutateAsync(values);
            toast.success("Workflow created");
            setCreateOpen(false);
            router.push(`/workflows/${workflowId}`);
          } catch {
            toast.error("Failed to create workflow");
          }
        }}
      />
    </div>
  );
}
