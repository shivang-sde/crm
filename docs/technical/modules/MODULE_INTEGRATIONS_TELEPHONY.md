# Module: Integrations, Webhook Ingress, Outbound HTTP, Dialer/Click-to-Call, Voice UI

| | |
|---|---|
| **Doc ID** | CRM-TD-M11 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 · covers `modules\integration`, `modules\dialer` + voice frontend |
| **Evidence basis** | Repo inspection at commit `0fb9361`; voice stack files [uncommitted]. Manual QA evidence: `manual_test_guide.md`, `backend\wf58_*` traces. |

## 1. Purpose & scope

Integration backend: connector/provider registry, per-tenant + per-user connectors, credential storage (AES-256), inbound provider webhooks (HMAC-verified, normalized mapping), generic outbound HTTP connections with security policy, click-to-call orchestration through a provider abstraction, and a browser softphone (SIP over WebRTC via sip.js). **Excluded:** pre-built third-party OAuth/connectors, generic inbound connector webhook tolerance beyond call mapping (records module covers record ingression).

## 2. API surface

| Controller | Base | Endpoints |
|---|---|---|
| `PlatformProviderController` | `/api/v1/platform/providers` | GET, POST, PUT /{id}, PATCH /{id}/status (platform) |
| `CallingAdminController` | `/api/v1` | GET /integrations/providers, GET /integrations/providers/{providerKey}, GET/POST /integrations/connector-instances, PUT /integrations/connector-instances/{id}, PATCH /{id}/status, GET/PUT /{id}/credentials, GET/PUT /{id}/webhook-config, POST /{id}/webhook-config/regenerate-secret, GET/POST /call-settings/connect-triggers, PUT/DELETE /call-settings/connect-triggers/{id}, GET/PUT /call-settings/layout-config, GET/PUT /voice/connections/generic-sip |
| `CallingUserSettingsController` | `/api/v1/settings` | GET /connectors, GET/DELETE /connectors/{connectorId}/credentials, GET /connectors/{id}/credential-status, GET …/sip-connection, GET/PUT …/agent-mapping |
| `ConnectorWebhookController` (PUBLIC) | `/api/v1/webhooks/connectors` | POST `/{tenantId}/{providerKey}/{triggerKey}` |
| `RecordWebhookIngressController` (records module, PUBLIC) | `/api/v1/records/webhooks` | POST `/{webhookKey}` |
| `ConnectorUserAgentController` | `/api/v1/connector-user-agents` | GET, GET /{id}, GET /me, POST, PUT /{id}, PATCH /{id}/status, DELETE /{id} |
| `UserCredentialController` | `/api/v1/my-credentials` | GET /status, POST |

## 3. Classes

