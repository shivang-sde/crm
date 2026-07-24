# Priority 0 — Verify before further architecture work

Before changing architecture, manually prove these five scenarios:

1. Lead outbound → connect → active UI → CDR
2. Contact outbound → connect → active UI → CDR
3. Account outbound → connect → active UI → CDR
4. Inbound known caller
5. Inbound unknown caller

Also test:

Admin credential
Agent A credential
Agent B credential
Agent without credential

Do not begin another refactor until these tests reveal the actual runtime gaps.

## Priority 1 — Fix likely testing blockers

Correct the manual guide’s way of finding the Call ID
The guide says to copy the Call ID from:
/calls/active/{callId}
But the active page only opens after the connect webhook. Before simulating the webhook, you may not be on that route.
After clicking Call, get the CRM Call ID from one of these:

Browser response

Inspect: POST /api/v1/calls/click-to-call

The response should contain:
{
  "data": {
    "callId": "..."
  }
}


Database
SELECT
    id,
    entity_type,
    entity_id,
    phone_number,
    status,
    created_by,
    created_at
FROM calls
WHERE tenant_id = '<TENANT_UUID>'
ORDER BY created_at DESC
LIMIT 5;
Provider link
SELECT
    call_id,
    correlation_key,
    external_call_id,
    created_at
FROM call_provider_links
WHERE tenant_id = '<TENANT_UUID>'
ORDER BY created_at DESC
LIMIT 5;

Expected before connect:

correlation_key = CRM Call UUID
external_call_id = null


2. Confirm saved database mappings were updated

Your DefaultWebhookMappingService seeds defaults only when no mappings already exist.

Therefore, if old incorrect rows already exist in connector_webhook_mappings, changing Java defaults will not replace them.
SELECT
    trigger_key,
    source_path,
    target_scope,
    target_path,
    is_required,
    is_active
FROM connector_webhook_mappings
WHERE tenant_id = '<TENANT_UUID>'
  AND connector_instance_id = '<CONNECTOR_UUID>'
ORDER BY trigger_key, source_path;

For testing, ensure the stored rows use:
call-connect:
$.call_uniqueid
$.lead_id
$.agent
$.agent_number
$.call_with
$.call_type
$.start_time

cdr:
$.call_id
$.uniqueid
$.lead_id
$.agent
$.agent_no
$.applicant_no
$.start_time
$.end_time
$.call_duration
$.rec_path
$.status

3. Confirm webhook verification mode

Your PowerShell guide sends no HMAC signature.

Therefore the webhook configuration must actually allow:
verificationMode = NONE
for local simulation.
SELECT
    connector_instance_id,
    is_active,
    verification_mode,
    signature_header_name
FROM connector_webhook_configs
WHERE tenant_id = '<TENANT_UUID>';

For local testing: verification_mode = NONE
For production, do not leave NONE without an alternative such as:

SellSpark-supported signature;
source IP allowlisting;
reverse-proxy authentication;
provider-specific shared token.

4. Verify the opening event targets the logged-in user

For outbound calls, the event should target:
Call.createdBy
Check after posting connect:
SELECT
    id,
    tenant_id,
    user_id,
    agent_id,
    call_id,
    external_call_id,
    trigger_key,
    delivery_status,
    created_at
FROM call_opening_events
ORDER BY created_at DESC
LIMIT 10;

Expected:
user_id = CRM user who clicked Call
call_id = original CRM Call ID
delivery_status = PENDING initially

If user_id is null, the logged-in user’s polling may not receive it.

5. Confirm opening-rule direction values

Your provider payload sends:
"call_type": "outbound"
The normalized mapper and decision engine must convert this to the exact enum used by the trigger rule, such as: OUTBOUND

Do not compare raw lowercase "outbound" against an enum value like "OUTGOING" without normalization.
Test both: outbound, inbound

Priority 2 — Improve provider abstraction

This is where your provider abstraction score can move from approximately 8/10 to 9.5/10.

