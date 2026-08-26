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
import { Badge } from "@/components/ui/badge";
import { ConditionRulesEditor } from "./ConditionRulesEditor";
import { BuilderNodeData } from "./utils/graph-mapper";
import {
  buildFieldOptions,
  WorkflowFieldOption,
} from "./utils/field-options";
import {
  deserializeConditionConfiguration,
  deserializeWaitConfiguration,
  isoToLocalInputValue,
  localInputValueToIso,
  serializeConditionConfiguration,
  serializeWaitConfiguration,
} from "./utils/node-config";
import { useWorkflowMetadata, useWorkflowReferenceData, useWorkflowRelationshipReferenceData, useWorkflowHttpConnections } from "@/lib/hooks/workflow";

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
  onChange: (configuration: Record<string, unknown>, name?: string) => void;
}

export function WorkflowNodeConfigPanel({
  node,
  readOnly = false,
  triggerEntityType,
  triggerEventType,
  nodeKeys = [],
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

  return (
    <div className="space-y-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        {node.data.nodeType} configuration
      </p>

      <div className="space-y-1">
        <Label htmlFor="cfg-name">Name</Label>
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
      <div className="space-y-1">
        <Label>When this happens</Label>
        <Select
          value={entityType}
          disabled={readOnly || metadataQuery.isLoading}
          onValueChange={(value) =>
            onChange({ ...configuration, entityType: value, eventType: "" })
          }
        >
          <SelectTrigger>
            <SelectValue placeholder="Entity" />
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
  onConfigurationChange,
}: {
  configuration: Record<string, unknown>;
  readOnly: boolean;
  metadata: ReturnType<typeof useWorkflowMetadata>["data"];
  entityType: string;
  /** Other node keys in the graph — enables "Previous Node Outputs" options. */
  nodeKeys?: string[];
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
  const localValue = isoToLocalInputValue(stored.resumeAt);

  return (
    <div className="space-y-1">
      <Label htmlFor="wait-resume-at">Resume at (local time)</Label>
      <Input
        id="wait-resume-at"
        type="datetime-local"
        value={localValue}
        disabled={readOnly}
        onChange={(event) =>
          onChange(
            serializeWaitConfiguration(
              configuration,
              localInputValueToIso(event.target.value)
            )
          )
        }
      />
      {!localValue && (
        <p className="text-xs text-red-500">A resume time is required.</p>
      )}
      {stored.resumeAt && (
        <p className="text-xs text-muted-foreground">
          Stored as UTC: {stored.resumeAt}
        </p>
      )}
      <p className="text-xs text-muted-foreground">
        The workflow pauses here until this moment, then continues.
      </p>
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
  onChange,
}: {
  configuration: Record<string, unknown>;
  readOnly: boolean;
  actions: string[];
  entityLabel?: string;
  entityType: string;
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
          <ConfigText
            label="Value"
            value={stringValue(config.value)}
            readOnly={readOnly}
            onChange={(value) => setConfig({ value })}
            placeholder={"Literal or {{entity.email}}"}
          />
        </>
      )}

      {actionType === "UPDATE_ENTITY_FIELD" && (
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
          <ConfigText
            label="Entity ID"
            value={stringValue(config.entityId)}
            readOnly={readOnly}
            onChange={(entityId) => setConfig({ entityId })}
            placeholder="{{entity.id}}"
          />
          <ConfigText
            label="Field (controlled key)"
            value={stringValue(config.field)}
            readOnly={readOnly}
            onChange={(field) => setConfig({ field })}
            placeholder="e.g. score"
          />
          <ConfigText
            label="Value"
            value={stringValue(config.value)}
            readOnly={readOnly}
            onChange={(value) => setConfig({ value })}
            placeholder="Literal or {{...}} token"
          />
        </>
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
          <ConfigText
            label="Entity ID"
            value={stringValue(config.entityId)}
            readOnly={readOnly}
            onChange={(entityId) => setConfig({ entityId })}
            placeholder="{{entity.id}}"
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
          <ConfigText
            label="Subject"
            value={stringValue(config.subject)}
            readOnly={readOnly}
            onChange={(subject) => setConfig({ subject })}
            placeholder="{{entity.fullName}} follow-up"
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
          <ConfigText
            label="Entity ID"
            value={stringValue(config.entityId)}
            readOnly={readOnly}
            onChange={(entityId) => setConfig({ entityId })}
            placeholder="{{entity.id}}"
          />
          <ConfigText
            label="Phone number override"
            value={stringValue(config.phoneNumber)}
            readOnly={readOnly}
            onChange={(phoneNumber) => setConfig({ phoneNumber })}
            placeholder="{{entity.phone}}"
          />
          <ConfigText
            label="Subject"
            value={stringValue(config.subject)}
            readOnly={readOnly}
            onChange={(subject) => setConfig({ subject })}
            placeholder="Call about {{entity.fullName}}"
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
  onChange,
}: {
  config: Record<string, unknown>;
  readOnly: boolean;
  onChange: (patch: Record<string, unknown>) => void;
}) {
  const connections = useWorkflowHttpConnections();
  const options = connections.data ?? [];
  const idempotency =
    config.idempotency && typeof config.idempotency === "object"
      ? (config.idempotency as Record<string, unknown>)
      : {};

  const headersText = objectToLines(config.headers);
  const queryText = objectToLines(config.queryParams);

  const parseLines = (text: string): Record<string, string> => {
    const result: Record<string, string> = {};
    for (const line of text.split("\n")) {
      const separator = line.indexOf(":");
      if (separator <= 0) continue;
      const key = line.slice(0, separator).trim();
      const value = line.slice(separator + 1).trim();
      if (key) result[key] = value;
    }
    return result;
  };

  return (
    <>
      <ConfigSelect
        label="Method"
        value={stringValue(config.method)}
        readOnly={readOnly}
        rawOptions={["GET", "POST", "PUT", "PATCH", "DELETE"].map((method) => ({
          value: method,
          label: method,
        }))}
        onValueChange={(method) => onChange({ method })}
      />
      <ConfigText
        label="URL"
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
      <div className="space-y-1">
        <Label>Headers (one per line — Key: Value)</Label>
        <Textarea
          rows={3}
          disabled={readOnly}
          value={headersText}
          onChange={(event) => onChange({ headers: parseLines(event.target.value) })}
          placeholder={"Accept: application/json\nX-Correlation-Id: {{trigger.eventId}}"}
        />
      </div>
      <div className="space-y-1">
        <Label>Query parameters (one per line — Key: Value)</Label>
        <Textarea
          rows={2}
          disabled={readOnly}
          value={queryText}
          onChange={(event) => onChange({ queryParams: parseLines(event.target.value) })}
          placeholder={"source: {{entity.source}}"}
        />
      </div>
      <div className="space-y-1">
        <Label>Body</Label>
        <Textarea
          rows={4}
          disabled={readOnly}
          value={typeof config.body === "object" ? JSON.stringify(config.body, null, 2) : stringValue(config.body)}
          onChange={(body) => onChange({ body })}
          placeholder={'{"leadId":"{{entity.id}}","name":"{{entity.fullName}}"}'}
        />
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

function objectToLines(value: unknown): string {
  if (value == null) return "";
  if (typeof value !== "object") return typeof value === "string" ? value : "";
  return Object.entries(value as Record<string, unknown>)
    .map(([key, item]) => `${key}: ${String(item)}`)
    .join("\n");
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