- Registry & execution: `ProviderRegistryService(Impl)`, `ConnectorInstanceService`, `ConnectorExecutionService`, `HttpProviderActionExecutor`, `ConnectorUserAgentService`, `TemplateResolver`/`SimpleTemplateResolver`, `WebhookMappingService`/`DefaultWebhookMappingService`, `CallWebhookMappingApplier`, `SellSparkVoiceSeedService`.
- Credentials: `ConnectorCredentialService`, `CredentialEncryptionService` → `DefaultCredentialEncryptionService` (AES-256, 32-byte key from `integration.security.encryption-key`), `ConnectorAuditSanitizer`.
- Webhooks: `ConnectorWebhookService/DefaultConnectorWebhookService`, `WebhookVerificationService` (HMAC/secret regeneration), `NormalizedCallWebhookMapper`, `JsonPathValueExtractor`, `HeaderSanitizer`.
- **Outbound (generic):** `outbound\OutboundHttpService(Impl)` + `OutboundHttpSecurityPolicy` + connection/credential entities; used by workflow HTTP action and acquisition polling transport.
- Dialer: `CallProviderLinkService`, `CallOpeningEventService`, `CallOpeningDecisionService`, `CallEntityResolutionService`, `CallConnectTriggerService`, `CallLayoutConfigService`.
- Frontend voice stack (`frontend\src\lib\voice\`, [uncommitted]): `runtime.ts` orchestrator, `sip\GenericSipAdapter.ts` (sip.js WS SIP), `sip\reconnect-policy.ts`, popup window runtime (host/controller/protocol bridge), `SoftphoneWidget/Panel/AudioDiagnostics` components (`components\voice\`); pages `(home)`-level `softphone`, `softphone-test`.

## 4. Click-to-call → call-record flow (verified call graph)

```
ClickToCallController / WorkflowClickToCall action
  → DefaultClickToCallService → provider execution → CallProviderLink saved (correlationKey = CRM call id)
  → provider places call → provider webhook → POST /api/v1/webhooks/connectors/{tenantId}/{providerKey}/{triggerKey}
  → WebhookVerificationService(HMAC) → NormalizedCallWebhookMapper → CallWebhookMappingApplier
  → CallOpeningDecisionService + CallEntityResolutionService (direction, agent mapping)
  → persists CallOpeningEvent + correlates/updates Call via CallProviderLink
  → softphone polls GET /api/v1/calls/opening-events/pending → acks POST /{eventId}/delivered
```
Manual QA script covering this: `manual_test_guide.md` (SellSpark scenario; PLANNED→HELD transitions).

## 5. Database

- Integration: `provider_definitions`, `provider_action_definitions`, `provider_trigger_definitions`, `connector_instances`, `connector_credentials`, `connector_executions`, `connector_webhook_configs`, `connector_webhook_events`, `connector_webhook_mappings`, `connector_user_agents` (V13/V14/V20/V21); outbound: `outbound_http_connections` (V36), `outbound_http_connection_credentials` (V43, repointed V44, user-credential support V48).
- Dialer: `call_provider_links`, `call_connect_triggers`, `call_layout_configs` (V13), `call_opening_events` (V15/V16).

## 6. Tests

`modules\integration\`: `controller\VoiceGenericSipAdminTest`; `service\{ConnectorAuditSanitizerTest, ConnectorCredentialServiceTest, ConnectorExecutionServiceImplTest, ConnectorWebhookConfigServiceTest, DefaultCredentialEncryptionServiceTest, HttpProviderActionExecutorTest, SimpleTemplateResolverTest}`; `webhook\{JsonPathValueExtractorTest, NormalizedCallWebhookMapperTest, SellSparkMappingTest, WebhookVerificationServiceTest}`; `modules\dialer\service\CallConnectTriggerServiceTest`.
**Gaps:** no automated E2E test connecting webhook ingress to Call/Softphone path (manual guide only); no renewal/rotation of connector secrets beyond regenerate endpoint; user-credential (V48) path test scope limited.

## 7. Security considerations

- Two public webhook ingress controllers: `/api/v1/webhooks/connectors/**`, `/api/v1/records/webhooks/**` — both HMAC/secret verified (`WebhookVerificationService`, record webhook V55 security + rotate-secret endpoint).
- `HeaderSanitizer` strips sensitive headers before audit; `ConnectorAuditSanitizer` scrubs credential/literal token values from execution audit.
- `OutboundHttpSecurityPolicy` restricts schemes/hosts per connection; polling/webhook transports share it when routed via OutboundHttpService.
- AES-256 credential storage; `DefaultCredentialEncryptionServiceTest` verifies the crypto contract; prod start-up validator enforces key length.

## 8. Dependencies

- workflow module (HTTP action, click-to-call action), call module (Call/CallOpeningEvent), acquisition (polling transport), records webhooks inbound.
- Rabbit MQ: connector execution audit only; workflow queue consumers live in `modules\workflow`.

## 9. Limitations & risks

- Voice softphone changes are large but [uncommitted]; SIP config + popup bridge UX on real phones is **unverified in production** — treat as pre-release.
- `fetchAndRemove` destructive mode in `RabbitManagementApiClient` exists but is intentionally uncalled (audit-verified in code comment) — keep unused until redrive UX is settled.
- No rate limiting or replay-window enforcement documented on webhook ingress — see `docs\hardening-fixes.md.txt` and Technical Risks doc.
