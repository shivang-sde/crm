"use client";

import "@xyflow/react/dist/style.css";

import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Connection,
  Edge,
  Node,
  NodeChange,
  EdgeChange,
  useReactFlow,
} from "@xyflow/react";

import { useMemo, useCallback } from "react";
import { toast } from "sonner";
import { buildNodeTypes } from "./nodes/WorkflowNode";
import { WorkflowEdge } from "./edges/WorkflowEdge";
import { getConnectionReason } from "./utils/connection-validation";

const nodeTypes = buildNodeTypes();
const edgeTypes = { workflow: WorkflowEdge };

interface WorkflowCanvasProps {
  nodes: Node[];
  edges: Edge[];
  readOnly?: boolean;
  onNodesChange: (changes: NodeChange[]) => void;
  onEdgesChange: (changes: EdgeChange[]) => void;
  onConnect: (connection: Connection) => void;
  onReconnect?: (oldEdge: Edge, newConnection: Connection) => void;
  onDrop: (event: React.DragEvent) => void;
  onDragOver: (event: React.DragEvent) => void;
  onSelectionChanged?: (params: { nodes: Node[]; edges: Edge[] }) => void;
}

export function WorkflowCanvas({
  nodes,
  edges,
  readOnly = false,
  onNodesChange,
  onEdgesChange,
  onConnect,
  onReconnect,
  onDrop,
  onDragOver,
  onSelectionChanged,
}: WorkflowCanvasProps) {
  const { fitView } = useReactFlow();

  const isValidConnection = useMemo(
    () => (connection: Connection | Edge): boolean => {
      const { valid } = getConnectionReason(connection, nodes as Array<{ id: string; data?: { nodeType?: string } }>, edges as Edge[], readOnly);
      return valid;
    },
    [edges, nodes, readOnly]
  );

  const handleConnectEnd = useCallback(
    (_event: MouseEvent | TouchEvent, connectionState: { fromNode?: { id: string } | null; toNode?: { id: string } | null; fromHandle?: { id: string | null } | null; toHandle?: { id: string | null } | null; from?: string; to?: string; fromHandleId?: string | null; toHandleId?: string | null }) => {
      // React Flow's connectionState shape varies by version; normalize
      const source = (connectionState as unknown as { from?: string; fromNode?: { id: string } })?.from ?? (connectionState as unknown as { fromNode?: { id: string } })?.fromNode?.id;
      const target = (connectionState as unknown as { to?: string; toNode?: { id: string } })?.to ?? (connectionState as unknown as { toNode?: { id: string } })?.toNode?.id;
      const sourceHandle = (connectionState as unknown as { fromHandleId?: string | null; fromHandle?: { id: string | null } })?.fromHandleId ?? (connectionState as unknown as { fromHandle?: { id: string | null } })?.fromHandle?.id ?? null;
      const targetHandle = (connectionState as unknown as { toHandleId?: string | null; toHandle?: { id: string | null } })?.toHandleId ?? (connectionState as unknown as { toHandle?: { id: string | null } })?.toHandle?.id ?? null;
      if (!source || !target) return; // dropped on pane
      const conn: Connection = { source, target, sourceHandle, targetHandle } as Connection;
      const { valid, reason } = getConnectionReason(conn, nodes as Array<{ id: string; data?: { nodeType?: string } }>, edges as Edge[], readOnly);
      if (!valid && reason) {
        toast.error(reason);
      }
    },
    [nodes, edges, readOnly]
  );

  return (
    <div className="h-full w-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onDrop={onDrop}
        onDragOver={(event) => {
          event.preventDefault();
          event.dataTransfer.dropEffect = "move";
        }}
        onSelectionChange={onSelectionChanged}
        isValidConnection={isValidConnection}
        onConnectEnd={handleConnectEnd as unknown as (event: MouseEvent | TouchEvent, connectionState: unknown) => void}
        onReconnect={onReconnect}
        edgesReconnectable={!readOnly}
        nodesDraggable={!readOnly}
        nodesConnectable={!readOnly}
        elementsSelectable
        deleteKeyCode={readOnly ? null : ["Backspace", "Delete"]}
        panOnDrag
        panOnScroll={false}
        zoomOnScroll
        zoomOnPinch
        zoomOnDoubleClick
        selectNodesOnDrag={false}
        fitView
        fitViewOptions={{ padding: 0.2, maxZoom: 1, minZoom: 0.2 }}
        minZoom={0.15}
        maxZoom={2}
        proOptions={{ hideAttribution: true }}
        style={{ width: "100%", height: "100%" }}
      >
        <Background gap={16} size={1.2} color="hsl(var(--muted-foreground) / 0.12)" />
        <Controls
          showInteractive={false}
          position="bottom-left"
          style={{ display: "flex", gap: 4 }}
          className="!m-2 !rounded-lg !border !bg-white !p-1 !shadow-sm [&>button]:!rounded-md [&>button]:!border-0"
        />
        <MiniMap
          pannable
          zoomable
          position="bottom-right"
          style={{ width: 140, height: 90 }}
          className="!m-2 !rounded-lg !border !bg-white !shadow-sm !overflow-hidden [&>svg]:!w-full [&>svg]:!h-full"
          maskColor="hsl(var(--muted) / 0.6)"
          nodeStrokeWidth={2}
        />
        <CanvasControls />
      </ReactFlow>
    </div>
  );
}

function CanvasControls() {
  const { zoomIn, zoomOut, fitView, getZoom, setViewport, getViewport } = useReactFlow();
  const zoomPct = Math.round(getZoom() * 100);
  return (
    <div className="absolute bottom-2 left-14 z-10 flex items-center gap-1 rounded-lg border bg-white p-1 shadow-sm">
      <button
        type="button"
        aria-label="Zoom out"
        className="rounded px-2 py-1 text-sm hover:bg-muted"
        onClick={() => zoomOut({ duration: 200 })}
      >
        −
      </button>
      <span className="min-w-[3.5rem] text-center text-xs tabular-nums">{zoomPct}%</span>
      <button
        type="button"
        aria-label="Zoom in"
        className="rounded px-2 py-1 text-sm hover:bg-muted"
        onClick={() => zoomIn({ duration: 200 })}
      >
        +
      </button>
      <div className="mx-1 h-4 w-px bg-border" />
      <button type="button" className="rounded px-2 py-1 text-xs hover:bg-muted" onClick={() => fitView({ padding: 0.2, duration: 200 })}>
        Fit
      </button>
      <button
        type="button"
        className="rounded px-2 py-1 text-xs hover:bg-muted"
        onClick={() => {
          const vp = getViewport();
          setViewport({ x: 0, y: 0, zoom: 1 }, { duration: 200 });
          void vp;
        }}
      >
        Reset
      </button>
    </div>
  );
}
