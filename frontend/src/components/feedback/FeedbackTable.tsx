import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDateTime } from "@/lib/utils";
import {
  FEEDBACK_TYPE_LABELS,
  feedbackSourceLabel,
  type FeedbackResponse,
} from "@/types/feedback";

interface FeedbackTableProps {
  items: FeedbackResponse[];
  showTicket?: boolean;
  showAccount?: boolean;
  canConvertComplaints?: boolean;
  convertingFeedbackId?: string | null;
  onConvertComplaint?: (feedback: FeedbackResponse) => void;
}

export function FeedbackTable({
  items,
  showTicket = true,
  showAccount = false,
  canConvertComplaints = false,
  convertingFeedbackId = null,
  onConvertComplaint,
}: FeedbackTableProps) {
  const ticketColumnClass = canConvertComplaints ? undefined : "hidden lg:table-cell";

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Type</TableHead>
          <TableHead>Score</TableHead>
          <TableHead>Comment</TableHead>
          <TableHead className="hidden sm:table-cell">Source</TableHead>
          <TableHead className="hidden md:table-cell">Recorded</TableHead>
          {showTicket && <TableHead className={ticketColumnClass}>Ticket</TableHead>}
          {showAccount && <TableHead className="hidden lg:table-cell">Account</TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((feedback) => (
          <TableRow key={feedback.id}>
            <TableCell className="font-medium">
              <Badge variant="secondary">{FEEDBACK_TYPE_LABELS[feedback.type]}</Badge>
            </TableCell>
            <TableCell>{feedback.score ?? "\u2014"}</TableCell>
            <TableCell className="max-w-md whitespace-normal">
              {feedback.comment ? (
                <span className="line-clamp-2">{feedback.comment}</span>
              ) : (
                <span className="text-muted-foreground">No comment</span>
              )}
            </TableCell>
            <TableCell className="hidden sm:table-cell">
              {feedbackSourceLabel(feedback.source)}
            </TableCell>
            <TableCell className="hidden md:table-cell">
              {formatDateTime(feedback.createdAt)}
            </TableCell>
            {showTicket && (
              <TableCell className={ticketColumnClass}>
                {feedback.ticketId ? (
                  <Link
                    href={`/tickets/${feedback.ticketId}`}
                    className="text-primary hover:underline"
                  >
                    View ticket
                  </Link>
                ) : feedback.type === "COMPLAINT" &&
                  canConvertComplaints &&
                  onConvertComplaint ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={convertingFeedbackId === feedback.id}
                    onClick={() => onConvertComplaint(feedback)}
                  >
                    {convertingFeedbackId === feedback.id
                      ? "Creating..."
                      : "Create ticket"}
                  </Button>
                ) : (
                  <span className="text-muted-foreground">&mdash;</span>
                )}
              </TableCell>
            )}
            {showAccount && (
              <TableCell className="hidden lg:table-cell">
                <Link
                  href={`/accounts/${feedback.accountId}`}
                  className="text-primary hover:underline"
                >
                  View account
                </Link>
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
