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
import { buildFieldOptions, findEntityMetadata } from "./utils/field-options";
import type { BuilderNode, BuilderEdge } from "./utils/graph-mapper";

interface WorkflowValuePickerProps {
  triggerEntityType?: string;
  currentNodeId?: string;
  nodes?: BuilderNode[];
  edges?: BuilderEdge[];
  onSelect: (insertion: string, meta: { label: string; path: string }) => void;
  align?: "start" | "center" | "end";
}

type PickerItem = {
  label: string;
  path: string;
  insertion: string;
  group: string;
  keywords: string;
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
}: WorkflowValuePickerProps) {
  const [open, setOpen] = useState(false);
  const metadataQuery = useWorkflowMetadata();
  const metadata = metadataQuery.data;
  const referenceData = useWorkflowReferenceData(triggerEntityType ?? "");
  const entityMeta = findEntityMetadata(metadata, triggerEntityType);
  const relationshipData = useWorkflowRelationshipReferenceData(entityMeta?.relationships);

  const items: PickerItem[] = useMemo(() => {
    const out: PickerItem[] = [];

    // Current Record / Trigger via field-options
    const fieldOptions = buildFieldOptions({
      metadata,
      triggerEntityType,
      referenceData,
      relationshipData,
    });

    for (const opt of fieldOptions) {
      const insertion = `{{${opt.field}}}`;
      out.push({
        label: opt.label,
        path: opt.field,
        insertion,
        group: opt.groupLabel,
        keywords: `${opt.label} ${opt.field} ${opt.groupLabel}`.toLowerCase(),
      });
    }

    // Credential namespace — available for HTTP_API CREDENTIAL mode; shown always for discoverability
    const credentialKeys = ["apiKey", "token", "username", "password", "accountId", "clientId", "secret", "clientSecret", "accessToken", "client_id", "client_secret"];
    for (const key of credentialKeys) {
      out.push({
        label: `Credential: ${key}`,
        path: `credential.${key}`,
        insertion: `{{credential.${key}}}`,
        group: "Credential",
        keywords: `credential ${key}`.toLowerCase(),
      });
    }

    // Previous Nodes (graph-aware)
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
          const actionType = typeof cfg.actionType === "string" ? cfg.actionType : "";
          if (actionType === "HTTP_API") {
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
            // Also expose generic response drill as placeholder for segment etc via advanced
          }
        }
        // For SET_CONTEXT_VALUE etc expose value path loosely
        if (n.data.nodeType === "ACTION") {
          const cfg = n.data.configuration as Record<string, unknown>;
          const at = typeof cfg.actionType === "string" ? cfg.actionType : "";
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
    } else if (nodes) {
      // Fallback if no graph-aware (e.g., before nodes loaded) - expose no previous nodes to avoid leaking future nodes
    }

    return out;
  }, [metadata, triggerEntityType, referenceData, relationshipData, nodes, edges, currentNodeId]);

  // Group items
  const grouped = useMemo(() => {
    const map = new Map<string, PickerItem[]>();
    for (const it of items) {
      const arr = map.get(it.group) ?? [];
      arr.push(it);
      map.set(it.group, arr);
    }
    return Array.from(map.entries());
  }, [items]);

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
                      onSelect={() => {
                        onSelect(item.insertion, { label: item.label, path: item.path });
                        setOpen(false);
                        setQuery("");
                      }}
                      className="flex flex-col items-start gap-0.5"
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
