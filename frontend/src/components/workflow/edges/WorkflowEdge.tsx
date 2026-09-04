"use client";

import { memo, useId } from "react";
import {
  BaseEdge,
  EdgeLabelRenderer,
  EdgeProps,
  getSmoothStepPath,
} from "@xyflow/react";

function WorkflowEdgeComponent({
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
  selected,
  sourceHandleId,
  id,
}: EdgeProps) {
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    borderRadius: 8,
  });

  const label = resolveEdgeLabel(data, sourceHandleId);
  const d = data as unknown as { executed?: boolean; dimmed?: boolean } | undefined;
  const isExecuted = Boolean(d?.executed);
  const isDimmed = Boolean(d?.dimmed);
  const rawId = useId();
  const markerId = `wf-arrow-${id ?? rawId.replace(/:/g, "")}`;
  const markerSelectedId = `${markerId}-selected`;
  const markerDimmedId = `${markerId}-dimmed`;
  const activeMarker = selected || isExecuted ? markerSelectedId : isDimmed ? markerDimmedId : markerId;
  return (
    <>
      <svg style={{ position: "absolute", width: 0, height: 0, overflow: "hidden" }} aria-hidden="true">
        <defs>
          <marker id={markerId} viewBox="0 0 10 10" refX={8} refY={5} markerWidth={8} markerHeight={8} orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--foreground)" opacity={isDimmed ? 0.35 : 1} />
          </marker>
          <marker id={markerSelectedId} viewBox="0 0 10 10" refX={8} refY={5} markerWidth={8} markerHeight={8} orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--primary)" />
          </marker>
          <marker id={markerDimmedId} viewBox="0 0 10 10" refX={8} refY={5} markerWidth={8} markerHeight={8} orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--muted-foreground)" />
          </marker>
        </defs>
      </svg>
      <BaseEdge
        path={edgePath}
        markerEnd={`url(#${activeMarker})`}
        style={{
          stroke: selected || isExecuted ? "var(--primary)" : isDimmed ? "var(--muted-foreground)" : "var(--foreground)",
          strokeWidth: selected ? 3 : isExecuted ? 2.8 : 2.2,
          opacity: isDimmed ? 0.45 : 1,
        }}
        interactionWidth={24}
      />
      {/* Invisible wider hit area for easy selection (n8n-style) */}
      <path
        d={edgePath}
        fill="none"
        stroke="transparent"
        strokeWidth={24}
        style={{ pointerEvents: "stroke" }}
      />
      {label && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: "absolute",
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              pointerEvents: "all",
            }}
            className={`nodrag nopan rounded-full border px-2 py-0.5 text-[10px] font-bold leading-none shadow-sm ${
              label === "TRUE"
                ? "bg-emerald-600 text-white border-emerald-600"
                : label === "FALSE"
                  ? "bg-rose-600 text-white border-rose-600"
                  : isExecuted
                    ? "bg-primary text-primary-foreground border-primary"
                    : isDimmed
                      ? "bg-white opacity-50"
                      : "bg-white"
            }`}
            aria-label={`Edge: ${label}${isExecuted ? " executed" : isDimmed ? " skipped" : ""}`}
          >
            {label} {isExecuted ? "✓" : label === "TRUE" ? "→" : label === "FALSE" ? "→" : ""}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

function resolveEdgeLabel(
  data: Record<string, unknown> | undefined,
  sourceHandleId: string | null | undefined
): string | null {
  if (!data) return null;
  const edgeData = data as {
    edgeKey?: string | null;
    configuration?: Record<string, unknown>;
  };
  const outcome =
    typeof edgeData.configuration?.outcome === "string"
      ? String(edgeData.configuration.outcome).trim().toUpperCase()
      : null;
  if (outcome === "TRUE" || outcome === "FALSE") return outcome;
  const branchKey =
    typeof edgeData.edgeKey === "string"
      ? edgeData.edgeKey.trim().toUpperCase()
      : null;
  if (branchKey === "TRUE" || branchKey === "FALSE") return branchKey;
  if (sourceHandleId === "true") return "TRUE";
  if (sourceHandleId === "false") return "FALSE";
  // No NEXT label unless backend explicitly signals it; keep sequential unlabeled.
  return null;
}

export const WorkflowEdge = memo(WorkflowEdgeComponent);
