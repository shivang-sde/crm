import React from 'react';
import { CallList } from '@/components/calls/CallList';
import { Button } from '@/components/ui/button';
import { useNavigate } from 'react-router-dom';
import { usePermissions } from '@/hooks/usePermissions';
import { Plus } from 'lucide-react';

export function CallsPage() {
  const navigate = useNavigate();
  const { hasPermission } = usePermissions();

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">Calls</h1>
          <p className="text-muted-foreground">Log and track phone calls</p>
        </div>
        {hasPermission('call:write') && (
          <Button onClick={() => navigate('/calls/new')}>
            <Plus className="mr-2 h-4 w-4" /> New Call
          </Button>
        )}
      </div>
      <CallList />
    </div>
  );
}
