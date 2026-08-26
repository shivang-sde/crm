"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, ArrowRight, ClipboardCopy, Loader2, Plus, SquarePen } from "lucide-react";

import { ProtectedRoute } from "@/components/shared/ProtectedRoute";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { roleApi } from "@/lib/api/roles";
import { apiErrorMessage } from "@/lib/api/api-utils";
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Role, RolePermission } from "@/types/rbac";
import { RolePermissionEditor } from "../components/RolePermissionEditor";

const SCOPE_RANK: Record<string, number> = { NONE: 0, OWN: 1, TEAM: 2, ALL: 3 };

/** Diff of the draft against the copy source (or empty start), for review. */
function PermissionReview({ baseline, draft }: { baseline: RolePermission[]; draft: RolePermission[] }) {
  const baselineById = new Map(baseline.map((p) => [p.id, p]));

  const changed = draft
    .filter((p) => baselineById.has(p.id))
    .map((p) => {
      const before = baselineById.get(p.id)!;
      const direction =
        SCOPE_RANK[p.accessScope] > SCOPE_RANK[before.accessScope]
          ? "↑"
          : SCOPE_RANK[p.accessScope] < SCOPE_RANK[before.accessScope]
            ? "↓"
            : null;
      return {
        key: p.id,
        label: `${p.module}:${p.action}`,
        before: before.accessScope,
        after: p.accessScope,
        indicator: direction ?? "=",
      };
    });
  const added = draft
    .filter((p) => !baselineById.has(p.id))
    .map((p) => ({ key: p.id, label: `${p.module}:${p.action}`, before: "—", after: p.accessScope, indicator: "+" }));
  const removed = baseline
    .filter((p) => !draft.some((d) => d.id === p.id))
    .map((p) => ({ key: p.id, label: `${p.module}:${p.action}`, before: p.accessScope, after: "—", indicator: "−" }));
  const rows = [...changed, ...added, ...removed].filter((r) => r.indicator !== "=").sort((a, b) => a.label.localeCompare(b.label));

  return (
    <div className="space-y-2">
      <h3 className="font-semibold">Review Changes</h3>
      <div className="text-sm text-gray-600 space-y-0.5">
        <p>
          Source role: <span className="font-medium text-gray-800">{baseline.length > 0 ? "Copied role" : "Start from scratch"}</span>
        </p>
        {rows.length === 0 ? (
          <p className="text-gray-500">{draft.length} permission{draft.length === 1 ? "" : "s"} configured · no differences from the source.</p>
        ) : (
          <>
            <div className="border rounded-lg overflow-hidden mt-2">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 text-left text-xs uppercase tracking-wide text-gray-500">
                  <tr>
                    <th className="px-3 py-2 font-medium">Permission</th>
                    <th className="px-3 py-2 font-medium">Previous</th>
                    <th className="px-3 py-2 font-medium">New</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.key} className="border-t">
                      <td className="px-3 py-1.5 font-mono text-xs">{row.label}</td>
                      <td className="px-3 py-1.5 text-gray-500">{row.before}</td>
                      <td className="px-3 py-1.5 font-medium">
                        <span aria-hidden className="mr-1 text-gray-400">{row.indicator === "=" ? "" : row.indicator}</span>
                        <span className={row.indicator === "+" ? "sr-only" : undefined}>{row.after}</span>
                        <span className="sr-only">
                          {row.indicator === "+" && " added"}
                          {row.indicator === "−" && " removed"}
                          {row.indicator === "↑" && " scope increased"}
                          {row.indicator === "↓" && " scope reduced"}
                        </span>
                        {row.indicator !== "=" && row.before !== "—" && (
                          <span aria-hidden className="ml-1 text-xs text-gray-400">(was {row.before})</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="text-xs text-gray-400 mt-1">{rows.length} change{rows.length === 1 ? "" : "s"}</p>
          </>
        )}
      </div>
    </div>
  );
}

function CreateRoleFlow() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [step, setStep] = useState<"mode" | "configure">("mode");
  const [copiedFromName, setCopiedFromName] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [baseline, setBaseline] = useState<RolePermission[]>([]);
  const [draft, setDraft] = useState<RolePermission[]>([]);

  // Step 1: mode selection + source picking.
  const [copyPathOpen, setCopyPathOpen] = useState(false);
  const [sourceRoleId, setSourceRoleId] = useState<string>("");

  const { data: roles, isLoading: rolesLoading } = useQuery({
    queryKey: ["roles"],
    queryFn: () => roleApi.getRoles(),
  });

  const {
    data: sourcePermissions,
    isError: sourcePermissionsError,
    refetch: refetchSourcePermissions,
  } = useQuery({
    queryKey: ["role-permissions", sourceRoleId],
    queryFn: () => roleApi.getRolePermissions(sourceRoleId),
    enabled: !!sourceRoleId,
  });

  const startFromScratch = () => {
    setSourceRoleId("");
    setBaseline([]);
    setDraft([]);
    setCopiedFromName(null);
    setStep("configure");
  };

  const continueWithCopy = () => {
    if (!sourceRoleId || !sourcePermissions || !roles) return;
    setBaseline(sourcePermissions.map((p) => ({ ...p })));
    setDraft(sourcePermissions.map((p) => ({ ...p })));
    setCopiedFromName(roles.find((r) => r.id === sourceRoleId)?.name ?? null);
    setStep("configure");
  };

  const createMutation = useMutation({
    mutationFn: () =>
      roleApi.createRole({
        name,
        description: description || undefined,
        permissions: draft.map((p) => ({ permissionId: p.id, accessScope: p.accessScope })),
      }),
    onSuccess: (newRole) => {
      toast.success("Role created successfully");
      queryClient.invalidateQueries({ queryKey: ["roles"] });
      router.push(`/roles/${newRole.id}`);
    },
    onError: (error: unknown) => {
      // Backend stays authoritative (RBAC-6 delegation); surface its message
      // and keep the draft intact so the user can correct scopes.
      toast.error(apiErrorMessage(error, "Failed to create role"));
    },
  });

  const backToMode = () => {
    setStep("mode");
    setCopyPathOpen(false);
    setSourceRoleId("");
  };

  // ---------------------------------------------------------------- Step 1
  if (step === "mode") {
    return (
      <div className="max-w-2xl mx-auto space-y-6">
        <ModeHeader />

        <div className="grid gap-4 sm:grid-cols-2">
          {/* Preferred path first */}
          <button
            type="button"
            onClick={() => setCopyPathOpen(true)}
            className={`text-left p-5 rounded-lg border-2 cursor-pointer transition-colors bg-white hover:border-primary ${
              copyPathOpen ? "border-primary" : "border-primary/40"
            }`}
          >
            <ClipboardCopy className="h-6 w-6 text-primary mb-3" />
            <h2 className="font-semibold">Copy an existing role</h2>
            <p className="text-sm text-gray-500 mt-1">
              Start with permissions from an existing role, then adjust.
            </p>
          </button>

          <button
            type="button"
            onClick={startFromScratch}
            className="text-left p-5 rounded-lg border-2 border-gray-200 cursor-pointer transition-colors bg-white hover:border-gray-400"
          >
            <SquarePen className="h-6 w-6 text-gray-400 mb-3" />
            <h2 className="font-semibold">Start from scratch</h2>
            <p className="text-sm text-gray-500 mt-1">
              Create an empty role and configure access manually.
            </p>
          </button>
        </div>

        {copyPathOpen && (
          <div className="bg-white p-5 rounded-lg border shadow-sm space-y-4">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="sourceRole">Copy permissions from</FieldLabel>
                <Select value={sourceRoleId} onValueChange={setSourceRoleId}>
                  <SelectTrigger id="sourceRole">
                    <SelectValue placeholder={rolesLoading ? "Loading roles..." : "Select a role"} />
                  </SelectTrigger>
                  <SelectContent>
                    {(roles ?? []).map((role: Role) => (
                      <SelectItem key={role.id} value={role.id}>
                        {role.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FieldDescription>
                  This will copy the role&apos;s current permissions. You can modify them before creating the new
                  role — afterwards the two roles are independent.
                </FieldDescription>
              </Field>

              {sourcePermissionsError && (
                <div className="flex items-center justify-between text-sm text-red-600">
                  <span>Could not load this role&apos;s permissions.</span>
                  <Button variant="outline" size="sm" onClick={() => refetchSourcePermissions()}>
                    Retry
                  </Button>
                </div>
              )}

              {sourceRoleId && !sourcePermissionsError && !sourcePermissions && (
                <p className="flex items-center gap-2 text-sm text-gray-500">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading permissions...
                </p>
              )}

              {sourcePermissions && sourcePermissions.length === 0 && (
                <p className="text-sm text-amber-600">
                  This role has no assigned permissions. You can still copy it and add permissions manually.
                </p>
              )}
            </FieldGroup>

            <div className="flex justify-end gap-2">
              <Button
                variant="outline"
                onClick={() => {
                  setCopyPathOpen(false);
                  setSourceRoleId("");
                }}
              >
                Back
              </Button>
              <Button onClick={continueWithCopy} disabled={!sourceRoleId || !sourcePermissions}>
                Continue
                <ArrowRight className="ml-2 h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </div>
    );
  }

  // ---------------------------------------------------------------- Step 2
  return (
    <div className="max-w-5xl mx-auto space-y-8">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={backToMode}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Create Role</h1>
          <p className="text-sm text-gray-500">
            {copiedFromName
              ? `Permissions copied from ${copiedFromName}. Both roles remain independent after creation.`
              : "Configure the new role's access manually."}
          </p>
        </div>
      </div>

      <div className="bg-white p-6 rounded-lg border shadow-sm space-y-6">
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="name">Role Name</FieldLabel>
            <Input
              id="name"
              placeholder="e.g. Sales Executive"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </Field>

          <Field>
            <FieldLabel htmlFor="description">Description (Optional)</FieldLabel>
            <Textarea
              id="description"
              placeholder="Brief description of what this role entails..."
              className="resize-none"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </Field>
        </FieldGroup>

        <PermissionReview baseline={baseline} draft={draft} />
      </div>

      <div className="bg-white p-6 rounded-lg border shadow-sm space-y-4">
        <div>
          <h3 className="font-semibold">Permissions</h3>
          <p className="text-sm text-gray-500 mt-0.5">
            Adjust the permission set before creating the role.
          </p>
        </div>

        <RolePermissionEditor draft={draft} baseline={baseline} onChange={setDraft} />
      </div>

      <div className="flex justify-end pt-4 border-t gap-4">
        <Button type="button" variant="outline" onClick={backToMode}>
          Cancel
        </Button>
        <Button
          onClick={() => createMutation.mutate()}
          disabled={!name.trim() || createMutation.isPending}
        >
          {createMutation.isPending ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Creating...
            </>
          ) : (
            <>
              <Plus className="mr-2 h-4 w-4" />
              Create Role
            </>
          )}
        </Button>
      </div>
    </div>
  );
}

function ModeHeader() {
  return (
    <div>
      <h1 className="text-2xl font-bold tracking-tight">Create Role</h1>
      <p className="text-sm text-gray-500 mt-1">How would you like to start?</p>
    </div>
  );
}

export default function CreateRolePage() {
  return (
    <ProtectedRoute requiredPermission={{ module: "admin", action: "role_manage" }}>
      <CreateRoleFlow />
    </ProtectedRoute>
  );
}
