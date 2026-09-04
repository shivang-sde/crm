"use client";

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, Pencil, Power, Search } from "lucide-react";

import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import { toast } from "sonner";
import { api } from "@/lib/api/client";
import { unwrapResponse } from "@/lib/api/api-utils";

interface Provider {
  id: string;
  providerKey: string;
  providerName: string;
  description?: string;
  category: string;
  isActive: boolean;
  supportsClickToCall: boolean;
}

function ProvidersList() {
  const queryClient = useQueryClient();
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editing, setEditing] = useState<Provider | null>(null);
  const [form, setForm] = useState({ providerKey: "", providerName: "", description: "", category: "CALLING", isActive: true, supportsClickToCall: true });

  const { data: providers, isLoading, refetch } = useQuery({
    queryKey: ["platform-providers"],
    queryFn: async () => {
      const res = await api.get("/platform/providers");
      return unwrapResponse(res) as Provider[];
    },
  });

  const createMutation = useMutation({
    mutationFn: async (data: typeof form) => {
      const res = await api.post("/platform/providers", data);
      return unwrapResponse(res);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["platform-providers"] });
      toast.success("Provider created");
      setIsDialogOpen(false);
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || "Create failed"),
  });

  const updateMutation = useMutation({
    mutationFn: async ({ id, data }: { id: string; data: typeof form }) => {
      const res = await api.put(`/platform/providers/${id}`, data);
      return unwrapResponse(res);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["platform-providers"] });
      toast.success("Provider updated");
      setIsDialogOpen(false);
    },
    onError: (e: any) => toast.error(e?.response?.data?.message || "Update failed"),
  });

  const toggleActive = async (provider: Provider) => {
    try {
      await api.patch(`/platform/providers/${provider.id}/status`, { isActive: !provider.isActive });
      queryClient.invalidateQueries({ queryKey: ["platform-providers"] });
      toast.success(provider.isActive ? "Provider deactivated" : "Provider activated");
    } catch {
      toast.error("Status update failed");
    }
  };

  const openCreate = () => {
    setEditing(null);
    setForm({ providerKey: "", providerName: "", description: "", category: "CALLING", isActive: true, supportsClickToCall: true });
    setIsDialogOpen(true);
  };

  const openEdit = (provider: Provider) => {
    setEditing(provider);
    setForm({
      providerKey: provider.providerKey,
      providerName: provider.providerName,
      description: provider.description || "",
      category: provider.category || "CALLING",
      isActive: provider.isActive,
      supportsClickToCall: provider.supportsClickToCall,
    });
    setIsDialogOpen(true);
  };

  const handleSubmit = () => {
    if (!form.providerKey.trim() || !form.providerName.trim()) {
      toast.error("Provider key and name are required");
      return;
    }
    if (editing) {
      updateMutation.mutate({ id: editing.id, data: form });
    } else {
      createMutation.mutate(form);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900" />
      </div>
    );
  }

  return (
    <div className="container mx-auto py-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Provider Definitions</h1>
          <p className="text-muted-foreground mt-1">Platform-level calling providers. Active providers are available to tenants for creating connections.</p>
        </div>
        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" /> New Provider
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Providers</CardTitle>
          <CardDescription>Manage platform provider catalog. Provider key is immutable after creation.</CardDescription>
        </CardHeader>
        <CardContent>
          {providers && providers.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Provider</TableHead>
                  <TableHead>Key</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Capability</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {providers.map((provider) => (
                  <TableRow key={provider.id}>
                    <TableCell className="font-medium">{provider.providerName}</TableCell>
                    <TableCell className="font-mono text-sm">{provider.providerKey}</TableCell>
                    <TableCell><Badge variant="outline">{provider.category}</Badge></TableCell>
                    <TableCell>{provider.supportsClickToCall ? <Badge>Click to Call</Badge> : <span className="text-xs text-muted-foreground">—</span>}</TableCell>
                    <TableCell>{provider.isActive ? <Badge className="bg-green-500">Active</Badge> : <Badge variant="destructive">Inactive</Badge>}</TableCell>
                    <TableCell>
                      <div className="flex gap-2">
                        <Button size="sm" variant="ghost" onClick={() => openEdit(provider)}><Pencil className="h-4 w-4" /></Button>
                        <Button size="sm" variant="ghost" onClick={() => toggleActive(provider)}><Power className={`h-4 w-4 ${provider.isActive ? "text-green-600" : "text-muted-foreground"}`} /></Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <div className="text-center py-12">
              <h3 className="text-lg font-semibold mb-2">No providers found</h3>
              <p className="text-muted-foreground mb-4">Create the first provider to make it available to tenants.</p>
              <Button onClick={openCreate} variant="outline"><Plus className="mr-2 h-4 w-4" /> Create provider</Button>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? "Edit Provider" : "New Provider"}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1">
              <Label>Provider name *</Label>
              <Input value={form.providerName} onChange={(e) => setForm({ ...form, providerName: e.target.value })} placeholder="Exotel" />
            </div>
            <div className="space-y-1">
              <Label>Provider key *</Label>
              <Input value={form.providerKey} onChange={(e) => setForm({ ...form, providerKey: e.target.value.toLowerCase() })} placeholder="exotel" disabled={!!editing} />
              {editing && <p className="text-xs text-muted-foreground">Provider key cannot be changed after creation.</p>}
            </div>
            <div className="space-y-1">
              <Label>Description</Label>
              <Textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Exotel cloud telephony provider" rows={2} />
            </div>
            <div className="space-y-1">
              <Label>Category</Label>
              <Input value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} placeholder="CALLING" />
            </div>
            <div className="flex items-center gap-2">
              <Checkbox id="clickToCall" checked={form.supportsClickToCall} onCheckedChange={(v) => setForm({ ...form, supportsClickToCall: !!v })} />
              <Label htmlFor="clickToCall">Click to Call capability</Label>
            </div>
            <div className="flex items-center gap-2">
              <Switch id="active" checked={form.isActive} onCheckedChange={(v) => setForm({ ...form, isActive: v })} />
              <Label htmlFor="active">Active — available to tenants</Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsDialogOpen(false)}>Cancel</Button>
            <Button onClick={handleSubmit} disabled={createMutation.isPending || updateMutation.isPending}>Save</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

export default function ProvidersPage() {
  return (
    <ProtectedRoute>
      <ProvidersList />
    </ProtectedRoute>
  );
}
