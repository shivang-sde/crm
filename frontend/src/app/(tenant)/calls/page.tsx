'use client';

import { useRouter } from 'next/navigation';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useCalls } from '@/lib/hooks/calls';
import { usePermissions } from '@/lib/hooks/usePermissions';
import { useState } from 'react';
import { CallResponse } from '@/types/calls';

export default function CallsPage() {
  const router = useRouter();
  const { canViewCalls, canEditCalls } = usePermissions();
  const [filters, setFilters] = useState({
    page: 0,
    size: 10,
    sort: 'createdAt,desc',
    search: '',
    status: '',
    callType: '',
  });

  const { data, isLoading } = useCalls(filters as any);
  
  console.log("call data", data)

  if (!canViewCalls) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-muted-foreground">You don&apos;t have permission to view calls.</p>
      </div>
    );
  }

  const handleCreateNew = () => {
    router.push('/calls/new');
  };

  const handleViewCall = (call: CallResponse) => {
    router.push(`/calls/${call.id}`);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Calls</h1>
          <p className="text-muted-foreground">Log and track your phone calls</p>
        </div>
        {canEditCalls && (
          <Button onClick={handleCreateNew}>
            <Plus className="mr-2 h-4 w-4" />
            New Call
          </Button>
        )}
      </div>

      <div className="rounded-md border p-8 text-center">
        <p className="text-muted-foreground">Call list component coming soon...</p>
        <p className="text-sm text-muted-foreground mt-2">
          Total calls: {data?.totalElements || 0}
        </p>
      </div>
    </div>
  );
}
