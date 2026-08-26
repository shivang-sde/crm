"use client";

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

import { useMemo } from "react";
import { buildNodeTypes } from "./nodes/WorkflowNode";

const nodeTypes = buildNodeTypes();

interface WorkflowCanvasProps {
  nodes: Node[];
  edges: Edge[];
  readOnly?: boolean;
  onNodesChange: (changes: NodeChange[]) => void;
  onEdgesChange: (changes: EdgeChange[]) => void;
  onConnect: (connection: Connection) => void;
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
  onDrop,
  onDragOver,
  onSelectionChanged,
}: WorkflowCanvasProps) {
  const { fitView } = useReactFlow();

  const isValidConnection = useMemo(
    () =>
      (connection: Connection | Edge): boolean => {
        if (connection.source === connection.target) return false;
        if (readOnly) return false;

        const sourceNode = nodes.find((node) => node.id === connection.source);
        if (!sourceNode) return false;

        const sourceType = (sourceNode.data as { nodeType?: string }).nodeType;
        // END is terminal; TRIGGER must not receive inbound connections.
        if (sourceType === "END") return false;
        if (connection.target === undefined) return false;
        const targetNode = nodes.find((node) => node.id === connection.target);
        if (!targetNode) return false;
        const targetType = (targetNode.data as { nodeType?: string }).nodeType;
        if (targetType === "TRIGGER") return false;

        const duplicate = edges.some(
          (edge) =>
            edge.source === connection.source &&
            edge.target === connection.target &&
            ((edge as Edge).sourceHandle ?? null) === (connection.sourceHandle ?? null)
        );
        if (duplicate) return false;

        // Linear node types allow exactly one outgoing edge.
        if (
          (sourceType === "TRIGGER" ||
            sourceType === "WAIT" ||
            sourceType === "ACTION") &&
          edges.some((edge) => edge.source === connection.source)
        ) {
          return false;
        }

        // CONDITION/BRANCH: one edge per outcome handle.
        if (
          (sourceType === "CONDITION" || sourceType === "BRANCH") &&
          connection.sourceHandle &&
          edges.some(
            (edge) =>
              edge.source === connection.source &&
              ((edge as Edge).sourceHandle ?? null) === connection.sourceHandle
          )
        ) {
          return false;
        }

        return true;
      },
    [edges, nodes, readOnly]
  );

  return (
    <div className="h-full w-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
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
        nodesDraggable={!readOnly}
        nodesConnectable={!readOnly}
        elementsSelectable
        deleteKeyCode={readOnly ? null : ["Backspace", "Delete"]}
        fitView
        minZoom={0.2}
        maxZoom={1.75}
      >
        <Background gap={16} />
        <Controls showInteractive={false} />
        <MiniMap pannable zoomable />
      </ReactFlow>
    </div>
  );
}
