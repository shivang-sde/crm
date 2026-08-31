"use client";

import { memo } from "react";
import {
  BaseEdge,
  EdgeLabelRenderer,
  EdgeProps,
  getBezierPath,
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
}: EdgeProps) {
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });

  const label = resolveEdgeLabel(data, sourceHandleId);
  const d = data as unknown as { executed?: boolean; dimmed?: boolean } | undefined;
  const isExecuted = Boolean(d?.executed);
  const isDimmed = Boolean(d?.dimmed);

  return (
    <>
      <BaseEdge
        path={edgePath}
        style={{
          stroke: selected ? "hsl(var(--primary))" : isExecuted ? "hsl(var(--primary))" : isDimmed ? "hsl(var(--muted-foreground))" : undefined,
          strokeWidth: selected || isExecuted ? 2.5 : isDimmed ? 1 : undefined,
          opacity: isDimmed ? 0.35 : undefined,
        }}
      />
      {label && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: "absolute",
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              pointerEvents: "all",
            }}
            className={`nodrag nopan rounded border px-1.5 py-0.5 text-[10px] font-semibold leading-none shadow-sm ${isExecuted ? "bg-primary text-primary-foreground border-primary" : isDimmed ? "bg-white opacity-50" : "bg-white"}`}
            aria-label={`Edge: ${label}${isExecuted ? " executed" : isDimmed ? " skipped" : ""}`}
          >
            {label} {isExecuted ? "✓" : ""}
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
