"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import { useContacts } from "@/lib/hooks/contacts";
import type { ContactResponse } from "@/types/contacts";

import {
  ContactDataTable,
  type ContactFilters,
} from "./ContactDataTable";

interface ContactListProps {
  canEdit?: boolean;
  canDelete?: boolean;
}

const INITIAL_FILTERS: ContactFilters = {
  page: 0,
  size: 20,
  sort: "createdAt,desc",
  search: "",
};

export function ContactList({
  canEdit = false,
  canDelete = false,
}: ContactListProps) {
  const router = useRouter();

  const [filters, setFilters] =
    useState<ContactFilters>(INITIAL_FILTERS);

  const [debouncedSearch, setDebouncedSearch] = useState("");

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedSearch(filters.search.trim());
    }, 400);

    return () => window.clearTimeout(timer);
  }, [filters.search]);

  const requestParams = useMemo(
    () => ({
      page: filters.page,
      size: filters.size,
      sort: filters.sort,
      search: debouncedSearch || undefined,
    }),
    [
      debouncedSearch,
      filters.page,
      filters.size,
      filters.sort,
    ],
  );

  const {
    data: contactsResult,
    isLoading,
    isFetching,
  } = useContacts(requestParams);

  const contacts = contactsResult?.data ?? [];
  const meta = contactsResult?.meta;

  const handleView = (contact: ContactResponse) => {
    router.push(`/contacts/${contact.id}`);
  };

  const handleEdit = (contact: ContactResponse) => {
    router.push(`/contacts/${contact.id}/edit`);
  };

  const handleDelete = (contact: ContactResponse) => {
    /*
     * Replace this with your delete confirmation dialog
     * and delete mutation.
     */
    console.log("Delete contact:", contact.id);
  };

  return (
    <ContactDataTable
      data={contacts}
      isLoading={isLoading || isFetching}
      filters={filters}
      onFiltersChange={setFilters}
      totalElements={meta?.total ?? 0}
      totalPages={meta?.totalPages ?? 0}
      canEdit={canEdit}
      canDelete={canDelete}
      onView={handleView}
      onEdit={handleEdit}
      onDelete={handleDelete}
    />
  );
}