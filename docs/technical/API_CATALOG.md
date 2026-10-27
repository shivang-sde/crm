# API Catalog

| | |
|---|---|
| **Doc ID** | CRM-TD-04 |
| **Version / Status** | 0.2 — DRAFT, UNREVIEWED (corrections applied 2026-10-26 per CRM-TD-10; see docs\technical\CHANGELOG.md) |
| **Prepared** | 2026-10-26 |
| **Evidence basis** | Verified from 65 controllers in the working tree (`backend\src\main\java\com\shivang\crm\modules\*\controller\`; committed baseline `origin/main` has 60). Total HTTP mapping annotations ≈ 419 (annotation grep, 2026-10-26). Per-endpoint detail lives in module docs + code. OpenAPI: `springdoc-openapi-starter-webmvc-ui` 3.0.2 in `backend\pom.xml` (interactive docs when enabled). |

## 1. Conventions

- Base path `/api/v1`; filter chain: `CommercialIntegrationAuthFilter` → `JwtAuthenticationFilter` → `TenantResolutionFilter` → `RbacFilter` (SecurityConfig).
- Auth: Bearer JWT; refresh cookie path `/api/v1/auth`; `@PreAuthorize("@rbac.has(...)"|) ` module:action permissions with DB catalog + access scopes (OWN/TEAM/ALL).
- Tenant scoping via `TenantContext`; soft-delete normalized (`deleted` flag + `...AndDeletedFalse` repos).
- Errors: `shared\exception\GlobalExceptionHandler` JSON contract; 401/403 handlers in SecurityConfig.
- Pagination: `page`/`size` params where applicable; search endpoints use `*Specifications`.

## 2. Endpoint families (grouped by base path & controllers)

| Family | Base path | Controllers | Notes |
|---|---|---|---|
| Auth | `/api/v1/auth` | AuthController | register/login/refresh/logout/change-password/forgot-password/me |
| Tenants (admin) | `/api/v1/tenants` | TenantProvisioningController, TenantController | provision is platform-level |
| Users / roles | `/api/v1/users`, `/api/v1/roles` | UserManagementController, RoleManagementController | role-permission editing incl. scope |
| Accounts | `/api/v1/accounts`, `/api/v1/account-custom-fields` | AccountController (+custom fields) | incl. `/{id}/360` [uncommitted] |
| Contacts | `/api/v1/contacts`, `/api/v1/contact-custom-fields` | ContactController (+custom fields) | |
| Leads | `/api/v1/leads`, `/api/v1/lead-statuses`, `/api/v1/lead-sources`, `/api/v1/lead-custom-fields` | LeadController etc. | assign/convert/status |
| Deals | `/api/v1/deals`, `/api/v1/deal-stages`, `/api/v1/deal-custom-fields`, `/api/v1/deals/{id}/line-items` | DealController, DealStageController, DealLineItemController, DealCustomFieldController | dashboard endpoint |
| Offerings | `/api/v1/offerings` | OfferingController | activate/deactivate |
| Entitlements | `/api/v1/entitlements` | CustomerEntitlementController | + upcoming-renewals |
| Tasks/meetings | `/api/v1/tasks`, `/api/v1/meetings` | TaskController, MeetingController | |
| Calls | `/api/v1/calls`, `/api/v1/calling-providers` | CallController, ClickToCallController, CallOpeningEventController, CallingProviderController | dispositions/linking |
| Tickets | `/api/v1/tickets`, `/api/v1/tickets/{id}/messages` | TicketController, TicketMessageController | assign/status |
| Feedback / health | `/api/v1/feedback`, `/api/v1/customer-health/risk-signals`, `/api/v1/accounts/{id}/health/risk` | FeedbackController, RiskSignalController | analytics endpoint |
| Workflows | `/api/v1/workflows` (definitions, versions, nodes, edges, executions) | WorkflowDefinitionController | + http-connections/credentials controllers |
| Integrations/voice | `/api/v1/integrations/providers`, `/api/v1/integrations/connector-instances`, `/api/v1/call-settings/*`, `/api/v1/settings/connectors`, `/api/v1/voice/connections/generic-sip`, `/api/v1/connector-user-agents`, `/api/v1/my-credentials`, `/api/v1/webhooks/connectors/{tenant}/{provider}/{trigger}` (public) | PlatformProviderController, CallingAdminController, CallingUserSettingsController, ConnectorWebhookController, ConnectorUserAgentController, UserCredentialController | 2 public ingress points |
| Records | `/api/v1/record-types`, `/api/v1/record-types/{id}/fields`, `/…/display-config`, `/api/v1/records`, `/api/v1/record-mapping-profiles`, `/api/v1/record-webhooks`, `/api/v1/records/webhooks/{webhookKey}` (public) | RecordTypeController, RecordFieldController, RecordDisplayConfigController, CrmRecordController, RecordMappingProfileController, RecordWebhookController (+Delivery), RecordWebhookIngressController | |
| Acquisition/forms | `/api/v1/acquisition/**`, `/api/v1/forms`, `/api/v1/public/{acquisition,direct,forms}/{publicKey}` (public) | LeadIngestion*Controllers, FormController | |
| Notifications | `/api/v1/notifications` | NotificationController (reminder module) | unread endpoints |
| Analytics | `/api/v1/analytics/**` | AnalyticsController | scope-aware, CSV exports |
| Operations | `/api/v1/operations/**` | OperatorMessageController | DLQ/outbox recovery |
| Demo data | `/api/v1/demo-data` | DemoDataController | install/reset/status |
| Commercial | `/api/v1/integrations/commercial/**`, `/api/v1/admin/commercial/**`, `/api/v1/commercial-documents` | Commercial*Controllers | API-key parity — see `backend\docs\COMMERCIAL_INTEGRATION_API.md` |

## 3. Public/unauthenticated ingress (complete, verified in SecurityConfig)

1. `/api/v1/auth/{login,refresh,register,forgot-password}`
2. `/api/v1/public/acquisition/**`, `/api/v1/public/direct/**`, `/api/v1/public/forms/**`
3. `/api/v1/webhooks/connectors/**`
4. `/api/v1/records/webhooks/**`
5. Actuator & Swagger (configuration-dependent).

## 4. Request/response contracts

Detailed per module in `docs\technical\modules\MODULE_*.md`; shared patterns: DTO classes under each module's `dto\`, searchable changes via `*Specifications`, Bean Validation on create/update requests. A structured OpenAPI artifact is available at runtime but is **not committed as a generated spec file** — gap for traceability (see Technical Risks: recommend exporting `openapi.json` per release).

## 5. Known inconsistencies

- `POST /api/v1/auth/reset-password` appears in the auth public path set, but the route is commented out in `AuthController` — public allow-list is larger than implemented surface (safe direction, but should be reconciled).
- `DealController` has commented-out `/{id}/won` and `/{id}/lost` routes; won path is handled by stage + atomic creation — external integrators must rely on stage-change semantics.
