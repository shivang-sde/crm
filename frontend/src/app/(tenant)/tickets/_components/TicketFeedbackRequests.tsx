"use client";

import { useState } from "react";
import { Copy, Link2, RefreshCw, XCircle } from "lucide-react";
import { toast } from "sonner";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { apiErrorMessage } from "@/lib/api/api-utils";
import {
  useCreateFeedbackRequest,
  useFeedbackRequests,
  useRegenerateFeedbackRequest,
  useRevokeFeedbackRequest,
} from "@/lib/hooks/feedback";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { formatDateTime } from "@/lib/utils";
import {
  FEEDBACK_REQUEST_STATUS_LABELS,
  FEEDBACK_TYPE_LABELS,
  type FeedbackRequestResponse,
  type FeedbackRequestStatus,
  type FeedbackRequestType,
} from "@/types/feedback";

const REQUEST_TYPES: FeedbackRequestType[] = ["CSAT", "NPS", "CES"];

function statusVariant(status: FeedbackRequestStatus) {
  if (status === "SUBMITTED") return "default" as const;
  if (status === "PENDING") return "secondary" as const;
  return "outline" as const;
}

export function TicketFeedbackRequests({ ticketId }: { ticketId: string }) {
  const { canEditTickets, canCreateFeedback } = usePermissions();
  const canManage = canEditTickets && canCreateFeedback;
  const [type, setType] = useState<FeedbackRequestType>("CSAT");
  const [link, setLink] = useState<string | null>(null);

  const { data: requests = [], isLoading, isError, error, refetch } =
    useFeedbackRequests(ticketId);
  const create = useCreateFeedbackRequest(ticketId);
  const revoke = useRevokeFeedbackRequest(ticketId);
  const regenerate = useRegenerateFeedbackRequest(ticketId);

  const pending = requests.find((request) => request.status === "PENDING");

  const showLink = (request: FeedbackRequestResponse) => {
    if (!request.tokenPath) return;
    setLink(`${window.location.origin}${request.tokenPath}`);
  };

  const handleCreate = async () => {
    try {
      showLink(await create.mutateAsync(type));
    } catch {
      // The mutation displays the backend message.
    }
  };

  const handleRegenerate = async (requestId: string) => {
    try {
      showLink(await regenerate.mutateAsync(requestId));
    } catch {
      // The mutation displays the backend message.
    }
  };

  const handleRevoke = async (requestId: string) => {
    if (!confirm("Revoke this feedback link? The customer will no longer be able to submit.")) {
      return;
    }
    try {
      await revoke.mutateAsync(requestId);
      setLink(null);
    } catch {
      // The mutation displays the backend message.
    }
  };

  const copyLink = async () => {
    if (!link) return;
    try {
      await navigator.clipboard.writeText(link);
      toast.success("Feedback link copied");
    } catch (err) {
      toast.error(apiErrorMessage(err, "Could not copy the link"));
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Feedback requests</CardTitle>
        <CardDescription>
          Ask the customer to rate this ticket. Links are single-use and expire after 30 days.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {canManage && (
          <div className="flex flex-wrap items-end gap-2">
            <div className="w-40 space-y-1">
              <label className="text-xs font-medium" htmlFor="feedback-request-type">
                Rating
              </label>
              <Select value={type} onValueChange={(value) => setType(value as FeedbackRequestType)}>
                <SelectTrigger id="feedback-request-type" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {REQUEST_TYPES.map((requestType) => (
                    <SelectItem key={requestType} value={requestType}>
                      {FEEDBACK_TYPE_LABELS[requestType]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Button
              onClick={() => void handleCreate()}
              disabled={Boolean(pending) || create.isPending}
            >
              <Link2 className="mr-2 h-4 w-4" />
              Generate link
            </Button>
            {pending && (
              <p className="pb-1 text-xs text-muted-foreground">
                A link is already pending. Regenerate it to get a new copy.
              </p>
            )}
          </div>
        )}

        {link && (
          <Alert>
            <AlertDescription className="space-y-2">
              <p className="text-xs">
                Copy this single-use link now. The token is stored only as a hash and cannot be
                shown again.
              </p>
              <div className="flex flex-wrap gap-2">
                <code className="min-w-0 flex-1 break-all rounded bg-muted px-2 py-1 text-xs">
                  {link}
                </code>
                <Button size="sm" variant="outline" onClick={() => void copyLink()}>
                  <Copy className="mr-1 h-4 w-4" />
                  Copy link
                </Button>
              </div>
            </AlertDescription>
          </Alert>
        )}

        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : isError ? (
          <div className="space-y-2">
            <p className="text-sm text-destructive">
              {apiErrorMessage(error, "Feedback requests could not be loaded.")}
            </p>
            <Button size="sm" variant="outline" onClick={() => void refetch()}>
              Retry
            </Button>
          </div>
        ) : requests.length === 0 ? (
          <p className="text-sm text-muted-foreground">No feedback request has been sent.</p>
        ) : (
          <div className="divide-y rounded-md border">
            {requests.map((request) => (
              <div
                key={request.id}
                className="flex flex-wrap items-center justify-between gap-2 p-3"
              >
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium">
                      {FEEDBACK_TYPE_LABELS[request.type]}
                    </span>
                    <Badge variant={statusVariant(request.status)}>
                      {FEEDBACK_REQUEST_STATUS_LABELS[request.status]}
                    </Badge>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    Created {formatDateTime(request.createdAt)} | Expires{" "}
                    {formatDateTime(request.expiresAt)}
                    {request.submittedAt
                      ? ` | Submitted ${formatDateTime(request.submittedAt)}`
                      : ""}
                  </p>
                </div>
                {canManage && request.status !== "SUBMITTED" && (
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => void handleRegenerate(request.id)}
                      disabled={regenerate.isPending}
                    >
                      <RefreshCw className="mr-1 h-4 w-4" />
                      Regenerate
                    </Button>
                    {request.status === "PENDING" && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => void handleRevoke(request.id)}
                        disabled={revoke.isPending}
                      >
                        <XCircle className="mr-1 h-4 w-4" />
                        Revoke
                      </Button>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
