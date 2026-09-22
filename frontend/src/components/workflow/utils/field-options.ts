import { WorkflowEntityMetadata, WorkflowMetadataResponse } from "@/types/workflow";
import { WorkflowReferenceData, WorkflowValueOption } from "@/lib/hooks/workflow";

export type { WorkflowValueOption };

/**
 * Field-option model for CONDITION / BRANCH editors.
 *
 * Groups mirror where values originate at runtime:
 *  - entity.*                       → primary entity context provider
 *  - entity.customFields.<key>      → tenant custom-field definitions
 *  - entity.<relKey>.<field>        → controlled one-hop related record
 *  - entity.<relKey>.customFields.<key> → related tenant custom fields
 *  - entity.data.<fieldKey>         → Record dynamic fields (tenant-defined)
 *  - trigger.metadata.*             → canonical event metadata
 *  - nodeOutputs.<nodeKey>          → upstream node output objects
 *
 * Only stable application field keys are offered — never persistence paths.
 */

export type FieldGroup =
  | "entity"
  | "custom"
  | `rel:${string}`
  | "recordData"
  | "metadata"
  | "nodeOutputs";

export interface WorkflowFieldOption {
  /** Full context path stored in condition config, e.g. "entity.status". */
  field: string;
  label: string;
  group: FieldGroup;
  /** Human-readable group header shown in the picker. */
  groupLabel: string;
  /** Controlled value options for this field, when applicable. */
  valueOptions?: WorkflowValueOption[];
}

export interface RelationshipMeta {
  key: string;
  label: string;
  relatedEntityType: string | null;
  fields: string[];
  customFieldsSupported: boolean;
}

export interface RecordFieldMeta {
  fieldKey: string;
  fieldLabel: string;
  fieldType: string;
  referenceEntityType?: string | null;
}

export interface FieldOptionContextInput {
  metadata?: WorkflowMetadataResponse;
  triggerEntityType?: string;
  referenceData?: WorkflowReferenceData;
  relationshipData?: Record<string, WorkflowReferenceData>;
  staticEnumOptions?: Record<string, WorkflowValueOption[]>;
  /** Record dynamic fields from RecordType/RecordField (for RECORD workflows) */
  recordFields?: RecordFieldMeta[];
  /** Current node type for node-aware filtering */
  nodeType?: string;
  /** Current action type for node-aware filtering */
  actionType?: string;
  /** Whether this is a trigger configuration (different from runtime) */
  isTriggerConfig?: boolean;
}

const ENTITY_DISPLAY_NAMES: Record<string, string> = {
  LEAD: "Lead",
  CONTACT: "Contact",
  ACCOUNT: "Account",
  DEAL: "Deal",
  TASK: "Task",
  MEETING: "Meeting",
  CALL: "Call",
  RECORD: "Record",
};

const RELATIONSHIP_LABELS: Record<string, string> = {
  account: "Account",
  contact: "Contact",
  lead: "Lead",
  convertedAccount: "Converted Account",
  convertedContact: "Converted Contact",
  related: "Related Record",
};

function titleCase(value: string): string {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/^./, (char) => char.toUpperCase());
}

const TRIGGER_METADATA_LABELS: Record<string, string> = {
  createdVia: "Created Via",
  ingestionConfigId: "Ingestion Configuration",
  ingestionEventId: "Ingestion Event",
};

export function findEntityMetadata(
  metadata: WorkflowMetadataResponse | undefined,
  entityType?: string
): WorkflowEntityMetadata | undefined {
  if (!metadata || !entityType) return undefined;
  return metadata.entities.find(
    (entity) => entity.entityType === entityType
  );
}

function withValueOptions(
  option: WorkflowFieldOption,
  sources: Array<WorkflowValueOption[] | undefined>
): WorkflowFieldOption {
  for (const source of sources) {
    if (source && source.length > 0) return { ...option, valueOptions: source };
  }
  return option;
}

function getEntityDisplayName(entityType: string): string {
  return ENTITY_DISPLAY_NAMES[entityType] || titleCase(entityType.toLowerCase());
}

function getEntityGroupLabel(entityType: string): string {
  return `Current ${getEntityDisplayName(entityType)}`;
}

