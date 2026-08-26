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
    data: {
      edgeKey: string | null;
      configuration: Record<string, unknown>;
      sourceNodeType?: string;
    };
  } | null;
  readOnly?: boolean;
  onChange: (data: { edgeKey: string | null; configuration: Record<string, unknown> }) => void;
  onDelete?: () => void;
}

export function WorkflowEdgeConfigPanel({
  edge,
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
  const outcome =
    typeof edge.data.configuration.outcome === "string"
      ? edge.data.configuration.outcome
      : "";

  return (
    <div className="space-y-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        Connection
      </p>

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
