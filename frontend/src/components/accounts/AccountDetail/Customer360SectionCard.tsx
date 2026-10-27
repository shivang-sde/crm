"use client";

import type { ReactNode } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import type { Customer360Section } from "@/types/accounts";

interface Customer360SectionCardProps {
  title: string;
  /** The paginated collection, or undefined while the request is in flight. */
  section: Customer360Section<unknown> | undefined;
  isLoading: boolean;
  isError: boolean;
  errorMessage: string;
  onRetry: () => void;
  page: number;
  onPageChange: (page: number) => void;
  emptyMessage: string;
  /**
   * Rendered only when the section loaded and has rows, so callers never have to
   * re-check `items.length` and can never render an empty table shell.
   */
  children: ReactNode;
}

/**
 * L3.2-P4: the shared frame for one Customer 360 tab.
 *
 * <p>Loading, empty, error and pagination are identical across the five sections, so
 * they live here once rather than being restated per tab. The one thing this
 * deliberately does NOT do is hide the pagination metadata: the backend returns it so
 * a caller can tell "page 2 of 5" from "no more records", and collapsing it into an
 * opaque list would reintroduce the problem P2 fixed.
 */
export function Customer360SectionCard({
  title,
  section,
  isLoading,
  isError,
  errorMessage,
  onRetry,
  page,
  onPageChange,
  emptyMessage,
  children,
}: Customer360SectionCardProps) {
  const totalPages = section?.totalPages ?? 0;
  const total = section?.total ?? 0;
  const hasPrevious = page > 0;
  // `hasNext` is the backend's own answer; totalPages is the fallback so the control
  // still works if the server ever omits one.
  const hasNext = section?.hasNext ?? page + 1 < totalPages;

  return (
    <div className="overflow-hidden rounded-lg border">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b bg-muted px-4 py-3">
        <p className="text-sm font-medium text-muted-foreground">{title}</p>
        {!isLoading && !isError && total > 0 && (
          <p className="text-xs text-muted-foreground">
            {total} {total === 1 ? "record" : "records"}
          </p>
        )}
      </div>

      {isLoading ? (
        <div className="space-y-2 p-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : isError ? (
        <div className="space-y-3 p-4">
          <p className="text-sm text-destructive">{errorMessage}</p>
          <Button variant="outline" size="sm" onClick={onRetry}>
            Retry
          </Button>
        </div>
      ) : section && section.items.length > 0 ? (
        <>{children}</>
      ) : (
        <p className="p-4 text-sm text-muted-foreground">{emptyMessage}</p>
      )}

      {!isLoading && !isError && totalPages > 1 && (
        <div className="flex items-center justify-between gap-2 border-t px-4 py-3">
          <Button
            variant="outline"
            size="sm"
            disabled={!hasPrevious}
            onClick={() => onPageChange(Math.max(0, page - 1))}
          >
            <ChevronLeft className="mr-1 h-4 w-4" />
            Previous
          </Button>
          <span className="text-sm text-muted-foreground">
            Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={!hasNext}
            onClick={() => onPageChange(page + 1)}
          >
            Next
            <ChevronRight className="ml-1 h-4 w-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
