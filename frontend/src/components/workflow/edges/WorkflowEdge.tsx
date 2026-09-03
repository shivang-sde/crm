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

  // ponytail: SVG marker defined in WorkflowCanvas (#wf-arrow), arrow via url()
  const markerId = selected || isExecuted ? "url(#wf-arrow-selected)" : isDimmed ? "url(#wf-arrow-dimmed)" : "url(#wf-arrow)";
  return (
    <>
      <BaseEdge
        path={edgePath}
        markerEnd={markerId}
        style={{
          stroke: selected ? "hsl(var(--primary))" : isExecuted ? "hsl(var(--primary))" : isDimmed ? "hsl(var(--muted-foreground))" : "hsl(var(--foreground) / 0.55)",
          strokeWidth: selected || isExecuted ? 2.6 : 1.9,
          opacity: isDimmed ? 0.35 : 1,
        }}
        interactionWidth={18}
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
