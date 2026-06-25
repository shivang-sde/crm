"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { useDealStages, useChangeDealStage, useAssignDeal, useMarkDealWon, useMarkDealLost } from "@/lib/hooks/deals";
import { userApi } from "@/lib/api/users";
import { useQuery } from "@tanstack/react-query";
import { DealResponse } from "@/types/deals";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { CheckCircle2, XCircle, Loader2 } from "lucide-react";

interface DealAssignmentProps {
  deal: DealResponse;
}

export function DealAssignment({ deal }: DealAssignmentProps) {
  const { canEditDeals } = usePermissions();
  const { data: stages } = useDealStages();

  const stageMutation = useChangeDealStage();
  const assignMutation = useAssignDeal();
  const markWonMutation = useMarkDealWon();
  const markLostMutation = useMarkDealLost();

  const [showWonDialog, setShowWonDialog] = useState(false);
  const [showLostDialog, setShowLostDialog] = useState(false);
  const [reason, setReason] = useState("");
  const [pendingStageId, setPendingStageId] = useState<string | null>(null);

  const { data: usersData } = useQuery({
    queryKey: ["users", "deal-assign"],
    queryFn: () => userApi.getUsers({ page: 0, isActive: true }),
    enabled: canEditDeals,
  });

  const isPending =
    stageMutation.isPending ||
    assignMutation.isPending ||
    markWonMutation.isPending ||
    markLostMutation.isPending;

  const wonStage = stages?.find((s) => s.recordCategory === "CLOSED_WON");
  const lostStage = stages?.find((s) => s.recordCategory === "CLOSED_LOST");
  const isWon = deal.recordCategory === "CLOSED_WON";
  const isLost = deal.recordCategory === "CLOSED_LOST";

  const handleConfirmWon = () => {
    if (pendingStageId) {
      stageMutation.mutate({ id: deal.id, stageId: pendingStageId, wonReason: reason || undefined });
    } else {
      markWonMutation.mutate({ id: deal.id, wonReason: reason || undefined, stageId: wonStage?.id });
    }
    setShowWonDialog(false);
    setReason("");
    setPendingStageId(null);
  };

  const handleConfirmLost = () => {
    if (!reason.trim()) return;
    if (pendingStageId) {
      stageMutation.mutate({ id: deal.id, stageId: pendingStageId, lostReason: reason });
    } else {
      markLostMutation.mutate({ id: deal.id, lostReason: reason, stageId: lostStage?.id });
    }
    setShowLostDialog(false);
    setReason("");
    setPendingStageId(null);
  };

  if (!canEditDeals) {
    return (
      <Card className="shadow-sm border border-muted">
        <CardHeader>
          <CardTitle className="text-base font-semibold text-foreground">Management</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <div>
            <span className="text-muted-foreground">Stage: </span>
            <span className="font-medium text-foreground">{deal.stage?.name}</span>
          </div>
          <div>
            <span className="text-muted-foreground">Owner ID: </span>
            <span className="font-medium text-foreground">{deal.ownerUserId || "Unassigned"}</span>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="shadow-sm border border-muted">
      <CardHeader>
        <CardTitle className="text-base font-semibold text-foreground">Manage Deal</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Deal Stage Selector */}
        <div>
          <p className="text-sm font-medium text-muted-foreground mb-2">Deal Stage</p>
          <Select
            value={deal.stage?.id}
            onValueChange={(stageId) => {
              const targetStage = stages?.find((s) => s.id === stageId);
              if (targetStage?.recordCategory === "CLOSED_WON") {
                setPendingStageId(stageId);
                setReason("");
                setShowWonDialog(true);
              } else if (targetStage?.recordCategory === "CLOSED_LOST") {
                setPendingStageId(stageId);
                setReason("");
                setShowLostDialog(true);
              } else {
                stageMutation.mutate({ id: deal.id, stageId });
              }
            }}
            disabled={isPending}
          >
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {stages?.map((s) => (
                <SelectItem key={s.id} value={s.id}>
                  {s.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Owner Selector */}
        <div>
          <p className="text-sm font-medium text-muted-foreground mb-2">Assign Owner</p>
          <Select
            value={deal.ownerUserId || "none"}
            onValueChange={(ownerUserId) => {
              assignMutation.mutate({ id: deal.id, ownerUserId: ownerUserId === "none" ? "" : ownerUserId });
            }}
            disabled={isPending}
          >
            <SelectTrigger className="w-full">
              <SelectValue placeholder="Unassigned" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="none">Unassigned</SelectItem>
              {usersData?.content.map((user) => (
                <SelectItem key={user.id} value={user.id}>
                  {user.firstName} {user.lastName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Won / Lost Status Action Buttons */}
        <div className="border-t pt-4 space-y-2">
          <p className="text-sm font-medium text-muted-foreground mb-2">Outcome Actions</p>
          <div className="grid grid-cols-2 gap-2">
            <Button
              variant="outline"
              size="sm"
              className="text-emerald-700 hover:text-emerald-800 hover:bg-emerald-50 border-emerald-200"
              onClick={() => {
                setPendingStageId(null);
                setReason("");
                setShowWonDialog(true);
              }}
              disabled={isPending || isWon}
            >
              {markWonMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <CheckCircle2 className="h-4 w-4 mr-1.5" />
              )}
              {isWon ? "Won" : "Mark Won"}
            </Button>

            <Button
              variant="outline"
              size="sm"
              className="text-rose-700 hover:text-rose-800 hover:bg-rose-50 border-rose-200"
              onClick={() => {
                setPendingStageId(null);
                setReason("");
                setShowLostDialog(true);
              }}
              disabled={isPending || isLost}
            >
              {markLostMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <XCircle className="h-4 w-4 mr-1.5" />
              )}
              {isLost ? "Lost" : "Mark Lost"}
            </Button>
          </div>
        </div>
      </CardContent>

      {/* Won Reason Dialog */}
      <Dialog open={showWonDialog} onOpenChange={setShowWonDialog}>
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
            <Button variant="outline" onClick={() => { setShowWonDialog(false); setPendingStageId(null); }}>
              Cancel
            </Button>
            <Button onClick={handleConfirmWon} disabled={isPending}>
              Confirm Won
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Lost Reason Dialog */}
      <Dialog open={showLostDialog} onOpenChange={setShowLostDialog}>
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
            <Button variant="outline" onClick={() => { setShowLostDialog(false); setPendingStageId(null); }}>
              Cancel
            </Button>
            <Button onClick={handleConfirmLost} disabled={isPending || !reason.trim()}>
              Confirm Lost
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
