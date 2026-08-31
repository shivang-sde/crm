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
import { ArrowLeft, Save, ShieldCheck, Settings2, PanelLeft } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import {
  workflowKeys,
  useActivateWorkflowVersion,
  useValidateWorkflowVersion,
  useWorkflow,
  useWorkflowGraph,
  useWorkflowVersions,
} from "@/lib/hooks/workflow";
import { useWorkflowSave } from "@/components/workflow/utils/use-workflow-save";
import { WorkflowCanvas } from "@/components/workflow/WorkflowCanvas";
import { WorkflowNodePalette, paletteItems } from "@/components/workflow/WorkflowNodePalette";
import { WorkflowNodeConfigPanel } from "@/components/workflow/WorkflowNodeConfigPanel";
import { WorkflowEdgeConfigPanel } from "@/components/workflow/WorkflowEdgeConfigPanel";
import { WorkflowValidationPanel } from "@/components/workflow/WorkflowValidationPanel";
import {
  buildGraphSnapshot,
  GraphSnapshot,
} from "@/components/workflow/utils/graph-reconciliation";
import {
  BuilderEdge,
  BuilderNode,
  generateNodeKey,
  newClientNodeId,
  toFlowGraph,
  applyLayeredLayout,
} from "@/components/workflow/utils/graph-mapper";
import { usePermissions } from "@/lib/hooks/usePermissions";
import {
  WorkflowNodeType,
  WorkflowValidationIssue,
} from "@/types/workflow";
import { ConnectionArmProvider, type ArmedHandle } from "@/components/workflow/utils/connection-arm";
import { getConnectionReason } from "@/components/workflow/utils/connection-validation";

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
  const versionsQuery = useWorkflowVersions(workflowId);
  const validate = useValidateWorkflowVersion(versionId);
  const activate = useActivateWorkflowVersion(workflowId);

  const [nodes, setNodes, onNodesChangeBase] = useNodesState<BuilderNode>([]);
  const [edges, setEdges, onEdgesChangeBase] = useEdgesState<BuilderEdge>([]);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [validationOpen, setValidationOpen] = useState(false);
  const [validationIssues, setValidationIssues] = useState<WorkflowValidationIssue[] | null>(
    null
  );
  const [validatedClean, setValidatedClean] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [inspectorOpen, setInspectorOpen] = useState(false);
  const [leaveConfirmOpen, setLeaveConfirmOpen] = useState(false);
  const [pendingHref, setPendingHref] = useState<string | null>(null);
  const [armedHandle, setArmedHandle] = useState<ArmedHandle>(null);
  const [insertionOpen, setInsertionOpen] = useState(false);
  const [confirmActivateOpen, setConfirmActivateOpen] = useState(false);

  const snapshotRef = useRef<GraphSnapshot>({ nodes: new Map(), edges: new Map() });
  const { plan, isDirty, saving, save, executeSave } = useWorkflowSave(versionId, nodes, edges, snapshotRef);

  const version = graphQuery.data?.version;
  const activeVersion = versionsQuery.data?.data.find((v) => v.status === "ACTIVE");
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

  useEffect(() => {
    setValidatedClean(false);
  }, [isDirty]);

  const handleVersionSwitch = useCallback(
    async (newVersionId: string) => {
      if (newVersionId === versionId) return;
      if (isDirty) {
        const confirmed = window.confirm(
          "You have unsaved changes. Switching versions will discard them. Continue?"
        );
        if (!confirmed) return;
      }
      router.push(`/workflows/${workflowId}/builder?versionId=${newVersionId}`);
    },
    [isDirty, router, versionId, workflowId]
  );

  useEffect(() => {
    if (isDirty) {
      const handler = (event: BeforeUnloadEvent) => {
        event.preventDefault();
        event.returnValue = "";
      };
      window.addEventListener("beforeunload", handler);
      return () => window.removeEventListener("beforeunload", handler);
    }
  }, [isDirty]);

  // Keep inspector sheet in sync with selection on small viewports.
  useEffect(() => {
    if (selectedNodeId || selectedEdgeId) {
      // Do not auto-open on desktop where panel is visible; this only affects mobile drawer UX
      // when user selects via canvas. We set a flag but Sheet open is user-controlled.
    }
  }, [selectedNodeId, selectedEdgeId]);

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

      if (readOnly) return;

      // Persist position changes to node configuration
      setNodes((currentNodes) => {
        let updated = false;
        const newNodes = currentNodes.map((node) => {
          const change = changes.find(
            (c): c is NodeChange<BuilderNode> & { position: { x: number; y: number } } =>
              c.type === "position" && c.id === node.id && "position" in c && c.position !== undefined
          );
          if (change) {
            const newPos = change.position;
            if (node.position.x !== newPos.x || node.position.y !== newPos.y) {
              updated = true;
              return {
                ...node,
                position: newPos,
                data: {
                  ...node.data,
                  configuration: {
                    ...node.data.configuration,
                    position: { x: Math.round(newPos.x), y: Math.round(newPos.y) },
                  },
                },
              };
            }
          }
          return node;
        });
        return updated ? newNodes : currentNodes;
      });
    },
    [onNodesChangeBase, readOnly]
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
          type: "workflow",
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
      setArmedHandle(null);
      setInsertionOpen(false);
    },
    [nodes, readOnly, setEdges]
  );

  const handleReconnect = useCallback(
    (oldEdge: Edge, newConnection: Connection) => {
      if (readOnly) return;
      const newSource = newConnection.source ?? oldEdge.source;
      const newTarget = newConnection.target ?? oldEdge.target;
      const newSourceHandle = newConnection.sourceHandle ?? (oldEdge as unknown as BuilderEdge).sourceHandle ?? null;
      const newTargetHandle = newConnection.targetHandle ?? (oldEdge as unknown as BuilderEdge).targetHandle ?? null;
      const sourceNode = nodes.find((n) => n.id === newSource);
      const isBranch = sourceNode?.data.nodeType === "BRANCH";
      const isCondition = sourceNode?.data.nodeType === "CONDITION";
      setEdges((current) =>
        current.map((edge) => {
          if (edge.id !== oldEdge.id) return edge;
          const prevData = (edge as BuilderEdge).data;
          return {
            ...edge,
            source: newSource,
            target: newTarget,
            sourceHandle: newSourceHandle,
            targetHandle: newTargetHandle,
            data: {
              edgeKey: isBranch ? (newSourceHandle === "false" ? "FALSE" : "TRUE") : prevData.edgeKey,
              configuration: isCondition
                ? { ...prevData.configuration, outcome: newSourceHandle === "false" ? "FALSE" : "TRUE" }
                : prevData.configuration,
            },
          } as BuilderEdge;
        })
      );
      setArmedHandle(null);
      setInsertionOpen(false);
    },
    [nodes, readOnly, setEdges]
  );

  const handleAddNode = useCallback(
    (nodeType: WorkflowNodeType) => {
      if (readOnly) return;
      // Place near viewport center for keyboard/sheet creation.
      let position = { x: 400, y: 160 };
      try {
        // Attempt to use center of viewport if available
        const viewportCenter = screenToFlowPosition({ x: window.innerWidth / 2, y: window.innerHeight / 2 });
        if (viewportCenter && Number.isFinite(viewportCenter.x)) position = viewportCenter;
      } catch {
        // fallback to default
      }
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
          configuration: {
            ...defaults.configuration,
            position: { x: Math.round(position.x), y: Math.round(position.y) },
          },
        },
      };
      setNodes((current) => [...current, newNode]);
      setSelectedNodeId(newNode.id);
      setSelectedEdgeId(null);
      setPaletteOpen(false);
      setInspectorOpen(true);
    },
    [nodes, readOnly, screenToFlowPosition, setNodes]
  );

  const handleArmSource = useCallback(
    (handle: { nodeId: string; handleId: string | null; handleType: "source" | "target" }) => {
      if (readOnly) return;
      if (handle.handleType === "source") {
        if (armedHandle?.nodeId === handle.nodeId && armedHandle?.handleId === handle.handleId) {
          setArmedHandle(null);
          toast.info("Connection cancelled");
        } else {
          setArmedHandle(handle);
          const node = nodes.find((n) => n.id === handle.nodeId);
          const label = node ? `${node.data.name} ${handle.handleId ?? ""}`.trim() : handle.handleId ?? "";
          toast(`Source armed: ${label} — focus a target input and press Enter to connect. Press Escape to cancel.`);
        }
      } else {
        // target activation while armed
        if (!armedHandle) {
          toast.error("Arm a source handle first — focus a source output and press Enter.");
          return;
        }
        const conn: Connection = {
          source: armedHandle.nodeId,
          target: handle.nodeId,
          sourceHandle: armedHandle.handleId,
          targetHandle: handle.handleId,
        };
        const { valid, reason } = getConnectionReason(conn, nodes as unknown as Array<{ id: string; data?: { nodeType?: string } }>, edges as Edge[], readOnly);
        if (!valid) {
          toast.error(reason ?? "Invalid connection.");
          return;
        }
        // reuse edge creation semantics
        const sourceNode = nodes.find((n) => n.id === conn.source);
        const isBranch = sourceNode?.data.nodeType === "BRANCH";
        const isCondition = sourceNode?.data.nodeType === "CONDITION";
        const branchEdgeKey = isBranch ? (conn.sourceHandle === "false" ? "FALSE" : "TRUE") : null;
        const newEdge: BuilderEdge = {
          id: newClientNodeId(),
          type: "workflow",
          source: conn.source,
          target: conn.target,
          sourceHandle: conn.sourceHandle ?? null,
          targetHandle: conn.targetHandle ?? null,
          data: {
            edgeKey: branchEdgeKey,
            configuration: isCondition ? { outcome: conn.sourceHandle === "false" ? "FALSE" : "TRUE" } : {},
          },
        };
        setEdges((cur) => addEdge(newEdge as unknown as Edge, cur) as BuilderEdge[]);
        toast.success("Connected");
        setArmedHandle(null);
        setInsertionOpen(false);
      }
    },
    [armedHandle, nodes, edges, readOnly, setEdges]
  );

  const handleClearArm = useCallback(() => {
    if (armedHandle) {
      setArmedHandle(null);
      toast.info("Connection cancelled");
    }
  }, [armedHandle]);

  const handleActivateTarget = useCallback(
    (nodeId: string, handleId: string | null) => {
      handleArmSource({ nodeId, handleId, handleType: "target" });
    },
    [handleArmSource]
  );

  const handleInsertionSelect = useCallback(
    (nodeType: WorkflowNodeType) => {
      if (!armedHandle || armedHandle.handleType !== "source") {
        toast.error("Arm a source handle first.");
        return;
      }
      // Check if insertion of this nodeType as target is valid
      const fakeTargetId = "insert-check";
      const conn: Connection = {
        source: armedHandle.nodeId,
        target: fakeTargetId,
        sourceHandle: armedHandle.handleId,
        targetHandle: "in",
      };
      // Simulate target node type check via helper: create fake nodes array with target
      const fakeNodes = [...nodes, { id: fakeTargetId, data: { nodeType } } as unknown as (typeof nodes)[number]];
      const { valid, reason } = getConnectionReason(conn, fakeNodes as unknown as Array<{ id: string; data?: { nodeType?: string } }>, edges as Edge[], readOnly);
      if (!valid) {
        toast.error(reason ?? "Cannot create that node here.");
        return;
      }
      const sourceNode = nodes.find((n) => n.id === armedHandle.nodeId);
      let position = { x: 400, y: 160 };
      if (sourceNode) {
        position = { x: sourceNode.position.x + 260, y: sourceNode.position.y + 100 };
      } else {
        try {
          const c = screenToFlowPosition({ x: window.innerWidth / 2, y: window.innerHeight / 2 });
          if (c && Number.isFinite(c.x)) position = c;
        } catch {}
      }
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
          configuration: {
            ...defaults.configuration,
            position: { x: Math.round(position.x), y: Math.round(position.y) },
          },
        },
      };
      const isBranch = sourceNode?.data.nodeType === "BRANCH";
      const isCondition = sourceNode?.data.nodeType === "CONDITION";
      const branchEdgeKey = isBranch ? (armedHandle.handleId === "false" ? "FALSE" : "TRUE") : null;
      const newEdge: BuilderEdge = {
        id: newClientNodeId(),
        type: "workflow",
        source: armedHandle.nodeId,
        target: newNode.id,
        sourceHandle: armedHandle.handleId ?? null,
        targetHandle: "in",
        data: {
          edgeKey: branchEdgeKey,
          configuration: isCondition ? { outcome: armedHandle.handleId === "false" ? "FALSE" : "TRUE" } : {},
        },
      };
      setNodes((cur) => [...cur, newNode]);
      setEdges((cur) => addEdge(newEdge as unknown as Edge, cur) as BuilderEdge[]);
      setSelectedNodeId(newNode.id);
      setSelectedEdgeId(null);
      setInsertionOpen(false);
      setArmedHandle(null);
      setInspectorOpen(true);
      toast.success(`${nodeType} created and connected`);
    },
    [armedHandle, nodes, edges, readOnly, screenToFlowPosition, setNodes, setEdges]
  );

  useEffect(() => {
    if (!armedHandle) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setArmedHandle(null);
        setInsertionOpen(false);
        toast.info("Connection cancelled");
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [armedHandle]);

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
          configuration: {
            ...defaults.configuration,
            position: { x: Math.round(position.x), y: Math.round(position.y) },
          },
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

  const handleSave = async () => {
    if (readOnly) return;
    const success = await save();
    if (!success) {
      toast.error("Failed to save workflow — your changes are preserved.");
      return;
    }
    await queryClient.invalidateQueries({ queryKey: workflowKeys.graph(versionId) });
    await queryClient.refetchQueries({ queryKey: workflowKeys.graph(versionId) });
    setValidatedClean(false);
    toast.success("Workflow saved");
  };

  const handleValidate = async () => {
    if (!versionId) return;
    if (isDirty) {
      toast.error("Save your draft before validating — validation checks the last saved version.");
      setValidationIssues([
        {
          code: "UNSAVED_CHANGES",
          message: "You have unsaved changes. Save your draft before validating to check the current canvas.",
        } as WorkflowValidationIssue,
      ]);
      setValidatedClean(false);
      setValidationOpen(true);
      return;
    }
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

  const handleLayout = useCallback(() => {
    if (readOnly) return;
    // Apply layered layout to all nodes, overwriting any persisted positions
    setNodes((currentNodes) => {
      const newNodes = [...currentNodes];
      applyLayeredLayout(newNodes, edges);
      // Update configuration with new positions
      return newNodes.map((node) => ({
        ...node,
        data: {
          ...node.data,
          configuration: {
            ...node.data.configuration,
            position: { x: Math.round(node.position.x), y: Math.round(node.position.y) },
          },
        },
      }));
    });
    toast.success("Layout applied — save to persist");
  }, [readOnly, edges]);

  const handleSelectValidationIssue = useCallback(
    (issue: WorkflowValidationIssue) => {
      const nodeId = (issue as unknown as { nodeId?: string | null }).nodeId ?? null;
      const nodeKey = (issue as unknown as { nodeKey?: string | null }).nodeKey ?? null;
      const edgeId = (issue as unknown as { edgeId?: string | null }).edgeId ?? null;
      if (nodeId) {
        const node = nodes.find((n) => n.id === nodeId);
        if (node) {
          setSelectedNodeId(node.id);
          setSelectedEdgeId(null);
          setInspectorOpen(true);
          setValidationOpen(false);
          setTimeout(() => setCenter(node.position.x, node.position.y, { zoom: 1.1, duration: 400 }), 80);
          return;
        }
      }
      if (nodeKey) {
        const node = nodes.find((n) => n.data.nodeKey === nodeKey);
        if (node) {
          setSelectedNodeId(node.id);
          setSelectedEdgeId(null);
          setInspectorOpen(true);
          setValidationOpen(false);
          setTimeout(() => setCenter(node.position.x, node.position.y, { zoom: 1.1, duration: 400 }), 80);
          return;
        }
      }
      if (edgeId) {
        const edge = edges.find((e) => e.id === edgeId);
        if (edge) {
          setSelectedEdgeId(edge.id);
          setSelectedNodeId(null);
          setInspectorOpen(true);
          setValidationOpen(false);
          // Center between source and target
          const source = nodes.find((n) => n.id === edge.source);
          const target = nodes.find((n) => n.id === edge.target);
          if (source && target) {
            setCenter((source.position.x + target.position.x) / 2, (source.position.y + target.position.y) / 2, {
              zoom: 1.0,
              duration: 400,
            });
          }
          return;
        }
      }
      // Fallback: try to parse nodeKey from message suffix ": <key>"
      const msg: string = issue.message ?? "";
      const colonIdx = msg.lastIndexOf(": ");
      if (colonIdx !== -1) {
        const candidate = msg.slice(colonIdx + 2).trim();
        const node = nodes.find((n) => n.data.nodeKey === candidate);
        if (node) {
          setSelectedNodeId(node.id);
          setSelectedEdgeId(null);
          setInspectorOpen(true);
          setValidationOpen(false);
          setTimeout(() => setCenter(node.position.x, node.position.y, { zoom: 1.1, duration: 400 }), 80);
          return;
        }
      }
      toast.info(issue.message);
    },
    [nodes, edges, setCenter]
  );

  const handleAttemptLeave = useCallback(
    (href: string, event?: React.MouseEvent) => {
      if (event) event.preventDefault();
      if (!isDirty) {
        router.push(href);
        return;
      }
      setPendingHref(href);
      setLeaveConfirmOpen(true);
    },
    [isDirty, router]
  );

  const handleStay = () => {
    setLeaveConfirmOpen(false);
    setPendingHref(null);
  };

  const handleLeaveWithoutSaving = () => {
    const href = pendingHref;
    setLeaveConfirmOpen(false);
    setPendingHref(null);
    if (href) router.push(href);
  };

  const handleStayAndSave = async () => {
    const success = await executeSave();
    if (success) {
      toast.success("Workflow saved");
      await queryClient.invalidateQueries({ queryKey: workflowKeys.graph(versionId) });
      await queryClient.refetchQueries({ queryKey: workflowKeys.graph(versionId) });
      setValidatedClean(false);
      const href = pendingHref;
      setLeaveConfirmOpen(false);
      setPendingHref(null);
      if (href) router.push(href);
    } else {
      toast.error("Save failed — please try again before leaving");
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
      await queryClient.invalidateQueries({ queryKey: workflowKeys.versions(workflowId) });
      await queryClient.refetchQueries({ queryKey: workflowKeys.versions(workflowId) });
    } catch {}
    setConfirmActivateOpen(true);
  };

  const handleConfirmActivate = async () => {
    try {
      await activate.mutateAsync(versionId);
      toast.success("Workflow activated");
      await queryClient.invalidateQueries({ queryKey: workflowKeys.graph(versionId) });
      await queryClient.invalidateQueries({ queryKey: workflowKeys.versions(workflowId) });
      setConfirmActivateOpen(false);
      router.push(`/workflows/${workflowId}`);
    } catch (error) {
      const msg = error instanceof Error ? error.message : String(error);
      if (msg.includes("WORKFLOW_CONCURRENT_ACTIVATION")) {
        toast.error("Another version was activated. Refresh the page and try again.");
      } else {
        toast.error("Failed to activate workflow version");
      }
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
          <a
            href={`/workflows/${workflowId}`}
            onClick={(e) => handleAttemptLeave(`/workflows/${workflowId}`, e)}
            className="mb-1 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground cursor-pointer"
          >
            <ArrowLeft className="h-4 w-4" /> Back to workflow
          </a>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-xl font-semibold">{workflowName}</h1>
            <span className="text-sm text-muted-foreground">
              Version {version?.versionNumber ?? "…"}
            </span>
            {version && (
              <Badge
                variant={
                  version.status === "ACTIVE"
                    ? "default"
                    : version.status === "DRAFT"
                      ? "outline"
                      : "secondary"
                }
              >
                {version.status === "ACTIVE"
                  ? "● ACTIVE — Live"
                  : version.status === "ARCHIVED"
                    ? "ARCHIVED"
                    : "DRAFT"}
              </Badge>
            )}
            {version && version.status !== "DRAFT" && (
              <span className="text-xs font-medium text-amber-700" role="status">
                {version.status === "ACTIVE" ? "Read-only — create a draft to edit" : "Archived — read-only"}
              </span>
            )}
            {versionsQuery.data && versionsQuery.data.data.length > 1 && !readOnly && (
              <div className="ml-2">
                <Select value={versionId} onValueChange={(v) => handleVersionSwitch(v)}>
                  <SelectTrigger className="w-48">
                    <SelectValue placeholder="Version" />
                  </SelectTrigger>
                  <SelectContent>
                    {versionsQuery.data.data.map((v) => (
                      <SelectItem key={v.id} value={v.id}>
                        v{v.versionNumber} — {v.status}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
            {isDirty ? (
              <Badge variant="destructive" aria-live="polite">Unsaved changes — Save draft before validating</Badge>
            ) : saving ? (
              <Badge variant="secondary" aria-live="polite">Saving…</Badge>
            ) : (
              <Badge variant="secondary" aria-live="polite">Saved</Badge>
            )}
          </div>
        </div>

        {!readOnly && (
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={handleLayout}
              disabled={nodes.length === 0}
              title="Auto-arrange all nodes (save to persist)"
            >
              Layout
            </Button>
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

      {!readOnly && (
        <div className="flex gap-2 lg:hidden">
          <Sheet open={paletteOpen} onOpenChange={setPaletteOpen}>
            <SheetTrigger asChild>
              <Button variant="outline" size="sm" className="flex-1">
                <PanelLeft className="mr-2 h-4 w-4" /> Add Node
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-[85vw] sm:max-w-sm overflow-auto p-4">
              <SheetHeader>
                <SheetTitle>Add node</SheetTitle>
              </SheetHeader>
              <div className="mt-4">
                <WorkflowNodePalette disabled={readOnly} onAdd={handleAddNode} />
              </div>
            </SheetContent>
          </Sheet>
          <Sheet open={inspectorOpen} onOpenChange={setInspectorOpen}>
            <SheetTrigger asChild>
              <Button variant="outline" size="sm" className="flex-1">
                <Settings2 className="mr-2 h-4 w-4" /> Configure
                {selectedNode ? ` · ${selectedNode.data.name}` : selectedEdge ? " · Connection" : ""}
              </Button>
            </SheetTrigger>
            <SheetContent side="right" className="w-[85vw] sm:max-w-sm overflow-auto p-4">
              <SheetHeader>
                <SheetTitle>{selectedNode ? `${selectedNode.data.nodeType} configuration` : selectedEdge ? "Connection" : "Inspector"}</SheetTitle>
              </SheetHeader>
              <div className="mt-4">
                {selectedNode ? (
                  <WorkflowNodeConfigPanel
                    node={selectedNode}
                    readOnly={readOnly}
                    triggerEntityType={triggerEntityType}
                    triggerEventType={triggerEventType}
                    nodeKeys={nodes.map((n) => n.data.nodeKey)}
                    nodes={nodes}
                    edges={edges}
                    onChange={handleNodeConfigurationChange}
                  />
                ) : (
                  <WorkflowEdgeConfigPanel
                    edge={
                      selectedEdge
                        ? {
                            id: selectedEdge.id,
                            source: selectedEdge.source,
                            target: selectedEdge.target,
                            data: {
                              edgeKey: (selectedEdge.data as BuilderEdge["data"]).edgeKey,
                              configuration: (selectedEdge.data as BuilderEdge["data"]).configuration,
                              sourceNodeType: nodes.find((n) => n.id === selectedEdge.source)?.data.nodeType,
                            },
                          }
                        : null
                    }
                    nodes={nodes.map((n) => ({ id: n.id, data: { name: n.data.name, nodeKey: n.data.nodeKey } }))}
                    readOnly={readOnly}
                    onChange={handleEdgeDataChange}
                    onDelete={handleSelectedEdgeDelete}
                  />
                )}
              </div>
            </SheetContent>
          </Sheet>
        </div>
      )}

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 lg:grid-cols-[200px_1fr_280px]">
        <Card className="hidden overflow-auto p-3 lg:block">
          <WorkflowNodePalette disabled={readOnly} onAdd={handleAddNode} />
        </Card>

        <Card className="relative min-h-[420px] overflow-hidden">
          {nodes.length === 0 && !graphQuery.isLoading && (
            <div className="pointer-events-none absolute inset-0 z-10 flex flex-col items-center justify-center text-center text-muted-foreground">
              <p className="font-medium">Start building your workflow</p>
              <p className="text-xs">Drag a Trigger onto the canvas to get started.</p>
            </div>
          )}
          <ConnectionArmProvider value={{ armed: armedHandle, arm: handleArmSource, clear: handleClearArm, activateTarget: handleActivateTarget, readOnly }}>
            <WorkflowCanvas
              nodes={nodes}
              edges={edges}
              readOnly={readOnly}
              onNodesChange={handleNodesChange}
              onEdgesChange={(changes) => onEdgesChangeBase(changes as EdgeChange<BuilderEdge>[])}
              onConnect={handleConnect}
              onReconnect={handleReconnect}
              onDrop={handleDrop}
              onDragOver={(event) => {
                event.preventDefault();
                event.dataTransfer.dropEffect = "move";
              }}
              onSelectionChanged={handleSelectionChanged}
            />
          </ConnectionArmProvider>
          {armedHandle && !readOnly && (
            <div
              className="pointer-events-auto absolute bottom-3 left-1/2 z-20 flex max-w-[90%] -translate-x-1/2 flex-wrap items-center justify-center gap-2 rounded-full border bg-white px-3 py-1.5 text-xs shadow-md"
              role="status"
              aria-live="polite"
            >
              <span className="font-medium">
                Source armed: {nodes.find((n) => n.id === armedHandle.nodeId)?.data.name ?? armedHandle.nodeId.slice(0, 6)}{" "}
                {armedHandle.handleId && armedHandle.handleId !== "out" && armedHandle.handleId !== "in" ? armedHandle.handleId : ""}
              </span>
              <span className="hidden text-muted-foreground sm:inline">— Tab to target, Enter to connect</span>
              <Button variant="outline" size="xs" onClick={() => setInsertionOpen(true)}>
                Insert new node
              </Button>
              <Button variant="ghost" size="xs" onClick={handleClearArm} aria-label="Cancel connection">
                Cancel
              </Button>
            </div>
          )}
        </Card>

        <Card className="hidden overflow-auto p-3 lg:block">
          {selectedNode ? (
            <WorkflowNodeConfigPanel
              node={selectedNode}
              readOnly={readOnly}
              triggerEntityType={triggerEntityType}
              triggerEventType={triggerEventType}
              nodeKeys={nodes.map((n) => n.data.nodeKey)}
              nodes={nodes}
              edges={edges}
              onChange={handleNodeConfigurationChange}
            />
          ) : (
            <WorkflowEdgeConfigPanel
              edge={
                selectedEdge
                  ? {
                      id: selectedEdge.id,
                      source: selectedEdge.source,
                      target: selectedEdge.target,
                      data: {
                        edgeKey: (selectedEdge.data as BuilderEdge["data"]).edgeKey,
                        configuration: (selectedEdge.data as BuilderEdge["data"]).configuration,
                        sourceNodeType: nodes.find((n) => n.id === selectedEdge.source)?.data
                          .nodeType,
                      },
                    }
                  : null
              }
              nodes={nodes.map((n) => ({ id: n.id, data: { name: n.data.name, nodeKey: n.data.nodeKey } }))}
              readOnly={readOnly}
              onChange={handleEdgeDataChange}
              onDelete={handleSelectedEdgeDelete}
            />
          )}
        </Card>
      </div>

      <p className="text-center text-xs text-muted-foreground">
        Node positions are saved with the workflow version. Use <kbd className="px-1.5 py-0.5 text-[10px] bg-muted rounded border">Layout</kbd> to auto-arrange.
      </p>

      <WorkflowValidationPanel
        open={validationOpen}
        onOpenChange={setValidationOpen}
        issues={validationIssues ?? []}
        onSelectIssue={handleSelectValidationIssue}
      />

      <Dialog open={leaveConfirmOpen} onOpenChange={setLeaveConfirmOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Unsaved changes</DialogTitle>
            <DialogDescription>You have unsaved workflow changes.</DialogDescription>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">If you leave now, your changes will be lost.</p>
          <DialogFooter className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variant="outline" size="sm" onClick={handleStay}>
              Cancel
            </Button>
            <Button variant="outline" size="sm" onClick={handleLeaveWithoutSaving}>
              Leave without saving
            </Button>
            <Button size="sm" onClick={handleStayAndSave} disabled={saving}>
              Stay and save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={insertionOpen} onOpenChange={setInsertionOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>Choose node to insert</DialogTitle>
            <DialogDescription>
              {armedHandle
                ? `Creates a new node and connects ${nodes.find((n) => n.id === armedHandle.nodeId)?.data.name ?? ""} ${armedHandle.handleId ?? ""} → new node.`
                : "Choose a node type."}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-2">
            {paletteItems.map((item) => {
              if (!armedHandle) {
                return (
                  <Button
                    key={item.nodeType}
                    variant="outline"
                    size="sm"
                    className="justify-start"
                    onClick={() => handleInsertionSelect(item.nodeType)}
                  >
                    {item.label}
                  </Button>
                );
              }
              const fakeTargetId = "insert-check";
              const conn: Connection = {
                source: armedHandle.nodeId,
                target: fakeTargetId,
                sourceHandle: armedHandle.handleId,
                targetHandle: "in",
              } as Connection;
              const fakeNodes = [
                ...nodes,
                { id: fakeTargetId, data: { nodeType: item.nodeType } } as unknown as (typeof nodes)[number],
              ];
              const { valid, reason } = getConnectionReason(
                conn,
                fakeNodes as unknown as Array<{ id: string; data?: { nodeType?: string } }>,
                edges as unknown as Edge[],
                readOnly
              );
              return (
                <Button
                  key={item.nodeType}
                  variant="outline"
                  size="sm"
                  className="justify-start"
                  disabled={!valid}
                  title={!valid ? reason ?? undefined : undefined}
                  onClick={() => handleInsertionSelect(item.nodeType)}
                >
                  <span className="flex-1 text-left">{item.label}</span>
                  {!valid && <span className="text-xs text-muted-foreground">{reason ?? "Unavailable"}</span>}
                </Button>
              );
            })}
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={confirmActivateOpen} onOpenChange={setConfirmActivateOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Activate workflow version?</DialogTitle>
            <DialogDescription>
              {version ? `You are about to activate version ${version.versionNumber}.` : "You are about to activate this version."}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2 text-sm">
            <p>This version will become the live workflow.</p>
            {activeVersion ? (
              <p className="text-muted-foreground">
                Current live version: v{activeVersion.versionNumber}. Activating v{version?.versionNumber} will archive v{activeVersion.versionNumber}.
              </p>
            ) : (
              <p className="text-muted-foreground">This version will become the live workflow.</p>
            )}
          </div>
          <DialogFooter className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variant="outline" size="sm" onClick={() => setConfirmActivateOpen(false)} disabled={activate.isPending}>
              Cancel
            </Button>
            <Button size="sm" onClick={handleConfirmActivate} disabled={activate.isPending}>
              {activate.isPending ? "Activating..." : "Activate version"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
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
