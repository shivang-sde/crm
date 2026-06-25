"use client";

import { useDroppable } from "@dnd-kit/core";
import { cn } from "@/lib/utils";
import { DealResponse } from "@/types/deals";
import { DealStageSummary } from "@/types/deal-stages";
import { DealCard } from "./DealCard";
import { DollarSign } from "lucide-react";

interface DealColumnProps {
  stage: DealStageSummary;
  deals: DealResponse[];
  dragEnabled?: boolean;
}

export function DealColumn({ stage, deals, dragEnabled }: DealColumnProps) {
  const headerColor = stage.color || "#6366f1";

  const { setNodeRef, isOver } = useDroppable({
    id: stage.id,
    data: { stageId: stage.id },
  });

  // Total value of all deals in this column
  const totalValue = deals.reduce((sum, d) => sum + (d.amount ?? 0), 0);
  const hasCurrency = deals.find((d) => d.currency)?.currency || "USD";

  return (
    <div className="flex-shrink-0 w-72 flex flex-col max-h-[calc(100vh-14rem)]">
      {/* Column Header */}
      <div
        className="rounded-t-lg px-3 py-2.5 text-sm font-semibold text-white flex items-center justify-between"
        style={{ backgroundColor: headerColor }}
      >
        <span>
          {stage.name}
          <span className="ml-2 opacity-75 font-normal text-xs">({deals.length})</span>
        </span>
        {totalValue > 0 && (
          <span className="flex items-center gap-0.5 text-xs opacity-90 font-medium">
            <DollarSign className="h-3 w-3" />
            {totalValue.toLocaleString()}
          </span>
        )}
      </div>

      {/* Drop Zone */}
      <div
        ref={setNodeRef}
        className={cn(
          "flex-1 overflow-y-auto rounded-b-lg border border-t-0 bg-muted/30 p-2 space-y-2 min-h-[200px] transition-colors",
          isOver && dragEnabled && "bg-primary/10 ring-2 ring-primary/30 ring-inset"
        )}
      >
        {deals.length === 0 ? (
          <p className="text-xs text-muted-foreground text-center py-6">
            {dragEnabled ? "Drop deals here" : "No deals"}
          </p>
        ) : (
          deals.map((deal) => (
            <DealCard key={deal.id} deal={deal} dragEnabled={dragEnabled} />
          ))
        )}
      </div>
    </div>
  );
}
