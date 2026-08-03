"use client";

import Link from "next/link";
import { Plus } from "lucide-react";

import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { ContactList } from "@/components/contacts/ContactList/ContactList";
import { usePermissions } from "@/lib/hooks/usePermissions";

function ContactsPageContent() {
  const {
    canEditContacts,
    canDeleteContacts,
  } = usePermissions();

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            Contacts
          </h1>

          <p className="mt-1 text-sm text-muted-foreground">
            Manage people associated with accounts, deals and
            other CRM records.
          </p>
        </div>

        {canEditContacts && (
          <Button asChild>
            <Link href="/contacts/new">
              <Plus className="mr-2 h-4 w-4" />
              New Contact
            </Link>
          </Button>
        )}
      </div>

      <ContactList
        canEdit={canEditContacts}
        canDelete={canDeleteContacts}
      />
    </div>
  );
}

export default function ContactsPage() {
  return (
    <ProtectedRoute
      requiredPermission={{
        module: "contact",
        action: "read",
      }}
    >
      <ContactsPageContent />
    </ProtectedRoute>
  );
}