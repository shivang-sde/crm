"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, CalendarRange, Pencil, Power, ShieldCheck, Trash2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { EntitlementDetailView } from "@/components/entitlements/EntitlementDetailView";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { useActivateEntitlement, useEntitlement, useSuspendEntitlement, useTerminateEntitlement, useUpdateEntitlement } from "@/lib/hooks/entitlements";
import { usePermissions } from "@/lib/hooks/usePermissions";
import { entitlementUpdateSchema, EntitlementUpdateFormValues, CustomerEntitlementResponse } from "@/types/entitlements";

function EntitlementDetailPageContent() {
  const params = useParams<{ id?: string | string[] }>();
  const router = useRouter();
  const rawId = params?.id;
  const id = typeof rawId === "string" ? rawId : rawId?.[0];
  const { canEditEntitlements } = usePermissions();
  const { data: entitlement, isLoading } = useEntitlement(id);
  const updateMutation = useUpdateEntitlement();
  const activateMutation = useActivateEntitlement();
  const suspendMutation = useSuspendEntitlement();
  const terminateMutation = useTerminateEntitlement();
  const [isEditing, setIsEditing] = useState(false);
  const [showTerminate, setShowTerminate] = useState(false);

  const form = useForm<EntitlementUpdateFormValues>({
    resolver: zodResolver(entitlementUpdateSchema as any),
    defaultValues: {
      description: entitlement?.description ?? "",
      quantity: entitlement?.quantity ?? undefined,
      startDate: entitlement?.start_date ?? "",
      endDate: entitlement?.end_date ?? "",
      renewable: entitlement?.renewable ?? false,
      autoRenew: entitlement?.auto_renew ?? false,
      renewalNoticeDays: entitlement?.renewal_notice_days ?? undefined,
      ownerUserId: entitlement?.owner_user_id ?? "",
    },
  });

  useEffect(() => {
    form.reset({
      description: entitlement?.description ?? "",
      quantity: entitlement?.quantity ?? undefined,
      startDate: entitlement?.start_date ?? "",
      endDate: entitlement?.end_date ?? "",
      renewable: entitlement?.renewable ?? false,
      autoRenew: entitlement?.auto_renew ?? false,
      renewalNoticeDays: entitlement?.renewal_notice_days ?? undefined,
      ownerUserId: entitlement?.owner_user_id ?? "",
    });
  }, [entitlement, form]);

  const handleSubmit = (values: EntitlementUpdateFormValues) => {
    if (!id || !entitlement) return;
    const payload = {
      description: values.description || null,
      quantity: values.quantity ?? null,
      start_date: values.startDate || null,
      end_date: values.endDate || null,
      renewable: values.renewable ?? null,
      auto_renew: values.autoRenew ?? null,
      renewal_notice_days: values.renewalNoticeDays ?? null,
      owner_user_id: values.ownerUserId || null,
    };
    updateMutation.mutate({ id, data: payload }, { onSuccess: () => setIsEditing(false) });
  };

  const isActionPending = activateMutation.isPending || suspendMutation.isPending || terminateMutation.isPending || updateMutation.isPending;

  if (isLoading) {
    return <div className="py-12 text-center text-muted-foreground">Loading entitlement...</div>;
  }

  if (!entitlement) {
    return <div className="py-12 text-center text-muted-foreground">Entitlement not found.</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/entitlements">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to entitlements
          </Link>
        </Button>
        <div className="flex flex-wrap gap-2">
          {canEditEntitlements ? (
            <Button variant="outline" size="sm" onClick={() => setIsEditing(true)}>
              <Pencil className="mr-2 h-4 w-4" /> Edit
            </Button>
          ) : null}
          {canEditEntitlements && entitlement.status !== "ACTIVE" && entitlement.status !== "TERMINATED" && entitlement.status !== "CANCELLED" && entitlement.status !== "RENEWED" ? (
            <Button variant="outline" size="sm" onClick={() => activateMutation.mutate(entitlement.id)} disabled={isActionPending}>
              <Power className="mr-2 h-4 w-4" /> Activate
            </Button>
          ) : null}
          {canEditEntitlements && entitlement.status === "ACTIVE" ? (
            <Button variant="outline" size="sm" onClick={() => suspendMutation.mutate(entitlement.id)} disabled={isActionPending}>
              <Power className="mr-2 h-4 w-4" /> Suspend
            </Button>
          ) : null}
          {canEditEntitlements && entitlement.status !== "TERMINATED" && entitlement.status !== "CANCELLED" ? (
            <Button variant="destructive" size="sm" onClick={() => setShowTerminate(true)} disabled={isActionPending}>
              <Trash2 className="mr-2 h-4 w-4" /> Terminate
            </Button>
          ) : null}
        </div>
      </div>

      <EntitlementDetailView entitlement={entitlement} canEdit={canEditEntitlements} />

      <Dialog open={isEditing} onOpenChange={setIsEditing}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Edit entitlement</DialogTitle>
            <DialogDescription>Update the editable entitlement fields without changing the underlying provisioned source.</DialogDescription>
          </DialogHeader>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="description">Description</Label>
              <Textarea id="description" {...form.register("description")} />
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="quantity">Quantity</Label>
                <Input id="quantity" type="number" step="0.01" {...form.register("quantity")} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="renewalNoticeDays">Renewal notice days</Label>
                <Input id="renewalNoticeDays" type="number" {...form.register("renewalNoticeDays")} />
              </div>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="startDate">Start date</Label>
                <Input id="startDate" type="date" {...form.register("startDate")} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="endDate">End date</Label>
                <Input id="endDate" type="date" {...form.register("endDate")} />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="ownerUserId">Owner user id</Label>
              <Input id="ownerUserId" {...form.register("ownerUserId")} />
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <div>
                <p className="font-medium">Renewable</p>
                <p className="text-sm text-muted-foreground">Allow this entitlement to renew.</p>
              </div>
              <Switch checked={Boolean(form.watch("renewable"))} onCheckedChange={(value) => form.setValue("renewable", value)} />
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <div>
                <p className="font-medium">Auto renew</p>
                <p className="text-sm text-muted-foreground">Automatically renew if enabled.</p>
              </div>
              <Switch checked={Boolean(form.watch("autoRenew"))} onCheckedChange={(value) => form.setValue("autoRenew", value)} />
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setIsEditing(false)}>Cancel</Button>
              <Button type="submit" disabled={updateMutation.isPending || isActionPending}>Save changes</Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={showTerminate} onOpenChange={setShowTerminate}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Terminate entitlement?</AlertDialogTitle>
            <AlertDialogDescription>This will end the entitlement lifecycle for this customer product or service.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction className="bg-destructive hover:bg-destructive/90" onClick={() => terminateMutation.mutate(entitlement.id)}>
              Confirm termination
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

export default function EntitlementDetailPage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "entitlement", action: "read" }}>
      <EntitlementDetailPageContent />
    </ProtectedRoute>
  );
}
