"use client";

import { useParams } from "next/navigation";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { useContact } from "@/lib/hooks/contacts";
import { ContactDetail } from "@/components/contacts/ContactDetail/ContactDetail";

function ContactDetailPageContent() {
  const params = useParams<{ id?: string | string[] }>();
  const rawId = params?.id;
  const id = typeof rawId === "string" ? rawId : rawId?.[0];
  const { data: contact, isLoading } = useContact(id);

  if (isLoading) {
    return <div className="py-12 text-center text-muted-foreground">Loading contact...</div>;
  }

  if (!contact) {
    return <div className="py-12 text-center text-muted-foreground">Contact not found.</div>;
  }

  return <ContactDetail contact={contact} />;
}

export default function ContactDetailPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "contact", action: "read" }}>
      <ContactDetailPageContent />
    </ProtectedRoute>
  );
}
