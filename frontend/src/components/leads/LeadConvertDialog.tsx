"use client";

import { type ComponentProps, useMemo, useState } from "react";
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
import { useConvertLead } from "@/lib/hooks/leads";
import { useAccounts } from "@/lib/hooks/accounts";
import { useContacts } from "@/lib/hooks/contacts";
import { LeadConvertRequest, LeadResponse } from "@/types/leads";
import { AccountResponse } from "@/types/accounts";
import { ContactResponse } from "@/types/contacts";

interface LeadConvertDialogProps {
  lead: LeadResponse;
  triggerLabel?: string;
  triggerVariant?: ComponentProps<typeof Button>["variant"];
  triggerClassName?: string;
  onSuccess?: () => void;
}

export function LeadConvertDialog({
  lead,
  triggerLabel = "Convert",
  triggerVariant = "outline",
  triggerClassName = "",
  onSuccess,
}: LeadConvertDialogProps) {
  const [open, setOpen] = useState(false);
  const [accountQuery, setAccountQuery] = useState("");
  const [contactQuery, setContactQuery] = useState("");
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);
  const [selectedContactId, setSelectedContactId] = useState<string | null>(null);

  const convertMutation = useConvertLead();
  const accountsResult = useAccounts({ page: 0, size: 100 });
  const contactsResult = useContacts({ page: 0, size: 100 });

  const accountResults = useMemo(() => {
    const accounts = accountsResult.data?.data || [];
    const query = accountQuery.trim().toLowerCase();
    if (!query) {
      return accounts;
    }
    return accounts.filter((account) =>
      [account.name, account.email, account.phone]
        .filter(Boolean)
        .some((field) => field?.toLowerCase().includes(query))
    );
  }, [accountsResult.data, accountQuery]);

  const filteredContactResults = useMemo(() => {
    const contacts = contactsResult.data?.data || [];
    const query = contactQuery.trim().toLowerCase();

    let results = contacts;
    if (selectedAccountId) {
      results = results.filter((contact) => contact.accountId === selectedAccountId);
    }

    if (!query) {
      return results;
    }

    return results.filter((contact) =>
      [contact.firstName, contact.lastName, contact.email, contact.phone]
        .filter(Boolean)
        .some((field) => field?.toLowerCase().includes(query))
    );
  }, [contactsResult.data, contactQuery, selectedAccountId]);

  const selectedAccount = useMemo(
    () => accountResults.find((account) => account.id === selectedAccountId) || null,
    [accountResults, selectedAccountId]
  );

  const selectedContact = useMemo(
    () =>
      (contactsResult.data?.data || []).find((contact) => contact.id === selectedContactId) || null,
    [contactsResult.data, selectedContactId]
  );

  const isSubmitting = convertMutation.isPending;
  const isAccountSearchEmpty = accountQuery.trim().length === 0;
  const isContactSearchEmpty = contactQuery.trim().length === 0;

  const handleConvert = async () => {
    const payload: LeadConvertRequest = {
      accountId: selectedAccountId ?? undefined,
      contactId: selectedContactId ?? undefined,
    };

    await convertMutation.mutateAsync({ id: lead.id, payload });
    setOpen(false);
    setAccountQuery("");
    setContactQuery("");
    setSelectedAccountId(null);
    setSelectedContactId(null);
    onSuccess?.();
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <Button
        variant={triggerVariant}
        size="sm"
        className={triggerClassName}
        onClick={() => setOpen(true)}
      >
        {triggerLabel}
      </Button>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Convert lead to account/contact</DialogTitle>
          <DialogDescription>
            Select an existing account or contact to reuse, or leave the fields blank to create new records from the lead.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          <div className="space-y-3">
            <div className="flex items-center justify-between gap-2">
              <Label htmlFor="lead-convert-account">Existing account</Label>
              {selectedAccountId && (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  onClick={() => setSelectedAccountId(null)}
                >
                  Clear
                </Button>
              )}
            </div>
            <Input
              id="lead-convert-account"
              placeholder="Search accounts by name, email or phone"
              value={accountQuery}
              onChange={(event) => setAccountQuery(event.target.value)}
              disabled={isSubmitting}
            />
            {selectedAccount ? (
              <div className="rounded-md border border-primary/20 bg-primary/5 p-3 text-sm text-foreground">
                Selected account: <strong>{selectedAccount.name}</strong>
              </div>
            ) : null}
            <div className="space-y-2">
              {isAccountSearchEmpty && accountsResult.isLoading ? (
                <p className="text-muted-foreground text-sm">Loading accounts...</p>
              ) : accountsResult.isError ? (
                <p className="text-destructive text-sm">Unable to fetch accounts.</p>
              ) : accountResults.length ? (
                accountResults.map((account: AccountResponse) => (
                  <button
                    type="button"
                    key={account.id}
                    className={`flex w-full items-center justify-between rounded-lg border px-3 py-2 text-left text-sm transition hover:border-primary/70 ${
                      selectedAccountId === account.id
                        ? "border-primary bg-primary/10"
                        : "border-border bg-transparent"
                    }`}
                    onClick={() => {
                      setSelectedAccountId(account.id);
                      if (selectedContactId && selectedContact?.accountId !== account.id) {
                        setSelectedContactId(null);
                      }
                    }}
                  >
                    <span>{account.name}</span>
                    <span className="text-muted-foreground text-xs">
                      {account.email || account.phone || "Account"}
                    </span>
                  </button>
                ))
              ) : (
                <p className="text-muted-foreground text-sm">
                  No accessible accounts were found. Your role may not have access to existing accounts.
                </p>
              )}
            </div>
          </div>

          <div className="space-y-3">
            <div className="flex items-center justify-between gap-2">
              <Label htmlFor="lead-convert-contact">Existing contact</Label>
              {selectedContactId && (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  onClick={() => setSelectedContactId(null)}
                >
                  Clear
                </Button>
              )}
            </div>
            <Input
              id="lead-convert-contact"
              placeholder="Search contacts by name, email, or phone"
              value={contactQuery}
              onChange={(event) => setContactQuery(event.target.value)}
              disabled={isSubmitting}
            />
            {selectedContact ? (
              <div className="rounded-md border border-primary/20 bg-primary/5 p-3 text-sm text-foreground">
                Selected contact: <strong>{selectedContact.firstName} {selectedContact.lastName}</strong>
              </div>
            ) : null}
            <div className="space-y-2">
              {isContactSearchEmpty && contactsResult.isLoading ? (
                <p className="text-muted-foreground text-sm">Loading contacts...</p>
              ) : contactsResult.isError ? (
                <p className="text-destructive text-sm">Unable to fetch contacts.</p>
              ) : filteredContactResults.length ? (
                filteredContactResults.map((contact: ContactResponse) => (
                  <button
                    type="button"
                    key={contact.id}
                    className={`flex w-full items-center justify-between rounded-lg border px-3 py-2 text-left text-sm transition hover:border-primary/70 ${
                      selectedContactId === contact.id
                        ? "border-primary bg-primary/10"
                        : "border-border bg-transparent"
                    }`}
                    onClick={() => setSelectedContactId(contact.id)}
                  >
                    <span>{contact.firstName} {contact.lastName}</span>
                    <span className="text-muted-foreground text-xs">
                      {contact.email || contact.phone || "Contact"}
                    </span>
                  </button>
                ))
              ) : (
                <p className="text-muted-foreground text-sm">
                  No accessible contacts were found. Your role may not have access to existing contacts.
                </p>
              )}
            </div>
          </div>

          <div className="rounded-lg border border-muted bg-muted/5 p-3 text-sm text-muted-foreground">
            When you convert the lead, existing selected records will be reused. If no account or contact is chosen, new records will be created from the lead information.
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" size="sm" onClick={() => setOpen(false)} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button size="sm" onClick={handleConvert} disabled={isSubmitting}>
            {isSubmitting ? "Converting..." : "Convert lead"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
