import { WorkflowGraphNodeResponse, WorkflowGraphEdgeResponse, WorkflowNodeType } from "@/types/workflow";

export interface BuilderNodeData extends Record<string, unknown> {
  nodeKey: string;
  nodeType: WorkflowNodeType;
  name: string;
  configuration: Record<string, unknown>;
}

export interface BuilderNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: BuilderNodeData;
}

export interface BuilderEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string | null;
  targetHandle?: string | null;
  data: {
    edgeKey: string | null;
    configuration: Record<string, unknown>;
  };
}

export const CLIENT_ID_PREFIX = "ui-";

export function isClientId(id: string): boolean {
  return id.startsWith(CLIENT_ID_PREFIX);
}

export function newClientNodeId(): string {
  return `${CLIENT_ID_PREFIX}${crypto.randomUUID()}`;
}

const NODE_TYPE_PREFIXES: Record<string, string> = {
  TRIGGER: "trigger",
  CONDITION: "condition",
  ACTION: "action",
  END: "end",
  WAIT: "wait",
  BRANCH: "branch",
};

export function generateNodeKey(nodeType: WorkflowNodeType, existingKeys: string[]): string {
  const prefix = NODE_TYPE_PREFIXES[nodeType] ?? "node";
  let index = 1;
  let candidate = `${prefix}_${index}`;
  while (existingKeys.includes(candidate)) {
    index += 1;
    candidate = `${prefix}_${index}`;
  }
  return candidate;
}

/**
 * Converts a persisted backend graph into React Flow nodes/edges.
 *
 * The backend does not persist node positions, so a deterministic layered
 * layout is derived here. This is isolated so persisted positions can be
 * introduced later without touching UI code.
 */
export function toFlowGraph(
  nodes: WorkflowGraphNodeResponse[],
  edges: WorkflowGraphEdgeResponse[]
): { nodes: BuilderNode[]; edges: BuilderEdge[] } {
  const flowNodes: BuilderNode[] = nodes.map((node) => ({
    id: node.id,
    type: node.nodeType.toLowerCase(),
    position: { x: 0, y: 0 },
    data: {
      nodeKey: node.nodeKey,
      nodeType: node.nodeType,
      name: node.name,
      configuration: { ...(node.configuration ?? {}) },
    },
  }));

  const flowEdges: BuilderEdge[] = edges.map((edge) => {
    const outcome =
      typeof edge.configuration?.outcome === "string"
        ? String(edge.configuration.outcome).toLowerCase()
        : null;
    // BRANCH edges carry TRUE/FALSE in edgeKey (CONDITION uses
    // configuration.outcome). Map both to the matching source handle so the
    // graph reloads with correct visual routing.
    const branchKey =
      outcome === null && (edge.edgeKey === "TRUE" || edge.edgeKey === "FALSE")
        ? edge.edgeKey.toLowerCase()
        : null;

    return {
      id: edge.id,
      source: edge.sourceNodeId,
      target: edge.targetNodeId,
      sourceHandle:
        outcome === "true" || outcome === "false"
          ? outcome
          : branchKey,
      data: {
        edgeKey: edge.edgeKey,
        configuration: { ...(edge.configuration ?? {}) },
      },
    };
  });

  applyLayeredLayout(flowNodes, flowEdges);

  return { nodes: flowNodes, edges: flowEdges };
}

/**
 * Deterministic layout: breadth-first layers starting from TRIGGER nodes,
 * then any remaining unreachable nodes in their original order.
 */
function applyLayeredLayout(nodes: BuilderNode[], edges: BuilderEdge[]): void {
  const HORIZONTAL_SPACING = 280;
  const VERTICAL_SPACING = 160;

  const outgoing = new Map<string, string[]>();
  for (const edge of edges) {
    const list = outgoing.get(edge.source) ?? [];
    list.push(edge.target);
    outgoing.set(edge.source, list);
  }

  const depth = new Map<string, number>();
  const queue: string[] = [];

  for (const node of nodes) {
    if (node.data.nodeType === "TRIGGER") {
      depth.set(node.id, 0);
      queue.push(node.id);
    }
  }

  let head = 0;
  while (head < queue.length) {
    const current = queue[head];
    head += 1;
    const currentDepth = depth.get(current) ?? 0;
    for (const target of outgoing.get(current) ?? []) {
      if (!depth.has(target)) {
        depth.set(target, currentDepth + 1);
        queue.push(target);
      }
    }
  }

  let fallbackDepth = Math.max(0, ...Array.from(depth.values())) + 1;
  for (const node of nodes) {
    if (!depth.has(node.id)) {
      depth.set(node.id, fallbackDepth);
      fallbackDepth += 1;
    }
  }

  const byDepth = new Map<number, BuilderNode[]>();
  for (const node of nodes) {
    const d = depth.get(node.id) ?? 0;
    const list = byDepth.get(d) ?? [];
    list.push(node);
    byDepth.set(d, list);
  }

  for (const [d, list] of Array.from(byDepth.entries()).sort((a, b) => a[0] - b[0])) {
    list.forEach((node, index) => {
      const offsetX = ((list.length - 1) * HORIZONTAL_SPACING) / 2;
      node.position = {
        x: index * HORIZONTAL_SPACING - offsetX + 400,
        y: d * VERTICAL_SPACING + 60,
      };
    });
  }
}
