"use client";

import { useMemo, useState } from "react";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { useCreateDealLineItem, useDeleteDealLineItem, useDealLineItems, useUpdateDealLineItem } from "@/lib/hooks/deal-line-items";
import { useOfferings } from "@/lib/hooks/offerings";
import { DealLineItemCreateRequest, DealLineItemResponse, DealLineItemUpdateRequest } from "@/types/deal-line-items";

interface DealLineItemsSectionProps {
  dealId: string;
  /**
   * L3.0-P5: a won Deal is commercially frozen, so its lines are read-only. The
   * backend rejects create/update/delete on a closed Deal, and the form is
   * hidden to match rather than offering controls that must fail.
   */
  readOnly?: boolean;
}

// L3.0-P5: field names and money semantics now match the backend contract.
// `discountAmount` / `taxAmount` are absolute amounts, not percentages, and the
// line's own name and currency are not client-editable: they are snapshotted
// from the Offering, which is the single source of truth.
const emptyDraft = {
  offeringId: "",
  quantity: 1,
  unitPrice: 0,
  discountAmount: 0,
  taxAmount: 0,
  serviceStartDate: "",
  serviceEndDate: "",
  renewable: false,
  renewalNoticeDays: 0,
  description: "",
};

export function DealLineItemsSection({ dealId, readOnly = false }: DealLineItemsSectionProps) {
  const [draft, setDraft] = useState(emptyDraft);
  const [editingId, setEditingId] = useState<string | null>(null);

  const { data: lineItemsData, isLoading } = useDealLineItems(dealId);
  const { data: offeringsData } = useOfferings({ size: 100 });
  const createLineItem = useCreateDealLineItem(dealId);
  const updateLineItem = useUpdateDealLineItem(dealId);
  const deleteLineItem = useDeleteDealLineItem(dealId);

  const lineItems = useMemo(() => (lineItemsData ?? []) as DealLineItemResponse[], [lineItemsData]);
  const offerings = useMemo(() => (offeringsData?.data ?? []) as Array<{ id: string; name: string }>, [offeringsData]);

  const resetDraft = () => {
    setDraft(emptyDraft);
    setEditingId(null);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const payload: DealLineItemCreateRequest | DealLineItemUpdateRequest = {
      offeringId: draft.offeringId || undefined,
      quantity: Number(draft.quantity || 0),
      unitPrice: Number(draft.unitPrice || 0),
      discountAmount: Number(draft.discountAmount || 0),
      taxAmount: Number(draft.taxAmount || 0),
      serviceStartDate: draft.serviceStartDate || undefined,
      serviceEndDate: draft.serviceEndDate || undefined,
      renewable: draft.renewable,
      renewalNoticeDays: Number(draft.renewalNoticeDays || 0),
      description: draft.description || undefined,
    };

    if (editingId) {
      await updateLineItem.mutateAsync({ id: editingId, data: payload as DealLineItemUpdateRequest });
    } else {
      await createLineItem.mutateAsync(payload as DealLineItemCreateRequest);
    }
    resetDraft();
  };

  const startEdit = (item: DealLineItemResponse) => {
    setEditingId(item.id);
    setDraft({
      offeringId: item.offeringId ?? "",
      quantity: Number(item.quantity ?? 1),
      unitPrice: Number(item.unitPrice ?? 0),
      discountAmount: Number(item.discountAmount ?? 0),
      taxAmount: Number(item.taxAmount ?? 0),
      serviceStartDate: item.serviceStartDate ?? "",
      serviceEndDate: item.serviceEndDate ?? "",
      renewable: Boolean(item.renewable),
      renewalNoticeDays: Number(item.renewalNoticeDays ?? 0),
      description: item.description ?? "",
    });
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle>Line items</CardTitle>
          {!readOnly && (
            <Button type="button" variant="outline" size="sm" onClick={resetDraft}>
              <Plus className="mr-2 h-4 w-4" /> {editingId ? "Cancel edit" : "Add line item"}
            </Button>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {readOnly && lineItems.length > 0 && (
          <p className="text-sm text-muted-foreground">
            This deal is closed, so its commercial lines are locked.
          </p>
        )}

        {!readOnly && (
          <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border p-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>Offering</Label>
                <select
                  value={draft.offeringId}
                  onChange={(event) => setDraft({ ...draft, offeringId: event.target.value })}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                >
                  <option value="">Select an offering</option>
                  {offerings.map((offering) => (
                    <option key={offering.id} value={offering.id}>
                      {offering.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label>Quantity</Label>
                <Input type="number" min="0" step="1" value={draft.quantity} onChange={(event) => setDraft({ ...draft, quantity: Number(event.target.value) })} />
              </div>
              <div className="space-y-2">
                <Label>Unit price</Label>
                <Input type="number" min="0" step="0.01" value={draft.unitPrice} onChange={(event) => setDraft({ ...draft, unitPrice: Number(event.target.value) })} />
              </div>
              <div className="space-y-2">
                <Label>Discount amount</Label>
                <Input type="number" min="0" step="0.01" value={draft.discountAmount} onChange={(event) => setDraft({ ...draft, discountAmount: Number(event.target.value) })} />
              </div>
              <div className="space-y-2">
                <Label>Tax amount</Label>
                <Input type="number" min="0" step="0.01" value={draft.taxAmount} onChange={(event) => setDraft({ ...draft, taxAmount: Number(event.target.value) })} />
              </div>
              <div className="space-y-2">
                <Label>Service start date</Label>
                <Input type="date" value={draft.serviceStartDate} onChange={(event) => setDraft({ ...draft, serviceStartDate: event.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>Service end date</Label>
                <Input type="date" value={draft.serviceEndDate} onChange={(event) => setDraft({ ...draft, serviceEndDate: event.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>Renewal notice (days)</Label>
                <Input type="number" min="0" step="1" value={draft.renewalNoticeDays} onChange={(event) => setDraft({ ...draft, renewalNoticeDays: Number(event.target.value) })} />
              </div>
              <div className="space-y-2 md:col-span-2">
                <Label>Description</Label>
                <Textarea value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} />
              </div>
            </div>
            <div className="flex items-center gap-2">
              <input type="checkbox" checked={draft.renewable} onChange={(event) => setDraft({ ...draft, renewable: event.target.checked })} />
              <Label>Renewable</Label>
            </div>
            <div className="flex justify-end">
              <Button type="submit" disabled={createLineItem.isPending || updateLineItem.isPending}>
                {editingId ? "Update line item" : "Add line item"}
              </Button>
            </div>
          </form>
        )}

        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading line items…</p>
        ) : lineItems.length === 0 ? (
          <p className="text-sm text-muted-foreground">No line items yet.</p>
        ) : (
          <div className="space-y-3">
            {lineItems.map((item) => (
              <div key={item.id} className="flex items-start justify-between rounded-lg border p-3">
                <div>
                  <div className="flex items-center gap-2">
                    <h4 className="font-medium">{item.itemName ?? item.itemCode ?? "Catalog item"}</h4>
                    {item.currencyCode && <Badge variant="outline">{item.currencyCode}</Badge>}
                    {item.renewable && <Badge variant="secondary">Renewable</Badge>}
                  </div>
                  <p className="text-sm text-muted-foreground">
                    Qty {item.quantity} • {item.unitPrice ?? 0}
                    {item.discountAmount ? ` • discount ${item.discountAmount}` : ""}
                    {item.taxAmount ? ` • tax ${item.taxAmount}` : ""}
                    {item.lineTotal != null ? ` • total ${item.lineTotal}` : ""}
                  </p>
                  {(item.serviceStartDate || item.serviceEndDate) && (
                    <p className="text-sm text-muted-foreground">
                      {item.serviceStartDate ?? "—"} → {item.serviceEndDate ?? "—"}
                    </p>
                  )}
                  {item.description && <p className="text-sm text-muted-foreground">{item.description}</p>}
                </div>
                {!readOnly && (
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={() => startEdit(item)}>
                      <Pencil className="mr-2 h-4 w-4" /> Edit
                    </Button>
                    <Button variant="destructive" size="sm" onClick={() => deleteLineItem.mutate(item.id)}>
                      <Trash2 className="mr-2 h-4 w-4" /> Delete
                    </Button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
