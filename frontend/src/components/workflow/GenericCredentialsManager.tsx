"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { toast } from "sonner";
import { useQuery } from "@tanstack/react-query";
import { userApi } from "@/lib/api/users";
import {
  useHttpCredentialTenantStatus,
  useHttpCredentialUserStatus,
  usePutHttpCredentialTenant,
  usePutHttpCredentialUser,
  useDeleteHttpCredentialTenant,
  useDeleteHttpCredentialUser,
} from "@/lib/hooks/workflow";

function KeyValueEditor({
  pairs,
  setPairs,
  readOnly,
}: {
  pairs: Array<{ key: string; value: string }>;
  setPairs: (p: Array<{ key: string; value: string }>) => void;
  readOnly?: boolean;
}) {
  return (
    <div className="space-y-2">
      {pairs.map((row, idx) => (
        <div key={idx} className="flex gap-2">
          <Input
            placeholder="Key (e.g. apiKey)"
            value={row.key}
            disabled={readOnly}
            onChange={(e) => {
              const next = [...pairs];
              next[idx] = { ...row, key: e.target.value };
              setPairs(next);
            }}
            className="flex-1"
          />
          <Input
            placeholder="Value"
            type="password"
            value={row.value}
            disabled={readOnly}
            onChange={(e) => {
              const next = [...pairs];
              next[idx] = { ...row, value: e.target.value };
              setPairs(next);
            }}
            className="flex-1"
          />
          {!readOnly && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => setPairs(pairs.filter((_, i) => i !== idx))}
            >
              ✕
            </Button>
          )}
        </div>
      ))}
      {!readOnly && (
        <Button type="button" variant="outline" size="sm" onClick={() => setPairs([...pairs, { key: "", value: "" }])}>
          Add entry
        </Button>
      )}
    </div>
  );
}

