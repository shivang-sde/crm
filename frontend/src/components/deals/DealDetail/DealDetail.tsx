"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { useState } from "react";
import { useDeleteDeal } from "@/lib/hooks/deals";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { DealResponse } from "@/types/deals";
import { DealBasicInfo } from "./DealBasicInfo";
import { DealCustomFields } from "./DealCustomFields";
import { DealTimeline } from "./DealTimeline";
import { DealNotes } from "./DealNotes";
import { DealAssignment } from "./DealAssignment";
import { DealLineItemsSection } from "./DealLineItemsSection";

interface DealDetailProps {
  deal: DealResponse;
}

export function DealDetail({ deal }: DealDetailProps) {
  const router = useRouter();
  const { canEditDeals } = usePermissions();
  const deleteMutation = useDeleteDeal();
  const [showDelete, setShowDelete] = useState(false);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/deals">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to deals
          </Link>
        </Button>
        <div className="flex gap-2">
          {canEditDeals && (
            <Button variant="outline" size="sm" asChild>
              <Link href={`/deals/${deal.id}/edit`}>
                <Pencil className="mr-2 h-4 w-4" />
                Edit
              </Link>
            </Button>
          )}
          {canEditDeals && (
            <Button variant="outline" size="sm" className="text-destructive" onClick={() => setShowDelete(true)}>
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <DealBasicInfo deal={deal} />
          <DealCustomFields deal={deal} />
          <DealLineItemsSection dealId={deal.id} />
          <DealTimeline dealId={deal.id} />
          <DealNotes dealId={deal.id} />
        </div>
        <div className="space-y-6">
          <DealAssignment deal={deal} />
        </div>
      </div>

      <AlertDialog open={showDelete} onOpenChange={setShowDelete}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this deal?</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone. The deal and its activity history will be removed.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive hover:bg-destructive/90"
              onClick={() =>
                deleteMutation.mutate(deal.id, {
                  onSuccess: () => router.push("/deals"),
                })
              }
            >
              {deleteMutation.isPending ? "Deleting..." : "Delete"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
