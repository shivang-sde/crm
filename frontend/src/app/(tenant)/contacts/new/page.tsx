"use client";

import { useRouter } from "next/navigation";
import Link from "next/link";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { ContactForm } from "@/components/contacts/ContactForm/ContactForm";
import { usePermissions } from "@/lib/hooks/usePermissions";

function NewContactPageContent() {
  const { canEditContacts } = usePermissions();
  const router = useRouter();

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">New Contact</h1>
          <p className="text-sm text-muted-foreground">Create a new contact record.</p>
        </div>
        <Button variant="outline" size="sm" asChild>
          <Link href="/contacts">Back to contacts</Link>
        </Button>
      </div>
      {canEditContacts && (
        <ContactForm onSuccess={(contact) => router.push(`/contacts/${contact.id}`)} />
      )}
    </div>
  );
}

export default function NewContactPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "contact", action: "write" }}>
      <NewContactPageContent />
    </ProtectedRoute>
  );
}
