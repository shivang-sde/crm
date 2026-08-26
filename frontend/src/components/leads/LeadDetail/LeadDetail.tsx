"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

import { useDeleteLead } from "@/lib/hooks/leads";
import { usePermissions } from "@/lib/hooks/usePermissions";
import type { LeadResponse } from "@/types/leads";

import { LeadBasicInfo } from "./LeadBasicInfo";
import { LeadConvertDialog } from "../LeadConvertDialog";
import { LeadCustomFields } from "./LeadCustomFields";
import { LeadTimeline } from "./LeadTimeline";
import { LeadNotes } from "./LeadNotes";
import { LeadAssignment } from "./LeadAssignment";

import { ClickToCallButton } from "@/components/call-opening/ClickToCallButton";
import { EntityCallHistory } from "@/components/calls/EntityCallHistory";

interface LeadDetailProps {
  lead: LeadResponse;
}

export function LeadDetail({ lead }: LeadDetailProps) {
  const router = useRouter();
  const { canEditLeads, canDeleteLeads } = usePermissions();
  const deleteMutation = useDeleteLead();
  const [showDelete, setShowDelete] = useState(false);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/leads">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to leads
          </Link>
        </Button>

        <div className="flex flex-wrap gap-2">
          {lead.phone && (
            <ClickToCallButton
              entityType="lead"
              entityId={lead.id}
              phoneNumber={lead.phone}
              label="Call lead"
              variant="outline"
              size="sm"
            />
          )}

          {canEditLeads && !lead.isConverted && (
            <LeadConvertDialog
              triggerLabel="Convert"
              lead={lead}
            />
          )}

          {canEditLeads && (
            <Button variant="outline" size="sm" asChild>
              <Link href={`/leads/${lead.id}/edit`}>
                <Pencil className="mr-2 h-4 w-4" />
                Edit
              </Link>
            </Button>
          )}

          {canDeleteLeads && (
            <Button
              variant="outline"
              size="sm"
              className="text-destructive"
              onClick={() => setShowDelete(true)}
            >
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <LeadBasicInfo lead={lead} />

          <LeadCustomFields lead={lead} />

          <EntityCallHistory
            entityType="lead"
            entityId={lead.id}
            title="Lead call history"
            pageSize={10}
          />
        </div>

        <div className="space-y-6" >
          <LeadAssignment lead={lead} />
          <LeadNotes leadId={lead.id} />
          <LeadTimeline leadId={lead.id} />
        </div>
      </div>

      <AlertDialog
        open={showDelete}
        onOpenChange={setShowDelete}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              Delete this lead?
            </AlertDialogTitle>

            <AlertDialogDescription>
              This action cannot be undone. The lead and
              its activity history will be removed.
            </AlertDialogDescription>
          </AlertDialogHeader>

          <AlertDialogFooter>
            <AlertDialogCancel>
              Cancel
            </AlertDialogCancel>

            <AlertDialogAction
              className="bg-destructive hover:bg-destructive/90"
              onClick={() =>
                deleteMutation.mutate(lead.id, {
                  onSuccess: () =>
                    router.push("/leads"),
                })
              }
            >
              {deleteMutation.isPending
                ? "Deleting..."
                : "Delete"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}