"use client";

import React from "react";
import { Dialog, DialogTrigger, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Edit } from "lucide-react";
import { DealForm } from "./DealForm";
import { DealResponse } from "@/types/deals";

interface Props {
  deal: DealResponse;
  onUpdated?: (deal: DealResponse) => void;
}

export function EditDealDialog({ deal, onUpdated }: Props) {
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm">
          <Edit className="mr-2 h-4 w-4" /> Edit
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit Deal</DialogTitle>
        </DialogHeader>
        <DealForm initialData={deal} onSuccess={(d) => onUpdated?.(d)} />
        <DialogFooter />
      </DialogContent>
    </Dialog>
  );
}
