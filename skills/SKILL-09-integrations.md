# SKILL-09: Integration Framework & Webhooks

## PURPOSE
Defines how this CRM integrates with external systems. Every agent building integrations, webhooks, CPaaS features, or external API sync MUST follow this design.

---

## 1. INTEGRATION TYPES

| Type             | Direction         | Example                                     |
|------------------|-------------------|---------------------------------------------|
| Outgoing Webhook | CRM → External    | Lead created → notify external system       |
| Incoming Webhook | External → CRM    | Website form → create CRM lead              |
| REST API Sync    | Bidirectional     | Sync contacts with email marketing tool     |
| CPaaS (Twilio)   | CRM ↔ Twilio      | Call logging, SMS, WhatsApp                 |
| OAuth Apps       | CRM ↔ Third-party | Google Calendar, Slack, etc.                |

---

## 2. DB SCHEMA

```sql
-- Registered webhooks (outgoing)
webhooks
  id, tenant_id, name, url, secret, events[] (JSONB array of event types),
  is_active, created_by, created_at

-- Incoming webhook tokens
webhook_tokens
  id, tenant_id, name, token (hashed), allowed_actions, created_by

-- Webhook delivery logs
webhook_logs
  id, webhook_id, event_type, payload, response_status, response_body,
  attempt, delivered_at, error_message

-- External API configs
integration_configs
  id, tenant_id, provider (twilio|sendgrid|slack|...), config_json (encrypted), is_active

-- API tokens for external apps to call this CRM
api_tokens
  id, tenant_id, name, token_hash, scopes[], last_used_at, expires_at
```

---

## 3. OUTGOING WEBHOOKS

### Flow
```
CRM event published
  ↓
Integration Service subscribes to event queue
  ↓
Finds active webhooks for this tenant + event type
  ↓
POST to webhook URL with payload + signature
  ↓
Log response in webhook_logs
  ↓
On failure → retry with exponential backoff
```

### Webhook Payload Format
```json
{
  "event": "lead.created",
  "timestamp": "2024-01-15T10:30:00Z",
  "tenantId": "tenant-uuid",
  "data": {
    "id": "lead-uuid",
    "name": "Amit Kumar",
    "email": "amit@abc.com",
    "status": "new"
  }
}
```

### Webhook Signature (Security)
Sign every payload with HMAC-SHA256 using the webhook `secret`:
```
X-CRM-Signature: sha256=<hmac_of_payload>
```
Receiver should verify signature before processing.

---

## 4. INCOMING WEBHOOKS

Used by external systems (website forms, chatbots) to push data into CRM.

```
POST /api/v1/integrations/inbound/{token}
{
  "action": "create_lead",
  "data": {
    "name": "Visitor Name",
    "email": "visitor@example.com",
    "source": "website_form"
  }
}
```

Backend:
1. Validate token against `webhook_tokens`
2. Resolve tenant from token
3. Execute allowed action (e.g., create lead)
4. Return created record ID

---

## 5. CPaaS INTEGRATION (Twilio)

### Call Logging
```
Incoming call → Twilio webhook → CRM records activity
Outgoing call (click-to-call) → CRM triggers Twilio → log call activity
```

### SMS / WhatsApp
- Workflow action triggers SMS/WhatsApp via Twilio API
- Template variables filled from deal/lead data
- Delivery status tracked via Twilio webhooks back to CRM

### Integration Config (stored encrypted)
```json
{
  "account_sid": "AC...",
  "auth_token": "...",
  "phone_number": "+1..."
}
```

---

## 6. EMAIL INTEGRATION

Provider: AWS SES or SendGrid

Used for:
- Workflow-triggered emails (templates)
- Email open/click tracking
- Email logging to contact activity timeline

Bounce/complaint handling: webhook from SES → update contact email_status.

---

## 7. REST API FOR EXTERNAL APPS

External systems can call this CRM's API using API tokens:

```
Authorization: Token <api_token>
GET /api/v1/leads
```

API token scopes:
- `leads:read`, `leads:write`
- `contacts:read`, `contacts:write`
- `deals:read`
- etc.

Token is tenant-scoped. No cross-tenant access possible.

---

## 8. SECURITY RULES

- ✅ Validate webhook URLs (block private IP ranges — SSRF protection)
- ✅ HMAC signature on all outgoing webhooks
- ✅ Verify signature on incoming webhooks
- ✅ Encrypt integration credentials at rest (AES-256)
- ✅ Rate limit webhook delivery attempts
- ✅ Expire API tokens (configurable, default 1 year)
- ❌ Never log decrypted credentials
- ❌ Never expose Twilio auth tokens in API responses

---

## 9. NEVER DO THESE

- ❌ Call external URLs synchronously in API request thread (use async queue)
- ❌ Store credentials in plaintext
- ❌ Allow webhooks to internal/private IP addresses
- ❌ Skip webhook delivery logging (no debugging possible)
- ❌ No retry for failed webhook deliveries
- ❌ Expose raw integration configs to non-admin users
