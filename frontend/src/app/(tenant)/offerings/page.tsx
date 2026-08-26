"use client";

import { useMemo, useState } from "react";
import { Plus, Search, Trash2, Pencil, Power } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { useOfferings, useCreateOffering, useUpdateOffering, useDeleteOffering, useToggleOfferingStatus } from "@/lib/hooks/offerings";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { OfferingForm } from "@/components/offerings/OfferingForm/OfferingForm";
import { OfferingCreateRequest, OfferingResponse, OfferingUpdateRequest } from "@/types/offerings";

export default function OfferingsPage() {
  const [search, setSearch] = useState("");
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<OfferingResponse | null>(null);

  const { canEditOfferings, canDeleteOfferings } = usePermissions();

  const { data, isLoading } = useOfferings({ search, size: 50 });
  const createOffering = useCreateOffering();
  const updateOffering = useUpdateOffering();
  const deleteOffering = useDeleteOffering();
  const toggleOffering = useToggleOfferingStatus();

  const offerings = useMemo(() => data?.data ?? [], [data]);

  const handleCreate = async (values: OfferingCreateRequest | OfferingUpdateRequest) => {
    await createOffering.mutateAsync(values as OfferingCreateRequest);
    setOpen(false);
    setEditing(null);
  };

  const handleUpdate = async (values: OfferingCreateRequest | OfferingUpdateRequest) => {
    if (!editing?.id) return;
    await updateOffering.mutateAsync({ id: editing.id, data: values as OfferingUpdateRequest });
    setOpen(false);
    setEditing(null);
  };

  const handleDelete = async (id: string) => {
    await deleteOffering.mutateAsync(id);
  };

  const handleToggle = async (offering: OfferingResponse) => {
    await toggleOffering.mutateAsync({ id: offering.id, active: !offering.active });
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Offerings Catalog</h1>
          <p className="text-sm text-muted-foreground">Manage reusable catalog items for deals and quotes.</p>
        </div>
        <Dialog open={open} onOpenChange={setOpen}>
          {canEditOfferings && (
            <DialogTrigger asChild>
              <Button onClick={() => setEditing(null)}>
                <Plus className="mr-2 h-4 w-4" /> New offering
              </Button>
            </DialogTrigger>
          )}
          <DialogContent className="max-w-3xl">
            <DialogHeader>
              <DialogTitle>{editing ? "Edit offering" : "Create offering"}</DialogTitle>
            </DialogHeader>
            <OfferingForm
              initialValues={editing ?? undefined}
              onSubmit={editing ? handleUpdate : handleCreate}
              submitLabel={editing ? "Update offering" : "Create offering"}
              isSubmitting={createOffering.isPending || updateOffering.isPending}
            />
          </DialogContent>
        </Dialog>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-4">
            <CardTitle>Catalog</CardTitle>
            <div className="relative w-full max-w-sm">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search offerings" className="pl-9" />
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Loading offerings…</p>
          ) : offerings.length === 0 ? (
            <p className="text-sm text-muted-foreground">No offerings found.</p>
          ) : (
            <div className="space-y-3">
              {offerings.map((offering) => (
                <div key={offering.id} className="flex items-center justify-between rounded-lg border p-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="font-medium">{offering.name}</h3>
                      <Badge variant={offering.active ? "default" : "secondary"}>{offering.active ? "Active" : "Inactive"}</Badge>
                      <Badge variant="outline">{offering.offeringType ?? "OTHER"}</Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">{offering.code} • {offering.currencyCode ?? "USD"} {offering.defaultPrice ?? 0}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {canEditOfferings && (
                      <Button variant="outline" size="sm" onClick={() => { setEditing(offering); setOpen(true); }}>
                        <Pencil className="mr-2 h-4 w-4" /> Edit
                      </Button>
                    )}
                    {canEditOfferings && (
                      <Button variant="outline" size="sm" onClick={() => handleToggle(offering)}>
                        <Power className="mr-2 h-4 w-4" /> {offering.active ? "Deactivate" : "Activate"}
                      </Button>
                    )}
                    {canDeleteOfferings && (
                      <Button variant="destructive" size="sm" onClick={() => handleDelete(offering.id)}>
                        <Trash2 className="mr-2 h-4 w-4" /> Delete
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
