"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useCreateFeedback } from "@/lib/hooks/feedback";
import { useTickets } from "@/lib/hooks/tickets";
import type { AccountResponse } from "@/types/accounts";
import {
  FEEDBACK_SCORE_RANGES,
  FEEDBACK_TYPE_LABELS,
  type FeedbackType,
} from "@/types/feedback";

const FEEDBACK_TYPES: FeedbackType[] = [
  "CSAT",
  "NPS",
  "CES",
  "GENERAL_FEEDBACK",
  "COMPLAINT",
];

interface LogFeedbackDialogProps {
  account: Pick<AccountResponse, "id" | "name" | "email">;
  onSuccess?: () => void;
}

function defaultScore(type: FeedbackType): string {
  const range = FEEDBACK_SCORE_RANGES[type];
  return range ? String(range.min) : "";
}

export function LogFeedbackDialog({ account, onSuccess }: LogFeedbackDialogProps) {
  const [open, setOpen] = useState(false);
  const [type, setType] = useState<FeedbackType>("CSAT");
  const [score, setScore] = useState(defaultScore("CSAT"));
  const [email, setEmail] = useState(account.email ?? "");
  const [comment, setComment] = useState("");
  const [ticketId, setTicketId] = useState("none");

  const createFeedback = useCreateFeedback();
  const tickets = useTickets({ accountId: account.id, page: 0, size: 100 }, open);
  const scoreRange = FEEDBACK_SCORE_RANGES[type];
  const savedTickets = tickets.data?.data ?? [];

  const handleTypeChange = (value: string) => {
    const next = value as FeedbackType;
    setType(next);
    setScore(defaultScore(next));
  };

  const handleOpenChange = (nextOpen: boolean) => {
    if (nextOpen) {
      setEmail(account.email ?? "");
    }
    setOpen(nextOpen);
  };

  const handleSubmit = async () => {
    const trimmedEmail = email.trim();
    if (!trimmedEmail || (scoreRange && score === "")) {
      return;
    }

    try {
      await createFeedback.mutateAsync({
        email: trimmedEmail,
        type,
        score: scoreRange ? Number(score) : undefined,
        comment: comment.trim() || undefined,
        source: "INTERNAL",
        expectedAccountId: account.id,
        ticketId: ticketId === "none" ? undefined : ticketId,
      });
    } catch {
      return;
    }

    setComment("");
    setScore(defaultScore(type));
    setOpen(false);
    onSuccess?.();
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <Button size="sm" onClick={() => setOpen(true)}>
        Log feedback
      </Button>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Log feedback</DialogTitle>
          <DialogDescription>
            Record feedback for {account.name}. The email must belong to this account; it is
            resolved on the server and is not stored.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="feedback-type">Type</Label>
            <Select value={type} onValueChange={handleTypeChange}>
              <SelectTrigger id="feedback-type" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {FEEDBACK_TYPES.map((option) => (
                  <SelectItem key={option} value={option}>
                    {FEEDBACK_TYPE_LABELS[option]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {scoreRange && (
            <div className="space-y-2">
              <Label htmlFor="feedback-score">Score</Label>
              <Select value={score} onValueChange={setScore}>
                <SelectTrigger id="feedback-score" className="w-full">
                  <SelectValue placeholder="Select score" />
                </SelectTrigger>
                <SelectContent>
                  {Array.from(
                    { length: scoreRange.max - scoreRange.min + 1 },
                    (_, index) => scoreRange.min + index
                  ).map((value) => (
                    <SelectItem key={value} value={String(value)}>
                      {value}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">{scoreRange.label}</p>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="feedback-email">Customer email</Label>
            <Input
              id="feedback-email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="customer@example.com"
              required
            />
            <p className="text-xs text-muted-foreground">
              Must resolve to {account.name}; a mismatch is rejected.
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="feedback-ticket">Related ticket (optional)</Label>
            <Select value={ticketId} onValueChange={setTicketId}>
              <SelectTrigger id="feedback-ticket" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none">No ticket</SelectItem>
                {savedTickets.map((ticket) => (
                  <SelectItem key={ticket.id} value={ticket.id}>
                    {ticket.subject}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="feedback-comment">Comment (optional)</Label>
            <Textarea
              id="feedback-comment"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              maxLength={4000}
              rows={3}
              placeholder="What did the customer say?"
            />
          </div>
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={createFeedback.isPending}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={
              createFeedback.isPending ||
              !email.trim() ||
              (Boolean(scoreRange) && score === "")
            }
          >
            {createFeedback.isPending ? "Saving..." : "Save feedback"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
