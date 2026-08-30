"use client";

import type { BuilderNode, BuilderEdge } from "../utils/graph-mapper";
import { toFlowGraph } from "../utils/graph-mapper";
import type { WorkflowExecutionNodeExecutionResponse, WorkflowGraphResponse } from "@/types/workflow";

export type NodeVisualState = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "SKIPPED" | "WAITING";

export interface ExecutionOverlay {
  nodes: BuilderNode[];
  edges: BuilderEdge[];
  executedEdgeIds: Set<string>;
  skippedNodeIds: Set<string>;
  nodeExecutionById: Map<string, WorkflowExecutionNodeExecutionResponse>;
  nodeExecutionByKey: Map<string, WorkflowExecutionNodeExecutionResponse>;
}

export function getVisualState(nodeExec: WorkflowExecutionNodeExecutionResponse | undefined, nodeType?: string): NodeVisualState {
  if (!nodeExec) return "PENDING";
  if (nodeExec.status === "FAILED") return "FAILED";
  if (nodeExec.status === "COMPLETED") return "COMPLETED";
  if (nodeExec.status === "SKIPPED") return "SKIPPED";
  if (nodeExec.status === "RUNNING") return "RUNNING";
  // PENDING with WAIT and nextAttemptAt is WAITING
  if (nodeExec.status === "PENDING" && nodeType === "WAIT" && nodeExec.nextAttemptAt) return "WAITING";
  if (nodeExec.status === "PENDING") return "PENDING";
  return nodeExec.status as NodeVisualState;
}

export function buildExecutionOverlay(
  graph: WorkflowGraphResponse | undefined,
  nodeExecutions: WorkflowExecutionNodeExecutionResponse[]
): ExecutionOverlay | null {
  if (!graph) return null;

  const { nodes: flowNodes, edges: flowEdges } = toFlowGraph(graph.nodes, graph.edges);

  const byId = new Map<string, WorkflowExecutionNodeExecutionResponse>();
  const byKey = new Map<string, WorkflowExecutionNodeExecutionResponse>();
  for (const ne of nodeExecutions) {
    byId.set(ne.nodeId, ne);
    byKey.set(ne.nodeKey, ne);
  }

  const executedEdgeIds = new Set<string>();
  for (const ne of nodeExecutions) {
    const sel = (ne.outputContext as unknown as { selectedEdgeId?: string } | null)?.selectedEdgeId;
    if (typeof sel === "string" && sel) executedEdgeIds.add(sel);
  }

  // Also for SKIPPED: alternative edges from same source are not executed
  const skippedNodeIds = new Set<string>();
  for (const ne of nodeExecutions) {
    if (ne.status === "SKIPPED") skippedNodeIds.add(ne.nodeId);
  }

  // Historical missing nodes: add placeholder nodes for executions whose nodeId not in graph
  const graphNodeIds = new Set(flowNodes.map((n) => n.id));
  const placeholderNodes: BuilderNode[] = [];
  for (const ne of nodeExecutions) {
    if (!graphNodeIds.has(ne.nodeId)) {
      // place at bottom after existing nodes
      placeholderNodes.push({
        id: ne.nodeId,
        type: ne.nodeType.toLowerCase(),
        position: { x: 400, y: (flowNodes.length + placeholderNodes.length + 2) * 160 },
        data: {
          nodeKey: ne.nodeKey,
          nodeType: ne.nodeType,
          name: `${ne.nodeKey} (historical)`,
          configuration: {},
          isHistorical: true,
        } as unknown as BuilderNode["data"],
      });
    }
  }

  // Overlay execution data onto nodes
  const nodes: BuilderNode[] = [...flowNodes, ...placeholderNodes].map((n) => {
    const exec = byId.get(n.id) ?? byKey.get(n.data.nodeKey);
    const visual = getVisualState(exec, n.data.nodeType);
    return {
      ...n,
      data: {
        ...n.data,
        executionStatus: visual,
        execution: exec ?? null,
        // for status badge on node
      } as BuilderNode["data"],
    };
  });

  const edges: BuilderEdge[] = flowEdges.map((e) => {
    const executed = executedEdgeIds.has(e.id);
    // Determine if edge is from a completed branch/condition that took other path -> de-emphasize
    const sourceExec = byId.get(e.source);
    const sourceTookOther = sourceExec?.status === "COMPLETED" && !executedEdgeIds.has(e.id) && (sourceExec.outputContext as unknown as { selectedEdgeId?: string })?.selectedEdgeId != null;
    const dimmed = skippedNodeIds.has(e.target) || sourceTookOther;
    return {
      ...e,
      data: {
        ...e.data,
        executed,
        dimmed,
      } as BuilderEdge["data"],
    };
  });

  return { nodes, edges, executedEdgeIds, skippedNodeIds, nodeExecutionById: byId, nodeExecutionByKey: byKey };
}

export function formatExecutionDuration(startedAt: string | null, completedAt: string | null): string {
  if (!startedAt || !completedAt) return "—";
  const ms = new Date(completedAt).getTime() - new Date(startedAt).getTime();
  if (ms < 0) return "—";
  if (ms < 1000) return `${ms}ms`;
  const s = Math.floor(ms / 1000);
  if (s < 60) return `${s}s`;
  return `${Math.floor(s / 60)}m ${s % 60}s`;
}
