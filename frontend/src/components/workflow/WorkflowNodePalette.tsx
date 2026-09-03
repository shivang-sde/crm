"use client";

import { Zap, GitBranch, PlayCircle, CheckCircle2, Clock, Split } from "lucide-react";
import { WorkflowNodeType } from "@/types/workflow";

interface PaletteItem {
  nodeType: WorkflowNodeType;
  label: string;
  description: string;
}

export const paletteItems: PaletteItem[] = [
  {
    nodeType: "TRIGGER",
    label: "Trigger",
    description: "Starts the workflow when an entity event occurs.",
  },
  {
    nodeType: "CONDITION",
    label: "IF / ELSE",
    description: "Check conditions and continue through either the TRUE or FALSE path.",
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

export function WorkflowNodePalette({
  disabled,
  onAdd,
  hasTrigger,
}: {
  disabled?: boolean;
  onAdd?: (nodeType: WorkflowNodeType) => void;
  hasTrigger?: boolean;
}) {
  const handleDragStart = (event: React.DragEvent, nodeType: WorkflowNodeType) => {
    event.dataTransfer.setData("application/workflow-node-type", nodeType);
    event.dataTransfer.effectAllowed = "move";
  };

  const handleActivate = (nodeType: WorkflowNodeType) => {
    if (disabled) return;
    if (hasTrigger && nodeType === "TRIGGER") return;
    if (onAdd) onAdd(nodeType);
  };

  const renderItem = (item: PaletteItem) => {
    const isTrigger = item.nodeType === "TRIGGER";
    const isDisabledTrigger = isTrigger && hasTrigger;
    return (
      <div
        key={item.nodeType}
        role="button"
        tabIndex={disabled || isDisabledTrigger ? -1 : 0}
        aria-label={isDisabledTrigger ? "Trigger already configured" : `Add ${item.label} node`}
        aria-disabled={disabled || isDisabledTrigger ? true : undefined}
        draggable={!disabled && !isDisabledTrigger}
        onDragStart={(event) => handleDragStart(event, item.nodeType)}
        onClick={() => handleActivate(item.nodeType)}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            handleActivate(item.nodeType);
          }
        }}
        className={`rounded-lg border p-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${disabled || isDisabledTrigger ? "cursor-not-allowed opacity-50 bg-muted/30" : "cursor-grab bg-white hover:border-primary/60 active:cursor-grabbing focus-visible:border-primary"}`}
      >
        <div className="flex items-center gap-2">
          {item.nodeType === "TRIGGER" && <Zap className="h-4 w-4 text-amber-500" aria-hidden="true" />}
          {item.nodeType === "CONDITION" && <GitBranch className="h-4 w-4 text-violet-500" aria-hidden="true" />}
          {item.nodeType === "BRANCH" && <Split className="h-4 w-4 text-indigo-500" aria-hidden="true" />}
          {item.nodeType === "ACTION" && <PlayCircle className="h-4 w-4 text-blue-500" aria-hidden="true" />}
          {item.nodeType === "WAIT" && <Clock className="h-4 w-4 text-orange-500" aria-hidden="true" />}
          {item.nodeType === "END" && <CheckCircle2 className="h-4 w-4 text-emerald-500" aria-hidden="true" />}
          <span className="text-sm font-medium">{item.label}</span>
          {isDisabledTrigger && <span className="ml-auto text-[10px] font-medium text-muted-foreground">Already configured</span>}
        </div>
        <p className="mt-1 text-xs text-muted-foreground">{isDisabledTrigger ? "Trigger is already set from the version. Edit the WHEN card instead." : item.description}</p>
        {!isDisabledTrigger && <p className="mt-1 text-[11px] text-muted-foreground">Drag to canvas or press Enter to add.</p>}
      </div>
    );
  };

  // BRANCH is intentionally hidden from the normal palette — it is binary TRUE/FALSE like CONDITION.
  // Keep paletteItems entry for compatibility with persisted BRANCH nodes, but expose only IF/ELSE (CONDITION) to users.
  // Future multi-case ROUTE would require a separate engine capability (see WorkflowNode audit).
  const logicItems = paletteItems.filter((i) => i.nodeType === "CONDITION");
  const actionItems = paletteItems.filter((i) => i.nodeType === "ACTION");
  const flowItems = paletteItems.filter((i) => i.nodeType === "WAIT" || i.nodeType === "END");

  return (
    <div className="space-y-3">
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Add Step</p>
        <p className="text-[11px] text-muted-foreground">What should happen next?</p>
      </div>
      {hasTrigger ? (
        <div className="space-y-2">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Trigger</p>
          {renderItem(paletteItems.find((i) => i.nodeType === "TRIGGER")!)}
        </div>
      ) : null}
      <div className="space-y-2">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Logic</p>
        {logicItems.map(renderItem)}
      </div>
      <div className="space-y-2">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Actions</p>
        {actionItems.map(renderItem)}
        <p className="px-1 text-[11px] text-muted-foreground">Includes: Update Record, Assign Owner, Create Task, Click to Call, HTTP Request — choose action type after adding.</p>
      </div>
      <div className="space-y-2">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Flow</p>
        {flowItems.map(renderItem)}
      </div>
    </div>
  );
}
