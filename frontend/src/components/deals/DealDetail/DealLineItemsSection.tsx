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
}

const emptyDraft = {
  offeringId: "",
  lineName: "",
  lineDescription: "",
  quantity: 1,
  unitPrice: 0,
  discountPercent: 0,
  taxPercent: 0,
  startDate: "",
  endDate: "",
  renewable: false,
};

export function DealLineItemsSection({ dealId }: DealLineItemsSectionProps) {
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
      lineName: draft.lineName,
      lineDescription: draft.lineDescription || undefined,
      quantity: Number(draft.quantity || 0),
      unitPrice: Number(draft.unitPrice || 0),
      discountPercent: Number(draft.discountPercent || 0),
      taxPercent: Number(draft.taxPercent || 0),
      startDate: draft.startDate || undefined,
      endDate: draft.endDate || undefined,
      renewable: draft.renewable,
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
      lineName: item.lineName,
      lineDescription: item.lineDescription ?? "",
      quantity: Number(item.quantity ?? 1),
      unitPrice: Number(item.unitPrice ?? 0),
      discountPercent: Number(item.discountPercent ?? 0),
      taxPercent: Number(item.taxPercent ?? 0),
      startDate: item.startDate ?? "",
      endDate: item.endDate ?? "",
      renewable: Boolean(item.renewable),
    });
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle>Line items</CardTitle>
          <Button type="button" variant="outline" size="sm" onClick={resetDraft}>
            <Plus className="mr-2 h-4 w-4" /> {editingId ? "Cancel edit" : "Add line item"}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
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
              <Label>Line name</Label>
              <Input value={draft.lineName} onChange={(event) => setDraft({ ...draft, lineName: event.target.value })} required />
            </div>
            <div className="space-y-2 md:col-span-2">
              <Label>Description</Label>
              <Textarea value={draft.lineDescription} onChange={(event) => setDraft({ ...draft, lineDescription: event.target.value })} />
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
              <Label>Discount %</Label>
              <Input type="number" min="0" max="100" step="0.01" value={draft.discountPercent} onChange={(event) => setDraft({ ...draft, discountPercent: Number(event.target.value) })} />
            </div>
            <div className="space-y-2">
              <Label>Tax %</Label>
              <Input type="number" min="0" max="100" step="0.01" value={draft.taxPercent} onChange={(event) => setDraft({ ...draft, taxPercent: Number(event.target.value) })} />
            </div>
            <div className="space-y-2">
              <Label>Start date</Label>
              <Input type="date" value={draft.startDate} onChange={(event) => setDraft({ ...draft, startDate: event.target.value })} />
            </div>
            <div className="space-y-2">
              <Label>End date</Label>
              <Input type="date" value={draft.endDate} onChange={(event) => setDraft({ ...draft, endDate: event.target.value })} />
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
                    <h4 className="font-medium">{item.lineName}</h4>
                    {item.renewable && <Badge variant="secondary">Renewable</Badge>}
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {item.offeringName ?? item.offeringCode ?? "Catalog item"} • Qty {item.quantity} • ${item.unitPrice ?? 0}
                  </p>
                  {item.lineDescription && <p className="text-sm text-muted-foreground">{item.lineDescription}</p>}
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" onClick={() => startEdit(item)}>
                    <Pencil className="mr-2 h-4 w-4" /> Edit
                  </Button>
                  <Button variant="destructive" size="sm" onClick={() => deleteLineItem.mutate(item.id)}>
                    <Trash2 className="mr-2 h-4 w-4" /> Delete
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
