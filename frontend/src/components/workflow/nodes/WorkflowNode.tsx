"use client";

import { memo } from "react";
import { Handle, Position, NodeProps } from "@xyflow/react";
import {
  Zap,
  GitBranch,
  PlayCircle,
  CheckCircle2,
  Clock,
  Split,
  AlertTriangle,
} from "lucide-react";
import { BuilderNodeData } from "../utils/graph-mapper";
import { describeAction, describeTrigger } from "../utils/node-config";

const typeStyles: Record<string, { icon: typeof Zap; border: string; badge: string }> = {
  TRIGGER: { icon: Zap, border: "border-amber-400", badge: "bg-amber-100 text-amber-800" },
  CONDITION: { icon: GitBranch, border: "border-violet-400", badge: "bg-violet-100 text-violet-800" },
  BRANCH: { icon: Split, border: "border-indigo-400", badge: "bg-indigo-100 text-indigo-800" },
  ACTION: { icon: PlayCircle, border: "border-blue-400", badge: "bg-blue-100 text-blue-800" },
  END: { icon: CheckCircle2, border: "border-emerald-400", badge: "bg-emerald-100 text-emerald-800" },
  WAIT: { icon: Clock, border: "border-orange-400", badge: "bg-orange-100 text-orange-800" },
};

function subtitleFor(data: BuilderNodeData): string {
  switch (data.nodeType) {
    case "TRIGGER":
      return describeTrigger(data.configuration);
    case "ACTION":
      return describeAction(data.configuration, data.name);
    case "END":
      return "";
    default:
      return data.name;
  }
}

function WorkflowNodeComponent({ data, selected }: NodeProps) {
  const nodeData = data as BuilderNodeData;
  const style = typeStyles[nodeData.nodeType] ?? {
    icon: AlertTriangle,
    border: "border-red-400",
    badge: "bg-red-100 text-red-800",
  };
  const Icon = style.icon;

  return (
    <div
      className={`min-w-[180px] max-w-[240px] rounded-lg border-2 bg-white px-3 py-2 shadow-sm ${
        selected ? "border-primary ring-2 ring-primary/30" : style.border
      }`}
    >
      <Handle type="target" position={Position.Top} id="in" />

      <div className="flex items-center gap-2">
        <span className={`flex h-6 w-6 items-center justify-center rounded ${style.badge}`}>
          <Icon className="h-4 w-4" />
        </span>
        <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          {nodeData.nodeType}
        </span>
      </div>

      <p className="mt-1 truncate text-sm font-medium">{nodeData.name}</p>
      {subtitleFor(nodeData) && (
        <p className="truncate text-xs text-muted-foreground">{subtitleFor(nodeData)}</p>
      )}


      {nodeData.nodeType === "CONDITION" || nodeData.nodeType === "BRANCH" ? (
        <>
          <Handle
            type="source"
            position={Position.Bottom}
            id="true"
            className="!bottom-0 !left-1/4"
          />
          <Handle
            type="source"
            position={Position.Bottom}
            id="false"
            className="!bottom-0 !left-3/4"
          />
          <div className="pointer-events-none absolute bottom-0 left-0 right-0 -mb-4 flex justify-between px-4 text-[10px] text-muted-foreground">
            <span>TRUE</span>
            <span>FALSE</span>
          </div>
        </>
      ) : (
        nodeData.nodeType !== "END" && (
          <Handle type="source" position={Position.Bottom} id="out" />
        )
      )}
    </div>
  );
}

export const WorkflowNode = memo(WorkflowNodeComponent);

export function buildNodeTypes(): Record<string, typeof WorkflowNode> {
  return {
    trigger: WorkflowNode,
    condition: WorkflowNode,
    action: WorkflowNode,
    end: WorkflowNode,
    wait: WorkflowNode,
    branch: WorkflowNode,
  };
}
