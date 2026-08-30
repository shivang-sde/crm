"use client";

import { useCallback, useMemo } from "react";
import { ReactFlow, Background, Controls, MiniMap, Node, Edge, ReactFlowProvider, useReactFlow } from "@xyflow/react";
import { buildNodeTypes } from "../nodes/WorkflowNode";
import { WorkflowEdge } from "../edges/WorkflowEdge";
import { buildExecutionOverlay } from "./execution-graph";
import type { WorkflowGraphResponse, WorkflowExecutionNodeExecutionResponse } from "@/types/workflow";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

const nodeTypes = buildNodeTypes();
const edgeTypes = { workflow: WorkflowEdge };

interface ExecutionVisualGraphProps {
  graph?: WorkflowGraphResponse;
  nodeExecutions?: WorkflowExecutionNodeExecutionResponse[];
  executionStatus?: string;
  selectedNodeId?: string | null;
  onNodeSelect?: (nodeId: string | null, nodeKey?: string) => void;
  isLoading?: boolean;
}

function ExecutionGraphInner({
  graph,
  nodeExecutions = [],
  selectedNodeId,
  onNodeSelect,
}: ExecutionVisualGraphProps) {
  const overlay = useMemo(() => buildExecutionOverlay(graph, nodeExecutions), [graph, nodeExecutions]);

  const nodes = useMemo(() => {
    if (!overlay) return [];
    return overlay.nodes.map((n) => ({
      ...n,
      selected: n.id === selectedNodeId,
    })) as unknown as Node[];
  }, [overlay, selectedNodeId]);

  const edges = useMemo(() => {
    if (!overlay) return [];
    return overlay.edges as unknown as Edge[];
  }, [overlay]);

  const onNodesChange = useCallback(() => {}, []);
  const onEdgesChange = useCallback(() => {}, []);
  const onConnect = useCallback(() => {}, []);

  const onSelectionChange = useCallback(
    ({ nodes: selNodes }: { nodes: Node[]; edges: Edge[] }) => {
      const picked = selNodes[0];
      if (picked) onNodeSelect?.(picked.id, (picked.data as { nodeKey?: string })?.nodeKey);
      else onNodeSelect?.(null);
    },
    [onNodeSelect]
  );

  const onNodeClick = useCallback(
    (_: unknown, node: Node) => {
      onNodeSelect?.(node.id, (node.data as { nodeKey?: string })?.nodeKey);
      // center slightly
      // setCenter will be called by parent via effect if needed
    },
    [onNodeSelect]
  );

  if (!graph) {
    return (
      <div className="flex h-[420px] w-full items-center justify-center">
        <p className="text-sm text-muted-foreground">No graph available for this execution.</p>
      </div>
    );
  }

  if (nodes.length === 0) {
    return (
      <div className="flex h-[420px] w-full items-center justify-center">
        <p className="text-sm text-muted-foreground">Graph is empty.</p>
      </div>
    );
  }

  return (
    <div className="h-[420px] w-full lg:h-[520px]">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onSelectionChange={onSelectionChange}
        onNodeClick={onNodeClick}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable
        fitView
        fitViewOptions={{ padding: 0.15 }}
        minZoom={0.2}
        maxZoom={1.5}
        proOptions={{ hideAttribution: true }}
      >
        <Background gap={16} />
        <Controls showInteractive={false} />
        <MiniMap pannable zoomable />
      </ReactFlow>
    </div>
  );
}

export function ExecutionVisualGraph(props: ExecutionVisualGraphProps) {
  if (props.isLoading) {
    return (
      <Card className="overflow-hidden">
        <div className="space-y-3 p-4">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-[400px] w-full" />
        </div>
      </Card>
    );
  }
  return (
    <ReactFlowProvider>
      <ExecutionGraphInner {...props} />
    </ReactFlowProvider>
  );
}

// Wrapped provider for standalone usage
export function ExecutionVisualGraphWithProvider(props: ExecutionVisualGraphProps) {
  return <ExecutionVisualGraph {...props} />;
}
