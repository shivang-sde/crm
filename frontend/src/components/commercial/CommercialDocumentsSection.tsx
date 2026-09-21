"use client";

import { useState } from "react";
import { ExternalLink, FileText, FileWarning, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useCommercialDocuments } from "@/lib/hooks/commercial-integration";
import { CommercialDocumentListParams } from "@/lib/api/commercial-integration";

type Filter = "ALL" | "QUOTATION" | "INVOICE";

interface Props {
  accountId?: string;
  contactId?: string;
}

export function CommercialDocumentsSection({ accountId, contactId }: Props) {
  const [filter, setFilter] = useState<Filter>("ALL");
  const [page, setPage] = useState(0);
  const size = 10;

  const params: CommercialDocumentListParams = {
    accountId,
    contactId,
    page,
    size,
    ...(filter !== "ALL" ? { externalType: filter } : {}),
  };

  const { data, isLoading, isError, refetch } = useCommercialDocuments(params);
  const docs = data?.data ?? [];
  const meta = data?.meta;

  const onFilter = (f: Filter) => {
    setFilter(f);
    setPage(0);
  };

  let content;
  if (isLoading) {
    content = (
      <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading commercial documents...
      </div>
    );
  } else if (isError) {
    content = (
      <div className="py-6 text-center">
        <p className="text-sm text-destructive">Unable to load commercial documents.</p>
        <Button variant="outline" size="sm" className="mt-2" onClick={() => refetch()}>
          Retry
        </Button>
      </div>
    );
  } else if (docs.length === 0) {
    content = (
      <div className="py-8 text-center">
        <FileWarning className="mx-auto h-8 w-8 text-muted-foreground/50" />
        <p className="mt-2 text-sm text-muted-foreground">No commercial documents found.</p>
        <p className="mt-1 text-xs text-muted-foreground">
          Quotations and invoices synchronized from the connected commercial platform will appear here.
        </p>
      </div>
    );
  } else {
    content = (
      <>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Type</TableHead>
              <TableHead>External ID</TableHead>
              <TableHead>Updated</TableHead>
              <TableHead>PDF</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {docs.map((d) => (
              <TableRow key={d.id}>
                <TableCell className="capitalize">
                  <span className="inline-flex items-center gap-1">
                    <FileText className="h-3.5 w-3.5 text-muted-foreground" />
                    {d.externalType.toLowerCase()}
                  </span>
                </TableCell>
                <TableCell className="font-mono text-xs max-w-[180px] truncate" title={d.externalId}>
                  {d.externalId}
                </TableCell>
                <TableCell className="text-xs text-muted-foreground">
                  {d.externalUpdatedAt ? new Date(d.externalUpdatedAt).toLocaleDateString() : "—"}
                </TableCell>
                <TableCell>
                  {d.pdfUrl ? (
                    <a
                      href={d.pdfUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
                    >
                      Open PDF <ExternalLink className="h-3 w-3" />
                    </a>
                  ) : (
                    <span className="text-xs text-muted-foreground">PDF unavailable</span>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {meta && meta.totalPages > 1 && (
          <div className="flex items-center justify-between border-t px-4 py-2 text-xs">
            <span className="text-muted-foreground">
              Page {meta.page + 1} of {meta.totalPages} · {meta.total} total
            </span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                Previous
              </Button>
              <Button variant="outline" size="sm" disabled={page + 1 >= meta.totalPages} onClick={() => setPage((p) => p + 1)}>
                Next
              </Button>
            </div>
          </div>
        )}
      </>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border bg-white">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b bg-muted/50 px-4 py-3">
        <p className="text-sm font-medium">Commercial Documents</p>
        <div className="flex gap-1">
          <Button variant={filter === "ALL" ? "default" : "outline"} size="sm" onClick={() => onFilter("ALL")}>
            All
          </Button>
          <Button variant={filter === "QUOTATION" ? "default" : "outline"} size="sm" onClick={() => onFilter("QUOTATION")}>
            Quotations
          </Button>
          <Button variant={filter === "INVOICE" ? "default" : "outline"} size="sm" onClick={() => onFilter("INVOICE")}>
            Invoices
          </Button>
        </div>
      </div>
      <div className="px-2">{content}</div>
    </div>
  );
}
