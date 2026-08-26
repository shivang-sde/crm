"use client";

import { Zap, GitBranch, PlayCircle, CheckCircle2, Clock, Split } from "lucide-react";
import { WorkflowNodeType } from "@/types/workflow";

interface PaletteItem {
  nodeType: WorkflowNodeType;
  label: string;
  description: string;
}

const items: PaletteItem[] = [
  {
    nodeType: "TRIGGER",
    label: "Trigger",
    description: "Starts the workflow when an entity event occurs.",
  },
  {
    nodeType: "CONDITION",
    label: "Condition",
    description: "Branches on TRUE/FALSE field conditions.",
  },
  {
    nodeType: "BRANCH",
    label: "Branch",
    description: "Routes execution through TRUE or FALSE based on conditions.",
  },
  {
    nodeType: "ACTION",
    label: "Action",
    description: "Performs a side effect such as NO_OP or Create Task.",
  },
  {
    nodeType: "WAIT",
    label: "Wait",
    description: "Pause workflow execution until a specified time.",
  },
  {
    nodeType: "END",
    label: "End",
    description: "Terminates the workflow.",
  },
];

export function WorkflowNodePalette({ disabled }: { disabled?: boolean }) {
  const handleDragStart = (event: React.DragEvent, nodeType: WorkflowNodeType) => {
    event.dataTransfer.setData("application/workflow-node-type", nodeType);
    event.dataTransfer.effectAllowed = "move";
  };

  return (
    <div className="space-y-2">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        Node palette
      </p>
      {items.map((item) => (
        <div
          key={item.nodeType}
          draggable={!disabled}
          onDragStart={(event) => handleDragStart(event, item.nodeType)}
          className={`rounded-lg border p-3 ${disabled ? "cursor-not-allowed opacity-50" : "cursor-grab bg-white hover:border-primary/60 active:cursor-grabbing"}`}
        >
          <div className="flex items-center gap-2">
            {item.nodeType === "TRIGGER" && <Zap className="h-4 w-4 text-amber-500" />}
            {item.nodeType === "CONDITION" && <GitBranch className="h-4 w-4 text-violet-500" />}
            {item.nodeType === "BRANCH" && <Split className="h-4 w-4 text-indigo-500" />}
            {item.nodeType === "ACTION" && <PlayCircle className="h-4 w-4 text-blue-500" />}
            {item.nodeType === "WAIT" && <Clock className="h-4 w-4 text-orange-500" />}
            {item.nodeType === "END" && <CheckCircle2 className="h-4 w-4 text-emerald-500" />}
            <span className="text-sm font-medium">{item.label}</span>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">{item.description}</p>
        </div>
      ))}
    </div>
  );
}
