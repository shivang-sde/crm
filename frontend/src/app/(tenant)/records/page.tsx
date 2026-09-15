"use client";

import { useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { Database, Plus, Loader2, Settings } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useRecordTypes, useRecordFields } from "@/lib/hooks/records";
import { useCreateRecord } from "@/lib/hooks/records";
import { RecordList } from "@/components/records/RecordList";
import { RecordForm } from "@/components/records/RecordForm";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";

function RecordsContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const initialTypeId = searchParams.get("recordTypeId") || undefined;

  const { data: typesData, isLoading: typesLoading } = useRecordTypes(0, 100);
  const types = typesData?.data?.filter((t) => t.isActive) ?? [];
  const [selectedTypeId, setSelectedTypeId] = useState<string | undefined>(initialTypeId);
  const [createOpen, setCreateOpen] = useState(false);

  const { data: fields } = useRecordFields(selectedTypeId);
  const createMutation = useCreateRecord();

  useEffect(() => {
    if (!selectedTypeId && types.length > 0) {
      const first = types[0].id;
      setSelectedTypeId(first);
      router.replace(`/records?recordTypeId=${first}`);
    }
  }, [types, selectedTypeId, router]);

  useEffect(() => {
    if (initialTypeId) setSelectedTypeId(initialTypeId);
  }, [initialTypeId]);

  function handleTypeChange(val: string) {
    setSelectedTypeId(val);
    router.push(`/records?recordTypeId=${val}`);
  }

  if (typesLoading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (types.length === 0) {
    return (
      <div className="max-w-3xl mx-auto py-16 text-center space-y-4">
        <div className="rounded-full bg-muted p-6 w-fit mx-auto">
          <Database className="h-8 w-8 text-muted-foreground" />
        </div>
        <h2 className="text-xl font-semibold">No Record Types configured yet</h2>
        <p className="text-sm text-muted-foreground">Create a Record Type before creating records. Example: CDR, Payment, Shipment.</p>
        <Button asChild>
          <Link href="/settings/records">
            <Settings className="h-4 w-4 mr-2" /> Go to Record Types
          </Link>
        </Button>
      </div>
    );
  }

  const selectedType = types.find((t) => t.id === selectedTypeId);

  return (
    <div className="space-y-6 p-6 max-w-6xl mx-auto">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight flex items-center gap-2">
            <Database className="h-6 w-6 text-indigo-600" /> Records
          </h1>
          <p className="text-sm text-muted-foreground">Select a Record Type to view and manage its records.</p>
        </div>
        <Button variant="outline" size="sm" asChild>
          <Link href="/settings/records">
            <Settings className="h-4 w-4 mr-1" /> Configure Types
          </Link>
        </Button>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between rounded-xl border bg-white p-4 shadow-sm">
        <div className="flex items-center gap-3">
          <span className="text-sm font-medium">Record Type</span>
          <Select value={selectedTypeId} onValueChange={handleTypeChange}>
            <SelectTrigger className="w-[260px]">
              <SelectValue placeholder="Select type" />
            </SelectTrigger>
            <SelectContent>
              {types.map((t) => (
                <SelectItem key={t.id} value={t.id}>
                  {t.name} ({t.key})
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button onClick={() => setCreateOpen(true)} disabled={!selectedTypeId || !fields || fields.filter((f) => f.isActive).length === 0}>
          <Plus className="h-4 w-4 mr-1" /> New Record
        </Button>
      </div>

      {selectedType && (
        <div className="flex items-center gap-2 text-sm">
          <span className="font-medium">{selectedType.name}</span>
          <span className="font-mono text-xs bg-muted px-2 py-0.5 rounded">{selectedType.key}</span>
          {selectedType.description && <span className="text-muted-foreground hidden sm:inline">— {selectedType.description}</span>}
        </div>
      )}

      {selectedTypeId ? (
        <RecordList recordTypeId={selectedTypeId} />
      ) : (
        <p className="text-sm text-muted-foreground">Select a Record Type.</p>
      )}

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>New {selectedType?.name} Record</DialogTitle>
          </DialogHeader>
          {fields && (
            <RecordForm
              fields={fields}
              isPending={createMutation.isPending}
              submitLabel="Create"
              onSubmit={(data) =>
                createMutation.mutate(
                  { recordTypeId: selectedTypeId!, data },
                  { onSuccess: () => setCreateOpen(false) }
                )
              }
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

export default function RecordsPage() {
  return (
    <ProtectedRoute>
      <RecordsContent />
    </ProtectedRoute>
  );
}
