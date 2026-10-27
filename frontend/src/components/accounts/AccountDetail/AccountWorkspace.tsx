"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useState } from "react";

import { AccountHealthPanel } from "@/components/customer-health/AccountHealthPanel";
import { Customer360SectionCard } from "./Customer360SectionCard";

import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

import { apiErrorMessage } from "@/lib/api/api-utils";
import { useCustomer360 } from "@/lib/hooks/accounts";
import { useConvertComplaintToTicket } from "@/lib/hooks/feedback";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { useUserLookup } from "@/lib/hooks/useUserLookup";
import { LogFeedbackDialog } from "@/components/feedback/LogFeedbackDialog";
import { FeedbackTable } from "@/components/feedback/FeedbackTable";

import { formatDateTime, formatLabel } from "@/lib/utils";

import type { AccountResponse } from "@/types/accounts";
import type { FeedbackResponse } from "@/types/feedback";
import { Customer360TimelineEntityType } from "@/types/accounts";
// Reuse the L3.1 Ticket presentation rather than re-deriving status and SLA styling.
import { TicketSlaBadge } from "@/app/(tenant)/tickets/_components/TicketSla";
import { TicketStatusBadge } from "@/app/(tenant)/tickets/_components/TicketStatusControl";

/**
 * L3.2-P4: the Customer 360 workspace.
 *
 * <p>One request backs the whole workspace. The backend deliberately exposes a single
 * `/accounts/{id}/360` endpoint (L3.2-P1/P2/P3) that composes the Account read with
 * four domain reads plus the timeline, each with its own authorization. Splitting it
 * into per-section calls in the frontend would duplicate that gate and lose the
 * consistent snapshot, so pagination is expressed as query parameters instead.
 *
 * <p>The consequence is worth stating plainly: because there is one endpoint, paging
 * the timeline re-requests the other sections too. That is the backend's contract, not
 * a frontend shortcut. Page state is still per-section — five separate values in the
 * query key — so moving the timeline never moves the contacts page.
 *
 * <p>Authorization stays where it belongs. The endpoint is gated on `account:read`
 * (asserted by the route's `ProtectedRoute`), and each section shows exactly what the
 * backend scoped to the caller. Under OWN or TEAM scope a record can be absent from
 * both its section and the timeline; `total` reflects what is visible, which is why an
 * empty section is not evidence that nothing exists.
 */
const SECTION_PAGE_SIZE = 20;

const TIMELINE_ENTITY_STYLES: Record<Customer360TimelineEntityType, string> = {
  ACCOUNT: "bg-slate-100 text-slate-800",
  CONTACT: "bg-cyan-100 text-cyan-800",
  DEAL: "bg-emerald-100 text-emerald-800",
  TICKET: "bg-blue-100 text-blue-800",
  ENTITLEMENT: "bg-violet-100 text-violet-800",
};

interface AccountWorkspaceProps {
  account: AccountResponse;
  /** Overview-only panels that have no Customer 360 equivalent and must be preserved. */
  overviewPanels?: ReactNode;
}

