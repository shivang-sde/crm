"use client";

import Link from "next/link";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ContactResponse } from "@/types/contacts";
import { ClickToCallButton } from "@/components/call-opening/ClickToCallButton";

interface ContactTableProps {
  contacts: ContactResponse[];
}

export function ContactTable({ contacts }: ContactTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Email</TableHead>
          <TableHead>Phone</TableHead>
          <TableHead>Account</TableHead>
          <TableHead>Actions</TableHead>
          <TableHead>Created</TableHead>
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
            <TableCell>{contact.accountId}</TableCell>
            <TableCell>
              {contact.phone ? (
                <ClickToCallButton
                  entityType="contact"
                  entityId={contact.id}
                  phoneNumber={contact.phone}
                  label="Call"
                  variant="ghost"
                  size="icon"
                />
              ) : (
                <span className="text-muted-foreground">—</span>
              )}
            </TableCell>
            <TableCell className="text-sm text-muted-foreground">
              {new Date(contact.createdAt).toLocaleDateString()}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
