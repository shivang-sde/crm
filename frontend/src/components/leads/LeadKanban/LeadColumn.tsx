"use client";

import { useDroppable } from "@dnd-kit/core";
import { cn } from "@/lib/utils";
import { LeadResponse, LeadStatusSummary } from "@/types/leads";
import { LeadCard } from "./LeadCard";

interface LeadColumnProps {
  status: LeadStatusSummary;
  leads: LeadResponse[];
  dragEnabled?: boolean;
}

export function LeadColumn({ status, leads, dragEnabled }: LeadColumnProps) {
  const headerColor = status.color || "#6366f1";

  const { setNodeRef, isOver } = useDroppable({
    id: status.id,
    data: { statusId: status.id },
  });

  return (
    <div className="flex-shrink-0 w-72 flex flex-col max-h-[calc(100vh-12rem)]">
      <div
        className="rounded-t-lg px-3 py-2 font-medium text-sm text-white"
        style={{ backgroundColor: headerColor }}
      >
        {status.name}
        <span className="ml-2 opacity-80">({leads.length})</span>
      </div>
      <div
        ref={setNodeRef}
        className={cn(
          "flex-1 overflow-y-auto rounded-b-lg border border-t-0 bg-muted/30 p-2 space-y-2 min-h-[200px] transition-colors",
          isOver && dragEnabled && "bg-primary/10 ring-2 ring-primary/30 ring-inset"
        )}
      >
        {leads.length === 0 ? (
          <p className="text-xs text-muted-foreground text-center py-4">
            {dragEnabled ? "Drop leads here" : "No leads"}
          </p>
        ) : (
          leads.map((lead) => (
            <LeadCard key={lead.id} lead={lead} dragEnabled={dragEnabled} />
          ))
        )}
      </div>
    </div>
  );
}