export interface BuildFieldOptionsResult {
  options: WorkflowFieldOption[];
  groupedOptions: Array<{ group: FieldGroup; groupLabel: string; options: WorkflowFieldOption[] }>;
  hasEntity: boolean;
  entityType?: string;
}

/**
 * Canonical field option builder for the workflow-wide Insert Value picker.
 *
 * Provides node-aware, entity-aware, dynamically-loaded field options
 * with empty-group suppression and logical ordering.
 */
export function buildFieldOptions({
  metadata,
  triggerEntityType,
  referenceData,
  relationshipData = {},
  staticEnumOptions = {},
  recordFields = [],
  nodeType,
  actionType,
  isTriggerConfig = false,
}: FieldOptionContextInput): BuildFieldOptionsResult {
  const options: WorkflowFieldOption[] = [];
  const entity = findEntityMetadata(metadata, triggerEntityType);
  const hasEntity = !!entity;
  const entityType = entity?.entityType;
  const entityGroupLabel = entityType ? getEntityGroupLabel(entityType) : "Current Entity";

  // Track which groups have content for empty-group suppression
  const groupHasContent = new Set<FieldGroup>();

  if (entity) {
    const relationshipKeys = new Set(entity.relationships.map((rel) => rel.key));

    // Standard entity fields (not relationships)
    for (const field of entity.fields) {
      if (relationshipKeys.has(field)) continue; // rendered as its own group
      options.push(
        withValueOptions(
          {
            field: `entity.${field}`,
            label: titleCase(field),
            group: "entity",
            groupLabel: entityGroupLabel,
          },
          [referenceData?.optionsByField[`entity.${field}`], staticEnumOptions[`entity.${field}`]]
        )
      );
      groupHasContent.add("entity");
    }

    // Custom fields for standard entities (LEAD, CONTACT, ACCOUNT, DEAL, TASK, MEETING, CALL)
    const customFieldKeys = referenceData?.customFieldKeys ?? [];
    if (customFieldKeys.length > 0) {
      for (const key of customFieldKeys) {
        options.push({
          field: `entity.customFields.${key}`,
          label: `Custom: ${titleCase(key)}`,
          group: "custom",
          groupLabel: "Custom Fields",
        });
      }
      groupHasContent.add("custom");
    }

    // Declared relationships (Lead convertedAccount/convertedContact, Contact account, Deal account/contact/lead, etc.)
    for (const rel of entity.relationships as Array<RelationshipMeta & { key: string }>) {
      const relRef = relationshipData[rel.key];
      const group: FieldGroup = `rel:${rel.key}`;
      const groupLabel = rel.label || RELATIONSHIP_LABELS[rel.key] || titleCase(rel.key);
      let hasRelFields = false;

      for (const field of rel.fields) {
        options.push(
          withValueOptions(
            {
              field: `entity.${rel.key}.${field}`,
              label: `${groupLabel} → ${titleCase(field)}`,
              group,
              groupLabel,
            },
            [
              relRef?.optionsByField[`entity.${field}`],
              referenceData?.optionsByField[`entity.${field}`],
              staticEnumOptions[`entity.${rel.key}.${field}`],
            ]
          )
        );
        hasRelFields = true;
      }

      if (rel.customFieldsSupported && rel.relatedEntityType) {
        for (const key of relRef?.customFieldKeys ?? []) {
          options.push({
            field: `entity.${rel.key}.customFields.${key}`,
            label: `${groupLabel} → Custom: ${titleCase(key)}`,
            group,
            groupLabel,
          });
          hasRelFields = true;
        }
      }

      if (hasRelFields) {
        groupHasContent.add(group);
      }
    }
  }

  // RECORD dynamic fields (Record Data) — from tenant-defined RecordField definitions
  if (recordFields.length > 0) {
    const recordDataGroupLabel = entityType === "RECORD" ? "Record Data" : "Record Data";
    for (const rf of recordFields) {
      if (rf.fieldType === "REFERENCE" && rf.referenceEntityType) {
        // REFERENCE fields are handled via related records section
        const refType = rf.referenceEntityType.trim().toUpperCase();
        const relatedKey = refType.toLowerCase();
        const relatedMeta = metadata?.entities.find((e) => e.entityType === refType);
        if (relatedMeta) {
          const group: FieldGroup = `rel:${relatedKey}`;
          const groupLabel = relatedMeta.label || RELATIONSHIP_LABELS[relatedKey] || titleCase(relatedKey);
          for (const field of relatedMeta.fields) {
            // Skip relationship fields to avoid duplication
            if (relatedMeta.relationships.some((r) => r.key === field)) continue;
            options.push(
              withValueOptions(
                {
                  field: `entity.${relatedKey}.${field}`,
                  label: `${groupLabel} → ${titleCase(field)}`,
                  group,
                  groupLabel,
                },
                [
                  relationshipData[relatedKey]?.optionsByField[`entity.${field}`],
                  referenceData?.optionsByField[`entity.${field}`],
                  staticEnumOptions[`entity.${relatedKey}.${field}`],
                ]
              )
            );
            groupHasContent.add(group);
          }
          if (relatedMeta.customFieldsSupported) {
            for (const key of relationshipData[relatedKey]?.customFieldKeys ?? []) {
              options.push({
                field: `entity.${relatedKey}.customFields.${key}`,
                label: `${groupLabel} → Custom: ${titleCase(key)}`,
                group,
                groupLabel,
              });
              groupHasContent.add(group);
            }
          }
        } else {
          // Fallback for unsupported related entity types (e.g., TASK, MEETING, CALL)
          const group: FieldGroup = `rel:${relatedKey}`;
          const groupLabel = RELATIONSHIP_LABELS[relatedKey] || titleCase(relatedKey);
          options.push({
            field: `entity.${relatedKey}.id`,
            label: `${groupLabel} → ID`,
            group,
            groupLabel,
          });
          groupHasContent.add(group);
        }
      } else {
        // Standard Record data field
        options.push({
          field: `entity.data.${rf.fieldKey}`,
          label: `${recordDataGroupLabel} → ${rf.fieldLabel} (${rf.fieldKey})`,
          group: "recordData",
          groupLabel: recordDataGroupLabel,
        });
        groupHasContent.add("recordData");
      }
    }
  }

  // Trigger metadata — only for trigger configurations or when trigger metadata is relevant
  const showTriggerMetadata = isTriggerConfig || nodeType === "TRIGGER" || !entity;
  if (showTriggerMetadata) {
    const seen = new Set<string>();
    for (const candidate of entity?.events ?? []) {
      for (const field of candidate.metadataFields) {
        if (seen.has(field)) continue;
        seen.add(field);
        const label = TRIGGER_METADATA_LABELS[field] ?? titleCase(field);
        options.push(
          withValueOptions(
            { field: `trigger.metadata.${field}`, label, group: "metadata", groupLabel: "Trigger Metadata" },
            [referenceData?.optionsByField[`trigger.metadata.${field}`]]
          )
        );
        groupHasContent.add("metadata");
      }
    }
  }

  // Logical group ordering
  const groupOrder: FieldGroup[] = [
    "entity",
    "recordData",
    "custom",
    ...Array.from(groupHasContent).filter((g) => g.startsWith("rel:")),
    "metadata",
    "nodeOutputs",
  ];

  // Build grouped options with empty-group suppression
  const groupedOptions: Array<{ group: FieldGroup; groupLabel: string; options: WorkflowFieldOption[] }> = [];
  for (const group of groupOrder) {
    if (!groupHasContent.has(group)) continue;
    const groupOptions = options.filter((opt) => opt.group === group);
    if (groupOptions.length === 0) continue;
    const groupLabel = groupOptions[0].groupLabel;
    groupedOptions.push({ group, groupLabel, options: groupOptions });
  }

  // Also include any groups not in the standard order (edge cases)
  const allGroups = new Set(options.map((opt) => opt.group));
  for (const group of allGroups) {
    if (groupOrder.includes(group)) continue;
    const groupOptions = options.filter((opt) => opt.group === group);
    if (groupOptions.length > 0) {
      groupedOptions.push({ group, groupLabel: groupOptions[0].groupLabel, options: groupOptions });
    }
  }

  return {
    options,
    groupedOptions,
    hasEntity,
    entityType,
  };
}

export { getEntityDisplayName, getEntityGroupLabel };