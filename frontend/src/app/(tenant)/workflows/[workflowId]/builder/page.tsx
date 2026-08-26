"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import {
  addEdge,
  Connection,
  Edge,
  Node,
  NodeChange,
  EdgeChange,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
} from "@xyflow/react";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Save, ShieldCheck } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  workflowKeys,
  useActivateWorkflowVersion,
  useCreateWorkflowEdge,
  useCreateWorkflowNode,
  useDeleteWorkflowEdge,
  useDeleteWorkflowNode,
  useUpdateWorkflowEdge,
  useUpdateWorkflowNode,
  useValidateWorkflowVersion,
  useWorkflow,
  useWorkflowGraph,
} from "@/lib/hooks/workflow";
import { WorkflowCanvas } from "@/components/workflow/WorkflowCanvas";
import { WorkflowNodePalette } from "@/components/workflow/WorkflowNodePalette";
import { WorkflowNodeConfigPanel } from "@/components/workflow/WorkflowNodeConfigPanel";
import { WorkflowEdgeConfigPanel } from "@/components/workflow/WorkflowEdgeConfigPanel";
import { WorkflowValidationPanel } from "@/components/workflow/WorkflowValidationPanel";
import {
  buildGraphSnapshot,
  planGraphSave,
  GraphSnapshot,
} from "@/components/workflow/utils/graph-reconciliation";
import {
  BuilderEdge,
  BuilderNode,
  generateNodeKey,
  newClientNodeId,
  toFlowGraph,
} from "@/components/workflow/utils/graph-mapper";
import { usePermissions } from "@/lib/hooks/usePermissions";
import {
  WorkflowNodeType,
  WorkflowValidationIssue,
} from "@/types/workflow";

const NODE_DEFAULTS: Record<
  string,
  { name: string; configuration: Record<string, unknown>; advancedDeferred?: boolean }
> = {
  TRIGGER: { name: "Trigger", configuration: { entityType: "", eventType: "" } },
  CONDITION: { name: "Condition", configuration: { logic: "AND", conditions: [] } },
  ACTION: { name: "No Op", configuration: { actionType: "NO_OP", message: "" } },
  END: { name: "End", configuration: {} },
  WAIT: { name: "Wait", configuration: { resumeAt: "" } },
  BRANCH: { name: "Branch", configuration: { logic: "AND", conditions: [] } },
};

