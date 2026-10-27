"use client";

import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { FeedbackTable } from "@/components/feedback/FeedbackTable";
import { apiErrorMessage } from "@/lib/api/api-utils";
import { useFeedback } from "@/lib/hooks/feedback";

const PAGE_SIZE = 20;

interface TicketFeedbackProps {
  ticketId: string;
}

export function TicketFeedback({ ticketId }: TicketFeedbackProps) {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, error, refetch } = useFeedback({
    ticketId,
    page,
    size: PAGE_SIZE,
  });

  const items = data?.data ?? [];
  const totalPages = data?.meta.totalPages ?? 0;
  const total = data?.meta.total ?? 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Feedback</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {isLoading ? (
          <div className="space-y-2">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : isError ? (
          <div className="space-y-3">
            <p className="text-sm text-destructive">
              {apiErrorMessage(error, "Feedback could not be loaded.")}
            </p>
            <Button variant="outline" size="sm" onClick={() => void refetch()}>
              Retry
            </Button>
          </div>
        ) : items.length === 0 ? (
          <p className="text-sm text-muted-foreground">No feedback linked to this ticket.</p>
        ) : (
          <>
            <FeedbackTable items={items} showTicket={false} showAccount />
            {totalPages > 1 && (
              <div className="flex items-center justify-between gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  <ChevronLeft className="mr-1 h-4 w-4" />
                  Previous
                </Button>
                <span className="text-sm text-muted-foreground">
                  Page {page + 1} of {totalPages} · {total}{" "}
                  {total === 1 ? "record" : "records"}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= totalPages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                  <ChevronRight className="ml-1 h-4 w-4" />
                </Button>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
