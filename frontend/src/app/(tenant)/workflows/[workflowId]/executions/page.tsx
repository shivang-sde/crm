"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { workflowKeys, useWorkflow, useWorkflowExecutions } from "@/lib/hooks/workflow";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { WorkflowExecutionStatus } from "@/types/workflow";

const statusLabels: Record<WorkflowExecutionStatus, string> = {
  PENDING: "Pending",
  RUNNING: "Running",
  COMPLETED: "Completed",
  FAILED: "Failed",
};

function ExecutionStatusBadge({ status }: { status: WorkflowExecutionStatus }) {
  const variant =
    status === "COMPLETED"
      ? "default"
      : status === "FAILED"
        ? "destructive"
        : "secondary";
  return <Badge variant={variant}>{statusLabels[status] ?? status}</Badge>;
}

const PAGE_SIZE = 20;

export default function WorkflowExecutionsPage() {
  const params = useParams<{ workflowId: string }>();
  const workflowId = params?.workflowId ?? "";
  const queryClient = useQueryClient();

  const { canViewWorkflows } = usePermissions();
  const [statusFilter, setStatusFilter] = useState<WorkflowExecutionStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);

  const workflowQuery = useWorkflow(workflowId);
  const executionsQuery = useWorkflowExecutions({
    ...(statusFilter !== "ALL" ? { status: statusFilter } : {}),
    workflowId,
    page,
    size: PAGE_SIZE,
  });

  if (!canViewWorkflows) {
    return (
      <div className="space-y-6 p-6">
        <h1 className="text-2xl font-semibold">Executions</h1>
        <p className="text-sm text-muted-foreground">
          You do not have permission to view workflow executions.
        </p>
      </div>
    );
  }

  const executions = executionsQuery.data?.data ?? [];
  const meta = executionsQuery.data?.meta;
  const isFiltered = statusFilter !== "ALL";

  const handleRefresh = () => {
    queryClient.invalidateQueries({
      queryKey: workflowKeys.executions({ workflowId }),
    });
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <Link
            href={`/workflows/${workflowId}`}
            className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to workflow
          </Link>
          <h1 className="text-2xl font-semibold">
            {workflowQuery.data ? workflowQuery.data.name : "Workflow"} — Executions
          </h1>
          <p className="text-sm text-muted-foreground">
            Runtime runs of this workflow triggered by entity events.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Select
            value={statusFilter}
            onValueChange={(value) => {
              setStatusFilter(value as WorkflowExecutionStatus | "ALL");
              setPage(0);
            }}
          >
            <SelectTrigger className="w-[160px]">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All</SelectItem>
              {(Object.keys(statusLabels) as WorkflowExecutionStatus[]).map((status) => (
                <SelectItem key={status} value={status}>
                  {statusLabels[status]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" onClick={handleRefresh}>
            <RefreshCw className="mr-2 h-4 w-4" /> Refresh
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Runs</CardTitle>
        </CardHeader>
        <CardContent>
          {executionsQuery.isLoading ? (
            <div className="space-y-3">
              <div className="h-20 w-full motion-safe:animate-pulse rounded-lg bg-muted" />
              <div className="h-20 w-full motion-safe:animate-pulse rounded-lg bg-muted" />
            </div>
          ) : executionsQuery.isError ? (
            <div className="space-y-2">
              <p className="text-sm font-medium">Unable to load executions.</p>
              <p className="text-sm text-muted-foreground">Please try again.</p>
              <Button variant="outline" size="sm" onClick={() => executionsQuery.refetch()}>
                Try again
              </Button>
            </div>
          ) : executions.length === 0 ? (
            isFiltered ? (
              <div className="space-y-2">
                <p className="text-sm text-muted-foreground">
                  No executions match the selected status.
                </p>
                <Button variant="outline" size="sm" onClick={() => setStatusFilter("ALL")}>
                  Show all statuses
                </Button>
              </div>
            ) : (
              <div className="space-y-2">
                <p className="text-sm font-medium">No workflow executions yet.</p>
                <p className="text-sm text-muted-foreground">
                  Activate this workflow and trigger its event to see executions here.
                </p>
                <Link href={`/workflows/${workflowId}`}>
                  <Button variant="outline" size="sm">Open Workflow</Button>
                </Link>
              </div>
            )
          ) : (
            <div className="space-y-3">
              {executions.map((execution) => (
                <Link
                  key={execution.id}
                  href={`/workflows/${workflowId}/executions/${execution.id}`}
                  className="block rounded-lg border p-4 transition-colors hover:bg-muted/40"
                >
                  <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <div className="flex items-center gap-1.5">
                          {(execution.status === "RUNNING" || execution.status === "PENDING") && (
                            <span className="relative flex h-2 w-2">
                              <span className="absolute inline-flex h-full w-full motion-safe:animate-ping rounded-full bg-blue-400 opacity-75"></span>
                              <span className="relative inline-flex h-2 w-2 rounded-full bg-blue-500"></span>
                            </span>
                          )}
                          <ExecutionStatusBadge status={execution.status} />
                        </div>
                        <span className="truncate font-medium">
                          {execution.entityType}.{execution.eventType}
                        </span>
                        <span className="truncate text-xs text-muted-foreground">
                          entity {execution.entityId}
                        </span>
                      </div>
                      <p className="mt-1 truncate text-xs text-muted-foreground">
                        {execution.createdAt
                          ? `Created ${new Date(execution.createdAt).toLocaleString()}`
                          : ""}
                      </p>
                    </div>

                    <div className="min-w-0 md:text-right">
                      {execution.status === "FAILED" && (
                        <p className="text-xs text-orange-600">Retry available</p>
                      )}
                      {execution.errorCode && (
                        <p className="truncate text-xs text-red-500">{execution.errorCode}</p>
                      )}
                      {execution.completedAt && execution.startedAt && (
                        <p className="text-xs text-muted-foreground">
                          Duration {formatDuration(execution.startedAt, execution.completedAt)}
                        </p>
                      )}
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          )}

          {meta && meta.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between border-t pt-4">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <span className="text-sm text-muted-foreground">
                Page {meta.page + 1} of {meta.totalPages} · {meta.total} runs
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  setPage((current) => Math.min(meta.totalPages - 1, current + 1))
                }
                disabled={page >= meta.totalPages - 1}
              >
                Next
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function formatDuration(
  startedAt: string | null,
  completedAt: string | null
): string {
  if (!startedAt || !completedAt) return "—";
  const ms = new Date(completedAt).getTime() - new Date(startedAt).getTime();
  if (ms < 0) return "—";
  if (ms < 1000) return `${ms}ms`;
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
}