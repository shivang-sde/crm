# Integrations & Async Processing

| | |
|---|---|
| **Doc ID** | CRM-TD-07 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 |
| **Evidence basis** | Direct inspection of `shared\event`, `shared\config`, `shared\resilience`, module schedulers, and Rabbit configs; uncommitted additions (`WorkflowMessageRecoverer`, `ReturnedMessageTracker`, `RabbitPublisherReturnConfig`) flagged. |

## 1. Canonical event model

- `CanonicalCrmEvent` — domain-aggregate/type constants (LEAD, ACCOUNT, CONTACT, DEAL, CALL, TASK, MEETING, TICKET, RECORD, OFFERING, ENTITLEMENT, FEEDBACK) × event types (CREATED, UPDATED, OWNER_CHANGED, STATUS_CHANGED, STAGE_TRANSITIONED, CONVERTED, COMPLETED, EXPIRED, RECEIVED…).
- `CanonicalCrmEventPublisher` → `CanonicalCrmEventOutboxService.enqueue` — transactional write to `crm_event_outbox` (V31); statuses incl. terminal DEAD (V65); `CausalEventContext` lineage stops workflow-generated re-publications from recursing.

## 2. Outbox → RabbitMQ pipeline

- `OutboxEventPublisher` `@Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms}")` — prod 5000 ms, dev/default 30 000 ms (code default 5000), batch 20, attempt budget → DEAD.
- Topic exchange `crm.events`; routing key `crm.{aggregateType}.{eventType}` (`CrmEventRabbitConfig`).
- Delivery verification: publisher-confirms + `setMandatory(true)` (`RabbitPublisherReturnConfig`) → unroutable returns hit `ReturnedMessageTracker` → `UnroutableMessageException` → outbox row DEAD (fail-loud, never silently lost).
- Claim discipline via `OutboxEventClaimService` — supersede-safe (DEAD/stale rows cannot be auto-resurrected except through operations requeue).

## 3. Workflow consumption

- Durable queue `crm.workflow.events`, binding `crm.#` → `crm.events`, DLX/DLQ `crm.workflow.events.dlx` (`WorkflowRabbitConfig`, tested by `WorkflowRabbitConfigTest`).
- Listener container `workflowRabbitListenerContainerFactory`: MANUAL ack, prefetch 50, concurrency 1–4, stateless retry interceptor max 3 attempts (500 ms → 10 s backoff) excluding non-retryable types.
- `WorkflowEventConsumer` drives graph runtime; exhausted retries → `WorkflowMessageRecoverer` (metric `crm.rabbitmq.delivery.deadLettered`) → reject (no requeue) → DLQ.
- **Operator recovery** (explicit, never scheduled): `modules\operations` DLQ browse/redrive + outbox dead requeue, each audited in `operator_message_actions`.

## 4. Redis usage & resilience (all in `shared\resilience`/module services)

| Use | Class | Failure behavior |
|---|---|---|
| Permission cache | `PermissionEvaluatorService` + `PermissionCacheInitializer/Evictor` | DB fallback on miss |
| Login throttle | `LoginThrottleService` | fail-open bounded in-process map (10k) |
| Token blacklist | `TokenBlacklistService` | configurable BYPASS/DENY |
| Workflow lease/claim | `WorkflowExecutionLeaseService`, claim services | DB-claim fallback paths |
| Shared guard | `RedisAvailabilityGuard` | single-window self-healing bypass; emits `crm.redis.degraded` via `InfrastructureMetrics` |

## 5. Scheduled jobs (complete list; verified `@Scheduled` classes)

| Job (class) | Trigger | Default interval |
|---|---|---|
| `shared\event\OutboxEventPublisher` | fixedDelay | prod 5s / dev 30s |
| `workflow\service\WorkflowExecutionDispatcher` | fixedDelay | 45s (code default 5s) |
| `workflow\service\WorkflowNodeRetryDispatcher` | fixedDelay | 45s |
| `workflow\service\WorkflowExecutionRecoveryScheduler` | fixedDelay | 60s |
| `acquisition\scheduler\PollingScheduler` | fixedDelay | 300s |
| `entitlement\service\EntitlementLifecycleScheduler.expireDueEntitlements` | fixedDelay | 86 400 000 ms (daily; property-gated) |
| `entitlement\service\EntitlementRenewalTaskScheduler.processDueRenewalFollowUps` | fixedDelay | daily; property-gated |
| `recurrence\service\RecurrenceGenerationScheduler` | fixedDelay | 60s |
| `reminder\service\ReminderProcessingScheduler` | fixedDelay | 30s |
| `ticket\scheduler\TicketSlaBreachScheduler.scanForSlaBreaches` | fixedDelay | 60s; property-gated |

All are claim-based and safe for multi-instance deployment (claims/leases in Postgres/Redis; instance identity from `WorkflowExecutionIdentity`).

## 6. External integrations inventory

| Integration | Direction | Mechanism | Key classes |
|---|---|---|---|
| Lead sources (webhook/polling/CSV) | inbound | public endpoints + `PollingScheduler` + HTTP client | `LeadIngestionIngressService`, `PollingService`, `ImportService` |
| Public forms | inbound | public submit endpoint backed by `FormService` | `LeadIngestionFormPublicController` |
| Workflow HTTP actions | outbound | `HttpApiActionExecutor` + managed tenant/user connections/credentials | `WorkflowHttpApiService`, `OutboundHttpService` |
| Generic outbound connections | outbound | `OutboundHttpService` + `OutboundHttpSecurityPolicy` | outbound entities (V36–V48) |
| Call provider webhooks | inbound | HMAC-verified key-routed ingress | `ConnectorWebhookController`, `WebhookVerificationService`, `NormalizedCallWebhookMapper` |
| Click-to-call | outbound | provider execution; correlation via `CallProviderLink.correlationKey` | `DefaultClickToCallService`, dialer decision services |
| Commercial system | bi-directional | API-key secured REST sync (feature-flagged) | `CommercialSyncService`, `CommercialIntegrationAuthFilter` — full contract `backend\docs\COMMERCIAL_INTEGRATION_API.md` |
| Record sources | inbound | `POST /api/v1/records/webhooks/{webhookKey}` incl. idempotency + delivery log | `RecordWebhookIngestionService` |
| Email/mail + Thymeleaf templates | outbound | **Dependencies present in `backend\pom.xml` only** (`spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`); no runtime mail/Thymeleaf configuration found in the repo | `backend\pom.xml`; password/reset flows in auth module |

## 7. Workflow entity coverage matrix (verified registrations)

| Entity | Context provider | Entity-update adapter | Owner-assignment adapter |
|---|---|---|---|
| Account | ✔ | ✔ | ✔ |
| Contact | ✔ | ✔ | ✔ |
| Lead | ✔ | ✔ | ✔ |
| Deal | ✔ | ✔ | ✔ |
| Task / Meeting / Call | ✔ | ✖ | ✖ |
| Record | ✔ | ✖ | ✖ |
| Ticket / Entitlement / Offering / Feedback | ✖ | ✖ | ✖ |

Implication: workflows can read context for all; can mutate/update-owner for the four core sales entities only. Extending coverage = registering new adapters (see Technical Risks).

## 8. Observability

- Micrometer + Prometheus metrics (pom); centralized signal registry `shared\resilience\InfrastructureMetrics` (outbox/rabbit/redis/resilience/throttle/operator actions); Spring Modulith + actuator + health endpoint used in Docker healthcheck.
- No log aggregation/trace config found in repo (logback defaults) — noted in Technical Risks.
