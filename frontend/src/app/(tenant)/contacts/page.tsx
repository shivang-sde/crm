"use client";

import Link from "next/link";
import { Plus } from "lucide-react";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { ContactList } from "@/components/contacts/ContactList/ContactList";
import { usePermissions } from "@/lib/hooks/usePermissions";

function ContactsPageContent() {
  const { canEditContacts } = usePermissions();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <h1 className="text-2xl font-bold tracking-tight">Contacts</h1>
        <div>
          {canEditContacts && (
            <Button asChild>
              <Link href="/contacts/new">
                <Plus className="mr-2 h-4 w-4" />
                New Contact
              </Link>
            </Button>
          )}
        </div>
      </div>
      <ContactList />
    </div>
  );
}

export default function ContactsPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "contact", action: "read" }}>
      <ContactsPageContent />
    </ProtectedRoute>
  );
}
