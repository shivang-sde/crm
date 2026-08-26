import { JsonObject } from "@/types/workflow";
import { BuilderNodeData } from "./graph-mapper";

/**
 * Configuration serialization helpers.
 *
 * Unknown configuration keys are preserved so backend configuration the UI
 * does not understand is never erased while editing.
 */

export function serializeTriggerConfiguration(
  existing: Record<string, unknown> | undefined,
  entityType: string,
  eventType: string
): Record<string, unknown> {
  return {
    ...(existing ?? {}),
    entityType: entityType.trim(),
    eventType: eventType.trim(),
  };
}

export function deserializeTriggerConfiguration(
  configuration: Record<string, unknown> | undefined
): { entityType: string; eventType: string } {
  return {
    entityType:
      typeof configuration?.entityType === "string" ? configuration.entityType : "",
    eventType:
      typeof configuration?.eventType === "string" ? configuration.eventType : "",
  };
}

export function serializeActionConfiguration(
  existing: Record<string, unknown> | undefined,
  actionType: string,
  message?: string
): Record<string, unknown> {
  const next: Record<string, unknown> = {
    actionType,
    ...(existing ?? {}),
  };

  if (actionType === "NO_OP") {
    next.message = typeof message === "string" ? message : "";
  }

  return next;
}

export function deserializeActionConfiguration(
  configuration: Record<string, unknown> | undefined
): { actionType: string; message: string } {
  return {
    actionType:
      typeof configuration?.actionType === "string" ? configuration.actionType : "",
    message: typeof configuration?.message === "string" ? configuration.message : "",
  };
}

const CONDITION_LOGIC_KEY = "logic";

export function serializeConditionConfiguration(
  existing: Record<string, unknown> | undefined,
  logic: "AND" | "OR",
  conditions: Array<{ field: string; operator: string; value?: string }>
): Record<string, unknown> {
  return {
    ...(existing ?? {}),
    [CONDITION_LOGIC_KEY]: logic,
    conditions: conditions.map((condition) => ({
      field: condition.field.trim(),
      operator: condition.operator,
      value: condition.value ?? "",
    })),
  };
}

export function deserializeConditionConfiguration(
  configuration: Record<string, unknown> | undefined
): { logic: "AND" | "OR"; conditions: Array<{ field: string; operator: string; value: string }> } {
  const rawConditions = Array.isArray(configuration?.conditions)
    ? (configuration.conditions as Array<Record<string, unknown>>)
    : [];

  return {
    logic: configuration?.[CONDITION_LOGIC_KEY] === "OR" ? "OR" : "AND",
    conditions: rawConditions.map((condition) => ({
      field: typeof condition.field === "string" ? condition.field : "",
      operator: typeof condition.operator === "string" ? condition.operator : "EQUALS",
      value: condition.value == null ? "" : String(condition.value),
    })),
  };
}

export function serializeEdgeConfiguration(
  existing: Record<string, unknown> | undefined,
  outcome?: "TRUE" | "FALSE"
): Record<string, unknown> {
  if (!outcome) {
    return { ...(existing ?? {}) };
  }
  return { ...(existing ?? {}), outcome };
}

export function serializeWaitConfiguration(
  existing: Record<string, unknown> | undefined,
  resumeAtIsoUtc: string
): Record<string, unknown> {
  return {
    ...(existing ?? {}),
    resumeAt: resumeAtIsoUtc,
  };
}

export function deserializeWaitConfiguration(
  configuration: Record<string, unknown> | undefined
): { resumeAt: string } {
  return {
    resumeAt:
      typeof configuration?.resumeAt === "string" ? configuration.resumeAt : "",
  };
}

/**
 * Converts a stored ISO-8601 UTC timestamp to a value usable by an
 * <input type="datetime-local"> (local time, no zone suffix).
 */
export function isoToLocalInputValue(iso: string): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * Converts a datetime-local input value (local time) to ISO-8601 UTC.
 */
export function localInputValueToIso(localValue: string): string {
  if (!localValue) return "";
  const date = new Date(localValue);
  if (Number.isNaN(date.getTime())) return "";
  return date.toISOString();
}

export function toNodeData(nodeType: string, nodeKey: string, name: string, configuration: JsonObject | null): BuilderNodeData {
  return {
    nodeKey,
    nodeType: nodeType as BuilderNodeData["nodeType"],
    name,
    configuration: { ...(configuration ?? {}) },
  };
}

export function describeTrigger(configuration: Record<string, unknown>): string {
  const entityType = typeof configuration.entityType === "string" ? configuration.entityType : "";
  const eventType = typeof configuration.eventType === "string" ? configuration.eventType : "";
  if (!entityType && !eventType) return "Not configured";
  return `${entityType || "?"}.${eventType || "?"}`;
}

export function describeAction(configuration: Record<string, unknown>, name: string): string {
  if (typeof configuration.actionType === "string") {
    return name || configuration.actionType;
  }
  return name || "Not configured";
}
