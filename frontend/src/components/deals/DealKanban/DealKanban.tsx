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
import { useDeals, useDealStages, useChangeDealStage } from "@/lib/hooks/deals";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { DealResponse } from "@/types/deals";
import { DealStageSummary } from "@/types/deal-stages";
import { DealColumn } from "./DealColumn";
import { DealCard } from "./DealCard";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";

export function DealKanban() {
  const { canEditDeals } = usePermissions();
  const { data: stages, isLoading: stagesLoading } = useDealStages();
  const { data: dealsResult, isLoading: dealsLoading } = useDeals({
    page: 0,
    size: 500,
  });
  const changeStage = useChangeDealStage();

  const [localDeals, setLocalDeals] = useState<DealResponse[]>([]);
  const [activeDeal, setActiveDeal] = useState<DealResponse | null>(null);

  // Dialog state for closed-stage drop
  const [showWonDialog, setShowWonDialog] = useState(false);
  const [showLostDialog, setShowLostDialog] = useState(false);
  const [reason, setReason] = useState("");
  const [pendingDrop, setPendingDrop] = useState<{ dealId: string; stageId: string } | null>(null);
  const [previousDeals, setPreviousDeals] = useState<DealResponse[]>([]);

  useEffect(() => {
    if (dealsResult?.data) {
      setLocalDeals(dealsResult.data);
    }
  }, [dealsResult]);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 8 },
    })
  );

  const sortedStages = useMemo(
    () =>
      [...(stages || [])].sort(
        (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
      ),
    [stages]
  );

  const stageMap = useMemo(() => {
    const map = new Map<string, DealStageSummary>();
    stages?.forEach((s) => map.set(s.id, s));
    return map;
  }, [stages]);

  function handleDragStart(event: DragStartEvent) {
    const deal = localDeals.find((d) => d.id === event.active.id);
    setActiveDeal(deal ?? null);
  }

  function handleDragEnd(event: DragEndEvent) {
    setActiveDeal(null);
    const { active, over } = event;
    if (!over || !canEditDeals) return;

    const dealId = String(active.id);
    const newStageId = String(over.id);
    const deal = localDeals.find((d) => d.id === dealId);
    if (!deal || deal.stage.id === newStageId) return;

    const newStage = stageMap.get(newStageId);
    if (!newStage) return;

    const prev = localDeals;

    // Optimistic update
    setLocalDeals((curr) =>
      curr.map((d) =>
        d.id === dealId ? { ...d, stage: newStage, recordCategory: newStage.recordCategory } : d
      )
    );

    if (newStage.recordCategory === "CLOSED_WON") {
      setPreviousDeals(prev);
      setPendingDrop({ dealId, stageId: newStageId });
      setReason("");
      setShowWonDialog(true);
    } else if (newStage.recordCategory === "CLOSED_LOST") {
      setPreviousDeals(prev);
      setPendingDrop({ dealId, stageId: newStageId });
      setReason("");
      setShowLostDialog(true);
    } else {
      changeStage.mutate(
        { id: dealId, stageId: newStageId },
        { onError: () => setLocalDeals(prev) }
      );
    }
  }

  const handleConfirmWon = () => {
    if (!pendingDrop) return;
    changeStage.mutate(
      { id: pendingDrop.dealId, stageId: pendingDrop.stageId, wonReason: reason || undefined },
      { onError: () => setLocalDeals(previousDeals) }
    );
    setShowWonDialog(false);
    setReason("");
    setPendingDrop(null);
  };

  const handleConfirmLost = () => {
    if (!pendingDrop || !reason.trim()) return;
    changeStage.mutate(
      { id: pendingDrop.dealId, stageId: pendingDrop.stageId, lostReason: reason },
      { onError: () => setLocalDeals(previousDeals) }
    );
    setShowLostDialog(false);
    setReason("");
    setPendingDrop(null);
  };

  const handleCancelDrop = (close: () => void) => {
    setLocalDeals(previousDeals);
    setPendingDrop(null);
    setReason("");
    close();
  };

  if (stagesLoading || dealsLoading) {
    return (
      <div className="flex justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (sortedStages.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center gap-3">
        <p className="text-muted-foreground text-sm">
          No pipeline stages configured yet.
        </p>
        <p className="text-muted-foreground text-xs">
          Go to Deal Settings → Stages to add your first stage.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {canEditDeals && (
        <p className="text-sm text-muted-foreground">
          Drag cards between columns to update deal stage.
        </p>
      )}
      <DndContext
        sensors={sensors}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
      >
        <div className="overflow-x-auto pb-4">
          <div className="flex gap-4 min-w-max">
            {sortedStages.map((stage) => (
              <DealColumn
                key={stage.id}
                stage={stage}
                deals={localDeals.filter((d) => d.stage.id === stage.id)}
                dragEnabled={canEditDeals}
              />
            ))}
          </div>
        </div>
        <DragOverlay>
          {activeDeal ? (
            <div className="w-72 rotate-2">
              <DealCard deal={activeDeal} dragEnabled={false} />
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>

      {/* Won Reason Dialog */}
      <Dialog open={showWonDialog} onOpenChange={(open) => { if (!open) handleCancelDrop(() => setShowWonDialog(false)); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mark Deal as Won</DialogTitle>
            <DialogDescription>
              Optionally provide a reason why this deal was won.
            </DialogDescription>
          </DialogHeader>
          <div className="py-2">
            <Textarea
              placeholder="Won reason (optional)..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => handleCancelDrop(() => setShowWonDialog(false))}>
              Cancel
            </Button>
            <Button onClick={handleConfirmWon} disabled={changeStage.isPending}>
              Confirm Won
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Lost Reason Dialog */}
      <Dialog open={showLostDialog} onOpenChange={(open) => { if (!open) handleCancelDrop(() => setShowLostDialog(false)); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mark Deal as Lost</DialogTitle>
            <DialogDescription>
              A reason is required to mark this deal as lost.
            </DialogDescription>
          </DialogHeader>
          <div className="py-2">
            <Textarea
              placeholder="Why was this deal lost? (required)..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => handleCancelDrop(() => setShowLostDialog(false))}>
              Cancel
            </Button>
            <Button onClick={handleConfirmLost} disabled={changeStage.isPending || !reason.trim()}>
              Confirm Lost
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
