# Module: Activities & Communication (Tasks, Meetings, Calls, Recurrence, Reminders/Notifications)

| | |
|---|---|
| **Doc ID** | CRM-TD-M09 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\task`, `modules\meeting`, `modules\call`, `modules\recurrence`, `modules\reminder` (notifications live here; `modules\notification` is an empty directory) |
| **Evidence basis** | Repo inspection at commit `0fb9361`. Voice/softphone UI additions [uncommitted] tracked in `MODULE_INTEGRATIONS_TELEPHONY.md`. |

## 1. Purpose & scope

Interaction records across the customer lifecycle: tasks, meetings, calls (with dispositions/recording metadata), automatic task generation from recurrence schedules, reminder planning/delivery, and in-app user notifications. **Excluded:** email/SMS/WhatsApp message sending; calendar two-way sync.

## 2. API surface

| Controller | Base | Endpoints |
|---|---|---|
| `TaskController` | `/api/v1/tasks` | POST; GET, GET /{id}; PUT/DELETE /{id}; PATCH /{id}/complete, /{id}/reopen |
| `MeetingController` | `/api/v1/meetings` | POST; GET, GET /{id}; PUT/DELETE /{id} |
| `CallController` | `/api/v1/calls` | POST; GET, GET /{id}; PUT /{id}; PATCH /{id}/link-entity, /{id}/disposition; DELETE /{id} |
| `CallOpeningEventController` | `/api/v1/calls/opening-events` | GET /pending, POST /{eventId}/delivered |
| `ClickToCallController` | `/api/v1/calls` | POST /click-to-call |
| `CallingProviderController` | `/api/v1/calling-providers` | GET (visible provider options) |
| `NotificationController` (reminder module) | `/api/v1/notifications` | GET, GET /unread-count; PATCH /{id}/read, /read-all |

Recurrence is headless (no controller); schedules created/managed by UI lib logic writing `recurrence_schedules` via service classes.

## 3. Classes

- `TaskService`, `MeetingService`, `CallService` (+ specific `DefaultClickToCallService` used by workflow + frontend), `CallingProviderService`.
- `RecurrenceScheduleService`, `RecurrenceGenerationService`, `RecurrenceCalculator`/`DefaultRecurrenceCalculator`, `RecurrenceGenerationScheduler`.
- `NotificationService`, `ReminderPlanningService`, `ReminderDeliveryService`, `ReminderClaimService`, `ReminderOwnerResolverRegistry` + resolvers (Task/Call/Meeting/MeetingAttendee), `ReminderProcessingScheduler`.

## 4. Database

- `tasks`, `meetings`, `calls` (V11 `task_call_meeting_tables`); `calls` disposition/duration/recording extras (V16, V17, V19); `recurrence_schedules` (V24); `reminders` (V22), `user_notifications` (V23); dialer tables `call_opening_events` (V15+V16 index), `call_provider_links`/`call_connect_triggers`/`call_layout_configs` (V13 shared with integration).

## 5. Events & scheduled jobs

- TASK (CREATED, STATUS_CHANGED, COMPLETED), MEETING (CREATED, STATUS_CHANGED), CALL (CREATED, COMPLETED).
- Schedulers: `RecurrenceGenerationScheduler` (60s default), `ReminderProcessingScheduler` (30s default), plus call-related flows via workflow/click-to-call services (see telephony doc).

## 6. Auth / permissions / tenant isolation

- `task`/`call`/`meeting` module permissions; `call` also involves calling config exclusion prefixes in `RbacFilter` (`CALLING_CONFIG_EXCLUDED_PREFIXES`) — settings endpoints gated by annotations.
- All entities extend `TenantOwnedEntity`; `EntityResolverService` routes link-entity/disposition changes tenant-safely.

## 7. Tests

`modules\call\service\CallServiceTest`, `ClickToCallServiceTest`; `modules\recurrence\calculator\DefaultRecurrenceCalculatorTest`; `modules\reminder\repository\UserNotificationRepositoryTest` and `service\{NotificationServiceTest, ReminderDeliveryAndClaimServiceTest, ReminderPlanningServiceTest}`. Task/meeting/recurrence scheduler idempotency, tasks, only calculator: absent. **Gaps noted.**

## 8. Dependencies

- Call module ↔ dialer + integration module (`CallProviderLink` correlation; softphone polling) — see telephony doc.
- Workflow context providers: `TaskWorkflowEntityContextProvider`, `MeetingWorkflowEntityContextProvider`, `CallWorkflowEntityContextProvider` (no update/owner adapters for these three).
- Reads/writes generic `activities` rows (V7 table).

## 9. Limitations & risks

- No task/meeting service-layer tests committed (only recurrence calculator + call services).
- Reminder delivery failure policy: max attempts + timeout config exists (`app.reminders.*`); dead-letter path of failed reminder deliveries not surfaced via operations module (DLQ is workflow-queue-specific only) — operational gap.