export function GenericCredentialsManager() {
  const tenantStatus = useHttpCredentialTenantStatus();
  const putTenant = usePutHttpCredentialTenant();
  const delTenant = useDeleteHttpCredentialTenant();

  const [tenantPairs, setTenantPairs] = useState<Array<{ key: string; value: string }>>([{ key: "apiKey", value: "" }]);
  const [selectedUserId, setSelectedUserId] = useState<string>("");
  const userStatus = useHttpCredentialUserStatus(selectedUserId || undefined);
  const putUser = usePutHttpCredentialUser();
  const delUser = useDeleteHttpCredentialUser();
  const [userPairs, setUserPairs] = useState<Array<{ key: string; value: string }>>([{ key: "apiKey", value: "" }]);
  const tenantUsersQuery = useQuery({
    queryKey: ["tenant-users", "active"],
    queryFn: () => userApi.getUsers({ page: 0, isActive: true }),
    staleTime: 60 * 1000,
  });
  const userOptions = (tenantUsersQuery.data?.content ?? []).map((u: { id?: string; firstName?: string; lastName?: string; email?: string }) => ({
    value: String(u.id ?? ""),
    label: [u.firstName, u.lastName].filter(Boolean).join(" ") || String(u.email ?? u.id),
  }));

  const saveTenant = async () => {
    const credential: Record<string, string> = {};
    for (const p of tenantPairs) if (p.key.trim() && p.value.trim()) credential[p.key.trim()] = p.value;
    if (Object.keys(credential).length === 0) {
      toast.error("Enter at least one key and value");
      return;
    }
    try {
      await putTenant.mutateAsync(credential);
      toast.success("Workspace credential saved securely");
      setTenantPairs([{ key: "apiKey", value: "" }]);
    } catch {
      toast.error("Failed to save workspace credential");
    }
  };

  const saveUser = async () => {
    if (!selectedUserId) {
      toast.error("Select a user");
      return;
    }
    const credential: Record<string, string> = {};
    for (const p of userPairs) if (p.key.trim() && p.value.trim()) credential[p.key.trim()] = p.value;
    if (Object.keys(credential).length === 0) {
      toast.error("Enter at least one key and value");
      return;
    }
    try {
      await putUser.mutateAsync({ userId: selectedUserId, credential });
      toast.success("User credential saved securely");
      setUserPairs([{ key: "apiKey", value: "" }]);
    } catch {
      toast.error("Failed to save user credential");
    }
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Workspace credential</CardTitle>
          <CardDescription>
            Shared credential usable when HTTP API node selects “Workspace”. Values are encrypted at rest and never shown after save. Use the same key names you reference as {"{{credential.apiKey}}"} in workflows.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-2">
            {tenantStatus.data?.configured ? <Badge>Configured</Badge> : <Badge variant="outline">Not configured</Badge>}
            {tenantStatus.data?.configured && tenantStatus.data.keys.length > 0 && (
              <span className="text-xs text-muted-foreground">Keys: {tenantStatus.data.keys.join(", ")}</span>
            )}
            {tenantStatus.data?.configured && (
              <Button size="sm" variant="ghost" onClick={() => delTenant.mutateAsync().then(() => toast.success("Workspace credential deleted")).catch(() => toast.error("Delete failed"))} disabled={delTenant.isPending}>
                Delete
              </Button>
            )}
          </div>
          <KeyValueEditor pairs={tenantPairs} setPairs={setTenantPairs} />
          <Button size="sm" onClick={saveTenant} disabled={putTenant.isPending}>
            {putTenant.isPending ? "Saving…" : "Save workspace credential"}
          </Button>
          <p className="text-xs text-muted-foreground">Leave value blank to keep existing secret. After save the form is cleared and only metadata (keys, status) is shown.</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>User credential</CardTitle>
          <CardDescription>
            Credential belonging to a specific user. Used when HTTP API node selects “Specific user” or “Workflow user” / “Record owner” (resolved at execution time). Tenant isolation enforced.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="space-y-1">
            <Label>User</Label>
            <Select value={selectedUserId} onValueChange={setSelectedUserId} disabled={tenantUsersQuery.isLoading}>
              <SelectTrigger><SelectValue placeholder={tenantUsersQuery.isLoading ? "Loading users…" : "Select user"} /></SelectTrigger>
              <SelectContent>
                {tenantUsersQuery.isLoading ? (
                  <div className="p-2 text-sm text-muted-foreground">Loading users…</div>
                ) : tenantUsersQuery.isError ? (
                  <div className="p-2 text-sm text-muted-foreground">Could not load users. <button className="underline" onClick={() => tenantUsersQuery.refetch()}>Retry</button></div>
                ) : userOptions.length === 0 ? (
                  <div className="p-2 text-sm text-muted-foreground">No active users found for this workspace.</div>
                ) : (
                  <>
                    {userOptions.map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                    ))}
                    {selectedUserId && !userOptions.some((o) => o.value === selectedUserId) && (
                      <SelectItem value={selectedUserId}>Previously selected — unavailable / inactive</SelectItem>
                    )}
                  </>
                )}
              </SelectContent>
            </Select>
            {tenantUsersQuery.isError && <p className="text-xs text-muted-foreground">Unable to load users. Check permissions and try again.</p>}
          </div>
          {selectedUserId && (
            <div className="flex items-center gap-2">
              {userStatus.data?.configured ? <Badge>Configured</Badge> : <Badge variant="outline">Not configured</Badge>}
              {userStatus.data?.configured && userStatus.data.keys.length > 0 && (
                <span className="text-xs text-muted-foreground">Keys: {userStatus.data.keys.join(", ")}</span>
              )}
              {userStatus.data?.configured && (
                <Button size="sm" variant="ghost" onClick={() => delUser.mutateAsync(selectedUserId).then(() => toast.success("User credential deleted")).catch(() => toast.error("Delete failed"))} disabled={delUser.isPending}>
                  Delete
                </Button>
              )}
            </div>
          )}
          <KeyValueEditor pairs={userPairs} setPairs={setUserPairs} />
          <Button size="sm" onClick={saveUser} disabled={putUser.isPending || !selectedUserId}>
            {putUser.isPending ? "Saving…" : "Save user credential"}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
