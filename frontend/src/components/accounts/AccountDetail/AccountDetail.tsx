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
import { AccountContacts } from "./AccountContacts";
import { AccountTimeline } from "./AccountTimeline";
import { AccountNotes } from "./AccountNotes";
import { ClickToCallButton } from "@/components/call-opening/ClickToCallButton";

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

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2 space-y-4">
          <div className="rounded-lg border bg-white p-6">
            <div>
              <h2 className="text-xl font-semibold">{account.name}</h2>
              <p className="text-sm text-muted-foreground">{account.industry || "No industry specified"}</p>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <p className="text-sm font-medium text-muted-foreground">Email</p>
                <p>{account.email || "—"}</p>
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Phone</p>
                <p>{account.phone || "—"}</p>
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Website</p>
                <p>{account.website || "—"}</p>
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Owner</p>
                <p>{account.ownerUserId || "Unassigned"}</p>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <p className="text-sm font-medium text-muted-foreground">Annual Revenue</p>
                <p>{account.annualRevenue ? `$${account.annualRevenue.toLocaleString()}` : "—"}</p>
              </div>
              <div>
                <p className="text-sm font-medium text-muted-foreground">Employees</p>
                <p>{account.employeeCount ?? "—"}</p>
              </div>
            </div>

            <div className="space-y-2">
              <p className="text-sm font-medium text-muted-foreground">Address</p>
              <p>
                {account.addressLine1 || ""}
                {account.city ? `, ${account.city}` : ""}
                {account.state ? `, ${account.state}` : ""}
                {account.postalCode ? ` ${account.postalCode}` : ""}
                {account.country ? `, ${account.country}` : ""}
              </p>
            </div>

            <div>
              <p className="text-sm font-medium text-muted-foreground">Description</p>
              <p>{account.description || "No description provided."}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-6 xl:grid-cols-[2fr_1fr]">
            <div className="space-y-6">
              <AccountTimeline accountId={account.id} />
            </div>
            <div className="space-y-6">
              <AccountNotes accountId={account.id} />
            </div>
          </div>
        </div>

        <div className="space-y-4">
          <div className="rounded-lg border bg-white p-6">
            <p className="text-sm font-medium text-muted-foreground">Account details</p>
            <div className="mt-4 grid gap-3">
              <div>
                <p className="text-xs uppercase tracking-wide text-muted-foreground">Lead source</p>
                <p>{account.leadId ? "Converted from lead" : "Direct account"}</p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-muted-foreground">Created at</p>
                <p>{new Date(account.createdAt).toLocaleString()}</p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-wide text-muted-foreground">Updated at</p>
                <p>{new Date(account.updatedAt).toLocaleString()}</p>
              </div>
            </div>
          </div>

          <div className="rounded-lg border bg-white p-6">
            <h3 className="text-sm font-semibold">Contacts</h3>
            <AccountContacts accountId={account.id} />
          </div>
        </div>
      </div>

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
