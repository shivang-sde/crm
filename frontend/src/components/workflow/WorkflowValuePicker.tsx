"use client";

import { useMemo, useState } from "react";
import { Braces, ChevronDown, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { useWorkflowMetadata, useWorkflowReferenceData, useWorkflowRelationshipReferenceData } from "@/lib/hooks/workflow";
import { buildFieldOptions, type WorkflowFieldOption, type RecordFieldMeta } from "./utils/field-options";
import type { BuilderNode, BuilderEdge } from "./utils/graph-mapper";
import { useQuery } from "@tanstack/react-query";
import { workflowApi } from "@/lib/api/workflow";
import { workflowKeys } from "@/lib/hooks/workflow";
import { recordFieldApi } from "@/lib/api/records";
import { useQueries } from "@tanstack/react-query";

interface WorkflowValuePickerProps {
  triggerEntityType?: string;
  currentNodeId?: string;
  nodes?: BuilderNode[];
  edges?: BuilderEdge[];
  onSelect: (insertion: string, meta: { label: string; path: string }) => void;
  align?: "start" | "center" | "end";
  credentialContext?: {
    authenticationMode?: string;
    credentialSource?: string;
    credentialSourceUserId?: string;
  };
  /** Current node type for node-aware filtering */
  nodeType?: string;
  /** Current action type for node-aware filtering */
  actionType?: string;
  /** Whether this is a trigger configuration (different from runtime) */
  isTriggerConfig?: boolean;
}

type PickerItem = {
  label: string;
  path: string;
  insertion: string;
  group: string;
  keywords: string;
  disabled?: boolean;
};

function ancestorsOf(currentNodeId: string | undefined, nodes: BuilderNode[] | undefined, edges: BuilderEdge[] | undefined): Set<string> {
  if (!currentNodeId || !nodes || !edges) return new Set();
  const incoming = new Map<string, string[]>();
  for (const e of edges) {
    const arr = incoming.get(e.target) ?? [];
    arr.push(e.source);
    incoming.set(e.target, arr);
  }
  const visited = new Set<string>();
  const queue: string[] = [currentNodeId];
  visited.add(currentNodeId);
  const ancestors = new Set<string>();
  while (queue.length > 0) {
    const cur = queue.shift()!;
    const preds = incoming.get(cur) ?? [];
    for (const p of preds) {
      if (!ancestors.has(p)) ancestors.add(p);
      if (!visited.has(p)) {
        visited.add(p);
        queue.push(p);
      }
    }
  }
  return ancestors;
}

export function WorkflowValuePicker({
  triggerEntityType,
  currentNodeId,
  nodes,
  edges,
  onSelect,
  align = "start",
  credentialContext,
  nodeType,
  actionType,
  isTriggerConfig = false,
}: WorkflowValuePickerProps) {
  const [open, setOpen] = useState(false);
  const metadataQuery = useWorkflowMetadata();
  const metadata = metadataQuery.data;
  const referenceData = useWorkflowReferenceData(triggerEntityType ?? "");
  const entityMeta = buildFieldOptions({ metadata, triggerEntityType }).hasEntity
    ? metadata?.entities.find((e) => e.entityType === triggerEntityType)
    : undefined;
  const relationshipData = useWorkflowRelationshipReferenceData(entityMeta?.relationships);

  // Fetch Record dynamic fields when trigger entity is RECORD
  const recordTypesQuery = useQuery({
    queryKey: ["record-types", "active"],
    queryFn: async () => {
      const res = await workflowApi.listRecordTypes?.(0, 100) ?? { data: [] };
      return (res.data ?? []).filter((rt: any) => rt.isActive);
    },
    enabled: triggerEntityType === "RECORD",
    staleTime: 5 * 60 * 1000,
  });

  const recordFieldQueries = useQueries({
    queries: (recordTypesQuery.data ?? [])
      .slice(0, 20)
      .map((rt: any) => ({
        queryKey: ["record-fields", rt.id],
        queryFn: () => recordFieldApi.list(rt.id),
        enabled: triggerEntityType === "RECORD" && !!rt.id,
        staleTime: 5 * 60 * 1000,
      })),
  });

  const recordFields: RecordFieldMeta[] = useMemo(() => {
    if (triggerEntityType !== "RECORD") return [];
    const seen = new Set<string>();
    const out: RecordFieldMeta[] = [];
    for (const q of recordFieldQueries) {
      const raw = (q as { data?: unknown }).data;
      const fields = Array.isArray(raw) ? (raw as Array<{ fieldKey: string; fieldLabel: string; fieldType: string; referenceEntityType?: string }>) : [];
      for (const f of fields) {
        const key = `${f.fieldKey}:${f.fieldType}:${f.referenceEntityType ?? ""}`;
        if (seen.has(key)) continue;
        seen.add(key);
        out.push({
          fieldKey: f.fieldKey,
          fieldLabel: f.fieldLabel,
          fieldType: f.fieldType,
          referenceEntityType: f.referenceEntityType,
        });
      }
    }
    return out;
  }, [recordFieldQueries, triggerEntityType]);

  // Dynamic credential keys — execution-time secrets, picker shows only key names
  const credentialSource = credentialContext?.credentialSource?.toUpperCase();
  const credentialUserId = credentialContext?.credentialSourceUserId;
  const isCredentialMode = credentialContext?.authenticationMode?.toUpperCase() === "CREDENTIAL";
  const shouldFetchDynamicKeys =
    isCredentialMode && (credentialSource === "TENANT" || credentialSource === "SPECIFIC_USER");
  const dynamicKeysQuery = useQuery({
    queryKey: [...workflowKeys.all, "http-credential-keys", credentialSource ?? "", credentialUserId ?? ""],
    queryFn: () => {
      if (credentialSource === "TENANT") return workflowApi.getHttpCredentialKeys("TENANT");
      if (credentialSource === "SPECIFIC_USER" && credentialUserId) return workflowApi.getHttpCredentialKeys("USER", credentialUserId);
      return Promise.resolve([] as string[]);
    },
    enabled: shouldFetchDynamicKeys && open,
    staleTime: 30 * 1000,
  });

  // Build canonical field options with node-aware filtering
  const { groupedOptions, hasEntity } = useMemo(() =>
    buildFieldOptions({
      metadata,
      triggerEntityType,
      referenceData,
      relationshipData,
      recordFields,
      nodeType,
      actionType,
      isTriggerConfig,
    }), [metadata, triggerEntityType, referenceData, relationshipData, recordFields, nodeType, actionType, isTriggerConfig]);

  const items: PickerItem[] = useMemo(() => {
    const out: PickerItem[] = [];

    // Entity / Record Data / Custom Fields / Related Records / Trigger Metadata
    for (const { groupLabel, options } of groupedOptions) {
      for (const opt of options) {
        const insertion = `{{${opt.field}}}`;
        out.push({
          label: opt.label,
          path: opt.field,
          insertion,
          group: groupLabel,
          keywords: `${opt.label} ${opt.field} ${groupLabel}`.toLowerCase(),
        });
      }
    }

    // Credential namespace — only when CREDENTIAL mode; never show fake static keys
    if (isCredentialMode) {
      const userOptions = referenceData.optionsByField["entity.ownerId"] ?? [];
      const selectedUserLabel =
        credentialSource === "SPECIFIC_USER" && credentialUserId
          ? userOptions.find((o) => o.value === credentialUserId)?.label ?? credentialUserId
          : undefined;
      if (credentialSource === "TENANT") {
        const group = "Credentials — Workspace";
        if (dynamicKeysQuery.isLoading) {
          out.push({
            label: "Loading credentials…",
            path: "credential.loading",
            insertion: "",
            group,
            keywords: "credential loading",
            disabled: true,
          });
        } else if (dynamicKeysQuery.isError) {
          out.push({
            label: "Unable to load credential keys.",
            path: "credential.error",
            insertion: "",
            group,
            keywords: "credential error",
            disabled: true,
          });
        } else if (dynamicKeysQuery.data && dynamicKeysQuery.data.length > 0) {
          for (const key of dynamicKeysQuery.data) {
            out.push({
              label: key,
              path: `credential.${key}`,
              insertion: `{{credential.${key}}}`,
              group,
              keywords: `credential ${key}`.toLowerCase(),
            });
          }
        } else {
          out.push({
            label: "No workspace credentials are configured.",
            path: "credential.empty",
            insertion: "",
            group,
            keywords: "credential empty",
            disabled: true,
          });
        }
      } else if (credentialSource === "SPECIFIC_USER") {
        const group = selectedUserLabel ? `Credentials — ${selectedUserLabel}` : "Credentials — Specific user";
        if (!credentialUserId) {
          out.push({
            label: "Select a user to view available credentials.",
            path: "credential.no-user",
            insertion: "",
            group,
            keywords: "credential no user",
            disabled: true,
          });
        } else if (dynamicKeysQuery.isLoading) {
          out.push({
            label: "Loading credentials…",
            path: "credential.loading",
            insertion: "",
            group,
            keywords: "credential loading",
            disabled: true,
          });
        } else if (dynamicKeysQuery.isError) {
          out.push({
            label: "Unable to load credential keys.",
            path: "credential.error",
            insertion: "",
            group,
            keywords: "credential error",
            disabled: true,
          });
        } else if (dynamicKeysQuery.data && dynamicKeysQuery.data.length > 0) {
          for (const key of dynamicKeysQuery.data) {
            out.push({
              label: key,
              path: `credential.${key}`,
              insertion: `{{credential.${key}}}`,
              group,
              keywords: `credential ${key}`.toLowerCase(),
            });
          }
        } else {
          out.push({
            label: "No credentials are configured for this user.",
            path: "credential.empty",
            insertion: "",
            group,
            keywords: "credential empty",
            disabled: true,
          });
        }
      } else if (credentialSource === "WORKFLOW_USER") {
        const group = "Credentials — Workflow user (runtime)";
        out.push({
          label: "Credential fields are resolved at runtime from the workflow user.",
          path: "credential.runtime",
          insertion: "",
          group,
          keywords: "credential runtime",
          disabled: true,
        });
      } else if (credentialSource === "RECORD_OWNER") {
        const group = "Credentials — Record owner (runtime)";
        out.push({
          label: "Credential fields are resolved at runtime from the record owner.",
          path: "credential.runtime",
          insertion: "",
          group,
          keywords: "credential runtime",
          disabled: true,
        });
      } else {
        const group = "Credentials";
        out.push({
          label: "Select a credential source to view available keys.",
          path: "credential.select-source",
          insertion: "",
          group,
          keywords: "credential select",
          disabled: true,
        });
      }
    }

    // Previous Nodes (graph-aware) — only when there are ancestors
    const ancestors = ancestorsOf(currentNodeId, nodes, edges);
    if (nodes && ancestors.size > 0) {
      for (const n of nodes) {
        if (!ancestors.has(n.id)) continue;
        const key = n.data.nodeKey;
        const name = n.data.name || key;
        // Base output
        out.push({
          label: `${name}`,
          path: `nodeOutputs.${key}`,
          insertion: `{{nodeOutputs.${key}}}`,
          group: "Previous Nodes",
          keywords: `${name} ${key} nodeOutputs`.toLowerCase(),
        });
        // HTTP specific subfields - safe additive paths that runtime exposes as outputContext
        if (n.data.nodeType === "ACTION") {
          const cfg = n.data.configuration as Record<string, unknown>;
          const at = typeof cfg.actionType === "string" ? cfg.actionType : "";
          if (at === "HTTP_API") {
            out.push({
              label: `${name} → statusCode`,
              path: `nodeOutputs.${key}.statusCode`,
              insertion: `{{nodeOutputs.${key}.statusCode}}`,
              group: "Previous Nodes",
              keywords: `${name} ${key} statusCode http`.toLowerCase(),
            });
            out.push({
              label: `${name} → response`,
              path: `nodeOutputs.${key}.response`,
              insertion: `{{nodeOutputs.${key}.response}}`,
              group: "Previous Nodes",
              keywords: `${name} ${key} response http`.toLowerCase(),
            });
          }
          // For SET_CONTEXT_VALUE etc expose value path loosely
          if (at === "SET_CONTEXT_VALUE") {
            const c = cfg.config as Record<string, unknown> | undefined;
            const k = typeof c?.key === "string" ? c.key : "";
            if (k) {
              out.push({
                label: `${name} → ${k}`,
                path: `nodeOutputs.${key}.${k}`,
                insertion: `{{nodeOutputs.${key}.${k}}}`,
                group: "Previous Nodes",
                keywords: `${name} ${key} ${k}`.toLowerCase(),
              });
            }
          }
        }
      }
    }

    return out;
  }, [groupedOptions, isCredentialMode, credentialSource, credentialUserId, dynamicKeysQuery.data, dynamicKeysQuery.isLoading, dynamicKeysQuery.isError, nodes, edges, currentNodeId]);

  // Group items — use the canonical group order from buildFieldOptions
  const grouped = useMemo(() => {
    const map = new Map<string, PickerItem[]>();
    for (const it of items) {
      const arr = map.get(it.group) ?? [];
      arr.push(it);
      map.set(it.group, arr);
    }
    // Preserve the canonical group order
    const groupOrder = [
      hasEntity ? "Current Lead" : "",
      hasEntity ? "Current Contact" : "",
      hasEntity ? "Current Account" : "",
      hasEntity ? "Current Deal" : "",
      hasEntity ? "Current Task" : "",
      hasEntity ? "Current Meeting" : "",
      hasEntity ? "Current Call" : "",
      hasEntity ? "Current Record" : "",
      "Record Data",
      "Custom Fields",
      ...Array.from(map.keys()).filter((k) => k.startsWith("Related") || k.startsWith("Converted")),
      "Trigger Metadata",
      "Previous Nodes",
      "Credentials — Workspace",
      "Credentials — Specific user",
      "Credentials — Workflow user (runtime)",
      "Credentials — Record owner (runtime)",
      "Credentials",
    ].filter(Boolean);
    const result: Array<[string, PickerItem[]]> = [];
    for (const g of groupOrder) {
      if (map.has(g)) {
        result.push([g, map.get(g)!]);
        map.delete(g);
      }
    }
    // Any remaining groups
    for (const [g, list] of map) {
      result.push([g, list]);
    }
    return result;
  }, [items, hasEntity]);

  const [query, setQuery] = useState("");
  const filtered = useMemo(() => {
    if (!query.trim()) return grouped;
    const q = query.trim().toLowerCase();
    return grouped
      .map(([group, list]) => [group, list.filter((i) => i.keywords.includes(q) || i.label.toLowerCase().includes(q) || i.path.toLowerCase().includes(q))] as const)
      .filter(([, list]) => list.length > 0);
  }, [grouped, query]);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" size="sm" className="h-7 gap-1.5 text-xs">
          <Braces className="h-3.5 w-3.5" />
          Insert value
          <ChevronDown className="h-3 w-3 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align={align} className="w-[340px] p-0">
        <Command shouldFilter={false}>
          <div className="flex items-center border-b px-2">
            <Search className="mr-2 h-4 w-4 shrink-0 opacity-50" aria-hidden="true" />
            <input
              aria-label="Search workflow values"
              placeholder="Search fields..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="flex h-9 w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              autoFocus
            />
          </div>
          <CommandList className="max-h-[320px]">
            {filtered.length === 0 ? (
              <div className="py-6 text-center text-sm text-muted-foreground">No results. Try a different search or use Advanced.</div>
            ) : (
              filtered.map(([group, list]) => (
                <CommandGroup key={group} heading={group}>
                  {list.map((item) => (
                    <CommandItem
                      key={item.path}
                      value={item.path}
                      disabled={item.disabled}
                      onSelect={() => {
                        if (item.disabled || !item.insertion) return;
                        onSelect(item.insertion, { label: item.label, path: item.path });
                        setOpen(false);
                        setQuery("");
                      }}
                      className={`flex flex-col items-start gap-0.5 ${item.disabled ? "opacity-60 pointer-events-none" : ""}`}
                    >
                      <span className="text-sm font-medium leading-none">{item.label}</span>
                      <span className="font-mono text-[11px] text-muted-foreground">{item.insertion}</span>
                    </CommandItem>
                  ))}
                </CommandGroup>
              ))
            )}
          </CommandList>
          <div className="border-t p-2 text-[11px] text-muted-foreground">
            Inserts a runtime token like <span className="font-mono">{"{{entity.id}}"}</span>. Supports{" "}
            <span className="font-mono">entity.* trigger.* nodeOutputs.* credential.*</span> (credential only when HTTP authentication is Credential).
          </div>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

// Helper for inline text fields with picker + input
export function PickerField({
  label,
  value,
  placeholder,
  readOnly,
  triggerEntityType,
  currentNodeId,
  nodes,
  edges,
  onChange,
  inputType,
  credentialContext,
  nodeType,
  actionType,
  isTriggerConfig,
}: {
  label: string;
  value: string;
  placeholder?: string;
  readOnly?: boolean;
  triggerEntityType?: string;
  currentNodeId?: string;
  nodes?: BuilderNode[];
  edges?: BuilderEdge[];
  onChange: (v: string) => void;
  inputType?: "input" | "textarea";
  credentialContext?: {
    authenticationMode?: string;
    credentialSource?: string;
    credentialSourceUserId?: string;
  };
  nodeType?: string;
  actionType?: string;
  isTriggerConfig?: boolean;
}) {
  const [hasInvalidRef, invalidRef] = useMemo(() => {
    if (!value) return [false, null] as const;
    const tokens = Array.from(value.matchAll(/\{\{\s*([^{}]+?)\s*\}\}/g)).map((m) => m[1].trim());
    for (const t of tokens) {
      if (t.startsWith("nodeOutputs.")) {
        const key = t.split(".")[1];
        if (key && nodes && !nodes.some((n) => n.data.nodeKey === key)) {
          return [true, `Unknown node: ${key}`] as const;
        }
      }
    }
    return [false, null] as const;
  }, [value, nodes]);

  const handleInsert = (insertion: string) => {
    // Append with space if value already has content and not ending with space
    if (!value) onChange(insertion);
    else if (value.endsWith(" ") || value.endsWith("\n")) onChange(value + insertion);
    else onChange(value + " " + insertion);
  };

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between gap-2">
        <span className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">{label}</span>
        {!readOnly && (
          <WorkflowValuePicker
            triggerEntityType={triggerEntityType}
            currentNodeId={currentNodeId}
            nodes={nodes}
            edges={edges}
            credentialContext={credentialContext}
            nodeType={nodeType}
            actionType={actionType}
            isTriggerConfig={isTriggerConfig}
            onSelect={(ins) => handleInsert(ins)}
          />
        )}
      </div>
      {inputType === "textarea" ? (
        <textarea
          value={value}
          placeholder={placeholder}
          disabled={readOnly}
          onChange={(e) => onChange(e.target.value)}
          rows={3}
          className="flex min-h-[60px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        />
      ) : (
        <Input value={value} placeholder={placeholder} disabled={readOnly} onChange={(e) => onChange(e.target.value)} />
      )}
      {hasInvalidRef && <p className="text-xs font-medium text-amber-600" role="alert">⚠ {invalidRef} — reference may be stale (node deleted).</p>}
      <p className="text-[11px] text-muted-foreground">Static text or <span className="font-mono">{"{{entity.*}}"}</span> tokens. Use Insert value to discover.</p>
    </div>
  );
}