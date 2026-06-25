"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useContacts } from "@/lib/hooks/contacts";
import { ContactTable } from "./ContactTable";

export function ContactList() {
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 400);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    setPage(0);
  }, [debouncedSearch]);

  const { data: contactsResult, isLoading } = useContacts({
    page,
    size: 20,
    search: debouncedSearch || undefined,
  });

  const contacts = contactsResult?.data ?? [];
  const meta = contactsResult?.meta;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex-1">
          <Input
            placeholder="Search contacts..."
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        <Button asChild>
          <Link href="/contacts/new">Create contact</Link>
        </Button>
      </div>

      <div className="bg-white rounded-lg border overflow-hidden">
        {isLoading ? (
          <div className="h-24 flex items-center justify-center text-muted-foreground">
            Loading contacts...
          </div>
        ) : contacts.length === 0 ? (
          <div className="h-24 flex items-center justify-center text-muted-foreground">
            No contacts found.
          </div>
        ) : (
          <ContactTable contacts={contacts} />
        )}

        {meta && meta.totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t">
            <div className="text-sm text-muted-foreground">
              Page {meta.page + 1} of {meta.totalPages} ({meta.total} contacts)
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((current) => current + 1)}
                disabled={meta ? page >= meta.totalPages - 1 : true}
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
