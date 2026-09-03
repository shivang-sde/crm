"use client";

import { Node } from "@xyflow/react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useMemo } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ConditionRulesEditor } from "./ConditionRulesEditor";
import { BuilderNodeData } from "./utils/graph-mapper";
import {
  buildFieldOptions,
  WorkflowFieldOption,
} from "./utils/field-options";
import {
  deserializeConditionConfiguration,
  deserializeWaitConfiguration,
  isNodeConfigured,
  isoToLocalInputValue,
  localInputValueToIso,
  serializeConditionConfiguration,
  serializeWaitConfiguration,
  serializeWaitDuration,
  serializeWaitUntil,
} from "./utils/node-config";
import { findEntityMetadata } from "./utils/field-options";
import { useWorkflowMetadata, useWorkflowReferenceData, useWorkflowRelationshipReferenceData, useWorkflowHttpConnections } from "@/lib/hooks/workflow";
import { PickerField, WorkflowValuePicker } from "./WorkflowValuePicker";
import type { BuilderNode, BuilderEdge } from "./utils/graph-mapper";

const TASK_STATUSES = ["NOT_STARTED", "IN_PROGRESS", "WAITING_ON_SOMEONE", "DEFERRED", "COMPLETED"];
const TASK_PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const LIFECYCLE_STATUSES = ["PLANNED", "HELD", "NOT_HELD", "CANCELLED"];

interface WorkflowNodeConfigPanelProps {
  node: Node<BuilderNodeData> | null;
  readOnly?: boolean;
  /** entityType of the graph's TRIGGER node — drives context-aware editors. */
  triggerEntityType?: string;
  triggerEventType?: string;
  /** Other node keys in the graph — enables "Previous Node Outputs" options. */
  nodeKeys?: string[];
  nodes?: BuilderNode[];
  edges?: BuilderEdge[];
  isDisconnected?: boolean;
  onChange: (configuration: Record<string, unknown>, name?: string) => void;
}

export function WorkflowNodeConfigPanel({
  node,
  readOnly = false,
  triggerEntityType,
  triggerEventType,
  nodeKeys = [],
  nodes,
  edges,
  isDisconnected = false,
  onChange,
}: WorkflowNodeConfigPanelProps) {
  const metadataQuery = useWorkflowMetadata();

  if (!node) {
    return (
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Configuration
        </p>
        <p className="text-sm text-muted-foreground">
          Select a node to configure it.
        </p>
      </div>
    );
  }

  const entityMetadata = metadataQuery.data?.entities.find(
    (entity) => entity.entityType === triggerEntityType
  );

  const panelGuidance = isNodeConfigured(node.data);
  const typeLabel = node.data.nodeType === "TRIGGER" ? "WHEN" : node.data.nodeType === "CONDITION" || node.data.nodeType === "BRANCH" ? "IF / ELSE" : node.data.nodeType === "ACTION" ? "THEN" : node.data.nodeType;
  return (
    <div className="space-y-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        {typeLabel} configuration
      </p>
      {!panelGuidance.configured && !readOnly && (
        <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-medium text-amber-800" role="status">
          ⚠ Configuration required — {panelGuidance.issues[0] ?? "complete required fields"}
        </p>
      )}
      {isDisconnected && !readOnly && (
        <div className="rounded-md border border-amber-200 bg-amber-50 p-3">
          <p className="text-xs font-medium text-amber-800">⚠ This step is not connected to the workflow.</p>
          <p className="text-[11px] text-amber-700">Connect it from a previous step using the handle or + Add next step, or delete it.</p>
        </div>
      )}

      <div className="space-y-1">
        <Label htmlFor="cfg-name">Node name</Label>
        <Input
          id="cfg-name"
          value={node.data.name}
          disabled={readOnly}
          onChange={(event) => onChange(node.data.configuration, event.target.value)}
        />
      </div>

      {node.data.nodeType === "TRIGGER" && (
        <TriggerConfig
          key={node.id}
          configuration={node.data.configuration}
          readOnly={readOnly}
          metadataQuery={metadataQuery}
          versionEventType={triggerEventType ?? ""}
          onChange={(config) => onChange(config)}
        />
      )}
      {(node.data.nodeType === "CONDITION" || node.data.nodeType === "BRANCH") && (
        <ContextAwareConditionConfig
          key={node.id}
          configuration={node.data.configuration}
          readOnly={readOnly}
          metadata={metadataQuery.data}
          entityType={triggerEntityType ?? ""}
          nodeKeys={nodeKeys}
          currentNodeId={node.id}
          nodes={nodes}
          edges={edges}
          onConfigurationChange={onChange}
        />
      )}
      {node.data.nodeType === "WAIT" && (
        <WaitConfig
          key={node.id}
          configuration={node.data.configuration}
          readOnly={readOnly}
          onChange={(config) => onChange(config)}
        />
      )}
      {node.data.nodeType === "ACTION" && (
        <ActionConfig
          key={node.id}
          configuration={node.data.configuration}
          readOnly={readOnly}
          actions={metadataQuery.data?.actions ?? []}
          entityLabel={
            metadataQuery.data?.entities.find(
              (entity) => entity.entityType === triggerEntityType
            )?.label
          }
          entityType={triggerEntityType ?? ""}
          currentNodeId={node.id}
          nodes={nodes}
          edges={edges}
          onChange={(config) => onChange(config)}
        />
      )}
      {node.data.nodeType === "END" && (
        <p className="text-sm text-muted-foreground">
          This node terminates the workflow.
        </p>
      )}
    </div>
  );
}

type MetadataQuery = ReturnType<typeof useWorkflowMetadata>;

