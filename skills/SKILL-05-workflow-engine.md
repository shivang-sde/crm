# SKILL-05: Workflow Engine (Automation)

## PURPOSE
Defines the event-driven workflow automation engine. Every agent working on workflows, automation, triggers, or event processing MUST follow this architecture. This is the "game changer" feature of the CRM.

---

## 1. CORE CONCEPT

```
CRM Action → Event Published → Trigger Matched → Conditions Evaluated → Actions Executed (async)
```

Example:
```
Deal stage changed to "Qualified"
  → Publish DEAL_UPDATED event
  → Match workflow: "When deal reaches Qualified"
  → Condition: stage == "Qualified"
  → Actions: Assign to senior sales manager + Send WhatsApp + Create follow-up task
```

---

## 2. ARCHITECTURE COMPONENTS

### Event Producer (CRM Modules)
CRM service publishes events after every write operation:
```
LEAD_CREATED, LEAD_UPDATED, LEAD_DELETED
DEAL_CREATED, DEAL_UPDATED, STAGE_CHANGED
CONTACT_CREATED, CONTACT_UPDATED
ACTIVITY_COMPLETED
```

### Event Queue
- **Phase 1**: RabbitMQ (simpler, good to start)
- **Phase 2**: Apache Kafka (when volume requires scale)

### Workflow Processor (Consumer)
- Reads events from queue
- Matches active workflows for that tenant + entity + event type
- Evaluates conditions
- Dispatches actions

### Action Executors (one per type)
| Action Type | Description                          |
|-------------|--------------------------------------|
| EMAIL       | Send templated email                 |
| SMS         | Send via Twilio                      |
| WHATSAPP    | Send via Twilio WhatsApp             |
| ASSIGN      | Update owner_id on record            |
| WEBHOOK     | POST to external URL                 |
| CREATE_TASK | Create Activity record               |
| NOTIFY      | In-app notification                  |

### Execution Store
Tracks every workflow run (status, retries, errors).

---

## 3. DB SCHEMA

```sql
-- Workflow definition
workflows: id, tenant_id, name, module, is_active, created_by

-- What triggers this workflow
workflow_triggers: id, workflow_id, event_type (CREATE|UPDATE|DELETE), entity (lead|deal|contact)

-- Conditions (with AND/OR grouping)
workflow_conditions: id, workflow_id, field, operator (=|!=|>|<|IN|CONTAINS), value, logical_group

-- Actions to execute (ordered)
workflow_actions: id, workflow_id, action_type, execution_order, config_json

-- Execution tracking
workflow_executions: id, workflow_id, entity_id, event_id, status (SUCCESS|FAILED|RETRY),
                     attempts, max_attempts, next_retry_at, error_message, executed_at

-- Scheduled/delayed actions
scheduled_actions: id, workflow_id, action_id, entity_id, execute_at, action_config, status
```

---

## 4. STANDARD EVENT STRUCTURE

All events published to the queue MUST use this format:
```json
{
  "eventType": "DEAL_UPDATED",
  "tenantId": "tenant-uuid",
  "entity": "deal",
  "entityId": "deal-uuid",
  "triggeredBy": "user-uuid",
  "payload": {
    "old": { "stage": "Prospecting", "value": 50000 },
    "new": { "stage": "Qualified", "value": 50000 }
  },
  "timestamp": "2024-01-15T10:30:00Z",
  "eventId": "unique-event-uuid"
}
```

`eventId` is required for **idempotency** (prevent duplicate execution).

---

## 5. CONDITION ENGINE

### Supported Operators
`=`, `!=`, `>`, `<`, `>=`, `<=`, `IN`, `NOT_IN`, `CONTAINS`, `IS_NULL`, `IS_NOT_NULL`

### AND / OR Grouping
```
(stage = "Qualified" AND value > 50000)
OR (source = "Website")
```
`logical_group` field enables this grouping in the DB.

### Evaluation
Conditions evaluate against `event.payload.new` for UPDATE events.

---

## 6. EXECUTION FLOW (Step by Step)

```
1. Event arrives in queue
2. Workflow Processor fetches matching workflows:
   WHERE tenant_id = event.tenantId
   AND entity = event.entity
   AND event_type = event.eventType
   AND is_active = true

3. Check idempotency:
   IF workflow_executions has (event_id, workflow_id) → SKIP (already processed)

4. Evaluate conditions against event.payload

5. If conditions pass → execute actions in order (execution_order)

6. Log result in workflow_executions (SUCCESS / FAILED)

7. On failure → schedule retry with exponential backoff
```

---

## 7. RETRY & FAILURE HANDLING

**Exponential Backoff:**
```
Attempt 1: retry after 1 minute
Attempt 2: retry after 5 minutes
Attempt 3: retry after 15 minutes
After max_attempts: move to Dead Letter Queue (DLQ)
```

**Failure scenarios to handle:**
- Webhook URL unreachable
- Email service timeout
- SMS delivery failure
- External API rate limit

---

## 8. IDEMPOTENCY (PREVENT DUPLICATE RUNS)

```sql
-- Unique constraint prevents duplicate processing
UNIQUE(event_id, workflow_id) in workflow_executions
```

If Kafka/RabbitMQ delivers the same event twice, the second run is a no-op.

---

## 9. SCHEDULED / DELAYED WORKFLOWS

Example: "Send follow-up email 2 days after deal created"

Flow:
```
Workflow action: EMAIL with delay = 2 days
  ↓
Workflow processor inserts into scheduled_actions:
  execute_at = now() + 2 days
  ↓
Scheduler job runs every minute, picks up due actions
  ↓
Executes action at the right time
```

---

## 10. SECURITY RULES FOR WORKFLOWS

- Validate webhook URLs (no internal/private IP addresses — SSRF protection)
- Rate limit action executions per tenant per hour
- Prevent infinite loops: add `execution_depth_limit` (max 5 recursive triggers)
- Workflow can only access data within its own tenant

---

## 11. OBSERVABILITY

Track and expose:
- Workflow execution success rate
- Top failing workflows (with error reasons)
- Average execution time
- Queue depth / lag

Dashboard: "Workflow Health" panel showing success/failure rates.

---

## 12. ASYNC IS MANDATORY

```
API → Save record → Publish event → Return 200 immediately
                         ↓
               (async) Workflow processor handles the rest
```

**NEVER execute workflows synchronously in the API request thread.**

---

## 13. NEVER DO THESE

- ❌ Running workflows synchronously (blocks API, timeouts)
- ❌ Hardcoding workflow logic in code
- ❌ No retry mechanism (silent failures)
- ❌ No idempotency (duplicate emails, SMS sent multiple times)
- ❌ No condition grouping (AND/OR)
- ❌ Skipping execution logging (no debugging capability)
- ❌ Allowing workflows to trigger themselves recursively without depth limit
