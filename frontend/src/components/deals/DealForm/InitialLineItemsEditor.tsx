"use client";

import { Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

import { DealLineItemCreateRequest } from "@/types/deal-line-items";

export interface InitialLineItemDraft {
  offeringId: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  taxAmount: number;
}

export const emptyLineItemDraft: InitialLineItemDraft = {
  offeringId: "",
  quantity: 1,
  unitPrice: 0,
  discountAmount: 0,
  taxAmount: 0,
};

interface InitialLineItemsEditorProps {
  drafts: InitialLineItemDraft[];
  onChange: (drafts: InitialLineItemDraft[]) => void;
  offerings: Array<{ id: string; name: string }>;
  required?: boolean;
  error?: string;
}

/**
 * L3.0-P5: collects a new Deal's initial line items so they can be submitted with
 * the Deal itself.
 *
 * <p>This exists because a Deal created directly in a won stage cannot be given
 * lines afterwards: a commercially closed Deal rejects line mutations. Submitting
 * them here means the Deal and its coverage are created in one request, and the
 * backend provisions the entitlements in the same transaction.
 *
 * <p>Amount-based money only. There is no discount or tax percentage: the backend
 * models absolute amounts, and sending a percentage was silently discarded.
 */
export function InitialLineItemsEditor({
  drafts,
  onChange,
  offerings,
  required = false,
  error,
}: InitialLineItemsEditorProps) {
  const update = (index: number, patch: Partial<InitialLineItemDraft>) => {
    onChange(drafts.map((draft, i) => (i === index ? { ...draft, ...patch } : draft)));
  };

  const add = () => onChange([...drafts, { ...emptyLineItemDraft }]);

  const remove = (index: number) => onChange(drafts.filter((_, i) => i !== index));

  return (
    <div className="space-y-3 border-t pt-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="font-semibold">Initial line items{required ? " *" : ""}</h3>
          <p className="text-sm text-muted-foreground">
            Created together with the deal. A won deal requires at least one line so its
            entitlements can be provisioned in the same transaction.
          </p>
        </div>
        <Button type="button" variant="outline" size="sm" onClick={add}>
          <Plus className="mr-2 h-4 w-4" /> Add line
        </Button>
      </div>

      {drafts.length === 0 ? (
        <p className="text-sm text-muted-foreground">No line items.</p>
      ) : (
        drafts.map((draft, index) => (
          <div
            key={index}
            className="grid items-end gap-3 rounded-lg border p-3 md:grid-cols-5"
          >
            <div className="space-y-2 md:col-span-2">
              <Label className="text-xs">Offering</Label>
              <select
                value={draft.offeringId}
                onChange={(event) => update(index, { offeringId: event.target.value })}
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
              <Label className="text-xs">Quantity</Label>
              <Input
                type="number"
                min="0"
                step="1"
                value={draft.quantity}
                onChange={(event) => update(index, { quantity: Number(event.target.value) })}
              />
            </div>
            <div className="space-y-2">
              <Label className="text-xs">Unit price</Label>
              <Input
                type="number"
                min="0"
                step="0.01"
                value={draft.unitPrice}
                onChange={(event) => update(index, { unitPrice: Number(event.target.value) })}
              />
            </div>
            <div className="space-y-2">
              <Label className="text-xs">Discount amount</Label>
              <Input
                type="number"
                min="0"
                step="0.01"
                value={draft.discountAmount}
                onChange={(event) => update(index, { discountAmount: Number(event.target.value) })}
              />
            </div>
            <div className="space-y-2">
              <Label className="text-xs">Tax amount</Label>
              <Input
                type="number"
                min="0"
                step="0.01"
                value={draft.taxAmount}
                onChange={(event) => update(index, { taxAmount: Number(event.target.value) })}
              />
            </div>
            <div className="flex justify-end md:col-span-5">
              <Button type="button" variant="ghost" size="sm" onClick={() => remove(index)}>
                <Trash2 className="mr-2 h-4 w-4" /> Remove line
              </Button>
            </div>
          </div>
        ))
      )}

      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}

/**
 * L3.0-P5: turns editor drafts into the request shape, dropping rows the user
 * left half-finished rather than sending an offering-less line the backend would
 * only reject.
 */
export function toLineItemRequests(drafts: InitialLineItemDraft[]): DealLineItemCreateRequest[] {
  return drafts
    .filter((draft) => draft.offeringId)
    .map((draft) => ({
      offeringId: draft.offeringId,
      quantity: draft.quantity,
      unitPrice: draft.unitPrice,
      discountAmount: draft.discountAmount,
      taxAmount: draft.taxAmount,
    }));
}
