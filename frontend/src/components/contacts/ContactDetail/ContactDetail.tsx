"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { useState } from "react";
import { useDeleteContact } from "@/lib/hooks/contacts";
import { useAccount } from "@/lib/hooks/accounts";
import { useUserLookup } from "@/lib/hooks/useUserLookup";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { ContactResponse } from "@/types/contacts";
import { ContactTimeline } from "./ContactTimeline";
import { ContactNotes } from "./ContactNotes";
import { ClickToCallButton } from "@/components/call-opening/ClickToCallButton";
import { EntityCallHistory } from "@/components/calls/EntityCallHistory";
import { ContactEntitlementsSection } from "@/components/entitlements/ContactEntitlementsSection";
import { EntityDealsSection } from "@/components/deals/EntityDealsSection";

interface ContactDetailProps {
  contact: ContactResponse;
}

export function ContactDetail({ contact }: ContactDetailProps) {
  const router = useRouter();
  const { canEditContacts } = usePermissions();
  const deleteMutation = useDeleteContact();
  const [showDelete, setShowDelete] = useState(false);

  const { data: account } = useAccount(contact.accountId);
  const { resolveUserName } = useUserLookup();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/contacts">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to contacts
          </Link>
        </Button>
        <div className="flex gap-2 flex-wrap">
          {contact.phone && (
            <ClickToCallButton
              entityType="contact"
              entityId={contact.id}
              phoneNumber={contact.phone}
              label="Call contact"
              variant="outline"
              size="sm"
            />
          )}
          {canEditContacts && (
            <Button variant="outline" size="sm" asChild>
              <Link href={`/contacts/${contact.id}/edit`}>
                <Pencil className="mr-2 h-4 w-4" />
                Edit
              </Link>
            </Button>
          )}
          {canEditContacts && (
            <Button
              variant="outline"
              size="sm"
              className="text-destructive"
              onClick={() => setShowDelete(true)}
            >
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2 space-y-4 rounded-lg border bg-white p-6">
          <div>
            <h2 className="text-xl font-semibold">
              {contact.firstName || "Unnamed"} {contact.lastName || ""}
            </h2>
            <p className="text-sm text-muted-foreground">{contact.title || "Contact"}</p>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <p className="text-sm font-medium text-muted-foreground">Email</p>
              <p>{contact.email || "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Phone</p>
              <p>{contact.phone || "—"}</p>
            </div>
            <div>
              <p className="text-sm font-medium text-muted-foreground">Department</p>
              <p>{contact.department || "—"}</p>
            </div>
          </div>

          <div>
            <p className="text-sm font-medium text-muted-foreground">Owner</p>
            <p>{contact.ownerUserId ? resolveUserName(contact.ownerUserId) : "Unassigned"}</p>
          </div>

          <div>
            <p className="text-sm font-medium text-muted-foreground">Contact created</p>
            <p>{new Date(contact.createdAt).toLocaleString()}</p>
          </div>
        </div>

        <div className="rounded-lg border bg-white p-6">
          <p className="text-sm font-medium text-muted-foreground">Account</p>
          <div className="mt-3 space-y-2">
            {account ? (
              <Link href={`/accounts/${account.id}`} className="text-primary hover:underline">
                {account.name}
              </Link>
            ) : (
              <p>{contact.accountId}</p>
            )}
          </div>
        </div>
      </div>

      <EntityDealsSection entityType="CONTACT" entityId={contact.id} />

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[2fr_1fr]">
  <div className="space-y-6">
    <ContactTimeline contactId={contact.id} />

    <ContactEntitlementsSection contactId={contact.id} />

    <EntityCallHistory
      entityType="contact"
      entityId={contact.id}
      title="Contact call history"
      pageSize={10}
    />
  </div>

  <div className="space-y-6">
    <ContactNotes contactId={contact.id} />
  </div>
</div>

      <AlertDialog open={showDelete} onOpenChange={setShowDelete}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this contact?</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone. The contact will be removed from the system.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive hover:bg-destructive/90"
              onClick={() =>
                deleteMutation.mutate(contact.id, {
                  onSuccess: () => router.push("/contacts"),
                })
              }
            >
              {deleteMutation.isPending ? "Deleting..." : "Delete"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
