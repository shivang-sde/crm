"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Hammer, RefreshCw, RotateCcw, Undo2 } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { ExecutionVisualGraph } from "@/components/workflow/execution/ExecutionVisualGraph";
import { ExecutionNodeInspector } from "@/components/workflow/execution/ExecutionNodeInspector";
import { workflowKeys, useReplayWorkflowExecution, useRetryWorkflowExecution, useWorkflowExecution, useWorkflowGraph, useWorkflow } from "@/lib/hooks/workflow";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { WorkflowExecutionStatus } from "@/types/workflow";

function StatusBadge({ status }: { status: string }) {
  const variant = status === "COMPLETED" ? "default" : status === "FAILED" ? "destructive" : "secondary";
  return <Badge variant={variant}>{status}</Badge>;
}
function formatDuration(startedAt: string | null, completedAt: string | null): string {
  if (!startedAt || !completedAt) return "-";
  const ms = new Date(completedAt).getTime() - new Date(startedAt).getTime();
  if (ms < 0) return "-";
  if (ms < 1000) return `${ms}ms`;
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
}
export default function WorkflowExecutionDetailPage() {
  const params = useParams<{ workflowId: string; executionId: string }>();
  const workflowId = params?.workflowId ?? "";
  const executionId = params?.executionId ?? "";
  const router = useRouter();
  const queryClient = useQueryClient();
  const { canViewWorkflows, canEditWorkflows } = usePermissions();
  const [replayOpen, setReplayOpen] = useState(false);
  const [retryOpen, setRetryOpen] = useState(false);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [inspectorOpen, setInspectorOpen] = useState(false);

  const executionQuery = useWorkflowExecution(executionId);
  const workflowQuery = useWorkflow(workflowId);
  const replay = useReplayWorkflowExecution();
  const retry = useRetryWorkflowExecution();

  const isActive = executionQuery.data?.status === "PENDING" || executionQuery.data?.status === "RUNNING";
  useEffect(() => {
    if (!isActive) return;
    const interval = setInterval(() => {
      queryClient.invalidateQueries({ queryKey: workflowKeys.executionDetail(executionId) });
    }, 5000);
    return () => clearInterval(interval);
  }, [isActive, executionId, queryClient]);

  const execution = executionQuery.data;
  const versionId = execution?.workflowVersionId ?? "";
  const graphQuery = useWorkflowGraph(versionId || undefined);
  const graph = graphQuery.data;
  const graphEdges = graph?.edges ?? [];
  const graphNodes = graph?.nodes ?? [];

  const isReplayable = execution?.status === "COMPLETED" || execution?.status === "FAILED";
  const isRetryable = execution?.status === "FAILED";
  const failedNode = execution?.nodeExecutions.find((n) => n.status === "FAILED");

  const handleRefresh = () => {
    queryClient.invalidateQueries({ queryKey: workflowKeys.executionDetail(executionId) });
  };

  const selectedExec = useMemo(() => {
    if (!selectedNodeId || !execution) return null;
    return execution.nodeExecutions.find((n) => n.nodeId === selectedNodeId) ?? execution.nodeExecutions.find((n) => n.nodeKey === graphNodes.find((gn) => gn.id === selectedNodeId)?.nodeKey) ?? null;
  }, [selectedNodeId, execution, graphNodes]);

  const selectedNodeMeta = useMemo(() => {
    if (!selectedNodeId) return { nodeKey: null, nodeType: null } as const;
    const gn = graphNodes.find((n) => n.id === selectedNodeId);
    if (gn) return { nodeKey: gn.nodeKey, nodeType: gn.nodeType } as const;
    const exec = execution?.nodeExecutions.find((e) => e.nodeId === selectedNodeId);
    if (exec) return { nodeKey: exec.nodeKey, nodeType: exec.nodeType } as const;
    return { nodeKey: null, nodeType: null } as const;
  }, [selectedNodeId, graphNodes, execution]);

  const onGraphSelect = (nodeId: string | null) => {
    setSelectedNodeId(nodeId);
    if (nodeId) setInspectorOpen(true);
  };

  const onTimelineSelect = (nodeId: string) => {
    setSelectedNodeId(nodeId);
    setInspectorOpen(true);
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <Link href={`/workflows/${workflowId}/executions`} className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to executions
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold">Execution</h1>
            {execution && (
              <div className="flex items-center gap-1.5">
                {(execution.status === "RUNNING" || execution.status === "PENDING") && (
                  <span className="relative flex h-2 w-2">
                    <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-blue-400 opacity-75"></span>
                    <span className="relative inline-flex h-2 w-2 rounded-full bg-blue-500"></span>
                  </span>
                )}
                <StatusBadge status={execution.status} />
              </div>
            )}
            {failedNode && <Badge variant="destructive">Failed node: {failedNode.nodeKey}</Badge>}
          </div>
          {execution && (
            <p className="mt-1 break-all font-mono text-xs text-muted-foreground">
              {execution.id}
              {execution.replayedFromExecutionId && ` · replayed from ${execution.replayedFromExecutionId}`}
            </p>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {execution && versionId && (
            <Link href={`/workflows/${workflowId}/builder?versionId=${versionId}${failedNode ? `&nodeId=${failedNode.nodeId}` : ""}`}>
              <Button variant="outline" size="sm"><Hammer className="mr-2 h-4 w-4" /> Open Version Builder</Button>
            </Link>
          )}
          <Button variant="outline" size="sm" onClick={handleRefresh}><RefreshCw className="mr-2 h-4 w-4" /> Refresh</Button>
          {canEditWorkflows && execution && isRetryable && (
            <Button size="sm" onClick={() => setRetryOpen(true)}><Undo2 className="mr-2 h-4 w-4" /> Retry Execution</Button>
          )}
          {canEditWorkflows && execution && isReplayable && (
            <Button variant="outline" size="sm" onClick={() => setReplayOpen(true)}><RotateCcw className="mr-2 h-4 w-4" /> Replay</Button>
          )}
        </div>
      </div>

      {execution && !isRetryable && (
        <p className="text-sm text-muted-foreground">
          {execution.status === "COMPLETED" ? "This execution is completed. Use Replay to create a new execution." : execution.status === "PENDING" || execution.status === "RUNNING" ? "This execution is currently running." : ""}
        </p>
      )}

      {executionQuery.isLoading ? (
        <div className="space-y-4">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-[420px] w-full" />
        </div>
      ) : executionQuery.isError || !execution ? (
        <Card>
          <CardContent className="space-y-2 pt-6">
            <p className="text-sm font-medium">Unable to load this execution.</p>
            <p className="text-sm text-muted-foreground">The execution may have been removed or you may not have permission.</p>
            <Button variant="outline" size="sm" onClick={() => executionQuery.refetch()}>Try again</Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <Card>
            <CardHeader><CardTitle>Overview</CardTitle></CardHeader>
            <CardContent className="grid gap-x-8 gap-y-3 text-sm md:grid-cols-2">
              <DetailRow label="Trigger">{`${execution.entityType}.${execution.eventType}`}</DetailRow>
              <DetailRow label="Status"><StatusBadge status={execution.status} /></DetailRow>
              <DetailRow label="Entity ID"><span className="break-all">{execution.entityId}</span></DetailRow>
              <DetailRow label="Version ID"><span className="break-all font-mono text-xs">{execution.workflowVersionId}</span>{graph && <span className="ml-2 text-xs text-muted-foreground">v{graph.version.versionNumber} · {graph.version.status}</span>}</DetailRow>
              <DetailRow label="Attempts">{execution.attemptCount ?? 0}</DetailRow>
              <DetailRow label="Duration">{formatDuration(execution.startedAt, execution.completedAt)}</DetailRow>
              <DetailRow label="Created At">{new Date(execution.createdAt).toLocaleString()}</DetailRow>
              <DetailRow label="Started At">{execution.startedAt ? new Date(execution.startedAt).toLocaleString() : "-"}</DetailRow>
              <DetailRow label="Completed At">{execution.completedAt ? new Date(execution.completedAt).toLocaleString() : "-"}</DetailRow>
              <DetailRow label="Last Heartbeat">{execution.lastHeartbeatAt ? new Date(execution.lastHeartbeatAt).toLocaleString() : "-"}</DetailRow>
              {(execution as unknown as { causedByExecutionId?: string | null }).causedByExecutionId && (
                <DetailRow label="Caused by"><Link href={`/workflows/${workflowId}/executions/${(execution as unknown as { causedByExecutionId: string }).causedByExecutionId}`} className="break-all font-mono text-xs text-primary hover:underline">{(execution as unknown as { causedByExecutionId: string }).causedByExecutionId}</Link></DetailRow>
              )}
              {(execution as unknown as { causedByEventId?: string | null }).causedByEventId && (
                <DetailRow label="Caused by event"><span className="break-all font-mono text-xs">{(execution as unknown as { causedByEventId: string }).causedByEventId}</span></DetailRow>
              )}
              {(execution as unknown as { chainDepth?: number | null }).chainDepth != null && (
                <DetailRow label="Chain depth">{String((execution as unknown as { chainDepth: number }).chainDepth)}</DetailRow>
              )}
              {execution.replayedFromExecutionId && (
                <DetailRow label="Replayed from"><Link href={`/workflows/${workflowId}/executions/${execution.replayedFromExecutionId}`} className="break-all font-mono text-xs text-primary hover:underline">{execution.replayedFromExecutionId}</Link></DetailRow>
              )}
            </CardContent>
          </Card>

          {graph && workflowQuery.data && workflowQuery.data.activeVersionId && workflowQuery.data.activeVersionId !== versionId && (
            <Card className="border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/30">
              <CardContent className="pt-6 text-sm">
                <p className="font-medium text-amber-900 dark:text-amber-100">Historical version — v{graph.version.versionNumber} · {graph.version.status}</p>
                <p className="text-amber-800 dark:text-amber-200">This execution ran workflow version {graph.version.versionNumber}. Current active version is different. The graph below is the exact version that executed (read-only).</p>
              </CardContent>
            </Card>
          )}
          {!graph && !graphQuery.isLoading && (
            <Card className="border-amber-200"><CardContent className="pt-6 text-sm text-muted-foreground">Historical graph unavailable for this version.</CardContent></Card>
          )}

          <Card>
            <CardHeader>
              <CardTitle>Visual Execution Graph</CardTitle>
              <p className="text-xs text-muted-foreground">Historical version v{graph?.version.versionNumber ?? "—"} · {graph?.version.status ?? ""} · Read-only · Tap a node to inspect</p>
            </CardHeader>
            <CardContent className="p-0">
              {graphQuery.isLoading ? (
                <div className="p-4"><Skeleton className="h-[420px] w-full" /></div>
              ) : (
                <ExecutionVisualGraph graph={graph} nodeExecutions={execution.nodeExecutions} executionStatus={execution.status} selectedNodeId={selectedNodeId} onNodeSelect={onGraphSelect} isLoading={graphQuery.isLoading} />
              )}
            </CardContent>
          </Card>
          <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1"><span className="h-2 w-2 rounded-full bg-emerald-500"></span> Completed</span>
            <span className="inline-flex items-center gap-1"><span className="h-2 w-2 rounded-full bg-blue-500"></span> Running</span>
            <span className="inline-flex items-center gap-1"><span className="h-2 w-2 rounded-full bg-red-500"></span> Failed</span>
            <span className="inline-flex items-center gap-1"><span className="h-2 w-2 rounded-full bg-slate-400"></span> Skipped/Pending</span>
            <span className="inline-flex items-center gap-1"><span className="h-2 w-2 rounded-full bg-amber-500"></span> Waiting</span>
          </div>

          {(execution.status === "FAILED" || execution.lastErrorCode) && (
            <Card className="border-red-200 dark:border-red-900">
              <CardHeader><CardTitle className="text-red-600 dark:text-red-400">Failure</CardTitle></CardHeader>
              <CardContent className="space-y-1 text-sm">
                {execution.lastErrorCode && <p className="font-medium text-red-600 dark:text-red-400">{execution.lastErrorCode}</p>}
                {execution.lastErrorMessage && <p className="break-words text-red-600 dark:text-red-400">{execution.lastErrorMessage}</p>}
                {!execution.lastErrorCode && !execution.lastErrorMessage && <p className="text-muted-foreground">No failure details were recorded for this run.</p>}
              </CardContent>
            </Card>
          )}

          <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
            <Card>
              <CardHeader><CardTitle>Node Timeline</CardTitle><p className="text-xs text-muted-foreground">Click a row to highlight the graph and open the inspector.</p></CardHeader>
              <CardContent>
                {execution.nodeExecutions.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No node executions were recorded for this run.</p>
                ) : (
                  <ol className="space-y-3 border-l-2 pl-5">
                    {execution.nodeExecutions.map((node) => {
                      const isSelected = selectedNodeId === node.nodeId;
                      return (
                        <li key={node.id} className="relative">
                          <span className={`absolute -left-[27px] top-2 flex h-3.5 w-3.5 rounded-full border-2 border-background ${node.status === "FAILED" ? "bg-red-500" : node.status === "COMPLETED" ? "bg-emerald-500" : node.status === "RUNNING" ? "animate-pulse bg-blue-500" : node.status === "SKIPPED" ? "bg-slate-400" : node.status === "WAITING" as unknown as string ? "bg-amber-500" : "bg-slate-400"}`} />
                          <div role="button" tabIndex={0} aria-label={`${node.nodeKey}, ${node.nodeType} node, ${node.status.toLowerCase()}`} onClick={() => onTimelineSelect(node.nodeId)} onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onTimelineSelect(node.nodeId); }}} className={`rounded-lg border p-3 text-left transition-colors hover:bg-muted/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring cursor-pointer ${isSelected ? "border-primary ring-1 ring-primary/30 bg-primary/5" : node.status === "FAILED" ? "border-red-300 bg-red-50/50 dark:border-red-900 dark:bg-red-950/30" : ""} ${node.status === "SKIPPED" ? "opacity-70" : ""}`}>
                            <div className="flex flex-wrap items-center gap-2">
                              <StatusBadge status={node.status} />
                              <span className="text-xs uppercase tracking-wide text-muted-foreground">{node.nodeType}</span>
                              <span className="font-medium">{node.nodeKey}</span>
                              {node.status === "SKIPPED" && <Badge variant="outline">— Skipped</Badge>}
                              {node.attemptCount != null && node.attemptCount > 1 && <Badge variant="outline">attempt {node.attemptCount}</Badge>}
                            </div>
                            <p className="mt-1 text-xs text-muted-foreground">
                              {node.startedAt ? `Started ${new Date(node.startedAt).toLocaleTimeString()}` : "Not started"}
                              {node.completedAt && node.startedAt && ` · Duration ${formatDuration(node.startedAt, node.completedAt)}`}
                            </p>
                            {node.nextAttemptAt && (
                              <p className="text-xs text-orange-600">{node.nodeType === "WAIT" ? `Waiting until ${new Date(node.nextAttemptAt).toLocaleString()}` : `Retry scheduled for ${new Date(node.nextAttemptAt).toLocaleString()}`}</p>
                            )}
                            {node.lastErrorCode && <p className="mt-1 text-sm text-red-500">{node.lastErrorCode}{node.lastErrorMessage ? ` - ${node.lastErrorMessage}` : ""}</p>}
                            <NodeOutputContext nodeType={node.nodeType} inputContext={node.inputContext} outputContext={node.outputContext} graphEdges={graphEdges} graphNodes={graphNodes} />
                          </div>
                        </li>
                      );
                    })}
                  </ol>
                )}
              </CardContent>
            </Card>

            <div className="hidden lg:block">
              <div className="sticky top-6">
                <ExecutionNodeInspector nodeKey={selectedNodeMeta.nodeKey} nodeType={selectedNodeMeta.nodeType} execution={selectedExec} onClose={() => setSelectedNodeId(null)} />
                {!selectedExec && selectedNodeId && <p className="mt-2 text-xs text-muted-foreground">Historical node inspectable via timeline data.</p>}
              </div>
            </div>
          </div>

          <Sheet open={inspectorOpen} onOpenChange={setInspectorOpen}>
            <SheetContent side="right" className="w-[90vw] overflow-auto sm:max-w-md p-0">
              <SheetHeader className="p-4 pb-0"><SheetTitle>Node Inspector</SheetTitle></SheetHeader>
              <div className="p-4">
                <ExecutionNodeInspector nodeKey={selectedNodeMeta.nodeKey} nodeType={selectedNodeMeta.nodeType} execution={selectedExec} onClose={() => setInspectorOpen(false)} />
              </div>
            </SheetContent>
          </Sheet>
        </>
      )}

      <Dialog open={replayOpen} onOpenChange={setReplayOpen}>
        <DialogContent className="max-w-md"><DialogHeader><DialogTitle>Replay execution</DialogTitle></DialogHeader>
          <p className="text-sm text-muted-foreground">Replay creates and runs a NEW execution from the beginning based on this run&apos;s original trigger event. The current run remains unchanged. Replay is available for Completed or Failed runs.</p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" size="sm" onClick={() => setReplayOpen(false)}>Cancel</Button>
            <Button size="sm" disabled={replay.isPending} onClick={async () => {
              try {
                const result = await replay.mutateAsync(executionId);
                toast.success("Execution replayed");
                setReplayOpen(false);
                queryClient.invalidateQueries({ queryKey: [...workflowKeys.all, "executions"] });
                if (result?.executionId) router.push(`/workflows/${workflowId}/executions/${result.executionId}`);
              } catch { toast.error("Failed to replay execution"); }
            }}>{replay.isPending ? "Replaying..." : "Replay execution"}</Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={retryOpen} onOpenChange={setRetryOpen}>
        <DialogContent className="max-w-md"><DialogHeader><DialogTitle>Retry execution</DialogTitle></DialogHeader>
          <p className="text-sm text-muted-foreground">This will resume the EXISTING workflow execution from its failed point. It does not create a new execution. Completed nodes are not re-run and their side effects are protected by idempotency.</p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" size="sm" onClick={() => setRetryOpen(false)}>Cancel</Button>
            <Button size="sm" disabled={retry.isPending} onClick={async () => {
              try { await retry.mutateAsync(executionId); toast.success("Execution retry requested"); setRetryOpen(false); handleRefresh(); }
              catch (error) {
                const message = typeof error === "object" && error !== null && "message" in error && typeof (error as { message?: string }).message === "string" ? ((error as { message: string }).message) : "";
                if (message.includes("NOT_RETRYABLE")) toast.error("This execution cannot be retried in its current state.");
                else toast.error("Failed to request execution retry");
              }
            }}>{retry.isPending ? "Requesting retry..." : "Resume execution"}</Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function NodeOutputContext({ nodeType, inputContext, outputContext, graphEdges, graphNodes }: { nodeType?: string; inputContext?: Record<string, unknown> | null; outputContext?: Record<string, unknown> | null; graphEdges: Array<{ id: string; sourceNodeId: string; targetNodeId: string; edgeKey: string | null; configuration: Record<string, unknown>; }>; graphNodes: Array<{ id: string; nodeKey: string }>; }) {
  const hasOutput = outputContext && Object.keys(outputContext).length > 0;
  const hasInput = inputContext && Object.keys(inputContext).length > 0;
  const selectedEdgeId = typeof outputContext?.selectedEdgeId === "string" ? outputContext.selectedEdgeId : null;
  const selectedEdge = selectedEdgeId ? graphEdges.find((e) => e.id === selectedEdgeId) : undefined;
  const targetNode = selectedEdge ? graphNodes.find((n) => n.id === selectedEdge.targetNodeId) : undefined;
  const outcome = typeof selectedEdge?.configuration?.outcome === "string" ? selectedEdge.configuration.outcome : null;
  const httpStatus = typeof outputContext?.statusCode === "number" ? (outputContext.statusCode as number) : null;
  const httpCorrelation = typeof outputContext?.correlationId === "string" ? (outputContext.correlationId as string) : null;
  const httpResponse = (outputContext as unknown as { response?: unknown } | null)?.response;
  const isHttpNode = hasOutput && (httpStatus !== null || httpResponse !== undefined);
  return (
    <>
      {typeof outputContext?.outcome === "string" && <p className="mt-1 text-xs font-medium text-indigo-700 dark:text-indigo-400">Branch outcome: {outputContext.outcome}</p>}
      {selectedEdge && <p className="mt-1 text-xs text-emerald-700 dark:text-emerald-400">Selected path: {outcome ?? selectedEdge.edgeKey ?? "edge"}{targetNode ? ` → ${targetNode.nodeKey}` : ""}</p>}
      {isHttpNode && (
        <div className="mt-2 rounded-md border bg-white p-2 text-xs">
          <p className="font-medium">HTTP result</p>
          <div className="mt-1 flex flex-wrap gap-2">
            {httpStatus !== null && <span className={`rounded px-1.5 py-0.5 text-[11px] font-semibold border ${httpStatus >= 200 && httpStatus < 300 ? "bg-emerald-50 text-emerald-700 border-emerald-200" : "bg-red-50 text-red-700 border-red-200"}`}>Status {httpStatus}</span>}
            {httpCorrelation && <span className="font-mono text-[11px] text-muted-foreground break-all">correlation {httpCorrelation.slice(0, 8)}</span>}
          </div>
          {httpResponse !== undefined && (
            <details className="mt-1"><summary className="cursor-pointer text-[11px] font-medium text-muted-foreground hover:text-foreground">Response data</summary><pre className="mt-1 max-h-40 overflow-auto whitespace-pre-wrap break-words rounded bg-muted/40 p-2 text-[11px] leading-relaxed">{typeof httpResponse === "string" ? httpResponse : JSON.stringify(httpResponse, null, 2)}</pre></details>
          )}
        </div>
      )}
      {hasInput ? (<details className="mt-2"><summary className="cursor-pointer text-xs font-medium text-muted-foreground hover:text-foreground">Input Context</summary><pre className="mt-1 max-h-56 overflow-auto whitespace-pre-wrap break-words rounded-md border bg-muted/40 p-3 text-xs leading-relaxed">{JSON.stringify(inputContext, null, 2)}</pre></details>) : (<p className="mt-2 text-xs text-muted-foreground">No input context</p>)}
      {hasOutput ? (<details className="mt-2"><summary className="cursor-pointer text-xs font-medium text-muted-foreground hover:text-foreground">Output Context</summary><pre className="mt-1 max-h-56 overflow-auto whitespace-pre-wrap break-words rounded-md border bg-muted/40 p-3 text-xs leading-relaxed">{JSON.stringify(outputContext, null, 2)}</pre></details>) : (<p className="mt-2 text-xs text-muted-foreground">No output context</p>)}
    </>
  );
}
function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (<div><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p><div>{children}</div></div>);
}