function BuilderInner() {
  const params = useParams<{ workflowId: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { screenToFlowPosition, fitView, setCenter } = useReactFlow();

  const workflowId = params?.workflowId ?? "";
  const versionId = searchParams.get("versionId") ?? "";
  const focusNodeId = searchParams.get("nodeId");

  const { canViewWorkflows, canEditWorkflows } = usePermissions();

  const workflowQuery = useWorkflow(workflowId);
  const graphQuery = useWorkflowGraph(versionId || undefined);
  const validate = useValidateWorkflowVersion(versionId);
  const activate = useActivateWorkflowVersion(workflowId);
  const createNodeMutation = useCreateWorkflowNode(versionId);
  const updateNodeMutation = useUpdateWorkflowNode(versionId);
  const deleteNodeMutation = useDeleteWorkflowNode(versionId);
  const createEdgeMutation = useCreateWorkflowEdge(versionId);
  const updateEdgeMutation = useUpdateWorkflowEdge(versionId);
  const deleteEdgeMutation = useDeleteWorkflowEdge(versionId);

  const [nodes, setNodes, onNodesChangeBase] = useNodesState<BuilderNode>([]);
  const [edges, setEdges, onEdgesChangeBase] = useEdgesState<BuilderEdge>([]);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [validationOpen, setValidationOpen] = useState(false);
  const [validationIssues, setValidationIssues] = useState<WorkflowValidationIssue[] | null>(
    null
  );
  const [validatedClean, setValidatedClean] = useState(false);

  const snapshotRef = useRef<GraphSnapshot>({ nodes: new Map(), edges: new Map() });

  const version = graphQuery.data?.version;
  const readOnly =
    !canEditWorkflows || Boolean(version && version.status !== "DRAFT");

  useEffect(() => {
    if (!graphQuery.data) return;
    const converted = toFlowGraph(graphQuery.data.nodes, graphQuery.data.edges);
    setNodes(converted.nodes);
    setEdges(converted.edges);
    snapshotRef.current = buildGraphSnapshot(
      graphQuery.data.nodes,
      graphQuery.data.edges
    );
    setTimeout(() => {
      const focusNode = focusNodeId ? converted.nodes.find((n) => n.id === focusNodeId) : null;
      if (focusNode) {
        setNodes((current) =>
          current.map((n) =>
            n.id === focusNode.id ? { ...n, selected: true } : n
          )
        );
        setSelectedNodeId(focusNode.id);
        setCenter(focusNode.position.x, focusNode.position.y, { zoom: 1.1, duration: 400 });
      } else {
        fitView({ padding: 0.15 });
      }
    }, 60);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [graphQuery.data]);

  const plan = useMemo(
    () =>
      versionId
        ? planGraphSave(snapshotRef.current, nodes, edges, () => "")
        : {
            nodesToCreate: [],
            nodesToUpdate: [],
            nodesToDelete: [] as string[],
            edgesToCreate: [],
            edgesToUpdate: [],
            edgesToDelete: [] as string[],
          },
    [nodes, edges, versionId]
  );

  const isDirty =
    plan.nodesToCreate.length > 0 ||
    plan.nodesToUpdate.length > 0 ||
    plan.nodesToDelete.length > 0 ||
    plan.edgesToCreate.length > 0 ||
    plan.edgesToUpdate.length > 0 ||
    plan.edgesToDelete.length > 0;

  useEffect(() => {
    setValidatedClean(false);
  }, [isDirty]);

  useEffect(() => {
    if (isDirty) {
      const handler = (event: BeforeUnloadEvent) => {
        event.preventDefault();
      };
      window.addEventListener("beforeunload", handler);
      return () => window.removeEventListener("beforeunload", handler);
    }
  }, [isDirty]);

  const selectedNode = useMemo(
    () => (nodes.find((n) => n.id === selectedNodeId) as BuilderNode | undefined) ?? null,
    [nodes, selectedNodeId]
  );
  const selectedEdge = useMemo(
    () => edges.find((e) => e.id === selectedEdgeId) ?? null,
    [edges, selectedEdgeId]
  );

  // Trigger context drives context-aware editors (conditions, actions).
  const triggerNode = nodes.find((n) => n.data.nodeType === "TRIGGER");
  const triggerEntityType =
    typeof triggerNode?.data.configuration.entityType === "string"
      ? triggerNode.data.configuration.entityType
      : version?.triggerEntityType ?? "";
  const triggerEventType =
    typeof triggerNode?.data.configuration.eventType === "string"
      ? triggerNode.data.configuration.eventType
      : version?.triggerEventType ?? "";

  const handleNodesChange = useCallback(
    (changes: NodeChange[]) => {
      onNodesChangeBase(changes as NodeChange<BuilderNode>[]);
    },
    [onNodesChangeBase]
  );

  const handleConnect = useCallback(
    (connection: Connection) => {
      if (readOnly) return;
      const sourceNode = nodes.find((n) => n.id === connection.source);
      const sourceIsCondition = sourceNode?.data.nodeType === "CONDITION";
      const sourceIsBranch = sourceNode?.data.nodeType === "BRANCH";

      setEdges((currentEdges) => {
        // BRANCH edges carry their outcome in edgeKey, derived from the
        // TRUE/FALSE source handle the connection starts from.
        const branchEdgeKey = sourceIsBranch
          ? connection.sourceHandle === "false"
            ? "FALSE"
            : "TRUE"
          : null;

        const newEdge: BuilderEdge = {
          id: newClientNodeId(),
          source: connection.source,
          target: connection.target ?? "",
          sourceHandle: connection.sourceHandle ?? null,
          targetHandle: connection.targetHandle ?? null,
          data: {
            edgeKey: branchEdgeKey,
            configuration: sourceIsCondition
              ? { outcome: connection.sourceHandle === "false" ? "FALSE" : "TRUE" }
              : {},
          },
        };
        return addEdge(newEdge as unknown as Edge, currentEdges) as BuilderEdge[];
      });
    },
    [nodes, readOnly, setEdges]
  );

  const handleDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      if (readOnly) return;

      const rawType = event.dataTransfer.getData("application/workflow-node-type");
      if (!rawType) return;
      const nodeType = rawType as WorkflowNodeType;

      const position = screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      });

      const existingKeys = nodes.map((n) => n.data.nodeKey);
      const defaults = NODE_DEFAULTS[nodeType] ?? { name: nodeType, configuration: {} };

      const newNode: BuilderNode = {
        id: newClientNodeId(),
        type: nodeType.toLowerCase(),
        position,
        data: {
          nodeKey: generateNodeKey(nodeType, existingKeys),
          nodeType,
          name: defaults.name,
          configuration: { ...defaults.configuration },
        },
      };

      setNodes((currentNodes) => [...currentNodes, newNode]);
    },
    [nodes, readOnly, screenToFlowPosition, setNodes]
  );

  const handleSelectionChanged = useCallback(
    (selection: { nodes: Node[]; edges: Edge[] }) => {
      setSelectedNodeId(selection.nodes[0]?.id ?? null);
      setSelectedEdgeId(selection.edges[0]?.id ?? null);
    },
    []
  );

  const handleNodeConfigurationChange = (
    configuration: Record<string, unknown>,
    name?: string
  ) => {
    if (!selectedNode) return;
    setNodes((currentNodes) =>
      currentNodes.map((node) =>
        node.id === selectedNode.id
          ? {
              ...node,
              data: {
                ...node.data,
                configuration,
                ...(name !== undefined ? { name } : {}),
              },
            }
          : node
      )
    );
  };

  const handleEdgeDataChange = (data: {
    edgeKey: string | null;
    configuration: Record<string, unknown>;
  }) => {
    if (!selectedEdge) return;
    setEdges((currentEdges) =>
      currentEdges.map((edge) =>
        edge.id === selectedEdge.id ? { ...edge, data: { ...edge.data, ...data } } : edge
      )
    );
  };

  const handleSelectedEdgeDelete = () => {
    if (!selectedEdge) return;
    setEdges((currentEdges) => currentEdges.filter((edge) => edge.id !== selectedEdge.id));
    setSelectedEdgeId(null);
  };

  const executeSavePlan = async (): Promise<boolean> => {
    let failed = false;

    const planNow = planGraphSave(snapshotRef.current, nodes, edges, () => "");
    const idMap = new Map<string, string>();
    const mapId = (id: string) => idMap.get(id) ?? id;

    for (const node of planNow.nodesToCreate) {
      try {
        const realId = await createNodeMutation.mutateAsync({
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
          await updateNodeMutation.mutateAsync({
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
          await deleteEdgeMutation.mutateAsync(edgeId);
        } catch {
          failed = true;
          break;
        }
      }
    }

    if (!failed) {
      for (const edge of planNow.edgesToUpdate) {
        try {
          await updateEdgeMutation.mutateAsync({
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
          await createEdgeMutation.mutateAsync({
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
          await deleteNodeMutation.mutateAsync(nodeId);
        } catch {
          failed = true;
          break;
        }
      }
    }

    return !failed;
  };

  const handleSave = async () => {
    if (readOnly || saving) return;
    setSaving(true);
    try {
      const success = await executeSavePlan();
      if (!success) {
        toast.error("Failed to save workflow — your changes are preserved.");
        return;
      }

      await queryClient.invalidateQueries({ queryKey: workflowKeys.graph(versionId) });
      await queryClient.refetchQueries({ queryKey: workflowKeys.graph(versionId) });
      setValidatedClean(false);
      toast.success("Workflow saved");
    } finally {
      setSaving(false);
    }
  };

  const handleValidate = async () => {
    if (!versionId) return;
    try {
      const issues = await validate.mutateAsync();
      setValidationIssues(issues ?? []);
      setValidatedClean((issues ?? []).length === 0);
      setValidationOpen(true);
      toast.success("Validation completed");
    } catch {
      toast.error("Validation request failed");
    }
  };

  const handleActivate = async () => {
    if (isDirty) {
      toast.error("Save your changes before activating");
      return;
    }
    if (!validatedClean) {
      toast.error("Run Validate successfully before activating");
      return;
    }
    try {
      await activate.mutateAsync(versionId);
      toast.success("Workflow activated");
      await queryClient.invalidateQueries({ queryKey: workflowKeys.graph(versionId) });
      await queryClient.invalidateQueries({ queryKey: workflowKeys.versions(workflowId) });
      router.push(`/workflows/${workflowId}`);
    } catch {
      toast.error("Failed to activate workflow version");
    }
  };

  if (!canViewWorkflows) {
    return (
      <div className="space-y-6 p-6">
        <h1 className="text-2xl font-semibold">Workflow Builder</h1>
        <p className="text-sm text-muted-foreground">
          You do not have permission to view workflows.
        </p>
      </div>
    );
  }

  if (!versionId) {
    return (
      <div className="space-y-6 p-6">
        <p className="text-sm text-muted-foreground">
          No workflow version selected. Open the builder from a workflow version.
        </p>
        <Link href={`/workflows/${workflowId}`} className="text-sm underline">
          ← Back to workflow
        </Link>
      </div>
    );
  }

  const workflowName = workflowQuery.data?.name ?? "Workflow";

  return (
    <div className="flex h-[calc(100vh-4rem)] flex-col p-4">
      <div className="mb-3 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <Link
            href={`/workflows/${workflowId}`}
            className="mb-1 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to workflow
          </Link>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-xl font-semibold">{workflowName}</h1>
            <span className="text-sm text-muted-foreground">
              Version {version?.versionNumber ?? "…"}
            </span>
            {version && (
              <Badge variant={version.status === "DRAFT" ? "outline" : "default"}>
                {version.status}
              </Badge>
            )}
            {isDirty ? (
              <Badge variant="destructive">Unsaved changes</Badge>
            ) : saving ? (
              <Badge variant="secondary">Saving...</Badge>
            ) : (
              <Badge variant="secondary">Saved</Badge>
            )}
          </div>
        </div>

        {!readOnly && (
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={handleValidate}
              disabled={validate.isPending}
            >
              Validate
            </Button>
            <Button
              size="sm"
              onClick={handleSave}
              disabled={saving || !isDirty}
            >
              <Save className="mr-2 h-4 w-4" /> Save Draft
            </Button>
            <Button
              size="sm"
              onClick={handleActivate}
              disabled={
                activate.isPending || isDirty || !validatedClean || version?.status !== "DRAFT"
              }
              title={
                version?.status !== "DRAFT"
                  ? "Only DRAFT versions can be activated"
                  : !validatedClean
                    ? "Run Validate before activating"
                    : isDirty
                      ? "Save your changes first"
                      : "Activate this version"
              }
            >
              <ShieldCheck className="mr-2 h-4 w-4" /> Activate
            </Button>
          </div>
        )}
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 lg:grid-cols-[200px_1fr_280px]">
        <Card className="hidden overflow-auto p-3 lg:block">
          <WorkflowNodePalette disabled={readOnly} />
        </Card>

        <Card className="relative min-h-[420px] overflow-hidden">
          {nodes.length === 0 && !graphQuery.isLoading && (
            <div className="pointer-events-none absolute inset-0 z-10 flex flex-col items-center justify-center text-center text-muted-foreground">
              <p className="font-medium">Start building your workflow</p>
              <p className="text-xs">Drag a Trigger onto the canvas to get started.</p>
            </div>
          )}
          <ReactFlowProvider>
            <WorkflowCanvas
              nodes={nodes}
              edges={edges}
              readOnly={readOnly}
              onNodesChange={handleNodesChange}
              onEdgesChange={(changes) => onEdgesChangeBase(changes as EdgeChange<BuilderEdge>[])}
              onConnect={handleConnect}
              onDrop={handleDrop}
              onDragOver={(event) => {
                event.preventDefault();
                event.dataTransfer.dropEffect = "move";
              }}
              onSelectionChanged={handleSelectionChanged}
            />
          </ReactFlowProvider>
        </Card>

        <Card className="hidden overflow-auto p-3 lg:block">
          {selectedNode ? (
            <WorkflowNodeConfigPanel
              node={selectedNode}
              readOnly={readOnly}
              triggerEntityType={triggerEntityType}
              triggerEventType={triggerEventType}
              nodeKeys={nodes.map((n) => n.data.nodeKey)}
              onChange={handleNodeConfigurationChange}
            />
          ) : (
            <WorkflowEdgeConfigPanel
              edge={
                selectedEdge
                  ? {
                      id: selectedEdge.id,
                      data: {
                        edgeKey: (selectedEdge.data as BuilderEdge["data"]).edgeKey,
                        configuration:
                          (selectedEdge.data as BuilderEdge["data"]).configuration,
                        sourceNodeType: nodes.find((n) => n.id === selectedEdge.source)?.data
                          .nodeType,
                      },
                    }
                  : null
              }
              readOnly={readOnly}
              onChange={handleEdgeDataChange}
              onDelete={handleSelectedEdgeDelete}
            />
          )}
        </Card>
      </div>

      <WorkflowValidationPanel
        open={validationOpen}
        onOpenChange={setValidationOpen}
        issues={validationIssues ?? []}
      />
    </div>
  );
}

export default function WorkflowBuilderPage() {
  return (
    <ReactFlowProvider>
      <BuilderInner />
    </ReactFlowProvider>
  );
}