export function AccountWorkspace({ account, overviewPanels }: AccountWorkspaceProps) {
  // Independent page state per section. Never a single shared page number.
  const [contactsPage, setContactsPage] = useState(0);
  const [dealsPage, setDealsPage] = useState(0);
  const [entitlementsPage, setEntitlementsPage] = useState(0);
  const [ticketsPage, setTicketsPage] = useState(0);
  const [feedbackPage, setFeedbackPage] = useState(0);
  const [timelinePage, setTimelinePage] = useState(0);
  const [convertingFeedbackId, setConvertingFeedbackId] = useState<string | null>(null);

  const { data, isLoading, isError, error, refetch } = useCustomer360(account.id, {
    contactsPage,
    contactsSize: SECTION_PAGE_SIZE,
    dealsPage,
    dealsSize: SECTION_PAGE_SIZE,
    entitlementsPage,
    entitlementsSize: SECTION_PAGE_SIZE,
    ticketsPage,
    ticketsSize: SECTION_PAGE_SIZE,
    feedbackPage,
    feedbackSize: SECTION_PAGE_SIZE,
    timelinePage,
    timelineSize: SECTION_PAGE_SIZE,
  });

  const { canViewFeedback, canCreateFeedback, canEditTickets } = usePermissions();
  const { resolveUserName } = useUserLookup();
  const convertComplaint = useConvertComplaintToTicket();

  const errorMessage = apiErrorMessage(error, "The customer 360 view could not be loaded.");
  const retry = () => {
    void refetch();
  };

  const handleConvertComplaint = async (feedback: FeedbackResponse) => {
    setConvertingFeedbackId(feedback.id);
    try {
      await convertComplaint.mutateAsync({
        feedbackId: feedback.id,
        accountId: account.id,
      });
    } catch {
      // The mutation reports the backend message through a toast.
    } finally {
      setConvertingFeedbackId(null);
    }
  };

  return (
    <Tabs defaultValue="overview" className="space-y-4">
      <TabsList>
        <TabsTrigger value="overview">Overview</TabsTrigger>
        <TabsTrigger value="contacts">Contacts</TabsTrigger>
        <TabsTrigger value="deals">Deals</TabsTrigger>
        <TabsTrigger value="entitlements">Entitlements</TabsTrigger>
        <TabsTrigger value="tickets">Tickets</TabsTrigger>
        {canViewFeedback && <TabsTrigger value="feedback">Feedback</TabsTrigger>}
        <TabsTrigger value="health">Health</TabsTrigger>
        <TabsTrigger value="timeline">Timeline</TabsTrigger>
      </TabsList>

      {/* ------------------------------ Overview ------------------------------ */}
      <TabsContent value="overview" className="space-y-4 pt-4">
        <Card>
          <CardHeader>
            <CardTitle className="text-xl">{account.name}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-muted-foreground">
              {account.industry || "No industry specified"}
            </p>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3 lg:grid-cols-5">
              {[
                { label: "Email", value: account.email || "—" },
                { label: "Phone", value: account.phone || "—" },
                { label: "Website", value: account.website || "—" },
                {
                  label: "Annual revenue",
                  value: account.annualRevenue
                    ? `$${account.annualRevenue.toLocaleString()}`
                    : "—",
                },
                { label: "Employees", value: account.employeeCount ?? "—" },
              ].map((field) => (
                <div key={field.label}>
                  <p className="text-sm font-medium text-muted-foreground">{field.label}</p>
                  <p className="break-words text-sm">{field.value}</p>
                </div>
              ))}
            </div>

            <div className="space-y-2">
              <p className="text-sm font-medium text-muted-foreground">Address</p>
              <p className="text-sm">
                {account.addressLine1 || ""}
                {account.city ? `, ${account.city}` : ""}
                {account.state ? `, ${account.state}` : ""}
                {account.postalCode ? ` ${account.postalCode}` : ""}
                {account.country ? `, ${account.country}` : ""}
              </p>
            </div>

            <div className="space-y-2">
              <p className="text-sm font-medium text-muted-foreground">Description</p>
              <p className="text-sm">{account.description || "No description provided."}</p>
            </div>

            <div className="grid gap-3 border-t pt-4 sm:grid-cols-3">
              <div>
                <p className="text-xs uppercase tracking-wide text-muted-foreground">
                  Lead source
                </p>
                <p className="text-sm">
                  {account.leadId ? "Converted from lead" : "Direct account"}
                </p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-muted-foreground">
                  Created at
                </p>
                <p className="text-sm">{formatDateTime(account.createdAt)}</p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-muted-foreground">
                  Updated at
                </p>
                <p className="text-sm">{formatDateTime(account.updatedAt)}</p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Counts come from the same response, so nothing here is a second query. */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Customer 360 summary</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <p className="text-sm text-muted-foreground">Loading customer 360…</p>
            ) : isError ? (
              <p className="text-sm text-destructive">{errorMessage}</p>
            ) : (
              <dl className="grid grid-cols-1 gap-4 sm:grid-cols-5">
                {(
                  [
                    ["Contacts", data?.contacts.total],
                    ["Deals", data?.deals.total],
                    ["Entitlements", data?.entitlements.total],
                    ["Tickets", data?.tickets.total],
                    ["Timeline events", data?.timeline.total],
                  ] as const
                ).map(([label, total]) => (
                  <div key={label}>
                    <dt className="text-xs uppercase tracking-wide text-muted-foreground">
                      {label}
                    </dt>
                    <dd className="text-2xl font-semibold">{total ?? "—"}</dd>
                  </div>
                ))}
              </dl>
            )}
          </CardContent>
        </Card>

        {overviewPanels}
      </TabsContent>

      {/* ------------------------------ Contacts ------------------------------ */}
      <TabsContent value="contacts" className="pt-4">
        <Customer360SectionCard
          title="Contacts"
          section={data?.contacts}
          isLoading={isLoading}
          isError={isError}
          errorMessage={errorMessage}
          onRetry={retry}
          page={contactsPage}
          onPageChange={setContactsPage}
          emptyMessage="No contacts yet."
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead className="hidden sm:table-cell">Phone</TableHead>
                <TableHead className="hidden md:table-cell">Job title</TableHead>
                <TableHead className="hidden lg:table-cell">Owner</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.contacts.items ?? []).map((contact) => (
                <TableRow key={contact.id}>
                  <TableCell className="font-medium">
                    <Link
                      href={`/contacts/${contact.id}`}
                      className="text-primary hover:underline"
                    >
                      {[contact.firstName, contact.lastName].filter(Boolean).join(" ") ||
                        "Unnamed contact"}
                    </Link>
                  </TableCell>
                  <TableCell className="break-all">{contact.email || "—"}</TableCell>
                  <TableCell className="hidden sm:table-cell">{contact.phone || "—"}</TableCell>
                  {/* The backend field is jobTitle; the frontend type also carries a
                      legacy `title` that the API never populates. */}
                  <TableCell className="hidden md:table-cell">
                    {contact.jobTitle || contact.department || "—"}
                  </TableCell>
                  <TableCell className="hidden lg:table-cell">
                    {contact.ownerUserId ? resolveUserName(contact.ownerUserId) : "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Customer360SectionCard>
      </TabsContent>

      {/* -------------------------------- Deals ------------------------------- */}
      <TabsContent value="deals" className="pt-4">
        <Customer360SectionCard
          title="Deals"
          section={data?.deals}
          isLoading={isLoading}
          isError={isError}
          errorMessage={errorMessage}
          onRetry={retry}
          page={dealsPage}
          onPageChange={setDealsPage}
          emptyMessage="No deals yet."
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Deal</TableHead>
                <TableHead>Stage</TableHead>
                <TableHead className="text-right">Amount</TableHead>
                <TableHead className="hidden sm:table-cell">Status</TableHead>
                <TableHead className="hidden lg:table-cell">Close date</TableHead>
                <TableHead className="hidden lg:table-cell">Owner</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.deals.items ?? []).map((deal) => (
                <TableRow key={deal.id}>
                  <TableCell className="font-medium">
                    <Link
                      href={`/deals/${deal.id}`}
                      className="text-primary hover:underline"
                    >
                      {deal.name}
                    </Link>
                  </TableCell>
                  <TableCell>
                    {/* Same stage-colour fallback as DealCard/DealColumn. */}
                    <Badge
                      variant="secondary"
                      style={{ backgroundColor: deal.stage?.color || "#6366f1" }}
                    >
                      {deal.stage?.name || "—"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    {deal.amount != null ? deal.amount.toLocaleString() : "—"}
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    {deal.isWon ? (
                      <Badge variant="secondary" className="bg-emerald-100 text-emerald-800">
                        Won
                      </Badge>
                    ) : deal.isLost ? (
                      <Badge variant="secondary" className="bg-red-100 text-red-800">
                        Lost
                      </Badge>
                    ) : (
                      <Badge variant="secondary">Open</Badge>
                    )}
                  </TableCell>
                  <TableCell className="hidden lg:table-cell">
                    {deal.expectedCloseDate || "—"}
                  </TableCell>
                  <TableCell className="hidden lg:table-cell">
                    {deal.ownerUserId ? resolveUserName(deal.ownerUserId) : "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Customer360SectionCard>
      </TabsContent>

      {/* ----------------------------- Entitlements --------------------------- */}
      <TabsContent value="entitlements" className="pt-4">
        <Customer360SectionCard
          title="Entitlements"
          section={data?.entitlements}
          isLoading={isLoading}
          isError={isError}
          errorMessage={errorMessage}
          onRetry={retry}
          page={entitlementsPage}
          onPageChange={setEntitlementsPage}
          emptyMessage="No entitlements yet."
        >
          {/* An entitlement is the customer's contractual/service right. It is not a
              physical asset, so serial numbers, condition and location do not apply. */}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Entitlement</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="hidden sm:table-cell">Start</TableHead>
                <TableHead className="hidden sm:table-cell">End</TableHead>
                <TableHead className="hidden md:table-cell text-right">Quantity</TableHead>
                <TableHead className="hidden lg:table-cell">Owner</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.entitlements.items ?? []).map((entitlement) => (
                <TableRow key={entitlement.id}>
                  <TableCell className="font-medium">
                    <Link
                      href={`/entitlements/${entitlement.id}`}
                      className="text-primary hover:underline"
                    >
                      {entitlement.name || entitlement.code || "Untitled entitlement"}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <Badge variant="secondary">
                      {entitlement.status ? formatLabel(entitlement.status) : "Unknown"}
                    </Badge>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    {entitlement.start_date || "—"}
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    {entitlement.end_date || "—"}
                  </TableCell>
                  <TableCell className="hidden md:table-cell text-right">
                    {entitlement.quantity ?? "—"}
                  </TableCell>
                  <TableCell className="hidden lg:table-cell">
                    {entitlement.owner_user_id
                      ? resolveUserName(entitlement.owner_user_id)
                      : "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Customer360SectionCard>
      </TabsContent>

      {/* ------------------------------- Tickets ------------------------------ */}
      <TabsContent value="tickets" className="pt-4">
        <Customer360SectionCard
          title="Tickets"
          section={data?.tickets}
          isLoading={isLoading}
          isError={isError}
          errorMessage={errorMessage}
          onRetry={retry}
          page={ticketsPage}
          onPageChange={setTicketsPage}
          emptyMessage="No tickets yet."
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Subject</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>SLA</TableHead>
                <TableHead className="hidden sm:table-cell">Owner</TableHead>
                <TableHead className="hidden lg:table-cell">Updated</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.tickets.items ?? []).map((ticket) => (
                <TableRow key={ticket.id}>
                  <TableCell className="font-medium">
                    <Link
                      href={`/tickets/${ticket.id}`}
                      className="text-primary hover:underline"
                    >
                      {ticket.subject}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <TicketStatusBadge status={ticket.status} />
                  </TableCell>
                  <TableCell>
                    <TicketSlaBadge ticket={ticket} />
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    {ticket.ownerId ? resolveUserName(ticket.ownerId) : "—"}
                  </TableCell>
                  <TableCell className="hidden lg:table-cell">
                    {formatDateTime(ticket.updatedAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Customer360SectionCard>
      </TabsContent>

      {/* ------------------------------- Feedback ------------------------------ */}
      {canViewFeedback && (
        <TabsContent value="feedback" className="space-y-3 pt-4">
          {canCreateFeedback && (
            <div className="flex justify-end">
              <LogFeedbackDialog account={account} onSuccess={() => void refetch()} />
            </div>
          )}
          <Customer360SectionCard
            title="Feedback"
            section={data?.feedback}
            isLoading={isLoading}
            isError={isError}
            errorMessage={errorMessage}
            onRetry={retry}
            page={feedbackPage}
            onPageChange={setFeedbackPage}
            emptyMessage="No feedback yet."
          >
            <FeedbackTable
              items={data?.feedback.items ?? []}
              canConvertComplaints={canCreateFeedback && canEditTickets}
              convertingFeedbackId={convertingFeedbackId}
              onConvertComplaint={(feedback) => void handleConvertComplaint(feedback)}
            />
          </Customer360SectionCard>
        </TabsContent>
      )}

      {/* -------------------------------- Health ------------------------------- */}
      <TabsContent value="health" className="pt-4">
        <AccountHealthPanel accountId={account.id} />
      </TabsContent>

      {/* ------------------------------- Timeline ------------------------------ */}
      <TabsContent value="timeline" className="pt-4">
        <Customer360SectionCard
          title="Timeline"
          section={data?.timeline}
          isLoading={isLoading}
          isError={isError}
          errorMessage={errorMessage}
          onRetry={retry}
          page={timelinePage}
          onPageChange={setTimelinePage}
          emptyMessage="No activity yet."
        >
          {/* Newest-first, as ordered by the backend. Not the generic activity feed:
              this is the account-scoped Customer 360 timeline, which only admits an
              activity when the record it belongs to is visible to the caller. */}
          <ul className="divide-y">
            {(data?.timeline.items ?? []).map((activity) => {
              const entityType = activity.entityType as Customer360TimelineEntityType;
              const isSystem = activity.actorType === "SYSTEM";

              return (
                <li key={activity.id} className="px-4 py-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge
                      variant="secondary"
                      className={TIMELINE_ENTITY_STYLES[entityType] ?? "bg-gray-100 text-gray-800"}
                    >
                      {formatLabel(entityType)}
                    </Badge>
                    <Badge variant="outline">{formatLabel(activity.activityType)}</Badge>
                    <span className="text-xs text-muted-foreground">
                      {formatDateTime(activity.createdAt)}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {/* performedBy is null for SYSTEM entries, so actorType decides.
                          Resolving a user here would show a misleading "Unknown User". */}
                      {isSystem ? "System" : resolveUserName(activity.performedBy)}
                    </span>
                    {activity.actorSource && !isSystem && (
                      <span className="text-xs text-muted-foreground">
                        via {activity.actorSource}
                      </span>
                    )}
                  </div>
                  {activity.description && (
                    <p className="mt-1 text-sm">{activity.description}</p>
                  )}
                </li>
              );
            })}
          </ul>
        </Customer360SectionCard>
      </TabsContent>
    </Tabs>
  );
}
