import { BuilderEdge, BuilderNode, isClientId } from "./graph-mapper";

export interface GraphSnapshot {
  nodes: Map<
    string,
    { nodeKey: string; nodeType: string; name: string; configuration: Record<string, unknown> }
  >;
  edges: Map<
    string,
    {
      sourceNodeId: string;
      targetNodeId: string;
      edgeKey: string | null;
      configuration: Record<string, unknown>;
    }
  >;
}

export interface GraphReconciliationPlan {
  nodesToCreate: BuilderNode[];
  nodesToUpdate: BuilderNode[];
  nodesToDelete: string[];
  edgesToCreate: BuilderEdge[];
  edgesToUpdate: BuilderEdge[];
  edgesToDelete: string[];
}

function stableStringify(value: unknown): string {
  return JSON.stringify(value, Object.keys(flatten(value)).sort());
}

function flatten(value: unknown): Record<string, unknown> {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return { value };
}

export function buildGraphSnapshot(
  serverNodes: Array<{
    id: string;
    nodeKey: string;
    nodeType: string;
    name: string;
    configuration: Record<string, unknown> | null;
  }>,
  serverEdges: Array<{
    id: string;
    sourceNodeId: string;
    targetNodeId: string;
    edgeKey: string | null;
    configuration: Record<string, unknown> | null;
  }>
): GraphSnapshot {
  return {
    nodes: new Map(
      serverNodes.map((node) => [
        node.id,
        {
          nodeKey: node.nodeKey,
          nodeType: node.nodeType,
          name: node.name,
          configuration: node.configuration ?? {},
        },
      ])
    ),
    edges: new Map(
      serverEdges.map((edge) => [
        edge.id,
        {
          sourceNodeId: edge.sourceNodeId,
          targetNodeId: edge.targetNodeId,
          edgeKey: edge.edgeKey,
          configuration: edge.configuration ?? {},
        },
      ])
    ),
  };
}

export function hasUnsavedChanges(snapshot: GraphSnapshot, nodes: BuilderNode[], edges: BuilderEdge[]): boolean {
  const plan = planGraphSave(snapshot, nodes, edges, () => "");
  return (
    plan.nodesToCreate.length > 0 ||
    plan.nodesToUpdate.length > 0 ||
    plan.nodesToDelete.length > 0 ||
    plan.edgesToCreate.length > 0 ||
    plan.edgesToUpdate.length > 0 ||
    plan.edgesToDelete.length > 0
  );
}

/**
 * Diffs the local React Flow graph against the persisted snapshot.
 *
 * The caller executes the returned plan in the dependency-safe order:
 *   1. create nodes        (returns real backend ids)
 *   2. update changed nodes
 *   3. delete removed edges
 *   4. update/create edges (source/target remapped through created-node ids)
 *   5. delete removed nodes
 */
export function planGraphSave(
  snapshot: GraphSnapshot,
  nodes: BuilderNode[],
  edges: BuilderEdge[],
  resolveId: (clientId: string) => string
): GraphReconciliationPlan {
  const localNodeIds = new Set(nodes.filter((n) => !isClientId(n.id)).map((n) => n.id));
  const localEdgeIds = new Set(edges.filter((e) => !isClientId(e.id)).map((e) => e.id));

  const nodesToCreate = nodes.filter((node) => isClientId(node.id));
  const nodesToUpdate = nodes.filter((node) => {
    if (isClientId(node.id)) return false;
    const server = snapshot.nodes.get(node.id);
    if (!server) return true;
    return (
      server.nodeKey !== node.data.nodeKey ||
      server.nodeType !== node.data.nodeType ||
      server.name !== node.data.name ||
      stableStringify(server.configuration ?? {}) !==
        stableStringify(node.data.configuration ?? {})
    );
  });

  const nodesToDelete = Array.from(snapshot.nodes.keys()).filter(
    (id) => !localNodeIds.has(id)
  );

  const edgesToCreate = edges.filter((edge) => isClientId(edge.id));
  const edgesToUpdate = edges.filter((edge) => {
    if (isClientId(edge.id)) return false;
    const server = snapshot.edges.get(edge.id);
    if (!server) return true;
    return (
      server.sourceNodeId !== edge.source ||
      server.targetNodeId !== edge.target ||
      (server.edgeKey ?? "") !== (edge.data.edgeKey ?? "") ||
      stableStringify(server.configuration ?? {}) !==
        stableStringify(edge.data.configuration ?? {})
    );
  });

  const edgesToDelete = Array.from(snapshot.edges.keys()).filter(
    (id) => !localEdgeIds.has(id)
  );

  void resolveId;

  return {
    nodesToCreate,
    nodesToUpdate,
    nodesToDelete,
    edgesToCreate,
    edgesToUpdate,
    edgesToDelete,
  };
}
