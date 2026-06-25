"use client";

import { useEffect, useMemo, useState } from "react";
import {
  DndContext,
  DragEndEvent,
  DragOverlay,
  DragStartEvent,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { Loader2 } from "lucide-react";
import {
  useChangeLeadStatus,
  useLeads,
  useLeadStatuses,
} from "@/lib/hooks/leads";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { LeadResponse, LeadStatusSummary } from "@/types/leads";
import { LeadColumn } from "./LeadColumn";
import { LeadCard } from "./LeadCard";

export function LeadKanban() {
  const { canEditLeads } = usePermissions();
  const { data: statuses, isLoading: statusesLoading } = useLeadStatuses();
  const { data: leadsResult, isLoading: leadsLoading } = useLeads({
    page: 0,
    size: 500,
  });
  const changeStatus = useChangeLeadStatus();

  const [localLeads, setLocalLeads] = useState<LeadResponse[]>([]);
  const [activeLead, setActiveLead] = useState<LeadResponse | null>(null);

  useEffect(() => {
    if (leadsResult?.data) {
      setLocalLeads(leadsResult.data);
    }
  }, [leadsResult]);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 8 },
    })
  );

  const sortedStatuses = useMemo(
    () =>
      [...(statuses || [])].sort(
        (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
      ),
    [statuses]
  );

  const statusMap = useMemo(() => {
    const map = new Map<string, LeadStatusSummary>();
    statuses?.forEach((s) => map.set(s.id, s));
    return map;
  }, [statuses]);

  function handleDragStart(event: DragStartEvent) {
    const lead = localLeads.find((l) => l.id === event.active.id);
    setActiveLead(lead ?? null);
  }

  function handleDragEnd(event: DragEndEvent) {
    setActiveLead(null);
    const { active, over } = event;
    if (!over || !canEditLeads) return;

    const leadId = String(active.id);
    const newStatusId = String(over.id);
    const lead = localLeads.find((l) => l.id === leadId);
    if (!lead || lead.status.id === newStatusId) return;

    const newStatus = statusMap.get(newStatusId);
    if (!newStatus) return;

    const previousLeads = localLeads;
    setLocalLeads((prev) =>
      prev.map((l) =>
        l.id === leadId ? { ...l, status: newStatus } : l
      )
    );

    changeStatus.mutate(
      { id: leadId, statusId: newStatusId },
      {
        onError: () => setLocalLeads(previousLeads),
      }
    );
  }

  if (statusesLoading || leadsLoading) {
    return (
      <div className="flex justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {canEditLeads && (
        <p className="text-sm text-muted-foreground">
          Drag cards between columns to update lead status.
        </p>
      )}
      <DndContext
        sensors={sensors}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
      >
        <div className="overflow-x-auto pb-4">
          <div className="flex gap-4 min-w-max">
            {sortedStatuses.map((status) => (
              <LeadColumn
                key={status.id}
                status={status}
                leads={localLeads.filter((l) => l.status.id === status.id)}
                dragEnabled={canEditLeads}
              />
            ))}
          </div>
        </div>
        <DragOverlay>
          {activeLead ? (
            <div className="w-72 rotate-2">
              <LeadCard lead={activeLead} dragEnabled={false} />
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>
    </div>
  );
}