1. Move SellSpark mapping defaults out of the generic mapping service

Currently, SellSpark-specific paths appear to be seeded from:

DefaultWebhookMappingService

That service should eventually be provider-neutral.

Preferred structure:

ProviderDefinition
  ├── ProviderActionDefinition
  ├── ProviderTriggerDefinition
  └── Default webhook mappings

Possible implementation:

public interface ProviderDefinitionSeeder {
    String providerKey();
    void seedProvider();
    void seedActions();
    void seedTriggers();
    void seedWebhookMappings();
}

SellSpark implementation:

@Component
public class SellSparkVoiceProviderSeeder
        implements ProviderDefinitionSeeder {
}

Then the generic webhook engine only loads mappings from the database. It must not know field names such as:

call_uniqueid
lead_id
rec_path

This is the main adjustment for future Twilio, Exotel, Knowlarity, Asterisk, or custom providers.

2. Make response success mapping configurable

Your click-to-call service currently understands SellSpark:

response.status == success
response.response = message

Eventually move this into the provider action definition:

{
  "successPath": "$.status",
  "successValues": ["success"],
  "messagePath": "$.response",
  "externalCallIdPath": null
}

Then DefaultClickToCallService receives a normalized execution result:

result.isBusinessSuccess()
result.getProviderMessage()
result.getExternalReferenceId()

It should not parse SellSpark response fields itself.

3. Avoid hardcoded provider keys in business services

Current service likely still uses:

execRequest.setProviderKey("sellspark_voice");

Later, resolve the calling connector through tenant configuration:

tenant default calling connector
or user-selected connector
or workflow-selected connector

The Call service should request:

CLICK_TO_CALL capability

rather than explicitly asking for SellSpark.

For the current MVP, keep the hardcoded key until tests pass.

4. Introduce provider capability metadata

Add capabilities such as:

CLICK_TO_CALL
CONNECT_WEBHOOK
CDR_WEBHOOK
INBOUND_CALL
OUTBOUND_CALL
RECORDING
PER_AGENT_CREDENTIALS
HMAC_WEBHOOK

This lets the UI and backend know which configuration sections to show without provider-name checks.

Priority 3 — Improve lifecycle and scalability
1. Add provider call state to CallProviderLink

Do not replace your CRM CallStatus. Add a separate provider lifecycle:

SCHEDULED
DIALING
RINGING
CONNECTED
COMPLETED
FAILED
BUSY
NO_ANSWER
REJECTED

Useful fields:

private String providerState;
private Instant connectedAt;
private Instant completedAt;
private Instant lastWebhookAt;
private Boolean connectReceived;
private Boolean cdrReceived;

This avoids overloading:

PLANNED
HELD
NOT_HELD
CANCELLED

which are CRM activity statuses, not a complete telephony lifecycle.

This can be a later migration after manual testing.

2. Replace activity-based idempotency checks

The plan mentions checking whether a CALL_CONNECTED activity already exists.

That is weaker than database-enforced idempotency.

Prefer:

connector_webhook_events unique idempotency key
+
call_provider_links state transition checks
+
unique opening event key

For example, add a unique partial index conceptually on:

tenant_id + call_id + trigger_key + delivery lifecycle

or persist an event identity on call_opening_events.

Avoid using Activity as the authoritative processing ledger.

3. Use state transitions atomically

Connect processing should update the provider link conditionally:

SCHEDULED → CONNECTED

CDR processing:

CONNECTED/SCHEDULED → COMPLETED

Duplicate CDR:

COMPLETED → no-op

Use transactional locking or an optimistic version field if duplicate callbacks can arrive concurrently.

4. Add retryable webhook processing

The raw event is already persisted. Later split webhook receipt from webhook processing:

HTTP callback
→ verify and persist event quickly
→ return 200
→ process asynchronously
→ retry transient failures
→ dead-letter permanent failures

You already have RabbitMQ, so this fits your stack.

States can be:

