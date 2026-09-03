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
  Loader2,
  XCircle,
  MinusCircle,
  Hourglass,
} from "lucide-react";
import { BuilderNodeData } from "../utils/graph-mapper";
import { describeAction, describeTrigger, isNodeConfigured } from "../utils/node-config";
import { useConnectionArm } from "../utils/connection-arm";

const typeStyles: Record<string, { icon: typeof Zap; border: string; badge: string }> = {
  TRIGGER: { icon: Zap, border: "border-amber-400", badge: "bg-amber-100 text-amber-800" },
  CONDITION: { icon: GitBranch, border: "border-violet-400", badge: "bg-violet-100 text-violet-800" },
  BRANCH: { icon: Split, border: "border-indigo-400", badge: "bg-indigo-100 text-indigo-800" },
  ACTION: { icon: PlayCircle, border: "border-blue-400", badge: "bg-blue-100 text-blue-800" },
  END: { icon: CheckCircle2, border: "border-emerald-400", badge: "bg-emerald-100 text-emerald-800" },
  WAIT: { icon: Clock, border: "border-orange-400", badge: "bg-orange-100 text-orange-800" },
};

function formatTriggerLabel(configuration: Record<string, unknown>): { title: string; sub: string } {
  const entityType = typeof configuration.entityType === "string" ? configuration.entityType : "";
  const eventType = typeof configuration.eventType === "string" ? configuration.eventType : "";
  const toTitle = (s: string) => s ? s.charAt(0).toUpperCase() + s.slice(1).toLowerCase() : "";
  const entity = toTitle(entityType);
  const event = toTitle(eventType);
  if (!entity && !event) return { title: "Not configured", sub: "" };
  const title = [entity, event].filter(Boolean).join(" ");
  const sub = entity && event ? `${entity} → ${event}` : describeTrigger(configuration);
  return { title, sub };
}

function subtitleFor(data: BuilderNodeData): string {
  switch (data.nodeType) {
    case "TRIGGER":
      return describeTrigger(data.configuration);
    case "ACTION":
      return describeAction(data.configuration, data.name);
    case "WAIT": {
      const waitType = typeof data.configuration.waitType === "string" ? String(data.configuration.waitType).toUpperCase() : (data.configuration as Record<string, unknown>).amount != null ? "DURATION" : "UNTIL";
      if (waitType === "DURATION") {
        const amount = (data.configuration as Record<string, unknown>).amount ?? 5;
        const unit = (data.configuration as Record<string, unknown>).unit ?? "MINUTES";
        return `Wait ${amount} ${String(unit).toLowerCase()}`;
      }
      const resumeAt = typeof data.configuration.resumeAt === "string" ? data.configuration.resumeAt : "";
      if (!resumeAt) return "Wait";
      try { return `Until ${new Date(resumeAt).toLocaleDateString()} ${new Date(resumeAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`; } catch { return "Wait"; }
    }
    case "END":
      return "";
    default:
      return data.name;
  }
}

