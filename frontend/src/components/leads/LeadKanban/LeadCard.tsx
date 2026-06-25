"use client";

import Link from "next/link";
import { useDraggable } from "@dnd-kit/core";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical } from "lucide-react";
import { cn } from "@/lib/utils";
import { LeadResponse } from "@/types/leads";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { LeadConvertDialog } from "../LeadConvertDialog";
import { SourceBadge } from "../shared/SourceBadge";

interface LeadCardProps {
  lead: LeadResponse;
  dragEnabled?: boolean;
}

export function LeadCard({ lead, dragEnabled = false }: LeadCardProps) {
  const name = [lead.firstName, lead.lastName].filter(Boolean).join(" ");

  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: lead.id,
    data: { lead, statusId: lead.status.id },
    disabled: !dragEnabled,
  });

  const style = transform
    ? { transform: CSS.Translate.toString(transform) }
    : undefined;

  const { canEditLeads } = usePermissions();

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        "rounded-lg border bg-white shadow-sm",
        isDragging && "opacity-60 ring-2 ring-primary z-50"
      )}
    >
      <div className="flex items-start gap-1 p-3">
        {dragEnabled && (
          <button
            type="button"
            className="mt-0.5 shrink-0 cursor-grab touch-none text-muted-foreground hover:text-foreground active:cursor-grabbing"
            aria-label="Drag lead"
            {...listeners}
            {...attributes}
          >
            <GripVertical className="h-4 w-4" />
          </button>
        )}
        <Link
          href={`/leads/${lead.id}`}
          className="min-w-0 flex-1 hover:opacity-90"
        >
          <p className="font-medium text-sm">{name}</p>
          {lead.company && (
            <p className="text-xs text-muted-foreground mt-1">{lead.company}</p>
          )}
          {lead.email && (
            <p className="text-xs text-muted-foreground truncate">{lead.email}</p>
          )}
          <div className="mt-2 flex items-center justify-between gap-2">
            <SourceBadge source={lead.source} />
            <span className="text-xs font-medium text-muted-foreground">
              {lead.score ?? 0} pts
            </span>
          </div>
        </Link>
      </div>
      {canEditLeads && !lead.isConverted && (
        <div className="border-t px-3 pb-3 pt-2">
          <LeadConvertDialog lead={lead} triggerLabel="Convert" triggerClassName="w-full" />
        </div>
      )}
    </div>
  );
}
