"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
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
import { useState } from "react";
import { useDeleteAccount } from "@/lib/hooks/accounts";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { AccountResponse } from "@/types/accounts";
import { AccountWorkspace } from "./AccountWorkspace";
import { AccountNotes } from "./AccountNotes";
import { ClickToCallButton } from "@/components/call-opening/ClickToCallButton";
import { EntityCallHistory } from "@/components/calls/EntityCallHistory";
import { CommercialDocumentsSection } from "@/components/commercial/CommercialDocumentsSection";

interface AccountDetailProps {
  account: AccountResponse;
}

export function AccountDetail({ account }: AccountDetailProps) {
  const router = useRouter();
  const { canEditAccounts } = usePermissions();
  const deleteMutation = useDeleteAccount();
  const [showDelete, setShowDelete] = useState(false);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/accounts">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to accounts
          </Link>
        </Button>
        <div className="flex gap-2 flex-wrap">
          {account.phone && (
            <ClickToCallButton
              entityType="account"
              entityId={account.id}
              phoneNumber={account.phone}
              label="Call account"
              variant="outline"
              size="sm"
            />
          )}
          {canEditAccounts && (
            <Button variant="outline" size="sm" asChild>
              <Link href={`/accounts/${account.id}/edit`}>
                <Pencil className="mr-2 h-4 w-4" />
                Edit
              </Link>
            </Button>
          )}
          {canEditAccounts && (
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

      {/*
        L3.2-P4: Customer 360 workspace. Contacts, deals, entitlements, tickets and
        the timeline now come from the single /accounts/{id}/360 response, so the
        previous per-section cards that each fetched their own endpoint are no longer
        rendered here — they would duplicate the same data behind a second
        authorization gate.

        The panels with no Customer 360 equivalent (notes, call history, commercial
        documents) stay on the Overview tab so nothing is lost.
      */}
      <AccountWorkspace
        account={account}
        overviewPanels={
          <div className="space-y-4">
            <AccountNotes accountId={account.id} />
            <CommercialDocumentsSection accountId={account.id} />
            <EntityCallHistory
              entityType="account"
              entityId={account.id}
              title="Account call history"
              pageSize={10}
            />
          </div>
        }
      />

      <AlertDialog open={showDelete} onOpenChange={setShowDelete}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this account?</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone. The account and its associated records will be deleted.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive hover:bg-destructive/90"
              onClick={() =>
                deleteMutation.mutate(account.id, {
                  onSuccess: () => router.push("/accounts"),
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
