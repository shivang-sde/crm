import type { Connection, Edge } from "@xyflow/react";

export type InvalidReason =
  | "Cannot connect a node to itself."
  | "Trigger cannot have an incoming connection."
  | "End cannot have an outgoing connection."
  | "Linear node already has an outgoing connection."
  | "Duplicate connection."
  | "Branch handle already has a connection."
  | "Target node not found."
  | "Source node not found."
  | "Connection not allowed in read-only mode."
  | null;

export function getConnectionReason(
  connection: Connection | Edge,
  nodes: Array<{ id: string; data?: { nodeType?: string } }>,
  edges: Edge[],
  readOnly: boolean
): { valid: boolean; reason: string | null } {
  if (connection.source === connection.target) {
    return { valid: false, reason: "Cannot connect a node to itself." };
  }
  if (readOnly) {
    return { valid: false, reason: "Connection not allowed in read-only mode." };
  }
  const sourceNode = nodes.find((n) => n.id === connection.source);
  if (!sourceNode) return { valid: false, reason: "Source node not found." };
  const sourceType = (sourceNode.data as { nodeType?: string } | undefined)?.nodeType;
  if (sourceType === "END") {
    return { valid: false, reason: "End cannot have an outgoing connection." };
  }
  const targetId = (connection as Connection).target ?? (connection as Edge).target;
  if (targetId === undefined) return { valid: false, reason: null }; // incomplete drag
  const targetNode = nodes.find((n) => n.id === targetId);
  if (!targetNode) return { valid: false, reason: "Target node not found." };
  const targetType = (targetNode.data as { nodeType?: string } | undefined)?.nodeType;
  if (targetType === "TRIGGER") {
    return { valid: false, reason: "Trigger cannot have an incoming connection." };
  }
  const duplicate = edges.some(
    (e) => e.source === connection.source && e.target === targetId && ((e as Edge).sourceHandle ?? null) === ((connection as Edge).sourceHandle ?? null)
  );
  if (duplicate) return { valid: false, reason: "Duplicate connection." };

  // ponytail: allow replacement — linear/branch already has outgoing is handled by replacing old edge in handleConnect/handleAddNextStep (Problem 2/3)
  // Previously blocked with "Linear node already has an outgoing connection." / "Branch handle already has a connection."
  return { valid: true, reason: null };
}
