# Module: Workflow Engine

| | |
|---|---|
| **Doc ID** | CRM-TD-M10 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\workflow` |
| **Evidence basis** | Repo inspection at commit `0fb9361`; uncommitted files: `WorkflowMessageRecoverer` (+config change), registry validation additions. E2E evidence: `frontend\FE_BE_WF19..21_REPORT.md`, `backend\wf58_*` manual traces. |

## 1. Purpose & scope

Event-driven, tenant-scoped automation: versioned graph definitions (trigger → condition/branch → action nodes → wait → end), executed from canonical CRM events via RabbitMQ; includes node idempotency, leases/claims, retry policy, recovery, and replay. Uses workflow definitions created/edited through a React Flow builder with per-node config UI.

**Not supported / deliberately absent:** HTTP web hooks are generic **outbound per node** only (`HttpApiActionExecutor` + `WorkflowHttpConnectionController`); no inbound workflow webhooks here (records module provides that). No native wait for user input; waits are time-based (`WaitNodeExecutor`).

## 2. API surface (3 controllers)

| Controller | Base | Representative endpoints (complete list in code) |
|---|---|---|
| `WorkflowDefinitionController` | `/api/v1/workflows` | GET metadata, GET `/`, GET `/{workflowId}`, GET `/{workflowId}/versions`, GET `/versions/{versionId}(,/graph)`, POST `/`, POST `/{workflowId}/versions`, POST `/{versionId}/clone`, PUT `/versions/{versionId}`, POST/PUT/DELETE `/{versionId}/nodes…` and `/edges…`, POST `/{versionId}/validate`, POST `/{versionId}/activate`, POST `/{workflowId}/activate|/deactivate`, GET/POST `/executions…` incl. retry + replay (operational) |
| `WorkflowHttpConnectionController` | `/api/v1/workflows` | GET/POST `/http-connections`, GET/PUT/DELETE `/http-connections/{id}`, POST `/{id}/test` |
| `WorkflowCredentialController` | `/api/v1/workflows/http-credentials` | GET `/tenant/status`, PUT `/tenant`, GET/PUT `/user/{userId}/status`/PUT, DELETE `/tenant|/user/{userId}`, GET `/keys` |

## 3. Core classes

- Definition: `WorkflowDefinitionService`, `WorkflowGraphValidationService`, `WorkflowMetadataService`, `WorkflowTriggerService`, entities `Workflow`, `WorkflowVersion`, `WorkflowNode`, `WorkflowEdge`.
- Runtime: `WorkflowGraphRuntimeService` (node stepping), `WorkflowExecutionRuntimeService`, `WorkflowExecutionDispatcher` (@Scheduled 45s), claim + lease services (`WorkflowExecutionClaimService`, `WorkflowExecutionLeaseService`, `WorkflowNodeExecutionClaimService`), `WorkflowNodeIdempotencyService`, `WorkflowNodeRetryDispatcher` (45s) + `WorkflowNodeRetryPolicyService`, `WorkflowExecutionRecoveryScheduler` (60s), `WorkflowExecutionReplayService`, persistence helper `WorkflowNodeExecutionPersistenceService`, query/control services.
- Executors: `WorkflowNodeExecutorRegistry` (Trigger, Condition, Branch, Wait, End node executors) and `WorkflowActionExecutorRegistry` (CreateTask, UpdateEntityField, AssignOwner, CreateClickToCall, HttpApi, SetContextValue, NoOp action executors) with support services (`WorkflowHttpApiService` + response interpreter, `WorkflowConditionEvaluator`, `WorkflowTaskCreationService`, `WorkflowEntityUpdateService`, `WorkflowOwnerAssignmentService`, `WorkflowRelatedRecordResolver`, `WorkflowValueResolver`/`ContextWorkflowValueResolver`, `WorkflowClickToCallService`).
- Event glue: `WorkflowEventConsumer` (`@RabbitListener` `crm.workflow.events`), `WorkflowRabbitConfig`, `WorkflowMessageRecoverer` (DLQ on exhausted retries) [uncommitted], causal lineage via `CausalEventContext`.
- Per-entity registration (context providers **in workflow package**, one per entity: Account, Contact, Lead, Deal, Task, Meeting, Call, Record + `*EntityUpdateAdapter`/`*OwnerAssignmentAdapter` for Account/Contact/Lead/Deal only).
- Trigger source + origin metadata (`LeadCreatedOriginWorkflowTest`; `WorkflowRecordReceivedTriggerAcceptanceTest`).

## 4. Database

- `V32` workflows/workflow_versions/workflow_executions; `V33` nodes/edges; `V34` node executions; `V35` execution identity; `V37` reliability metadata; `V38` node idempotency; `V39` replay provenance; `V41` execution lineage; `V49` version trigger filter.

## 5. Auth, permissions, tenant isolation

- Controller endpoints: `workflow` module read/write RBAC consistent with execution admin (retry/replay) semantics.
- Executions carry tenantId from originating event (TenantResolutionFilter is not part of async path); claim/lease checks scoped per tenant/id (see `WorkflowEventConsumerTest`, `WorkflowRegistryCollisionTest`).

## 6. Tests

`modules\workflow\`: `config\WorkflowRabbitConfigTest`, `service\{HttpApiResponseInterpreterTest, LeadCreatedOriginWorkflowTest, RecordRelatedCrmContextTest, RecordWorkflowEntityContextProviderTest, WorkflowDefinitionEntityTypeValidationTest, WorkflowEventConsumerTest, WorkflowRecordReceivedTriggerAcceptanceTest, WorkflowRegistryCollisionTest, WorkflowTriggerFilterValidationTest}`.
**Gaps:** no integration test for end-to-end event→execution persistence across restart (recovery scheduler *unit-level only*); wait-node/resume path not directly test-verified here.

## 7. Integration surface

- Consumes: every canonical event type (LEAD/ACCOUNT/CONTACT/DEAL/CALL/TASK/MEETING/RECORD.RECEIVED etc.).
- Publishes side-effects through direct service calls into respective modules (UpdateEntityField ↔ `*EntityUpdateAdapter`, CreateTask ↔ TaskService). Click-to-call route documented in telephony doc.
- Runtime debug UI: `(tenant)\workflows\{id}\executions` (+ `[executionId]` inspector), builder `…/builder`.

## 8. Limitations & risks

- Only Account/Contact/Lead/Deals have update & owner-assign adapters; ticket/entitlement/feedback entities can *trigger* but not be *mutated* by workflows — see coverage matrix in async doc.
- Idempotency keys are table-enforced (`WorkflowNodeIdempotency`); no global execution-level lock — concurrent duplicate execution prevented per node only.
- No wait-node resume E2E proof recorded.