RECEIVED
PROCESSING
PROCESSED
RETRY_PENDING
FAILED
DEAD_LETTERED

Do this after synchronous processing is manually proven.

5. Improve active-call delivery transport later

Four-second polling is acceptable for the MVP.

After validation, use:

SSE

before WebSocket unless you need bidirectional browser communication.

Call-opening events are server-to-client notifications, so SSE is simpler:

backend event stream
→ logged-in user
→ immediate opening instruction

Retain polling as fallback.

Per-agent credential hardening

The implementation reportedly prefers agent credentials and falls back to tenant credentials.

Verify the data model does not infer ownership only through a generic createdBy field.

Preferred explicit fields:

CredentialScope scope; // TENANT or USER
UUID userId;           // required for USER scope
String credentialName; // primary

Add a database uniqueness constraint such as:

tenant + connector instance + scope + user + credential name

This is clearer and safer than:

createdBy == current user

because createdBy describes audit ownership, not necessarily credential applicability.

Also verify:

Agent A cannot update Agent B credentials.
Admin cannot retrieve plaintext agent credentials.
Tenant fallback is an explicit setting, not accidental behavior.
Agent missing credentials receives a clear error.
Provider agent identifiers can map back to CRM users for inbound calls.
Manual guide corrections
Inbound opening cannot simply target any logged-in tenant user

The guide says:

“frontend should pick it up if you're logged into the tenant.”

That is too broad.

An inbound call should target:

CRM user mapped to provider agent

or a configured group/queue.

You need one of:

provider agent ID → CRM user mapping
provider credential owner → CRM user
DID/campaign → CRM team
tenant-wide fallback for testing only

Without agent routing, the backend may create the inbound Call but no correct user will receive the opening event.

CDR status should not always become HELD

The simulated CDR uses:

"status": "answered"

Map provider statuses explicitly:

answered/success/completed → HELD
no_answer/busy/failed      → NOT_HELD
cancelled                  → CANCELLED

Do not mark every CDR as HELD.

Practical scoring after these adjustments

After successful manual testing and Priority 1 fixes:

Architecture:                 9.5/10
Scalability:                  9.0/10
Provider abstraction:        8.5/10
Future multi-provider:       9.0/10
Production readiness:        8.5/10

After provider-neutral seeding, configurable response normalization, explicit credential scopes, atomic state transitions, asynchronous webhook retries, and SSE:

Architecture:                 9.7/10
Scalability:                  9.5/10
Provider abstraction:        9.6/10
Future multi-provider:       9.6/10
Production readiness:        9.5/10

A real 9.8 production-readiness rating additionally requires:

load testing
observability and alerts
rate limits
webhook replay UI
dead-letter processing
secret rotation
database backup/restore testing
deployment rollback testing
security testing
real SellSpark callback verification


**PROMPT**

Perform a post-E2E architectural hardening pass on the working calling integration.

Do not modify the working SellSpark click-to-call/connect/CDR flow until its existing tests and manual golden-path scenarios are recorded.

## Goal

Improve provider abstraction, multi-agent safety, idempotency, lifecycle tracking, and production readiness without breaking SellSpark.

## 1. Preserve current correlation

Keep:

```text
CRM Call UUID
→ provider leadId
→ CallProviderLink.correlationKey
```

Abstract the provider request-field name through the action template. Do not assume every provider calls it `leadId`.

## 2. Provider-neutral webhook mapping seeds

Move SellSpark-specific field paths out of `DefaultWebhookMappingService`.

Create a provider-seeding contract that owns:

```text
provider definition
actions
triggers
default request templates
default response mappings
default webhook mappings
capabilities
```

Create a SellSpark implementation.

The generic mapping service must only load and apply database mappings.

## 3. Configurable provider response normalization

Move SellSpark response knowledge out of `DefaultClickToCallService`.

Support action configuration for:

```text
successPath
successValues
messagePath
externalCallIdPath
```

Return a normalized execution outcome to the Call service.

