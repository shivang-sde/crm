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
 *  - trigger.metadata.*             → canonical event metadata
 *  - nodeOutputs.<nodeKey>          → upstream node output objects
 *
 * Only stable application field keys are offered — never persistence paths.
 */

export type FieldGroup =
  | "entity"
  | "custom"
  | `rel:${string}`
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

export interface FieldOptionContextInput {
  metadata?: WorkflowMetadataResponse;
  triggerEntityType?: string;
  referenceData?: WorkflowReferenceData;
  relationshipData?: Record<string, WorkflowReferenceData>;
  staticEnumOptions?: Record<string, WorkflowValueOption[]>;
}

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

export function buildFieldOptions({
  metadata,
  triggerEntityType,
  referenceData,
  relationshipData = {},
  staticEnumOptions = {},
}: FieldOptionContextInput): WorkflowFieldOption[] {
  const options: WorkflowFieldOption[] = [];
  const entity = findEntityMetadata(metadata, triggerEntityType);

  if (entity) {
    const relationshipKeys = new Set(entity.relationships.map((rel) => rel.key));
    for (const field of entity.fields) {
      if (relationshipKeys.has(field)) continue; // rendered as its own group
      options.push(
        withValueOptions(
          { field: `entity.${field}`, label: titleCase(field), group: "entity", groupLabel: "Current Record" },
          [referenceData?.optionsByField[`entity.${field}`], staticEnumOptions[`entity.${field}`]]
        )
      );
    }
    for (const key of referenceData?.customFieldKeys ?? []) {
      options.push({
        field: `entity.customFields.${key}`,
        label: `Custom: ${key}`,
        group: "custom",
        groupLabel: "Custom Fields",
      });
    }

    for (const rel of entity.relationships as Array<RelationshipMeta & { key: string }>) {
      const relRef = relationshipData[rel.key];
      const group: FieldGroup = `rel:${rel.key}`;
      const groupLabel = rel.label || RELATIONSHIP_LABELS[rel.key] || titleCase(rel.key);
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
      }
      if (rel.customFieldsSupported && rel.relatedEntityType) {
        for (const key of relRef?.customFieldKeys ?? []) {
          options.push({
            field: `entity.${rel.key}.customFields.${key}`,
            label: `${groupLabel} → Custom: ${key}`,
            group,
            groupLabel,
          });
        }
      }
    }
  }

  const seen = new Set<string>();
  for (const candidate of entity?.events ?? []) {
    for (const field of candidate.metadataFields) {
      if (seen.has(field)) continue;
      seen.add(field);
      options.push(
        withValueOptions(
          { field: `trigger.metadata.${field}`, label: titleCase(field), group: "metadata", groupLabel: "Trigger Metadata" },
          [referenceData?.optionsByField[`trigger.metadata.${field}`]]
        )
      );
    }
  }

  return options;
}