function WorkflowNodeComponent({ data, selected, id: nodeId }: NodeProps) {
  const nodeData = data as BuilderNodeData;
  const style = typeStyles[nodeData.nodeType] ?? {
    icon: AlertTriangle,
    border: "border-red-400",
    badge: "bg-red-100 text-red-800",
  };
  const Icon = style.icon;
  const cfgState = isNodeConfigured(nodeData);
  const execStatus = (nodeData as unknown as { executionStatus?: string }).executionStatus as
    | "PENDING"
    | "RUNNING"
    | "COMPLETED"
    | "FAILED"
    | "SKIPPED"
    | "WAITING"
    | undefined;
  const isExecutionMode = Boolean(execStatus);
  const isHistorical = (nodeData as unknown as { isHistorical?: boolean }).isHistorical;
  const arm = useConnectionArm();

  const execConfig = (() => {
    if (!execStatus) return null;
    switch (execStatus) {
      case "COMPLETED":
        return { icon: CheckCircle2, label: "Completed", cls: "bg-emerald-50 text-emerald-700 border-emerald-200" };
      case "FAILED":
        return { icon: XCircle, label: "Failed", cls: "bg-red-50 text-red-700 border-red-200" };
      case "RUNNING":
        return { icon: Loader2, label: "Running", cls: "bg-blue-50 text-blue-700 border-blue-200" };
      case "SKIPPED":
        return { icon: MinusCircle, label: "Skipped", cls: "bg-slate-50 text-slate-600 border-slate-200" };
      case "WAITING":
        return { icon: Hourglass, label: "Waiting", cls: "bg-amber-50 text-amber-700 border-amber-200" };
      case "PENDING":
      default:
        return { icon: Clock, label: "Pending", cls: "bg-slate-50 text-slate-500 border-slate-200" };
    }
  })();

  const isReadOnly = Boolean(arm?.readOnly);
  const armedForNode = (handleId: string | null, type: "source" | "target") =>
    arm?.armed?.nodeId === nodeId && arm?.armed?.handleId === handleId && arm?.armed?.handleType === type;

  const handleKeyDownFor = (handleId: string | null, type: "source" | "target") => (e: React.KeyboardEvent) => {
    if (isReadOnly || isExecutionMode) return;
    if (e.key === "Escape") {
      e.preventDefault();
      e.stopPropagation();
      arm?.clear();
      return;
    }
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      e.stopPropagation();
      if (type === "source") {
        if (arm?.armed?.nodeId === nodeId && arm?.armed?.handleId === handleId) {
          arm?.clear();
        } else {
          arm?.arm({ nodeId: nodeId ?? "", handleId, handleType: "source" });
        }
      } else {
        // target
        if (!arm?.armed) return;
        arm?.activateTarget(nodeId ?? "", handleId);
      }
    }
  };

  const handleClickFor = (handleId: string | null, type: "source" | "target") => (e: React.MouseEvent) => {
    if (isReadOnly || isExecutionMode) return;
    // For mouse, only handle click when not part of drag - allow keyboard-style arming via click for touch/tablet
    // If user clicks source handle, arm it; if clicks target while armed, connect
    if (type === "source") {
      e.stopPropagation();
      if (arm?.armed?.nodeId === nodeId && arm?.armed?.handleId === handleId) {
        arm?.clear();
      } else {
        arm?.arm({ nodeId: nodeId ?? "", handleId, handleType: "source" });
      }
    } else if (type === "target" && arm?.armed) {
      e.stopPropagation();
      arm?.activateTarget(nodeId ?? "", handleId);
    }
  };

  const isDisconnected = Boolean((nodeData as unknown as { isDisconnected?: boolean }).isDisconnected);
  // Business-friendly TRIGGER: WHEN → ⚡ Lead Created → Lead → Created → [Edit]
  if (nodeData.nodeType === "TRIGGER") {
    const { title, sub } = formatTriggerLabel(nodeData.configuration);
    const showConfigured = cfgState.configured;
    return (
      <div
        className={`min-w-[220px] max-w-[260px] rounded-xl border-2 bg-white px-4 py-3 shadow-sm ${
          selected ? "border-primary ring-2 ring-primary/30" : isDisconnected ? "border-amber-500 ring-1 ring-amber-200" : "border-amber-400"
        } ${isHistorical ? "opacity-80 border-dashed" : ""}`}
        role="button"
        tabIndex={0}
        aria-label={`WHEN ${title}, trigger, ${showConfigured ? "configured" : "configuration required"}`}
      >
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-bold uppercase tracking-widest text-amber-700">WHEN</span>
          <span className="flex h-6 w-6 items-center justify-center rounded bg-amber-100 text-amber-700">
            <Zap className="h-4 w-4" aria-hidden="true" />
          </span>
        </div>
        <p className="mt-2 flex items-center gap-1.5 text-[15px] font-semibold leading-none" title={title}>
          <span aria-hidden="true">⚡</span> {title || "Not configured"}
        </p>
        {sub && <p className="mt-1 text-xs text-muted-foreground" title={sub}>{sub}</p>}
        {isDisconnected && !isExecutionMode && (
          <p className="mt-1 flex items-center gap-1 text-[11px] font-medium text-amber-700" role="status">
            <AlertTriangle className="h-3 w-3 shrink-0" aria-hidden="true" /> Disconnected
          </p>
        )}
        {!showConfigured && !isExecutionMode && !isDisconnected && (
          <p className="mt-1 flex items-center gap-1 text-[11px] font-medium text-amber-700" role="status">
            <AlertTriangle className="h-3 w-3 shrink-0" aria-hidden="true" /> Configuration required
          </p>
        )}
        <div className="mt-3 flex justify-end">
          <span className="rounded-full border bg-white px-2.5 py-1 text-[11px] font-medium text-muted-foreground shadow-sm">Edit</span>
        </div>
        {isExecutionMode && execConfig ? (
          <span className={`mt-2 inline-flex items-center gap-1 rounded border px-1.5 py-0.5 text-[11px] font-medium ${execConfig.cls}`}>
            <execConfig.icon className={`h-3 w-3 ${execStatus === "RUNNING" ? "motion-safe:animate-spin" : ""}`} aria-hidden="true" />
            {execConfig.label}
          </span>
        ) : null}
        {/* Trigger has no incoming handle, only outgoing */}
        <Handle
          type="source"
          position={Position.Bottom}
          id="out"
          tabIndex={isReadOnly || isExecutionMode ? -1 : 0}
          role="button"
          aria-label={`Connect from trigger ${title} output${armedForNode("out", "source") ? ", armed" : ""}`}
          aria-pressed={armedForNode("out", "source") ? true : undefined}
          aria-disabled={isReadOnly || isExecutionMode ? true : undefined}
          onKeyDown={handleKeyDownFor("out", "source")}
          onClick={handleClickFor("out", "source")}
          className={`!h-3.5 !w-3.5 !border-2 !bg-white !shadow-sm transition-all hover:!scale-125 hover:!border-amber-500 hover:!bg-amber-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${armedForNode("out", "source") ? "!bg-primary !border-primary !scale-125" : "!border-amber-400"}`}
        />
      </div>
    );
  }

  return (
    <div
      className={`min-w-[180px] max-w-[240px] rounded-lg border-2 bg-white px-3 py-2 shadow-sm ${
        selected ? "border-primary ring-2 ring-primary/30" : isDisconnected ? "border-amber-400 ring-1 ring-amber-200" : style.border
      } ${isHistorical ? "opacity-80 border-dashed" : ""} ${execStatus === "FAILED" ? "ring-1 ring-red-200" : ""} ${execStatus === "SKIPPED" ? "opacity-60" : ""}`}
      role="button"
      tabIndex={0}
      aria-label={`${nodeData.name}, ${nodeData.nodeType} node, ${execStatus ? execStatus.toLowerCase() : cfgState.configured ? "configured" : "configuration required"}${isHistorical ? ", historical" : ""}`}
    >
      <Handle
        type="target"
        position={Position.Top}
        id="in"
        tabIndex={isReadOnly || isExecutionMode ? -1 : 0}
        role="button"
        aria-label={`Connect to ${nodeData.name} input`}
        aria-disabled={isReadOnly || isExecutionMode ? true : undefined}
        onKeyDown={handleKeyDownFor("in", "target")}
        onClick={handleClickFor("in", "target")}
        className={`!h-3.5 !w-3.5 !border-2 !bg-white !shadow-sm transition-all hover:!scale-125 hover:!border-primary hover:!bg-primary/10 ${armedForNode("in", "target") ? "!bg-primary !border-primary !scale-125" : "!border-slate-400"} focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2`}
      />

      <div className="flex items-center gap-2">
        <span className={`flex h-6 w-6 items-center justify-center rounded ${style.badge}`}>
          <Icon className="h-4 w-4" />
        </span>
        <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          {nodeData.nodeType === "CONDITION" || nodeData.nodeType === "BRANCH" ? "IF / ELSE" : nodeData.nodeType}
        </span>
      </div>

      <p className="mt-1 truncate text-sm font-medium" title={nodeData.name}>{nodeData.name}</p>
      {subtitleFor(nodeData) && (
        <p className="truncate text-xs text-muted-foreground" title={subtitleFor(nodeData)}>{subtitleFor(nodeData)}</p>
      )}
      {isDisconnected && !isExecutionMode && (
        <p className="mt-1 flex items-center gap-1 text-[11px] font-medium text-amber-700" role="status">
          <AlertTriangle className="h-3 w-3 shrink-0" aria-hidden="true" /> Disconnected
        </p>
      )}
      {isExecutionMode && execConfig ? (
        <span className={`mt-1 inline-flex items-center gap-1 rounded border px-1.5 py-0.5 text-[11px] font-medium ${execConfig.cls}`}>
          <execConfig.icon className={`h-3 w-3 ${execStatus === "RUNNING" ? "motion-safe:animate-spin" : ""}`} aria-hidden="true" />
          {execConfig.label}
          {isHistorical ? " · historical" : ""}
        </span>
      ) : (
        !cfgState.configured &&
        !isDisconnected && (
          <p className="mt-1 flex items-center gap-1 text-[11px] font-medium text-amber-700" role="status" aria-label="Configuration required">
            <AlertTriangle className="h-3 w-3 shrink-0" aria-hidden="true" />
            Configuration required
          </p>
        )
      )}
      {isHistorical && !isExecutionMode && (
        <p className="mt-1 text-[11px] text-muted-foreground">Historical node</p>
      )}


      {nodeData.nodeType === "CONDITION" || nodeData.nodeType === "BRANCH" ? (
        <>
          <Handle
            type="source"
            position={Position.Bottom}
            id="true"
            tabIndex={isReadOnly || isExecutionMode ? -1 : 0}
            role="button"
            aria-label={`Connect from ${nodeData.name} TRUE output${armedForNode("true", "source") ? ", armed" : ""}`}
            aria-pressed={armedForNode("true", "source") ? true : undefined}
            aria-disabled={isReadOnly || isExecutionMode ? true : undefined}
            onKeyDown={handleKeyDownFor("true", "source")}
            onClick={handleClickFor("true", "source")}
            className={`!bottom-0 !left-1/4 !h-3.5 !w-3.5 !border-2 !bg-white !shadow-sm transition-all hover:!scale-125 hover:!border-emerald-500 hover:!bg-emerald-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${armedForNode("true", "source") ? "!bg-emerald-600 !border-emerald-600 !scale-125" : "!border-emerald-500"}`}
          />
          <Handle
            type="source"
            position={Position.Bottom}
            id="false"
            tabIndex={isReadOnly || isExecutionMode ? -1 : 0}
            role="button"
            aria-label={`Connect from ${nodeData.name} FALSE output${armedForNode("false", "source") ? ", armed" : ""}`}
            aria-pressed={armedForNode("false", "source") ? true : undefined}
            aria-disabled={isReadOnly || isExecutionMode ? true : undefined}
            onKeyDown={handleKeyDownFor("false", "source")}
            onClick={handleClickFor("false", "source")}
            className={`!bottom-0 !left-3/4 !h-3.5 !w-3.5 !border-2 !bg-white !shadow-sm transition-all hover:!scale-125 hover:!border-rose-500 hover:!bg-rose-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${armedForNode("false", "source") ? "!bg-rose-600 !border-rose-600 !scale-125" : "!border-rose-500"}`}
          />
          <div className="pointer-events-none absolute bottom-0 left-0 right-0 -mb-5 flex justify-between px-2 text-[10px] font-bold">
            <span className="rounded-full border bg-emerald-50 px-1.5 py-0.5 text-emerald-700 border-emerald-200">TRUE</span>
            <span className="rounded-full border bg-rose-50 px-1.5 py-0.5 text-rose-700 border-rose-200">FALSE</span>
          </div>
        </>
      ) : (
        nodeData.nodeType !== "END" && (
          <Handle
            type="source"
            position={Position.Bottom}
            id="out"
            tabIndex={isReadOnly || isExecutionMode ? -1 : 0}
            role="button"
            aria-label={`Connect from ${nodeData.name} output${armedForNode("out", "source") ? ", armed" : ""}`}
            aria-pressed={armedForNode("out", "source") ? true : undefined}
            aria-disabled={isReadOnly || isExecutionMode ? true : undefined}
            onKeyDown={handleKeyDownFor("out", "source")}
            onClick={handleClickFor("out", "source")}
            className={`!h-3.5 !w-3.5 !border-2 !bg-white !shadow-sm transition-all hover:!scale-125 hover:!border-primary hover:!bg-primary/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${armedForNode("out", "source") ? "!bg-primary !border-primary !scale-125" : "!border-slate-400"}`}
          />
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
