"use client";

import { useCallback, useMemo } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  WorkflowFieldOption,
} from "./utils/field-options";
import { WorkflowValuePicker } from "./WorkflowValuePicker";
import type { BuilderNode, BuilderEdge } from "./utils/graph-mapper";

type WorkflowValueOption = { value: string; label: string };

export interface ConditionRule {
  field: string;
  operator: string;
  value: string;
}

const OPERATORS = [
  "EQUALS",
  "NOT_EQUALS",
  "GREATER_THAN",
  "GREATER_THAN_OR_EQUAL",
  "LESS_THAN",
  "LESS_THAN_OR_EQUAL",
  "CONTAINS",
  "NOT_CONTAINS",
  "IS_NULL",
  "IS_NOT_NULL",
  "IN",
  "NOT_IN",
] as const;

const NULL_OPERATORS = new Set(["IS_NULL", "IS_NOT_NULL"]);

interface ConditionRulesEditorProps {
  logic: "AND" | "OR";
  rules: ConditionRule[];
  readOnly?: boolean;
  onChange: (logic: "AND" | "OR", rules: ConditionRule[]) => void;
  /**
   * When provided, fields are chosen from grouped, context-aware options
   * (entity / custom field / trigger metadata) instead of free text.
   * Legacy free-text values are preserved and shown as-is until changed.
   */
  fieldOptions?: WorkflowFieldOption[];
  /** Resolves controlled value options for a selected field, if any. */
  resolveValueOptions?: (field: string) => WorkflowValueOption[] | null;
  triggerEntityType?: string;
  currentNodeId?: string;
  nodes?: BuilderNode[];
  edges?: BuilderEdge[];
}

/**
 * Ordered group headers derived from the options themselves; relationship
 * groups carry their own human-readable label from backend metadata.
 */
function orderedGroups(
  fieldOptions: WorkflowFieldOption[]
): Array<{ id: string; label: string }> {
  const groups = new Map<string, string>();
  for (const option of fieldOptions) {
    if (!groups.has(option.group)) groups.set(option.group, option.groupLabel);
  }
  return [...groups.entries()].map(([id, label]) => ({ id, label }));
}

/**
 * Shared rule editor for CONDITION and BRANCH nodes. Both persist the same
 * backend configuration model: { logic, conditions: [{field, operator, value}] }.
 */
export function ConditionRulesEditor({
  logic,
  rules,
  readOnly = false,
  onChange,
  fieldOptions,
  resolveValueOptions,
  triggerEntityType,
  currentNodeId,
  nodes,
  edges,
}: ConditionRulesEditorProps) {
  const updateRule = useCallback(
    (index: number, patch: Partial<ConditionRule>) => {
      const next = rules.map((rule, i) => (i === index ? { ...rule, ...patch } : rule));
      onChange(logic, next);
    },
    [rules, logic, onChange]
  );

  const orderedGroupsMemo = useMemo(
    () => orderedGroups(fieldOptions ?? []),
    [fieldOptions]
  );

  const knownFields = useMemo(
    () => new Set((fieldOptions ?? []).map((option) => option.field)),
    [fieldOptions]
  );

  return (
    <>
      <div className="space-y-1">
        <Label>Combine using</Label>
        <Select
          value={logic}
          disabled={readOnly}
          onValueChange={(value) => onChange(value as "AND" | "OR", rules)}
        >
          <SelectTrigger aria-label="Condition logic">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="AND">ALL conditions (AND)</SelectItem>
            <SelectItem value="OR">ANY condition (OR)</SelectItem>
          </SelectContent>
        </Select>
      </div>

{rules.map((rule, index) => {
        const options = resolveValueOptions?.(rule.field) ?? null;
        return (
          <div key={index} className="space-y-2 rounded-md border p-3">
            {fieldOptions && fieldOptions.length > 0 ? (
              <Select
                value={
                  knownFields.has(rule.field)
                    ? rule.field
                    : rule.field
                      ? "__legacy__"
                      : ""
                }
                disabled={readOnly}
                onValueChange={(value) =>
                  updateRule(index, { field: value === "__legacy__" ? "" : value })
                }
              >
                <SelectTrigger aria-label={`Condition ${index + 1} field`}>
                  <SelectValue placeholder="Field" />
                </SelectTrigger>
                <SelectContent>
                  {orderedGroupsMemo.map((group) => {
                      const groupOptions = fieldOptions.filter(
                        (option) => option.group === group.id
                      );
                      if (groupOptions.length === 0) return null;
                      return (
                        <SelectGroup key={group.id}>
                          <SelectLabel>{group.label}</SelectLabel>
                          {groupOptions.map((option) => (
                            <SelectItem key={option.field} value={option.field}>
                              {option.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      );
                    }
                  )}
                  {rule.field && !knownFields.has(rule.field) && (
                    <SelectGroup>
                      <SelectLabel>Existing (custom)</SelectLabel>
                      <SelectItem value="__legacy__">{rule.field}</SelectItem>
                    </SelectGroup>
                  )}
                </SelectContent>
              </Select>
            ) : (
              <Input
                placeholder="Field e.g. firstName"
                defaultValue={rule.field}
                disabled={readOnly}
                onBlur={(event) => updateRule(index, { field: event.target.value })}
              />
            )}
            {!readOnly && rule.field && !knownFields.has(rule.field) && fieldOptions && (
              <Input
                placeholder="Field path"
                defaultValue={rule.field}
                onBlur={(event) => updateRule(index, { field: event.target.value })}
              />
            )}
<Select
              value={rule.operator}
              disabled={readOnly}
              onValueChange={(value) => updateRule(index, { operator: value })}
            >
              <SelectTrigger aria-label={`Condition ${index + 1} operator`}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {OPERATORS.map((operator) => (
                  <SelectItem key={operator} value={operator}>
                    {operator}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {options && options.length > 0 ? (
              <Select
                value={options.some((option) => option.value === rule.value) ? rule.value : ""}
                disabled={readOnly || NULL_OPERATORS.has(rule.operator)}
                onValueChange={(value) => updateRule(index, { value })}
              >
                <SelectTrigger aria-label={`Condition ${index + 1} value`}>
                  <SelectValue placeholder="Value" />
                </SelectTrigger>
                <SelectContent>
                  {options.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <div className="flex gap-2">
                <Input
                  aria-label={`Condition ${index + 1} value`}
                  placeholder={rule.operator === "IN" || rule.operator === "NOT_IN" ? "Value — comma separated" : "Value"}
                  defaultValue={rule.value}
                  disabled={readOnly || NULL_OPERATORS.has(rule.operator)}
                  onBlur={(event) => updateRule(index, { value: event.target.value })}
                  className="flex-1"
                />
                {!readOnly && !NULL_OPERATORS.has(rule.operator) && (
                  <WorkflowValuePicker
                    triggerEntityType={triggerEntityType}
                    currentNodeId={currentNodeId}
                    nodes={nodes}
                    edges={edges}
                    onSelect={(insertion) => {
                      const nextVal = rule.value ? `${rule.value} ${insertion}` : insertion;
                      updateRule(index, { value: nextVal });
                    }}
                  />
                )}
              </div>
            )}
            {!readOnly && rules.length > 1 && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() =>
                  onChange(
                    logic,
                    rules.filter((_, i) => i !== index)
                  )
                }
              >
                Remove rule
              </Button>
            )}
          </div>
        );
      })}

      {!readOnly && (
        <Button
          variant="outline"
          size="sm"
          onClick={() => onChange(logic, [...rules, { field: "", operator: "EQUALS", value: "" }])}
        >
          Add rule
        </Button>
      )}
    </>
  );
}
