"use client";

import { useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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
import { toast } from "sonner";
import {
  useWorkflowHttpConnections,
  useCreateHttpConnection,
  useUpdateHttpConnection,
  useDeleteHttpConnection,
  useTestHttpConnection,
} from "@/lib/hooks/workflow";
import type { HttpConnectionOption } from "@/types/workflow";

const AUTH_TYPES = [
  { value: "NONE", label: "None (unauthenticated)" },
  { value: "API_KEY", label: "API key" },
  { value: "BEARER", label: "Bearer token" },
  { value: "BASIC_AUTH", label: "Basic auth" },
] as const;

interface FormState {
  id?: string;
  name: string;
  authType: string;
  active: boolean;
  apiKey: string;
  token: string;
  username: string;
  password: string;
}

const EMPTY_FORM: FormState = {
  name: "",
  authType: "NONE",
  active: true,
  apiKey: "",
  token: "",
  username: "",
  password: "",
};

function credentialPayload(form: FormState): Record<string, string> | undefined {
  switch (form.authType) {
    case "API_KEY":
      return form.apiKey ? { apiKey: form.apiKey } : undefined;
    case "BEARER":
      return form.token ? { token: form.token } : undefined;
    case "BASIC_AUTH":
      return form.username || form.password
        ? { username: form.username, password: form.password }
        : undefined;
    default:
      return undefined;
  }
}

export function HttpConnectionsManager() {
  const connections = useWorkflowHttpConnections();
  const createConnection = useCreateHttpConnection();
  const updateConnection = useUpdateHttpConnection();
  const deleteConnection = useDeleteHttpConnection();
  const testConnection = useTestHttpConnection();

  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [deleteTarget, setDeleteTarget] = useState<HttpConnectionOption | null>(null);
  const [testUrl, setTestUrl] = useState<Record<string, string>>({});

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setFormOpen(true);
  };

  const openEdit = (connection: HttpConnectionOption) => {
    setForm({
      id: connection.id,
      name: connection.name,
      authType: connection.authType || "NONE",
      active: connection.active,
      apiKey: "",
      token: "",
      username: "",
      password: "",
    });
    setFormOpen(true);
  };

  // Credential fields are write-only: blank means "keep the stored secret".
  const submit = async () => {
    if (!form.name.trim()) {
      toast.error("Name is required");
      return;
    }
    try {
      if (form.id) {
        await updateConnection.mutateAsync({
          id: form.id,
          name: form.name.trim(),
          authType: form.authType,
          active: form.active,
          ...(form.authType !== "NONE" ? { credential: credentialPayload(form) } : {}),
        });
        toast.success("Connection updated");
      } else {
        await createConnection.mutateAsync({
          name: form.name.trim(),
          authType: form.authType,
          active: form.active,
          ...(form.authType !== "NONE" ? { credential: credentialPayload(form) } : {}),
        });
        toast.success("Connection created");
      }
      setFormOpen(false);
    } catch {
      toast.error("Save failed", { description: "Check the values and try again." });
    }
  };

  const runTest = async (connection: HttpConnectionOption) => {
    const url = testUrl[connection.id]?.trim();
    if (!url) {
      toast.error("Enter an HTTPS test URL first");
      return;
    }
    try {
      const result = await testConnection.mutateAsync({ id: connection.id, url });
      if (result.success) {
        toast.success(`Test succeeded (${result.statusCode})`);
      } else {
        toast.error(`Test failed (${result.statusCode})`, { description: result.message });
      }
    } catch {
      toast.error("Test failed");
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          Credentials are encrypted at rest and referenced by workflows through
          a connection ID only.
        </p>
        <Button size="sm" onClick={openCreate}>New connection</Button>
      </div>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Auth type</TableHead>
            <TableHead>Credential</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Updated</TableHead>
            <TableHead className="w-56">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {(connections.data ?? []).map((connection) => (
            <TableRow key={connection.id}>
              <TableCell className="font-medium">{connection.name}</TableCell>
              <TableCell>{connection.authType}</TableCell>
              <TableCell>
                {connection.credentialConfigured ? (
                  <Badge variant="secondary">Configured</Badge>
                ) : (
                  <span className="text-xs text-muted-foreground">—</span>
                )}
              </TableCell>
              <TableCell>
                {connection.active ? (
                  <Badge>Active</Badge>
                ) : (
                  <Badge variant="outline">Inactive</Badge>
                )}
              </TableCell>
              <TableCell className="text-xs text-muted-foreground">
                {new Date(connection.updatedAt).toLocaleString()}
              </TableCell>
              <TableCell>
                <div className="flex flex-wrap items-center gap-2">
                  <Input
                    className="h-8 w-52"
                    placeholder="https://api.example.com/ping"
                    value={testUrl[connection.id] ?? ""}
                    disabled={connections.isFetching}
                    onChange={(event) =>
                      setTestUrl((current) => ({
                        ...current,
                        [connection.id]: event.target.value,
                      }))
                    }
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={testConnection.isPending}
                    onClick={() => runTest(connection)}
                  >
                    Test
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => openEdit(connection)}>
                    Edit
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setDeleteTarget(connection)}>
                    Delete
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
          {(connections.data ?? []).length === 0 && (
            <TableRow>
              <TableCell colSpan={6} className="text-center text-sm text-muted-foreground">
                No outbound HTTP connections yet.
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{form.id ? "Edit connection" : "New connection"}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="hc-name">Name</Label>
              <Input
                id="hc-name"
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                placeholder="Partner CRM API"
              />
            </div>
            <div className="space-y-1">
              <Label>Authentication</Label>
              <Select
                value={form.authType}
                onValueChange={(authType) => setForm({ ...form, authType })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {AUTH_TYPES.map((type) => (
                    <SelectItem key={type.value} value={type.value}>
                      {type.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {form.authType === "API_KEY" && (
              <div className="space-y-1">
                <Label htmlFor="hc-apikey">API key {form.id ? "(leave blank to keep)" : ""}</Label>
                <Input
                  id="hc-apikey"
                  type="password"
                  autoComplete="new-password"
                  value={form.apiKey}
                  onChange={(event) => setForm({ ...form, apiKey: event.target.value })}
                />
              </div>
            )}
            {form.authType === "BEARER" && (
              <div className="space-y-1">
                <Label htmlFor="hc-token">Token {form.id ? "(leave blank to keep)" : ""}</Label>
                <Input
                  id="hc-token"
                  type="password"
                  autoComplete="new-password"
                  value={form.token}
                  onChange={(event) => setForm({ ...form, token: event.target.value })}
                />
              </div>
            )}
            {form.authType === "BASIC_AUTH" && (
              <>
                <div className="space-y-1">
                  <Label htmlFor="hc-user">Username</Label>
                  <Input
                    id="hc-user"
                    autoComplete="off"
                    value={form.username}
                    onChange={(event) => setForm({ ...form, username: event.target.value })}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="hc-pass">Password {form.id ? "(leave blank to keep)" : ""}</Label>
                  <Input
                    id="hc-pass"
                    type="password"
                    autoComplete="new-password"
                    value={form.password}
                    onChange={(event) => setForm({ ...form, password: event.target.value })}
                  />
                </div>
              </>
            )}
            <div className="flex items-center gap-2">
              <Switch
                id="hc-active"
                checked={form.active}
                onCheckedChange={(active) => setForm({ ...form, active })}
              />
              <Label htmlFor="hc-active">Active — usable by workflows</Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFormOpen(false)}>Cancel</Button>
            <Button onClick={submit} disabled={createConnection.isPending || updateConnection.isPending}>
              Save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete connection?</AlertDialogTitle>
            <AlertDialogDescription>
              Workflows referencing this connection will fail with a controlled
              error until they are reconfigured.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={async () => {
                if (!deleteTarget) return;
                await deleteConnection.mutateAsync(deleteTarget.id);
                toast.success("Connection deleted");
                setDeleteTarget(null);
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
