# SKILL-10: Notification System

## PURPOSE
Defines the notification system for this CRM — in-app, email, and real-time alerts. Every agent working on notifications, alerts, or user communication features MUST follow this design.

---

## 1. NOTIFICATION TYPES

| Type       | Channel          | When Used                                           |
|------------|------------------|-----------------------------------------------------|
| In-App     | WebSocket / SSE  | Real-time alerts inside the CRM UI                 |
| Email      | SES / SendGrid   | Digest, important alerts, workflow-triggered        |
| SMS        | Twilio           | Workflow-triggered only (not system notifications)  |
| WhatsApp   | Twilio           | Workflow-triggered only                             |

---

## 2. DB SCHEMA

```sql
-- Individual notifications
notifications
  id, tenant_id, user_id, type (in_app|email|sms),
  title, body, entity_type, entity_id,
  is_read, read_at, created_at

-- Notification templates
notification_templates
  id, tenant_id, name, channel (email|sms|whatsapp),
  subject (for email), body_template, variables[] (JSONB),
  is_system (system templates cannot be deleted)

-- User notification preferences
user_notification_settings
  user_id, tenant_id,
  channel_email BOOLEAN, channel_in_app BOOLEAN, channel_sms BOOLEAN,
  notify_on_lead_assigned BOOLEAN,
  notify_on_deal_stage_changed BOOLEAN,
  notify_on_task_due BOOLEAN,
  notify_on_mention BOOLEAN,
  quiet_hours_start TIME, quiet_hours_end TIME
```

---

## 3. NOTIFICATION TRIGGERS

System-generated notifications (no workflow config needed):
- Lead assigned to you
- Task/activity due soon (1 hour before, 1 day before)
- @mention in a note or comment
- Deal shared with your team

Workflow-triggered notifications:
- Any custom rule the tenant configures via Workflow Engine

---

## 4. REAL-TIME IN-APP NOTIFICATIONS

Delivery mechanism: **WebSocket** (preferred) or **Server-Sent Events (SSE)**

Flow:
```
Event published → Notification Service creates notification record
  → Pushes via WebSocket to connected user session
  → If user offline → notification sits in DB, shown on next login
```

API for notification center:
```
GET  /api/v1/notifications?page=1&size=20&is_read=false
POST /api/v1/notifications/{id}/read
POST /api/v1/notifications/read-all
GET  /api/v1/notifications/count   → { "unread": 5 }
```

---

## 5. EMAIL NOTIFICATIONS

Uses templates with variable substitution:
```
Template: "Deal assigned to you"
Subject: "New Deal: {{deal_name}} has been assigned to you"
Body: "Hi {{user_name}}, a new deal worth {{deal_value}} from {{account_name}} has been assigned..."
```

Delivery:
- Respect user's `user_notification_settings.channel_email`
- Respect `quiet_hours_start` / `quiet_hours_end`
- Batch low-priority notifications into daily digest

---

## 6. NOTIFICATION FLOW (Event-Driven)

```
CRM action occurs (deal assigned)
  ↓
Event: DEAL_ASSIGNED published to queue
  ↓
Notification Service consumes event
  ↓
Determine recipients (assignee)
  ↓
Check user notification preferences
  ↓
For each active channel:
  - In-app: write to notifications table + push via WebSocket
  - Email: send via SES template
  ↓
Log delivery status
```

---

## 7. TEMPLATE VARIABLE SYSTEM

Templates support dynamic variables resolved from event payload:
```
{{user_name}}       → recipient's name
{{entity_name}}     → deal/lead/contact name
{{entity_url}}      → deep link to the record
{{actor_name}}      → who triggered the action
{{tenant_name}}     → organization name
```

---

## 8. NEVER DO THESE

- ❌ Send notifications synchronously in API thread
- ❌ Ignore user notification preferences
- ❌ No unread count endpoint (confusing UI)
- ❌ Hardcode notification messages (use templates)
- ❌ Send email without checking user's email preference setting
- ❌ No quiet hours support (spammy notifications = user churn)