function TriggerConfig({
  configuration,
  readOnly,
  metadataQuery,
  versionEventType,
  onChange,
}: {
  configuration: Record<string, unknown>;
  readOnly: boolean;
  metadataQuery: MetadataQuery;
  versionEventType: string;
  onChange: (configuration: Record<string, unknown>) => void;
}) {
  const storedType =
    typeof configuration.entityType === "string" ? configuration.entityType : "";
  const storedEvent =
    typeof configuration.eventType === "string" ? configuration.eventType : "";
  const entityType = storedType;
  const eventType = storedEvent || (!storedType ? versionEventType : "");
  const selectedEntity = metadataQuery.data?.entities.find(
    (entity) => entity.entityType === entityType
  );
  const selectedEvent = selectedEntity?.events.find(
    (event) => event.eventType === eventType
  );

  return (
    <>
      <p className="text-[11px] font-bold uppercase tracking-widest text-amber-700">WHEN THIS HAPPENS</p>
      <div className="space-y-1">
        <Label>Module</Label>
        <Select
          value={entityType}
          disabled={readOnly || metadataQuery.isLoading}
          onValueChange={(value) =>
            onChange({ ...configuration, entityType: value, eventType: "" })
          }
        >
          <SelectTrigger>
            <SelectValue placeholder="Module" />
          </SelectTrigger>
          <SelectContent>
            {(metadataQuery.data?.entities ?? []).map((entity) => (
              <SelectItem key={entity.entityType} value={entity.entityType}>
                {entity.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {selectedEntity && (
        <div className="space-y-1">
          <Label>Event</Label>
          <Select
            value={eventType}
            disabled={readOnly}
            onValueChange={(value) => onChange({ ...configuration, eventType: value })}
          >
            <SelectTrigger>
              <SelectValue placeholder="Event" />
            </SelectTrigger>
            <SelectContent>
              {selectedEntity.events.map((event) => (
                <SelectItem key={event.eventType} value={event.eventType}>
                  {event.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}

      {selectedEntity && eventType && (
        <div className="space-y-1">
          <Label className="text-xs">Available context data</Label>
          <div className="flex flex-wrap gap-1">
            {selectedEntity.fields.slice(0, 6).map((field) => (
              <Badge key={field} variant="outline" className="text-[10px]">
                {"entity." + field}
              </Badge>
            ))}
            {selectedEntity.customFieldsSupported && (
              <Badge variant="secondary" className="text-[10px]">
                {"entity.customFields.*"}
              </Badge>
            )}
            {selectedEvent && selectedEvent.metadataFields.length > 0 && (
              <Badge variant="secondary" className="text-[10px]">
                {"+ " + selectedEvent.metadataFields.length + " trigger.metadata fields"}
              </Badge>
            )}
          </div>
        </div>
      )}

      {!metadataQuery.isLoading && !metadataQuery.data && (
        <p className="text-xs text-orange-600">
          Workflow metadata is unavailable â€” existing values are preserved.
        </p>
      )}
    </>
  );
}

function ContextAwareConditionConfig({
  configuration,
  readOnly,
  metadata,
  entityType,
  nodeKeys = [],
  currentNodeId,
  nodes,
  edges,
  onConfigurationChange,
}: {
  configuration: Record<string, unknown>;
  readOnly: boolean;
  metadata: ReturnType<typeof useWorkflowMetadata>["data"];
  entityType: string;
  /** Other node keys in the graph — enables "Previous Node Outputs" options. */
  nodeKeys?: string[];
  currentNodeId?: string;
  nodes?: BuilderNode[];
  edges?: BuilderEdge[];
  onConfigurationChange: (configuration: Record<string, unknown>) => void;
}) {
  const deserialized = deserializeConditionConfiguration(configuration);
  const referenceData = useWorkflowReferenceData(entityType);
  const entityMetadata = metadata?.entities.find(
    (entity) => entity.entityType === entityType
  );
  const relationshipData = useWorkflowRelationshipReferenceData(
    entityMetadata?.relationships
  );

  const fieldOptions: WorkflowFieldOption[] = [
    ...buildFieldOptions({
      metadata,
      triggerEntityType: entityType,
      referenceData,
      relationshipData,
    }),
    // Previous Node Outputs — one entry per other node key.
    ...nodeKeys
      .filter((key) => key !== "trigger")
      .map((key) => ({
        field: `nodeOutputs.${key}`,
        label: `Node output: ${key}`,
        group: "nodeOutputs" as const,
        groupLabel: "Previous Node Outputs",
      })),
  ];

  const resolveValueOptions = (
    field: string
  ): Array<{ value: string; label: string }> | null =>
    fieldOptions.find((option) => option.field === field)?.valueOptions ?? null;

  return (
    <div className="space-y-3">
      <div className="rounded-md border bg-violet-50 p-2 text-xs dark:bg-violet-950/20">
        <p className="font-medium text-violet-900 dark:text-violet-100">IF / ELSE evaluates your conditions and chooses one of two paths: TRUE or FALSE.</p>
        <p className="text-[11px] text-muted-foreground">Use IF / ELSE when the workflow needs to make a yes/no decision. This is intentionally different from a future multi-case router.</p>
      </div>
      <ConditionRulesEditor
        logic={deserialized.logic}
        rules={deserialized.conditions.map(({ field, operator, value }) => ({
          field,
          operator,
          value,
        }))}
        readOnly={readOnly}
        fieldOptions={fieldOptions}
        resolveValueOptions={resolveValueOptions}
        triggerEntityType={entityType}
        currentNodeId={currentNodeId}
        nodes={nodes}
        edges={edges}
        onChange={(logic, rules) =>
          onConfigurationChange(
            serializeConditionConfiguration(
              configuration,
              logic,
              rules.map(({ field, operator, value }) => ({ field, operator, value }))
            )
          )
        }
      />
      <div className="rounded-md border bg-muted/40 p-2 text-xs">
        <p><span className="font-medium">TRUE path</span> — The workflow continues here when the conditions match.</p>
        <p className="mt-1"><span className="font-medium">FALSE path</span> — The workflow continues here when the conditions do not match.</p>
      </div>
    </div>
  );
}

function staticEnumOptionsFor(entityType: string): Record<string, Array<{ value: string; label: string }>> {
  const toOptions = (values: string[]) =>
    values.map((value) => ({ value, label: titleCase(value) }));
  const options: Record<string, Array<{ value: string; label: string }>> = {};

  if (entityType === "TASK") {
    options["entity.status"] = toOptions(TASK_STATUSES);
    options["trigger.metadata.newStatus"] = toOptions(TASK_STATUSES);
    options["trigger.metadata.previousStatus"] = toOptions(TASK_STATUSES);
    options["entity.priority"] = toOptions(TASK_PRIORITIES);
  }
  if (entityType === "MEETING" || entityType === "CALL") {
    options["entity.status"] = toOptions(LIFECYCLE_STATUSES);
    options["trigger.metadata.newStatus"] = toOptions(LIFECYCLE_STATUSES);
    options["trigger.metadata.previousStatus"] = toOptions(LIFECYCLE_STATUSES);
  }
  return options;
}

function titleCase(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/^./, (char) => char.toUpperCase());
}

function WaitConfig({
  configuration,
  readOnly,
  onChange,
}: {
  configuration: Record<string, unknown>;
  readOnly: boolean;
  onChange: (configuration: Record<string, unknown>) => void;
}) {
  const stored = deserializeWaitConfiguration(configuration);
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const localUntil = isoToLocalInputValue(stored.resumeAt);
  const isDuration = stored.waitType === "DURATION";

  const setDuration = (amount: number, unit: string) => {
    if (readOnly) return;
    onChange(serializeWaitDuration(configuration, amount, unit));
  };
  const setUntil = (localValue: string) => {
    if (readOnly) return;
    onChange(serializeWaitUntil(configuration, localInputValueToIso(localValue)));
  };

  return (
    <div className="space-y-4">
      <div>
        <Label className="text-sm font-medium">Wait for</Label>
        <p className="text-[11px] text-muted-foreground">Timer starts when workflow reaches this step.</p>
      </div>
      <div className="flex flex-col gap-2">
        <label className={`flex items-start gap-2 rounded-lg border p-3 cursor-pointer ${isDuration ? "border-primary bg-primary/5" : "hover:border-primary/40"}`}>
          <input type="radio" checked={isDuration} disabled={readOnly} onChange={() => setDuration(stored.amount || 5, stored.unit || "MINUTES")} className="mt-1" />
          <div className="flex-1">
            <span className="text-sm font-medium">A duration</span>
            <div className="mt-2 flex items-center gap-2">
              <Input type="number" min={1} value={stored.amount} disabled={readOnly || !isDuration} onChange={(e) => setDuration(Number(e.target.value) || 1, stored.unit)} className="w-20" />
              <Select value={stored.unit} disabled={readOnly || !isDuration} onValueChange={(v) => setDuration(stored.amount, v)}>
                <SelectTrigger className="w-[130px]"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="MINUTES">Minutes</SelectItem>
                  <SelectItem value="HOURS">Hours</SelectItem>
                  <SelectItem value="DAYS">Days</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {[
                [5, "MINUTES", "5m"],
                [15, "MINUTES", "15m"],
                [30, "MINUTES", "30m"],
                [1, "HOURS", "1h"],
                [1, "DAYS", "1d"],
              ].map(([a, u, label]) => (
                <Button key={label as string} type="button" variant="outline" size="sm" disabled={readOnly} onClick={() => setDuration(a as number, u as string)} className={stored.amount === a && stored.unit === u && isDuration ? "border-primary" : ""}>{label as string}</Button>
              ))}
            </div>
            <p className="mt-2 text-[11px] text-muted-foreground">Resumes {stored.amount} {String(stored.unit).toLowerCase()} after reaching this step. If time already passed, continues immediately.</p>
          </div>
        </label>
        <label className={`flex items-start gap-2 rounded-lg border p-3 cursor-pointer ${!isDuration ? "border-primary bg-primary/5" : "hover:border-primary/40"}`}>
          <input type="radio" checked={!isDuration} disabled={readOnly} onChange={() => setUntil(localUntil || isoToLocalInputValue(new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()))} className="mt-1" />
          <div className="flex-1">
            <span className="text-sm font-medium">Until a specific time</span>
            <div className="mt-2">
              <Input type="datetime-local" value={localUntil} disabled={readOnly || isDuration} onChange={(e) => setUntil(e.target.value)} />
            </div>
            {localUntil && !isDuration && (
              <p className="mt-1 text-[11px] text-muted-foreground">
                Pauses until {new Date(localUntil).toLocaleString()} · {timezone}. If time already passed, continues immediately.
              </p>
            )}
            <p className="mt-1 text-[11px] text-muted-foreground">Workflow pauses here until the selected date and time.</p>
          </div>
        </label>
      </div>
      {!isDuration && !localUntil && <p className="text-xs text-red-500">A resume time is required.</p>}
      {isDuration && (!stored.amount || stored.amount <= 0) && <p className="text-xs text-red-500">Amount must be positive.</p>}
    </div>
  );
}

const ACTION_LABELS: Record<string, string> = {
  NO_OP: "No-op",
  SET_CONTEXT_VALUE: "Set context value",
  UPDATE_ENTITY_FIELD: "Update entity field",
  ASSIGN_OWNER: "Assign owner",
  CREATE_TASK: "Create task",
  CLICK_TO_CALL: "Click to call",
  HTTP_API: "HTTP API / webhook",
};

const KNOWN_ACTIONS = new Set(Object.keys(ACTION_LABELS));

function ActionConfig({
  configuration,
  readOnly,
  actions,
  entityLabel,
  entityType,
  currentNodeId,
  nodes,
  edges,
  onChange,
}: {
  configuration: Record<string, unknown>;
  readOnly: boolean;
  actions: string[];
  entityLabel?: string;
  entityType: string;
  currentNodeId?: string;
  nodes?: BuilderNode[];
  edges?: BuilderEdge[];
  onChange: (configuration: Record<string, unknown>) => void;
}) {
  const actionType =
    typeof configuration.actionType === "string" ? configuration.actionType : "";
  const config =
    configuration.config && typeof configuration.config === "object"
      ? (configuration.config as Record<string, unknown>)
      : {};
  const message = typeof configuration.message === "string" ? configuration.message : "";
  const referenceData = useWorkflowReferenceData(entityType);
  const userOptions = referenceData.optionsByField["entity.ownerId"] ?? [];

  const setConfig = (patch: Record<string, unknown>) =>
    onChange({ ...configuration, actionType, config: { ...config, ...patch } });

  const setTopLevel = (patch: Record<string, unknown>) =>
    onChange({ ...configuration, actionType, ...patch });

  const targetEntityType = typeof config.entityType === "string" ? config.entityType : "";

  return (
    <>
      <div className="space-y-1">
        <Label>Action type</Label>
        <Select
          value={actionType}
          disabled={readOnly || actions.length === 0}
          onValueChange={(value) =>
            onChange({
              actionType: value,
              ...(value === "NO_OP"
                ? { message: message }
                : { config: {} }),
            })
          }
        >
          <SelectTrigger>
            <SelectValue placeholder="Select action" />
          </SelectTrigger>
          <SelectContent>
            {(actions.length > 0 ? actions : Object.keys(ACTION_LABELS)).map((action) => (
              <SelectItem key={action} value={action}>
                {ACTION_LABELS[action] ?? action}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {actionType === "NO_OP" && (
        <ConfigText
          label="Message"
          value={message}
          readOnly={readOnly}
          onChange={(next) => setTopLevel({ message: next })}
        />
      )}

      {actionType === "SET_CONTEXT_VALUE" && (
        <>
          <ConfigText
            label="Context key"
            value={stringValue(config.key)}
            readOnly={readOnly}
            onChange={(key) => setConfig({ key })}
          />
          <PickerField
            label="Value"
            value={stringValue(config.value)}
            placeholder={"Literal or {{entity.email}}"}
            readOnly={readOnly}
            triggerEntityType={entityType}
            currentNodeId={currentNodeId}
            nodes={nodes}
            edges={edges}
            onChange={(value) => setConfig({ value })}
          />
        </>
      )}

      {actionType === "UPDATE_ENTITY_FIELD" && (
        <UpdateEntityFieldGroup
          config={config}
          readOnly={readOnly}
          triggerEntityType={entityType}
          targetEntityType={targetEntityType}
          currentNodeId={currentNodeId}
          nodes={nodes}
          edges={edges}
          onChange={setConfig}
        />
      )}

      {actionType === "ASSIGN_OWNER" && (
        <>
          <ConfigSelect
            label="Target entity"
            value={targetEntityType}
            readOnly={readOnly}
            rawOptions={[
              { value: "LEAD", label: "Lead" },
              { value: "CONTACT", label: "Contact" },
              { value: "ACCOUNT", label: "Account" },
              { value: "DEAL", label: "Deal" },
            ]}
            fallback={entityType ? { value: entityType, label: entityType } : null}
            onValueChange={(value) => setConfig({ entityType: value })}
          />
          <TargetRecordField
            label="Record"
            value={stringValue(config.entityId)}
            readOnly={readOnly}
            triggerEntityType={entityType}
            onChange={(entityId) => setConfig({ entityId })}
          />
          <ConfigSelect
            label="Owner"
            value={stringValue(config.ownerId)}
            readOnly={readOnly}
            rawOptions={userOptions}
            fallback={config.ownerId ? { value: stringValue(config.ownerId), label: stringValue(config.ownerId) } : null}
            onValueChange={(ownerId) => setConfig({ ownerId })}
          />
        </>
      )}

      {actionType === "CREATE_TASK" && (
        <>
          <PickerField
            label="Subject"
            value={stringValue(config.subject)}
            placeholder="{{entity.fullName}} follow-up"
            readOnly={readOnly}
            triggerEntityType={entityType}
            currentNodeId={currentNodeId}
            nodes={nodes}
            edges={edges}
            onChange={(subject) => setConfig({ subject })}
          />
          <ConfigSelect
            label="Status"
            value={stringValue(config.status)}
            readOnly={readOnly}
            rawOptions={TASK_STATUSES.map((status) => ({ value: status, label: status }))}
            fallback={config.status ? { value: stringValue(config.status), label: stringValue(config.status) } : null}
            onValueChange={(status) => setConfig({ status })}
          />
          <ConfigSelect
            label="Priority"
            value={stringValue(config.priority)}
            readOnly={readOnly}
            rawOptions={TASK_PRIORITIES.map((priority) => ({ value: priority, label: priority }))}
            fallback={config.priority ? { value: stringValue(config.priority), label: stringValue(config.priority) } : null}
            onValueChange={(priority) => setConfig({ priority })}
          />
          <ConfigSelect
            label="Related entity (optional)"
            value={stringValue(config.entityType)}
            readOnly={readOnly}
            rawOptions={[
              { value: "", label: "None" },
              { value: "LEAD", label: "Lead" },
              { value: "CONTACT", label: "Contact" },
              { value: "ACCOUNT", label: "Account" },
              { value: "DEAL", label: "Deal" },
            ]}
            fallback={config.entityType ? { value: stringValue(config.entityType), label: stringValue(config.entityType) } : null}
            onValueChange={(entityType) => setConfig({ entityType: entityType || undefined })}
          />
          <TargetRecordField
            label="Record"
            value={stringValue(config.entityId)}
            readOnly={readOnly}
            triggerEntityType={entityType}
            onChange={(entityId) => setConfig({ entityId })}
          />
          <ConfigSelect
            label="Owner (assignee)"
            value={stringValue(config.ownerId)}
            readOnly={readOnly}
            rawOptions={[{ value: "", label: "Unassigned" }, ...userOptions]}
            fallback={config.ownerId ? { value: stringValue(config.ownerId), label: stringValue(config.ownerId) } : null}
            onValueChange={(ownerId) => setConfig({ ownerId })}
          />
        </>
      )}

      {actionType === "CLICK_TO_CALL" && (
        <>
          <ConfigSelect
            label="Target entity"
            value={targetEntityType}
            readOnly={readOnly}
            rawOptions={[
              { value: "LEAD", label: "Lead" },
              { value: "CONTACT", label: "Contact" },
              { value: "ACCOUNT", label: "Account" },
              { value: "DEAL", label: "Deal" },
            ]}
            fallback={null}
            onValueChange={(value) => setConfig({ entityType: value })}
          />
          <TargetRecordField
            label="Record"
            value={stringValue(config.entityId)}
            readOnly={readOnly}
            triggerEntityType={entityType}
            onChange={(entityId) => setConfig({ entityId })}
          />
          <PickerField
            label="Phone number override"
            value={stringValue(config.phoneNumber)}
            placeholder="{{entity.phone}}"
            readOnly={readOnly}
            triggerEntityType={entityType}
            currentNodeId={currentNodeId}
            nodes={nodes}
            edges={edges}
            onChange={(phoneNumber) => setConfig({ phoneNumber })}
          />
          <PickerField
            label="Subject"
            value={stringValue(config.subject)}
            placeholder="Call about {{entity.fullName}}"
            readOnly={readOnly}
            triggerEntityType={entityType}
            currentNodeId={currentNodeId}
            nodes={nodes}
            edges={edges}
            onChange={(subject) => setConfig({ subject })}
          />
          <p className="text-xs text-muted-foreground">
            Uses the tenant&apos;s configured calling provider. Phone is resolved
            from the linked record when no override is given.
          </p>
        </>
      )}

      {actionType === "HTTP_API" && (
        <HttpApiConfig
          config={config}
          readOnly={readOnly}
          triggerEntityType={entityType}
          currentNodeId={currentNodeId}
          nodes={nodes}
          edges={edges}
          onChange={setConfig}
        />
      )}

      {actionType !== "" && !KNOWN_ACTIONS.has(actionType) && (
        <p className="text-xs text-orange-600">
          Advanced configuration for {actionType} is deferred. Existing values are preserved.
        </p>
      )}
    </>
  );
}

function stringValue(value: unknown): string {
  if (value == null) return "";
  if (typeof value === "string") return value;
  return String(value);
}

/**
 * HTTP_API configuration editor. Mirrors the backend contract:
 * method, url, connectionId (credential reference), headers,
 * queryParams, body, idempotency{enabled,headerName}.
 */
function HttpApiConfig({
  config,
  readOnly,
  triggerEntityType,
  currentNodeId,
  nodes,
  edges,
  onChange,
}: {
  config: Record<string, unknown>;
  readOnly: boolean;
  triggerEntityType?: string;
  currentNodeId?: string;
  nodes?: BuilderNode[];
  edges?: BuilderEdge[];
  onChange: (patch: Record<string, unknown>) => void;
}) {
  const connections = useWorkflowHttpConnections();
  const options = connections.data ?? [];
  const idempotency =
    config.idempotency && typeof config.idempotency === "object"
      ? (config.idempotency as Record<string, unknown>)
      : {};

  const headersObj = (config.headers && typeof config.headers === "object" ? (config.headers as Record<string, string>) : {}) as Record<string, string>;
  const queryObj = (config.queryParams && typeof config.queryParams === "object" ? (config.queryParams as Record<string, string>) : {}) as Record<string, string>;

  const headerRows = useMemo(() => {
    const entries = Object.entries(headersObj);
    return entries.length > 0 ? entries.map(([k, v]) => ({ key: k, value: String(v) })) : [{ key: "", value: "" }];
  }, [headersObj]);
  const queryRows = useMemo(() => {
    const entries = Object.entries(queryObj);
    return entries.length > 0 ? entries.map(([k, v]) => ({ key: k, value: String(v) })) : [{ key: "", value: "" }];
  }, [queryObj]);

  const updateHeaders = (rows: Array<{ key: string; value: string }>) => {
    const obj: Record<string, string> = {};
    for (const r of rows) if (r.key.trim()) obj[r.key.trim()] = r.value;
    onChange({ headers: obj });
  };
  const updateQuery = (rows: Array<{ key: string; value: string }>) => {
    const obj: Record<string, string> = {};
    for (const r of rows) if (r.key.trim()) obj[r.key.trim()] = r.value;
    onChange({ queryParams: obj });
  };

  const bodyString = typeof config.body === "object" ? JSON.stringify(config.body, null, 2) : stringValue(config.body);
  const bodyError = useMemo(() => {
    const trimmed = bodyString.trim();
    if (!trimmed) return null;
    // Only validate when body looks like JSON
    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return null;
    // Replace template expressions with placeholder string to allow validation
    const withoutTemplates = trimmed.replace(/\{\{[^}]+\}\}/g, '"__template__"');
    try {
      JSON.parse(withoutTemplates);
      return null;
    } catch (e) {
      return (e as Error).message ?? "Invalid JSON";
    }
  }, [bodyString]);

  return (
    <>
      <div className="rounded-md border bg-blue-50 px-3 py-2 text-xs dark:bg-blue-950/30">
        <p className="font-medium text-blue-900 dark:text-blue-100">Output available</p>
        <p className="font-mono text-[11px] text-blue-700 dark:text-blue-300">response, statusCode</p>
        <p className="text-[11px] text-muted-foreground">Usable in later nodes via Insert value → Previous Nodes → this HTTP Request.</p>
      </div>
      <ConfigSelect
        label="Method *"
        value={stringValue(config.method)}
        readOnly={readOnly}
        rawOptions={["GET", "POST", "PUT", "PATCH", "DELETE"].map((method) => ({
          value: method,
          label: method,
        }))}
        onValueChange={(method) => onChange({ method })}
      />
      <ConfigText
        label="URL *"
        value={stringValue(config.url)}
        readOnly={readOnly}
        onChange={(url) => onChange({ url })}
        placeholder="https://example.com/hook"
      />
      <div className="space-y-1">
        <Label>Credential reference</Label>
        <Select
          value={config.connectionId ? stringValue(config.connectionId) : ""}
          disabled={readOnly || connections.isLoading}
          onValueChange={(connectionId) => onChange({ connectionId: connectionId === "__none__" ? null : connectionId })}
        >
          <SelectTrigger>
            <SelectValue placeholder="None (unauthenticated)" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__none__">None (unauthenticated)</SelectItem>
            {options.map((option) => (
              <SelectItem key={option.id} value={option.id}>
                {option.name} — {option.authType}
                {!option.active ? " (inactive)" : ""}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {Boolean(config.connectionId) && !options.some((option) => option.id === stringValue(config.connectionId)) && (
          <p className="text-xs text-orange-600">
            Selected connection is unavailable — it may have been deactivated.
          </p>
        )}
        <p className="text-xs text-muted-foreground">
          Credentials are stored in connection settings and referenced by ID only.
        </p>
      </div>
      <div className="space-y-2">
        <Label>Headers</Label>
        <div className="space-y-2">
          {headerRows.map((row, idx) => (
            <div key={idx} className="flex gap-2">
              <Input
                placeholder="Header name"
                value={row.key}
                disabled={readOnly}
                onChange={(e) => {
                  const next = [...headerRows];
                  next[idx] = { ...row, key: e.target.value };
                  updateHeaders(next);
                }}
                className="flex-1"
                aria-label={`Header ${idx + 1} name`}
              />
              <div className="flex flex-1 gap-1">
                <Input
                  placeholder="Value"
                  value={row.value}
                  disabled={readOnly}
                  onChange={(e) => {
                    const next = [...headerRows];
                    next[idx] = { ...row, value: e.target.value };
                    updateHeaders(next);
                  }}
                  className="flex-1"
                  aria-label={`Header ${idx + 1} value`}
                />
                {!readOnly && (
                  <WorkflowValuePicker
                    triggerEntityType={triggerEntityType}
                    currentNodeId={currentNodeId}
                    nodes={nodes}
                    edges={edges}
                    onSelect={(ins) => {
                      const next = [...headerRows];
                      const cur = next[idx].value;
                      const insertion = cur ? (cur.endsWith(" ") ? cur + ins : cur + " " + ins) : ins;
                      next[idx] = { ...row, value: insertion };
                      updateHeaders(next);
                    }}
                  />
                )}
              </div>
              {!readOnly && (
                <Button
                  type="button"
                  variant="ghost"
                  size="xs"
                  onClick={() => {
                    const next = headerRows.filter((_, i) => i !== idx);
                    updateHeaders(next.length === 0 ? [{ key: "", value: "" }] : next);
                  }}
                  aria-label={`Remove header ${idx + 1}`}
                >
                  ✕
                </Button>
              )}
            </div>
          ))}
          {!readOnly && (
            <Button type="button" variant="outline" size="sm" onClick={() => updateHeaders([...headerRows, { key: "", value: "" }])}>
              Add header
            </Button>
          )}
        </div>
      </div>
      <div className="space-y-2">
        <Label>Query parameters</Label>
        <div className="space-y-2">
          {queryRows.map((row, idx) => (
            <div key={idx} className="flex gap-2">
              <Input
                placeholder="Param name"
                value={row.key}
                disabled={readOnly}
                onChange={(e) => {
                  const next = [...queryRows];
                  next[idx] = { ...row, key: e.target.value };
                  updateQuery(next);
                }}
                className="flex-1"
                aria-label={`Query param ${idx + 1} name`}
              />
              <div className="flex flex-1 gap-1">
                <Input
                  placeholder="Value"
                  value={row.value}
                  disabled={readOnly}
                  onChange={(e) => {
                    const next = [...queryRows];
                    next[idx] = { ...row, value: e.target.value };
                    updateQuery(next);
                  }}
                  className="flex-1"
                  aria-label={`Query param ${idx + 1} value`}
                />
                {!readOnly && (
                  <WorkflowValuePicker
                    triggerEntityType={triggerEntityType}
                    currentNodeId={currentNodeId}
                    nodes={nodes}
                    edges={edges}
                    onSelect={(ins) => {
                      const next = [...queryRows];
                      const cur = next[idx].value;
                      const insertion = cur ? (cur.endsWith(" ") ? cur + ins : cur + " " + ins) : ins;
                      next[idx] = { ...row, value: insertion };
                      updateQuery(next);
                    }}
                  />
                )}
              </div>
              {!readOnly && (
                <Button
                  type="button"
                  variant="ghost"
                  size="xs"
                  onClick={() => {
                    const next = queryRows.filter((_, i) => i !== idx);
                    updateQuery(next.length === 0 ? [{ key: "", value: "" }] : next);
                  }}
                  aria-label={`Remove query param ${idx + 1}`}
                >
                  ✕
                </Button>
              )}
            </div>
          ))}
          {!readOnly && (
            <Button type="button" variant="outline" size="sm" onClick={() => updateQuery([...queryRows, { key: "", value: "" }])}>
              Add query param
            </Button>
          )}
        </div>
      </div>
      <div className="space-y-1">
        <Label htmlFor="http-body">Body</Label>
        <Textarea
          id="http-body"
          rows={4}
          disabled={readOnly}
          value={bodyString}
          onChange={(event) => onChange({ body: event.target.value })}
          placeholder={'{"leadId":"{{entity.id}}","name":"{{entity.fullName}}"}'}
          aria-invalid={bodyError ? true : undefined}
          aria-describedby={bodyError ? "http-body-error" : undefined}
        />
        {bodyError ? (
          <p id="http-body-error" className="text-xs font-medium text-red-600" role="alert">
            ⚠ Invalid JSON: {bodyError}
          </p>
        ) : bodyString.trim() ? (
          <p className="text-xs text-emerald-600">✓ Valid JSON</p>
        ) : null}
      </div>
      <div className="flex items-center gap-2">
        <Checkbox
          id="http-idempotency-enabled"
          checked={Boolean(idempotency.enabled)}
          disabled={readOnly}
          onCheckedChange={(checked) =>
            onChange({ idempotency: { ...idempotency, enabled: checked === true } })
          }
        />
        <Label htmlFor="http-idempotency-enabled" className="text-sm font-normal">
          Send idempotency key header
        </Label>
      </div>
      {Boolean(idempotency.enabled) && (
        <ConfigText
          label="Idempotency header name"
          value={stringValue(idempotency.headerName)}
          readOnly={readOnly}
          onChange={(headerName) => onChange({ idempotency: { ...idempotency, headerName } })}
          placeholder="Idempotency-Key"
        />
      )}
      <p className="text-xs text-muted-foreground">
        Only HTTPS URLs on port 443 are allowed. Raw credentials in headers or
        body keys are rejected at runtime and never logged or stored.
      </p>
    </>
  );
}

function UpdateEntityFieldGroup({
  config,
  readOnly,
  triggerEntityType,
  targetEntityType,
  currentNodeId,
  nodes,
  edges,
  onChange,
}: {
  config: Record<string, unknown>;
  readOnly: boolean;
  triggerEntityType: string;
  targetEntityType: string;
  currentNodeId?: string;
  nodes?: BuilderNode[];
  edges?: BuilderEdge[];
  onChange: (patch: Record<string, unknown>) => void;
}) {
  const metadata = useWorkflowMetadata().data;
  const targetReference = useWorkflowReferenceData(targetEntityType || triggerEntityType);
  const effectiveTarget = targetEntityType || triggerEntityType || "";
  const entityMeta = findEntityMetadata(metadata, effectiveTarget);
  const relationshipData = useWorkflowRelationshipReferenceData(entityMeta?.relationships);
  const fieldValue = stringValue(config.field);
  const valueStr = stringValue(config.value);

  // Reuse buildFieldOptions to get all field options including relationships
  const fieldOptionsWithGroup = buildFieldOptions({
    metadata,
    triggerEntityType,
    referenceData: useWorkflowReferenceData(triggerEntityType),
    relationshipData: useWorkflowRelationshipReferenceData(entityMeta?.relationships),
  }).filter((opt) => {
    // Filter out trigger metadata and previous node outputs for UPDATE_ENTITY_FIELD
    return opt.group !== "metadata" && opt.group !== "nodeOutputs";
  });

  const fieldOptions: Array<{ value: string; label: string; group: string }> = fieldOptionsWithGroup.map(opt => ({
    value: opt.field,
    label: opt.label,
    group: opt.groupLabel
  }));

  const resolveUpdateValueOptions = (): Array<{ value: string; label: string }> | null => {
    if (!fieldValue) return null;
    // Relationship field like entity.account.industry
    if (fieldValue.startsWith("entity.")) {
      const parts = fieldValue.split(".");
      if (parts.length >= 3) {
        const relKey = parts[1];
        const field = parts.slice(2).join(".");
        const relData = relationshipData[relKey];
        const candidateKeys = [`entity.${field}`, field, `entity.${relKey}.${field}`, fieldValue];
        for (const ck of candidateKeys) {
          const opts = relationshipData[relKey]?.optionsByField[ck] ?? targetReference.optionsByField[ck];
          if (opts && opts.length > 0) return opts;
        }
      }
      return targetReference.optionsByField[fieldValue] ?? null;
    }
    // Raw primary/custom
    return targetReference.optionsByField[`entity.${fieldValue}`] ?? targetReference.optionsByField[fieldValue] ?? null;
  };
  const valueOptions = resolveUpdateValueOptions();

  return (
    <>
      <ConfigSelect
        label="Target entity *"
        value={targetEntityType}
        readOnly={readOnly}
        rawOptions={[
          { value: "LEAD", label: "Lead" },
          { value: "CONTACT", label: "Contact" },
          { value: "ACCOUNT", label: "Account" },
          { value: "DEAL", label: "Deal" },
        ]}
        fallback={triggerEntityType ? { value: triggerEntityType, label: triggerEntityType } : null}
        onValueChange={(value) => onChange({ entityType: value })}
      />
      <TargetRecordField
        label="Record *"
        value={stringValue(config.entityId)}
        readOnly={readOnly}
        triggerEntityType={triggerEntityType}
        onChange={(entityId) => onChange({ entityId })}
      />
      <div className="space-y-1">
        <Label>Field *</Label>
        {fieldOptions.length > 0 ? (
          <Select
            value={fieldOptions.some((o) => o.value === fieldValue) ? fieldValue : fieldValue ? "__legacy__" : ""}
            disabled={readOnly}
            onValueChange={(v) => onChange({ field: v === "__legacy__" ? fieldValue : v })}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select field" />
            </SelectTrigger>
            <SelectContent>
              {Array.from(new Set(fieldOptions.map((o) => o.group))).map((group) => (
                <SelectGroup key={group}>
                  <SelectLabel>{group}</SelectLabel>
                  {fieldOptions
                    .filter((o) => o.group === group)
                    .map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>
                        {opt.label}
                      </SelectItem>
                    ))}
                </SelectGroup>
              ))}
              {fieldValue && !fieldOptions.some((o) => o.value === fieldValue) && (
                <SelectGroup>
                  <SelectLabel>Current</SelectLabel>
                  <SelectItem value="__legacy__">{fieldValue}</SelectItem>
                </SelectGroup>
              )}
            </SelectContent>
          </Select>
        ) : (
          <Input
            value={fieldValue}
            placeholder="e.g. status"
            disabled={readOnly}
            onChange={(e) => onChange({ field: e.target.value })}
          />
        )}
        <p className="text-[11px] text-muted-foreground">Choose the CRM field to update. Includes one-hop relationships when available (e.g., Account → Industry).</p>
      </div>
      <div className="space-y-1">
        <Label>Value *</Label>
        {valueOptions && valueOptions.length > 0 ? (
          <Select
            value={valueOptions.some((o) => o.value === valueStr) ? valueStr : valueStr ? "__legacy_val__" : ""}
            disabled={readOnly}
            onValueChange={(v) => onChange({ value: v === "__legacy_val__" ? valueStr : v })}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select value" />
            </SelectTrigger>
            <SelectContent>
              {valueOptions.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
              {valueStr && !valueOptions.some((o) => o.value === valueStr) && (
                <SelectGroup>
                  <SelectLabel>Current</SelectLabel>
                  <SelectItem value="__legacy_val__">{valueStr}</SelectItem>
                </SelectGroup>
              )}
            </SelectContent>
          </Select>
        ) : (
          <Input
            value={valueStr}
            placeholder="Literal or {{...}} token"
            disabled={readOnly}
            onChange={(e) => onChange({ value: e.target.value })}
          />
        )}
        <p className="text-[11px] text-muted-foreground">Supports literals or tokens like {"{{entity.email}}"}.</p>
      </div>
    </>
  );
}

function objectToLines(value: unknown): string {
  if (value == null) return "";
  if (typeof value !== "object") return typeof value === "string" ? value : "";
  return Object.entries(value as Record<string, unknown>)
    .map(([key, item]) => `${key}: ${String(item)}`)
    .join("\n");
}

function TargetRecordField({
  label,
  value,
  readOnly,
  triggerEntityType,
  onChange,
}: {
  label: string;
  value: string;
  readOnly?: boolean;
  triggerEntityType: string;
  onChange: (v: string) => void;
}) {
  const metadata = useWorkflowMetadata().data;
  const entityMeta = findEntityMetadata(metadata, triggerEntityType);
  const triggerLabel = metadata?.entities.find((e) => e.entityType === triggerEntityType)?.label ?? triggerEntityType ?? "Record";
  const options = useMemo(() => {
    const opts: Array<{ value: string; label: string; group: string }> = [];
    opts.push({ value: "{{entity.id}}", label: `Current ${triggerLabel}`, group: "Current Record" });
    for (const rel of (entityMeta?.relationships ?? []) as Array<{ key: string; label: string; relatedEntityType: string | null }>) {
      if (!rel.relatedEntityType) continue;
      const relLabel = rel.label || rel.key;
      opts.push({ value: `{{entity.${rel.key}.id}}`, label: `Related ${relLabel}`, group: "Related Records" });
    }
    opts.push({ value: "__custom__", label: "Specific record…", group: "Other" });
    return opts;
  }, [triggerEntityType, entityMeta, metadata, triggerLabel]);
  const isCustom = !!value && !options.some((o) => o.value === value);
  const selectValue = isCustom ? "__custom__" : options.some((o) => o.value === value) ? value : "";
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Select value={selectValue} disabled={readOnly} onValueChange={(v) => {
        if (v === "__custom__") onChange("");
        else onChange(v);
      }}>
        <SelectTrigger><SelectValue placeholder="Select record" /></SelectTrigger>
        <SelectContent>
          {Array.from(new Set(options.map((o) => o.group))).map((group) => (
            <SelectGroup key={group}>
              <SelectLabel>{group}</SelectLabel>
              {options.filter((o) => o.group === group).map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectGroup>
          ))}
        </SelectContent>
      </Select>
      {(selectValue === "__custom__" || isCustom) && (
        <Input value={isCustom ? value : ""} placeholder="Enter ID or {{entity.id}}" disabled={readOnly} onChange={(e) => onChange(e.target.value)} />
      )}
      <p className="text-[11px] text-muted-foreground">Current {triggerLabel} — The record that triggered this workflow.</p>
    </div>
  );
}

interface ConfigSelectProps {
  label: string;
  value: string;
  readOnly?: boolean;
  rawOptions: Array<{ value: string; label: string }>;
  fallback?: { value: string; label: string } | null;
  onValueChange: (value: string) => void;
}

function ConfigSelect({
  label,
  value,
  readOnly,
  rawOptions,
  fallback,
  onValueChange,
}: ConfigSelectProps) {
  const known =
    value !== "" && rawOptions.some((option) => option.value === value);

  return (
    <div className="space-y-1">
      {label ? <Label>{label}</Label> : null}
      <Select
        value={known ? value : value ? "__legacy__" : ""}
        disabled={readOnly}
        onValueChange={(next) => onValueChange(next === "__legacy__" ? value : next)}
      >
        <SelectTrigger>
          <SelectValue placeholder={label || "Select"} />
        </SelectTrigger>
        <SelectContent>
          {rawOptions.map((option) => (
            <SelectItem key={option.value || "__none__"} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
          {!known && value && (
            <SelectGroup>
              <SelectLabel>Current</SelectLabel>
              <SelectItem value="__legacy__">{fallback?.label ?? value}</SelectItem>
            </SelectGroup>
          )}
        </SelectContent>
      </Select>
    </div>
  );
}

function ConfigText({
  label,
  value,
  readOnly,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  readOnly?: boolean;
  onChange: (value: string) => void;
  placeholder?: string;
}) {
  return (
    <div className={label ? "space-y-1" : ""}>
      {label ? <Label>{label}</Label> : null}
      <Input
        value={value}
        placeholder={placeholder}
        disabled={readOnly}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}
