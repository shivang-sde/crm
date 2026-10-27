'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Plus } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { useTickets } from '@/lib/hooks/tickets';
import { usePermissions } from '@/lib/hooks/usePermissions';
import type { TicketStatus } from '@/types/tickets';
import TicketTable from './_components/TicketTable';

const PAGE_SIZE = 20;

export default function TicketsPage() {
  const router = useRouter();
  const { canViewTickets, canEditTickets } = usePermissions();
  const [filters, setFilters] = useState<{
    page: number;
    size: number;
    search: string;
    status: TicketStatus | '';
  }>({ page: 0, size: PAGE_SIZE, search: '', status: '' });

  const { data, isLoading } = useTickets({
    page: filters.page,
    size: filters.size,
    search: filters.search || undefined,
    status: filters.status || undefined,
  });

  if (!canViewTickets) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">You don&apos;t have permission to view tickets.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Tickets</h1>
          <p className="text-muted-foreground">Customer support tickets and their SLA state</p>
        </div>
        {canEditTickets && (
          <Button onClick={() => router.push('/tickets/new')}>
            <Plus className="mr-2 h-4 w-4" />
            New Ticket
          </Button>
        )}
      </div>

      <TicketTable
        data={data?.data ?? []}
        isLoading={isLoading}
        meta={data?.meta}
        filters={filters}
        onFiltersChange={setFilters}
        canEdit={canEditTickets}
      />
    </div>
  );
}
