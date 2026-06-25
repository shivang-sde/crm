"use client";

import React from "react";
import { Dialog, DialogTrigger, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { DealForm } from "./DealForm";
import { Plus } from "lucide-react";

interface Props {
  onCreated?: (id: string) => void;
}

export function CreateDealDialog({ onCreated }: Props) {
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button>
          <Plus className="mr-2 h-4 w-4" /> New Deal
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create Deal</DialogTitle>
        </DialogHeader>
        <DealForm onSuccess={(deal) => onCreated?.(deal.id)} />
        <DialogFooter />
      </DialogContent>
    </Dialog>
  );
}
