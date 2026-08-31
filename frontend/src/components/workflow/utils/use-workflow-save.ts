"use client";

import { useCallback, useMemo, useState } from "react";
import { planGraphSave, type GraphSnapshot } from "./graph-reconciliation";
import type { BuilderEdge, BuilderNode } from "./graph-mapper";
import {
  useCreateWorkflowNode,
  useCreateWorkflowEdge,
  useDeleteWorkflowNode,
  useDeleteWorkflowEdge,
  useUpdateWorkflowNode,
  useUpdateWorkflowEdge,
} from "@/lib/hooks/workflow";

export function useWorkflowSave(
  versionId: string,
  nodes: BuilderNode[],
  edges: BuilderEdge[],
  snapshotRef: React.MutableRefObject<GraphSnapshot>
) {
  const createNode = useCreateWorkflowNode(versionId);
  const updateNode = useUpdateWorkflowNode(versionId);
  const deleteNode = useDeleteWorkflowNode(versionId);
  const createEdge = useCreateWorkflowEdge(versionId);
  const updateEdge = useUpdateWorkflowEdge(versionId);
  const deleteEdge = useDeleteWorkflowEdge(versionId);

  const [saving, setSaving] = useState(false);

  const plan = useMemo(
    () =>
      versionId
        ? planGraphSave(snapshotRef.current, nodes, edges, () => "")
        : {
            nodesToCreate: [] as BuilderNode[],
            nodesToUpdate: [] as BuilderNode[],
            nodesToDelete: [] as string[],
            edgesToCreate: [] as BuilderEdge[],
            edgesToUpdate: [] as BuilderEdge[],
            edgesToDelete: [] as string[],
          },
    [nodes, edges, versionId, snapshotRef]
  );

  const isDirty =
    plan.nodesToCreate.length > 0 ||
    plan.nodesToUpdate.length > 0 ||
    plan.nodesToDelete.length > 0 ||
    plan.edgesToCreate.length > 0 ||
    plan.edgesToUpdate.length > 0 ||
    plan.edgesToDelete.length > 0;

  const executeSave = useCallback(async (): Promise<boolean> => {
    if (!versionId) return false;
    let failed = false;
    const planNow = planGraphSave(snapshotRef.current, nodes, edges, () => "");
    const idMap = new Map<string, string>();
    const mapId = (id: string) => idMap.get(id) ?? id;

    for (const node of planNow.nodesToCreate) {
      try {
        const realId = await createNode.mutateAsync({
          nodeKey: node.data.nodeKey,
          nodeType: node.data.nodeType,
          name: node.data.name,
          configuration: node.data.configuration,
        });
        idMap.set(node.id, realId);
      } catch {
        failed = true;
        break;
      }
    }

    if (!failed) {
      for (const node of planNow.nodesToUpdate) {
        try {
          await updateNode.mutateAsync({
            nodeId: node.id,
            data: {
              nodeKey: node.data.nodeKey,
              nodeType: node.data.nodeType,
              name: node.data.name,
              configuration: node.data.configuration,
            },
          });
        } catch {
          failed = true;
          break;
        }
      }
    }

    if (!failed) {
      for (const edgeId of planNow.edgesToDelete) {
        try {
          await deleteEdge.mutateAsync(edgeId);
        } catch {
          failed = true;
          break;
        }
      }
    }

    if (!failed) {
      for (const edge of planNow.edgesToUpdate) {
        try {
          await updateEdge.mutateAsync({
            edgeId: edge.id,
            data: {
              sourceNodeId: mapId(edge.source),
              targetNodeId: mapId(edge.target),
              edgeKey: edge.data.edgeKey,
              configuration: edge.data.configuration,
            },
          });
        } catch {
          failed = true;
          break;
        }
      }
      for (const edge of planNow.edgesToCreate) {
        try {
          await createEdge.mutateAsync({
            sourceNodeId: mapId(edge.source),
            targetNodeId: mapId(edge.target),
            edgeKey: edge.data.edgeKey,
            configuration: edge.data.configuration,
          });
        } catch {
          failed = true;
          break;
        }
      }
    }

    if (!failed) {
      for (const nodeId of planNow.nodesToDelete) {
        try {
          await deleteNode.mutateAsync(nodeId);
        } catch {
          failed = true;
          break;
        }
      }
    }

    return !failed;
  }, [nodes, edges, versionId, snapshotRef, createNode, updateNode, deleteNode, createEdge, updateEdge, deleteEdge]);

  const save = useCallback(async (): Promise<boolean> => {
    if (saving) return false;
    setSaving(true);
    try {
      return await executeSave();
    } finally {
      setSaving(false);
    }
  }, [executeSave, saving]);

  return { plan, isDirty, saving, save, executeSave, setSaving };
}
