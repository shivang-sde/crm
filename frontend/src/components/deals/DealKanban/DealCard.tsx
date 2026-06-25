"use client";

import Link from "next/link";
import { useDraggable } from "@dnd-kit/core";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical, DollarSign, Calendar, User } from "lucide-react";
import { cn } from "@/lib/utils";
import { DealResponse } from "@/types/deals";
import { usePermissions } from "@/lib/hooks/usePermissions";

interface DealCardProps {
  deal: DealResponse;
  dragEnabled?: boolean;
}

export function DealCard({ deal, dragEnabled = false }: DealCardProps) {
  const { canEditDeals } = usePermissions();

  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: deal.id,
    data: { deal, stageId: deal.stage.id },
    disabled: !dragEnabled,
  });

  const style = transform
    ? { transform: CSS.Translate.toString(transform) }
    : undefined;

  const stageColor = deal.stage?.color || "#6366f1";

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        "rounded-lg border bg-white shadow-sm hover:shadow-md transition-shadow",
        isDragging && "opacity-60 ring-2 ring-primary z-50"
      )}
    >
      <div className="flex items-start gap-1 p-3">
        {dragEnabled && (
          <button
            type="button"
            className="mt-0.5 shrink-0 cursor-grab touch-none text-muted-foreground hover:text-foreground active:cursor-grabbing"
            aria-label="Drag deal"
            {...listeners}
            {...attributes}
          >
            <GripVertical className="h-4 w-4" />
          </button>
        )}
        <Link
          href={`/deals/${deal.id}`}
          className="min-w-0 flex-1 hover:opacity-90"
        >
          {/* Deal name + status indicators */}
          <div className="flex items-start justify-between gap-2">
            <p className="font-semibold text-sm leading-tight">{deal.name}</p>
            <div className="flex-shrink-0 flex gap-1">
              {deal.recordCategory === "CLOSED_WON" && (
                <span className="inline-block w-2 h-2 rounded-full bg-emerald-500 mt-1" title="Won" />
              )}
              {deal.recordCategory === "CLOSED_LOST" && (
                <span className="inline-block w-2 h-2 rounded-full bg-rose-500 mt-1" title="Lost" />
              )}
            </div>
          </div>

          {/* Amount */}
          {deal.amount !== undefined && deal.amount !== null && (
            <div className="flex items-center gap-1 mt-2 text-xs text-muted-foreground">
              <DollarSign className="h-3 w-3" />
              <span className="font-medium text-foreground">
                {deal.amount.toLocaleString()} {deal.currency || "USD"}
              </span>
            </div>
          )}

          {/* Expected Revenue */}
          {deal.expectedRevenue !== undefined && deal.expectedRevenue !== null && deal.expectedRevenue > 0 && (
            <div className="flex items-center gap-1 mt-1 text-xs text-emerald-600">
              <DollarSign className="h-3 w-3" />
              <span className="font-medium">
                {deal.expectedRevenue.toLocaleString()} expected
              </span>
            </div>
          )}

          {/* Expected close date */}
          {deal.expectedCloseDate && (
            <div className="flex items-center gap-1 mt-1 text-xs text-muted-foreground">
              <Calendar className="h-3 w-3" />
              <span>{new Date(deal.expectedCloseDate).toLocaleDateString()}</span>
            </div>
          )}

          {/* Owner */}
          {deal.ownerUserId && (
            <div className="flex items-center gap-1 mt-1 text-xs text-muted-foreground truncate">
              <User className="h-3 w-3 flex-shrink-0" />
              <span className="truncate font-mono text-xs">{deal.ownerUserId.slice(0, 8)}…</span>
            </div>
          )}

          {/* Probability bar */}
          {deal.probability !== undefined && deal.probability !== null && (
            <div className="mt-2">
              <div className="flex items-center justify-between text-xs text-muted-foreground mb-1">
                <span>Probability</span>
                <span>{deal.probability}%</span>
              </div>
              <div className="h-1.5 w-full rounded-full bg-muted overflow-hidden">
                <div
                  className="h-full rounded-full transition-all"
                  style={{
                    width: `${Math.min(deal.probability, 100)}%`,
                    backgroundColor: stageColor,
                  }}
                />
              </div>
            </div>
          )}
        </Link>
      </div>
    </div>
  );
}