## 4. Provider capability model

Add provider/action capabilities:

```text
CLICK_TO_CALL
CONNECT_WEBHOOK
CDR_WEBHOOK
INBOUND_CALL
OUTBOUND_CALL
RECORDING
PER_AGENT_CREDENTIALS
HMAC_WEBHOOK
```

Use capabilities in admin UI and execution validation.

Avoid frontend/backend checks based on `providerKey == sellspark_voice`.

## 5. Provider call lifecycle

Extend `CallProviderLink` with a provider-specific lifecycle:

```text
SCHEDULED
DIALING
RINGING
CONNECTED
COMPLETED
FAILED
BUSY
NO_ANSWER
REJECTED
```

Also consider:

```text
connectedAt
completedAt
lastWebhookAt
connectReceived
cdrReceived
```

Keep CRM `CallStatus` separate.

Add a new Flyway migration rather than modifying an applied migration.

## 6. Explicit credential scope

Do not use `createdBy` as the authoritative credential owner.

Add:

```text
scope = TENANT | USER
userId nullable
credentialName
```

Add unique database constraints per connector/scope/user/name.

Resolution order:

```text
user credential
→ explicit tenant fallback when enabled
→ clear error
```

Verify users can manage only their own credentials.

## 7. Agent routing

Add an explicit provider-agent-to-CRM-user resolution strategy.

Support at least:

```text
credential owner mapping
provider agent ID mapping
tenant fallback for local testing
```

Opening events must target the intended CRM user, not every user in the tenant.

## 8. Idempotent state transitions

Do not use Activity existence as the authoritative duplicate check.

Use:

```text
webhook event idempotency key
provider link lifecycle/state
opening event uniqueness
database constraints
```

Implement atomic state transitions:

```text
SCHEDULED → CONNECTED
CONNECTED/SCHEDULED → COMPLETED
COMPLETED → duplicate no-op
```

Add optimistic locking or transactional locking where required.

## 9. Async webhook processing

After verification, persist the webhook quickly and process it asynchronously.

Use:

```text
RECEIVED
PROCESSING
PROCESSED
RETRY_PENDING
FAILED
DEAD_LETTERED
```

Add retry policies for transient failures and dead-letter handling for permanent failures.

Do not retry invalid signatures.

## 10. Verification strategy

Model webhook verification as provider capability/configuration:

```text
HMAC_SHA256
SHARED_TOKEN
IP_ALLOWLIST
NONE
```

`NONE` must not be the production default.

Document exactly what SellSpark supports.

## 11. Status mapping

Make provider status-to-CRM status mapping configurable.

Example:

```text
answered/success/completed → HELD
no_answer/busy/failed      → NOT_HELD
cancelled                  → CANCELLED
```

Do not hardcode every CDR as HELD.

## 12. Realtime delivery

Keep 4-second polling as fallback.

Add SSE for user-targeted call-opening events after the webhook flow is stable.

Do not remove polling until fallback behavior is tested.

## 13. Tests

Add tests for:

* provider-neutral mapping seeder
* configurable response normalization
* status mappings
* credential scope and user isolation
* agent-to-user routing
* concurrent duplicate connect callbacks
* concurrent duplicate CDR callbacks
* atomic state transitions
* retry and dead-letter behavior
* tenant isolation
* SSE delivery and polling fallback

## Final report

Report:

1. Current working flow preserved
2. Files changed
3. Migrations added
4. Provider-specific logic removed from generic services
5. Capability model
6. Credential scope model
7. Agent routing strategy
8. Lifecycle model
9. Idempotency guarantees
10. Retry behavior
11. Verification modes
12. Status mapping behavior
13. Test results
14. Remaining production gaps

Do not claim production readiness solely from compilation or unit tests.
The right sequence now is:
manual E2E testing
→ repair only actual blockers
→ commit the working milestone
→ apply the architectural hardening prompt in separate changes

That keeps your working SellSpark implementation stable while steadily improving multi-provider quality.