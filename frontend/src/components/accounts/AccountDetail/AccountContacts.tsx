"use client";

import Link from "next/link";
import { useAccountContacts } from "@/lib/hooks/accounts";
import { useUserLookup } from "@/lib/hooks/useUserLookup";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

interface AccountContactsProps {
  accountId: string;
}

export function AccountContacts({ accountId }: AccountContactsProps) {
  const { data: contactsResult, isLoading } = useAccountContacts(accountId, { page: 0, size: 20 });
  const contacts = contactsResult?.data ?? [];
  const { resolveUserName } = useUserLookup();

  if (isLoading) {
    return <div className="text-sm text-muted-foreground">Loading contacts…</div>;
  }

  if (contacts.length === 0) {
    return <div className="text-sm text-muted-foreground">No contacts found for this account.</div>;
  }

  return (
    <div className="overflow-hidden rounded-lg border">
      <div className="flex items-center justify-between border-b bg-muted px-6 py-3">
        <p className="text-sm font-medium text-muted-foreground">Contacts</p>
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Email</TableHead>
            <TableHead>Phone</TableHead>
            <TableHead>Title</TableHead>
            <TableHead>Owner</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {contacts.map((contact) => (
            <TableRow key={contact.id}>
              <TableCell className="font-medium">
                <Link href={`/contacts/${contact.id}`} className="text-primary hover:underline">
                  {contact.firstName || "Unnamed"} {contact.lastName || ""}
                </Link>
              </TableCell>
              <TableCell>{contact.email || "—"}</TableCell>
              <TableCell>{contact.phone || "—"}</TableCell>
              <TableCell>{contact.title || "—"}</TableCell>
              <TableCell>{contact.ownerUserId ? resolveUserName(contact.ownerUserId) : "—"}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
