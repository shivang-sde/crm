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
  const actionType = typeof configuration.actionType === "string" ? configuration.actionType : "";
  if (!actionType) return name || "Not configured";
  const conf =
    configuration.config && typeof configuration.config === "object"
      ? (configuration.config as Record<string, unknown>)
      : {};
  const s = (v: unknown) => (typeof v === "string" ? v : v == null ? "" : String(v));
  switch (actionType) {
    case "UPDATE_ENTITY_FIELD": {
      const field = s(conf.field);
      const value = s(conf.value);
      const entity = s(conf.entityType);
      if (field && value) return `${entity ? entity + " " : ""}${field} → ${value.slice(0, 30)}`;
      if (field) return `${field} → …`;
      return name || "Update field";
    }
    case "CREATE_TASK": {
      const subj = s(conf.subject);
      if (subj) return subj.slice(0, 40);
      return name || "Create task";
    }
    case "ASSIGN_OWNER": {
      const owner = s(conf.ownerId);
      const ent = s(conf.entityType);
      if (owner) return `${ent ? ent + " → " : ""}${owner.slice(0, 12)}`;
      return name || "Assign owner";
    }
    case "HTTP_API": {
      const method = s(conf.method);
      const url = s(conf.url);
      let host = "";
      try {
        host = url ? new URL(url).hostname : "";
      } catch {
        host = url.slice(0, 20);
      }
      if (method && host) return `${method} ${host}`;
      if (method) return method;
      if (host) return host;
      return name || "HTTP request";
    }
    case "SET_CONTEXT_VALUE": {
      const key = s(conf.key);
      const val = s(conf.value);
      if (key) return `${key} = ${val.slice(0, 20)}`;
      return name || "Set context";
    }
    case "CLICK_TO_CALL": {
      const phone = s(conf.phoneNumber);
      const subj = s(conf.subject);
      if (phone) return `Call ${phone.slice(0, 16)}`;
      if (subj) return subj.slice(0, 30);
      return name || "Click to call";
    }
    case "NO_OP":
      return s(configuration.message) || name || "No-op";
    default:
      return name || actionType;
  }
}

export function isNodeConfigured(data: BuilderNodeData): {
  configured: boolean;
  issues: string[];
} {
  const issues: string[] = [];
  const cfg = data.configuration ?? {};
  switch (data.nodeType) {
    case "TRIGGER": {
      const entityType = typeof cfg.entityType === "string" ? cfg.entityType.trim() : "";
      const eventType = typeof cfg.eventType === "string" ? cfg.eventType.trim() : "";
      if (!entityType) issues.push("Entity type required");
      if (!eventType) issues.push("Event type required");
      break;
    }
    case "CONDITION":
    case "BRANCH": {
      const logic = typeof cfg.logic === "string" ? String(cfg.logic).trim().toUpperCase() : "";
      if (logic && logic !== "AND" && logic !== "OR") issues.push("Logic must be AND or OR");
      const raw = Array.isArray(cfg.conditions) ? (cfg.conditions as Array<Record<string, unknown>>) : [];
      if (raw.length === 0) issues.push("At least one condition required");
      else {
        for (const c of raw) {
          const field = typeof c.field === "string" ? c.field.trim() : "";
          if (!field) issues.push("Condition field required");
        }
      }
      break;
    }
    case "ACTION": {
      const actionType = typeof cfg.actionType === "string" ? cfg.actionType.trim() : "";
      if (!actionType) {
        issues.push("Action type required");
        break;
      }
      const conf =
        cfg.config && typeof cfg.config === "object"
          ? (cfg.config as Record<string, unknown>)
          : {};
      if (actionType === "HTTP_API") {
        const url = typeof conf.url === "string" ? conf.url.trim() : "";
        const method = typeof conf.method === "string" ? conf.method.trim().toUpperCase() : "";
        if (!url) issues.push("URL required");
        if (!method) issues.push("Method required");
        else if (!["GET", "POST", "PUT", "PATCH", "DELETE"].includes(method))
          issues.push("Method must be GET, POST, PUT, PATCH, or DELETE");
      } else if (actionType === "CREATE_TASK") {
        const subject = typeof conf.subject === "string" ? conf.subject.trim() : "";
        if (!subject) issues.push("Subject required");
      } else if (actionType === "UPDATE_ENTITY_FIELD") {
        for (const k of ["entityType", "field"]) {
          const v = typeof conf[k] === "string" ? String(conf[k]).trim() : "";
          if (!v) issues.push(`${k} required`);
        }
        if (!("value" in conf)) issues.push("value required");
        const entityId = typeof conf.entityId === "string" ? conf.entityId.trim() : "";
        if (!entityId) issues.push("entityId required");
      } else if (actionType === "ASSIGN_OWNER") {
        for (const k of ["entityType", "entityId", "ownerId"]) {
          const v = typeof conf[k] === "string" ? String(conf[k]).trim() : "";
          if (!v) issues.push(`${k} required`);
        }
      } else if (actionType === "CLICK_TO_CALL") {
        const phone = typeof conf.phoneNumber === "string" ? conf.phoneNumber.trim() : "";
        const et = typeof conf.entityType === "string" ? conf.entityType.trim() : "";
        const eid = typeof conf.entityId === "string" ? conf.entityId.trim() : "";
        if (!phone && !(et && eid)) issues.push("phoneNumber or entityType+entityId required");
      } else if (actionType === "SET_CONTEXT_VALUE") {
        const key = typeof conf.key === "string" ? conf.key.trim() : "";
        if (!key) issues.push("Context key required");
      }
      break;
    }
    case "WAIT": {
      const resumeAt = typeof cfg.resumeAt === "string" ? cfg.resumeAt.trim() : "";
      if (!resumeAt) issues.push("Resume time required");
      else {
        const d = new Date(resumeAt);
        if (Number.isNaN(d.getTime())) issues.push("Resume time must be ISO-8601 UTC");
      }
      break;
    }
    case "END":
      break;
    default:
      break;
  }
  return { configured: issues.length === 0, issues };
}
