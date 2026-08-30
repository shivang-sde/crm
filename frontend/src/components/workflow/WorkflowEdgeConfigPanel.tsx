"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Trash2 } from "lucide-react";

interface WorkflowEdgeConfigPanelProps {
  edge: {
    id: string;
    source?: string;
    target?: string;
    data: {
      edgeKey: string | null;
      configuration: Record<string, unknown>;
      sourceNodeType?: string;
    };
  } | null;
  nodes?: Array<{ id: string; data: { name: string; nodeKey: string } }>;
  readOnly?: boolean;
  onChange: (data: { edgeKey: string | null; configuration: Record<string, unknown> }) => void;
  onDelete?: () => void;
}

export function WorkflowEdgeConfigPanel({
  edge,
  nodes = [],
  readOnly = false,
  onChange,
  onDelete,
}: WorkflowEdgeConfigPanelProps) {
  if (!edge) {
    return (
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Connection
        </p>
        <p className="text-sm text-muted-foreground">Select a connection to inspect it.</p>
      </div>
    );
  }

  const isConditionEdge = edge.data.sourceNodeType === "CONDITION";
  const isBranchEdge = edge.data.sourceNodeType === "BRANCH";
  const outcome =
    typeof edge.data.configuration.outcome === "string"
      ? edge.data.configuration.outcome
      : "";
  const sourceName = edge.source ? nodes.find((n) => n.id === edge.source)?.data.name ?? edge.source.slice(0, 8) : "—";
  const targetName = edge.target ? nodes.find((n) => n.id === edge.target)?.data.name ?? edge.target.slice(0, 8) : "—";
  const sourceKey = edge.source ? nodes.find((n) => n.id === edge.source)?.data.nodeKey ?? "" : "";
  const targetKey = edge.target ? nodes.find((n) => n.id === edge.target)?.data.nodeKey ?? "" : "";
  const outputLabel =
    outcome === "TRUE" || outcome === "FALSE"
      ? outcome
      : edge.data.edgeKey === "TRUE" || edge.data.edgeKey === "FALSE"
        ? edge.data.edgeKey
        : "NEXT";
  const typeLabel = isConditionEdge ? "CONDITION" : isBranchEdge ? "BRANCH" : "Sequential";

  return (
    <div className="space-y-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        Connection
      </p>
      <div className="rounded-md border bg-muted/40 p-3 text-xs leading-5">
        <div className="flex justify-between gap-2">
          <span className="font-medium text-muted-foreground">From</span>
          <span className="truncate font-medium text-foreground" title={sourceKey}>{sourceName}</span>
        </div>
        {sourceKey && <p className="truncate font-mono text-[11px] text-muted-foreground">{sourceKey}</p>}
        <div className="mt-1 flex justify-between gap-2">
          <span className="font-medium text-muted-foreground">Output</span>
          <span className="rounded bg-white px-1.5 py-0.5 text-[11px] font-semibold border">{outputLabel}</span>
        </div>
        <div className="mt-1 flex justify-between gap-2">
          <span className="font-medium text-muted-foreground">To</span>
          <span className="truncate font-medium text-foreground" title={targetKey}>{targetName}</span>
        </div>
        {targetKey && <p className="truncate font-mono text-[11px] text-muted-foreground">{targetKey}</p>}
        <div className="mt-1 flex justify-between gap-2">
          <span className="font-medium text-muted-foreground">Type</span>
          <span className="text-foreground">{typeLabel}</span>
        </div>
      </div>

      <div className="space-y-1">
        <Label htmlFor="edge-key">Edge key</Label>
        <Input
          id="edge-key"
          value={edge.data.edgeKey ?? ""}
          disabled={readOnly}
          onChange={(event) =>
            onChange({ edgeKey: event.target.value, configuration: edge.data.configuration })
          }
        />
      </div>

      {isConditionEdge && (
        <div className="space-y-1">
          <Label>Outcome</Label>
          <Select
            value={outcome}
            disabled={readOnly}
            onValueChange={(value) =>
              onChange({
                edgeKey: edge.data.edgeKey,
                configuration: { ...edge.data.configuration, outcome: value },
              })
            }
          >
            <SelectTrigger>
              <SelectValue placeholder="Select outcome" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="TRUE">TRUE</SelectItem>
              <SelectItem value="FALSE">FALSE</SelectItem>
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            A condition requires exactly one TRUE and one FALSE connection.
          </p>
        </div>
      )}

      {!isConditionEdge && (
        <p className="text-xs text-muted-foreground">
          This connection has no additional configuration.
        </p>
      )}

      {!readOnly && onDelete && (
        <Button variant="destructive" size="sm" onClick={onDelete}>
          <Trash2 className="mr-2 h-4 w-4" /> Delete connection
        </Button>
      )}
    </div>
  );
}
