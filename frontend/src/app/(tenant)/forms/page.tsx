"use client";

import { useState } from "react";
import Link from "next/link";
import { Plus, FileText, ExternalLink, Copy, Pencil, Trash2, Eye, Code } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
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
import { useForms, useCreateForm, useDeleteForm, useDuplicateForm } from "@/lib/hooks/forms";
import { usePermissions } from "@/lib/hooks/usePermissions";

function getEmbedCode(publicKey: string) {
  const origin = typeof window !== "undefined" ? window.location.origin : "https://crm.example.com";
  const src = `${origin}/forms/public/${publicKey}`;
  return `<iframe\n  src="${src}"\n  width="100%"\n  height="600"\n  frameborder="0"\n  loading="lazy"\n  style="border:0; max-width:100%;"\n  title="Lead Form">\n</iframe>\n<script>\nwindow.addEventListener("message", function(e) {\n  if (e.data && e.data.type === "FORM_HEIGHT_CHANGED" && e.data.publicKey === "${publicKey}") {\n    var iframe = document.querySelector('iframe[src*="${publicKey}"]');\n    if (iframe && e.data.height) iframe.style.height = e.data.height + "px";\n  }\n});\n</script>`;
}

export default function FormsPage() {
  const { canViewAcquisition } = usePermissions();
  const { data, isLoading, isError, refetch } = useForms();
  const createMut = useCreateForm();
  const deleteMut = useDeleteForm();
  const duplicateMut = useDuplicateForm();

  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [embedForm, setEmbedForm] = useState<{ publicKey: string; name: string } | null>(null);

  const forms = data?.data ?? [];

  if (!canViewAcquisition) {
    return (
      <div className="p-6">
        <p className="text-sm text-muted-foreground">You do not have permission to view forms.</p>
      </div>
    );
  }

  const handleCreate = async () => {
    if (!name.trim()) {
      toast.error("Form name is required");
      return;
    }
    try {
      const created = await createMut.mutateAsync({ name: name.trim(), description: description.trim() || undefined });
      toast.success("Form created");
      setCreateOpen(false);
      setName("");
      setDescription("");
      // Optionally navigate to builder
      // router.push(`/forms/${created.id}/edit`);
    } catch (e: any) {
      toast.error(e?.response?.data?.error?.message ?? "Failed to create form");
    }
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Lead Forms</h1>
          <p className="text-sm text-muted-foreground">Design forms, map to CRM, publish, and collect leads. Each form has its own lifecycle.</p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="mr-2 h-4 w-4" /> Create Form
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Forms</CardTitle>
          <CardDescription>Draft → Published → Unpublished. Published forms have a public URL.</CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Loading forms…</p>
          ) : isError ? (
            <div className="space-y-2">
              <p className="text-sm text-red-600">Failed to load forms.</p>
              <Button variant="outline" size="sm" onClick={() => refetch()}>
                Retry
              </Button>
            </div>
          ) : forms.length === 0 ? (
            <div className="rounded-lg border border-dashed p-8 text-center">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-muted">
                <FileText className="h-6 w-6 text-muted-foreground" />
              </div>
              <h3 className="mt-3 font-semibold">No forms yet</h3>
              <p className="text-sm text-muted-foreground">Create your first form to start collecting leads.</p>
              <Button className="mt-3" onClick={() => setCreateOpen(true)}>
                <Plus className="mr-2 h-4 w-4" /> Create Form
              </Button>
            </div>
          ) : (
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {forms.map((form) => (
                <Card key={form.id} className="overflow-hidden">
                  <CardHeader className="pb-2">
                    <div className="flex items-start justify-between gap-2">
                      <CardTitle className="text-base truncate">{form.name}</CardTitle>
                      <Badge variant={form.status === "PUBLISHED" ? "default" : form.status === "DRAFT" ? "secondary" : "outline"}>
                        {form.status}
                      </Badge>
                    </div>
                    <CardDescription className="truncate">{form.description ?? "No description"}</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-2">
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <span>{form.fields?.length ?? 0} fields</span>
                      <span>·</span>
                      <span>Updated {new Date(form.updatedAt).toLocaleDateString()}</span>
                    </div>
                    {form.publicKey && form.status === "PUBLISHED" && (
                      <div className="flex items-center gap-1 rounded-md border bg-muted/30 px-2 py-1">
                        <code className="text-xs truncate flex-1">{`/forms/public/${form.publicKey}`}</code>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-5 w-5"
                          onClick={() => {
                            navigator.clipboard.writeText(`${window.location.origin}/forms/public/${form.publicKey}`);
                            toast.success("Form URL copied");
                          }}
                        >
                          <Copy className="h-3 w-3" />
                        </Button>
                        <Link href={`/forms/public/${form.publicKey}`} target="_blank">
                          <Button variant="ghost" size="icon" className="h-5 w-5">
                            <ExternalLink className="h-3 w-3" />
                          </Button>
                        </Link>
                      </div>
                    )}
                    <div className="flex flex-wrap gap-2">
                      <Link href={`/forms/${form.id}/edit`}>
                        <Button size="sm" variant="outline">
                          <Pencil className="mr-1 h-3 w-3" /> Builder
                        </Button>
                      </Link>
                      {form.status === "PUBLISHED" && form.publicKey && (
                        <Button size="sm" variant="outline" onClick={() => setEmbedForm({ publicKey: form.publicKey!, name: form.name })}>
                          <Code className="mr-1 h-3 w-3" /> Embed
                        </Button>
                      )}
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={async () => {
                          try {
                            await duplicateMut.mutateAsync(form.id);
                            toast.success("Form duplicated");
                          } catch {
                            toast.error("Duplicate failed");
                          }
                        }}
                      >
                        <Copy className="mr-1 h-3 w-3" /> Duplicate
                      </Button>
                      <Button size="sm" variant="destructive" onClick={() => setDeleteId(form.id)}>
                        <Trash2 className="mr-1 h-3 w-3" /> Delete
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create form</DialogTitle>
            <DialogDescription>Start with a blank form or a template. You’ll design fields next.</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="formName">Form name *</Label>
              <Input id="formName" value={name} onChange={(e) => setName(e.target.value)} placeholder="Website Demo Request" />
            </div>
            <div className="space-y-1">
              <Label htmlFor="formDesc">Description</Label>
              <Textarea id="formDesc" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Collect leads for demo requests" rows={3} />
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setCreateOpen(false)}>
                Cancel
              </Button>
              <Button onClick={handleCreate} disabled={createMut.isPending}>
                {createMut.isPending ? "Creating…" : "Create form"}
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">Tip: After creation, add fields like “Business Email” mapped to Lead → Email.</p>
          </div>
        </DialogContent>
      </Dialog>

      <AlertDialog open={!!deleteId} onOpenChange={(o) => !o && setDeleteId(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete form?</AlertDialogTitle>
            <AlertDialogDescription>
              This will delete the form definition and its fields. Published public forms will become unavailable. Existing leads and events are preserved.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={async () => {
                if (!deleteId) return;
                try {
                  await deleteMut.mutateAsync(deleteId);
                  toast.success("Form deleted");
                  setDeleteId(null);
                } catch {
                  toast.error("Delete failed");
                }
              }}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={!!embedForm} onOpenChange={(o) => !o && setEmbedForm(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Embed — {embedForm?.name}</DialogTitle>
            <DialogDescription>Copy the iframe code to embed this form on an external website. Only the public URL is exposed.</DialogDescription>
          </DialogHeader>
          {embedForm && (
            <div className="space-y-3">
              <div>
                <p className="text-xs font-medium">Public URL</p>
                <div className="flex items-center gap-2 rounded-md border bg-muted/30 px-2 py-1">
                  <code className="text-xs truncate flex-1">{`${typeof window !== "undefined" ? window.location.origin : ""}/forms/public/${embedForm.publicKey}`}</code>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-5 w-5"
                    onClick={() => {
                      navigator.clipboard.writeText(`${window.location.origin}/forms/public/${embedForm.publicKey}`);
                      toast.success("Public URL copied");
                    }}
                  >
                    <Copy className="h-3 w-3" />
                  </Button>
                </div>
              </div>
              <div>
                <p className="text-xs font-medium">Embed code (iframe)</p>
                <pre className="rounded-md border bg-muted/30 p-3 text-xs overflow-auto max-h-64 whitespace-pre-wrap break-all">
                  {getEmbedCode(embedForm.publicKey)}
                </pre>
                <Button
                  size="sm"
                  className="mt-2"
                  onClick={() => {
                    navigator.clipboard.writeText(getEmbedCode(embedForm.publicKey));
                    toast.success("Embed code copied");
                  }}
                >
                  <Copy className="mr-1 h-3 w-3" /> Copy embed code
                </Button>
                <p className="text-xs text-muted-foreground mt-1">Width 100%, height 600px by default. Auto-resize via postMessage is included.</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
