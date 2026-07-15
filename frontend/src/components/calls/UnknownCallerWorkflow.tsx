"use client";

import React from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useLeads, useLeadStatuses } from "@/lib/hooks/leads";
import { useSearchContacts, useCreateContact } from "@/lib/hooks/contacts";
import { useCreateLead } from "@/lib/hooks/leads";
import { useLinkCallEntity } from "@/lib/hooks/calls";
import { accountApi } from "@/lib/api/accounts";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import type { LeadResponse } from "@/types/leads";
import type { ContactResponse } from "@/types/contacts";
import type { AccountResponse } from "@/types/accounts";
import { toast } from "sonner";
import type { LeadCreateRequest } from "@/types/leads";
import type { ContactCreateRequest } from "@/types/contacts";
import type { CallLinkRequest } from "@/types/calls";
import { usePermissions } from "@/lib/hooks/usePermissions";

interface UnknownCallerWorkflowProps {
  callId: string;
  phone?: string | null;
}

export function UnknownCallerWorkflow({ callId, phone }: UnknownCallerWorkflowProps) {
  const searchParams = useSearchParams();
  const [open, setOpen] = React.useState<boolean>(() => searchParams?.get("openUnknown") === "1");
  const [query, setQuery] = React.useState<string>(phone ?? "");

  const leadsQuery = useLeads({ search: query });
  const contactsQuery = useSearchContacts(query);
  const leadStatusesQuery = useLeadStatuses();
  const accountsQuery = useQuery({ queryKey: ["accounts", "search", query], queryFn: () => accountApi.searchAccounts(query), enabled: query.trim().length > 0 });
  const [selectedAccountId, setSelectedAccountId] = React.useState<string>("");

  const createLead = useCreateLead();
  const createContact = useCreateContact();
  const linkCallEntity = useLinkCallEntity();
  const { canEditCalls } = usePermissions();

  async function handleLink(entityType: "LEAD" | "CONTACT" | "ACCOUNT" | "DEAL", entityId: string) {
    try {
      const payload: CallLinkRequest = { entityType, entityId };
      await linkCallEntity.mutateAsync({ id: callId, request: payload });
      setOpen(false);
      toast.success("Call linked successfully");
    } catch (err) {
      console.error(err);
      toast.error("Failed to link call");
    }
  }

  async function handleCreateLead(data: { firstName: string; lastName?: string; email?: string }) {
    try {
      const defaultStatusId = leadStatusesQuery.data?.find((status) => status.isDefault)?.id ?? leadStatusesQuery.data?.[0]?.id;
      if (!defaultStatusId) {
        toast.error("Cannot create lead because no lead status is available");
        return;
      }

      const payload: LeadCreateRequest = {
        firstName: data.firstName,
        lastName: data.lastName || undefined,
        email: data.email || undefined,
        phone: phone ?? undefined,
        statusId: defaultStatusId,
      };

      const lead = await createLead.mutateAsync(payload);
      if (lead && lead.id) {
        const payloadLink: CallLinkRequest = { entityType: "LEAD", entityId: lead.id };
        await linkCallEntity.mutateAsync({ id: callId, request: payloadLink });
        toast.success("Lead created and linked");
        setOpen(false);
      }
    } catch (err) {
      console.error(err);
      toast.error("Failed to create and link lead");
    }
  }

  async function handleCreateContact(data: { firstName: string; lastName?: string; email?: string }) {
    try {
      if (!selectedAccountId) {
        toast.error("Select an account before creating a contact");
        return;
      }

      const payload: ContactCreateRequest = {
        accountId: selectedAccountId,
        firstName: data.firstName || undefined,
        lastName: data.lastName || undefined,
        email: data.email || undefined,
        phone: phone ?? undefined,
      };

      const contact = await createContact.mutateAsync(payload);
      if (contact && contact.id) {
        const payloadLink: CallLinkRequest = { entityType: "CONTACT", entityId: contact.id };
        await linkCallEntity.mutateAsync({ id: callId, request: payloadLink });
        toast.success("Contact created and linked");
        setOpen(false);
      }
    } catch (err) {
      console.error(err);
      toast.error("Failed to create and link contact");
    }
  }

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>Unknown caller</CardTitle>
          <CardDescription>Phone: {phone ?? "Unknown"}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setOpen(true)} disabled={!canEditCalls}>Search CRM / Create</Button>
          </div>
        </CardContent>
      </Card>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Resolve unknown caller</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            <div className="flex gap-2">
              <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search phone or name" />
              <Button onClick={() => { /* trigger queries via state */ }}>Search</Button>
            </div>

            <div>
              <h4 className="text-sm font-medium">Leads</h4>
              <div className="space-y-2">
                {leadsQuery.data?.data?.map((l: LeadResponse) => (
                  <div key={l.id} className="flex items-center justify-between">
                    <div>
                      <div className="text-sm font-medium">{l.firstName} {l.lastName}</div>
                      <div className="text-sm text-muted-foreground">{l.phone} {l.email ? `· ${l.email}` : ''}</div>
                    </div>
                    <div>
                      <Button variant="ghost" onClick={() => handleLink("LEAD", l.id)} disabled={!canEditCalls}>Link to call</Button>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div>
              <h4 className="text-sm font-medium">Contacts</h4>
              <div className="space-y-2">
                {contactsQuery.data?.map((c: ContactResponse) => (
                  <div key={c.id} className="flex items-center justify-between">
                    <div>
                      <div className="text-sm font-medium">{c.firstName} {c.lastName}</div>
                      <div className="text-sm text-muted-foreground">{c.phone} {c.email ? `· ${c.email}` : ''}</div>
                    </div>
                    <div>
                      <Button variant="ghost" onClick={() => handleLink("CONTACT", c.id)} disabled={!canEditCalls}>Link to call</Button>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div>
              <h4 className="text-sm font-medium">Accounts</h4>
              <div className="space-y-2">
                {accountsQuery.data?.map((a: AccountResponse) => (
                  <div key={a.id} className="flex items-center justify-between rounded border p-3">
                    <div>
                      <div className="text-sm font-medium">{a.name}</div>
                      <div className="text-sm text-muted-foreground">{a.phone} {a.email ? `· ${a.email}` : ''}</div>
                    </div>
                    <div className="flex gap-2">
                      <Button variant="ghost" onClick={() => handleLink("ACCOUNT", a.id)} disabled={!canEditCalls}>Link to call</Button>
                      <Button variant={selectedAccountId === a.id ? "secondary" : "outline"} onClick={() => setSelectedAccountId(a.id)}>
                        {selectedAccountId === a.id ? "Selected" : "Select"}
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <h4 className="text-sm font-medium">Create Lead</h4>
                <form onSubmit={async (e) => {
                  e.preventDefault();
                  const fd = new FormData(e.currentTarget as HTMLFormElement);
                  await handleCreateLead({
                    firstName: String(fd.get("firstName") || ""),
                    lastName: String(fd.get("lastName") || ""),
                    email: String(fd.get("email") || ""),
                  });
                }}>
                  <div className="space-y-2">
                    <Input name="firstName" placeholder="First name" required />
                    <Input name="lastName" placeholder="Last name" />
                    <Input name="email" placeholder="Email" />
                    <div className="flex justify-end">
                      <Button type="submit" disabled={!canEditCalls}>Create & Link</Button>
                    </div>
                  </div>
                </form>
              </div>

              <div>
                <h4 className="text-sm font-medium">Create Contact</h4>
                <div className="mb-3">
                  <p className="text-sm text-muted-foreground">Select an account before creating a contact.</p>
                  {selectedAccountId ? (
                    <p className="text-sm">Selected account ID: <span className="font-medium">{selectedAccountId}</span></p>
                  ) : (
                    <p className="text-sm text-red-500">No account selected yet.</p>
                  )}
                </div>
                <form onSubmit={async (e) => {
                  e.preventDefault();
                  const fd = new FormData(e.currentTarget as HTMLFormElement);
                  await handleCreateContact({
                    firstName: String(fd.get("firstName") || ""),
                    lastName: String(fd.get("lastName") || ""),
                    email: String(fd.get("email") || ""),
                  });
                }}>
                  <div className="space-y-2">
                    <Input name="firstName" placeholder="First name" required />
                    <Input name="lastName" placeholder="Last name" />
                    <Input name="email" placeholder="Email" />
                    <div className="flex justify-end">
                      <Button type="submit" disabled={!canEditCalls || !selectedAccountId}>Create & Link</Button>
                    </div>
                  </div>
                </form>
              </div>
            </div>
          </div>

          <DialogFooter>
            <div className="flex justify-end">
              <Button variant="outline" onClick={() => setOpen(false)}>Close</Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

export default UnknownCallerWorkflow;
