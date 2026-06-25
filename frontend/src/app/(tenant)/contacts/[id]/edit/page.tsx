"use client";

import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { ContactForm } from "@/components/contacts/ContactForm/ContactForm";
import { useContact } from "@/lib/hooks/contacts";
import { usePermissions } from "@/lib/hooks/usePermissions";

function EditContactPageContent() {
  const params = useParams();
  const id = typeof params.id === "string" ? params.id : params.id?.[0];
  const router = useRouter();
  const { data: contact, isLoading } = useContact(id);
  const { canEditContacts } = usePermissions();

  if (isLoading) {
    return <div className="py-12 text-center text-muted-foreground">Loading contact...</div>;
  }

  if (!contact) {
    return <div className="py-12 text-center text-muted-foreground">Contact not found.</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Edit Contact</h1>
          <p className="text-sm text-muted-foreground">Update contact information.</p>
        </div>
        <Button variant="outline" size="sm" asChild>
          <Link href={`/contacts/${contact.id}`}>Back to contact</Link>
        </Button>
      </div>
      {canEditContacts && (
        <ContactForm initialData={contact} onSuccess={(updated) => router.push(`/contacts/${updated.id}`)} />
      )}
    </div>
  );
}

export default function EditContactPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "contact", action: "write" }}>
      <EditContactPageContent />
    </ProtectedRoute>
  );
}
