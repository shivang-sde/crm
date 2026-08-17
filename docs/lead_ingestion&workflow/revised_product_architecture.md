Revised product architecture

                 INPUT ADAPTERS
────────────────────────────────────────
Webhook
Public API
Form
Polling
Connector
Import

                     │
                     ▼

             INGESTION ENGINE
────────────────────────────────────────
Ingestion Configuration
Raw Event
Field Mapping
Transform
Canonical Normalize
Validate
Deduplicate
Create/Update CRM Entity

                     │
                     ▼

                CRM DOMAIN
────────────────────────────────────────
Lead
Contact
Account
Deal
Task
Call
Meeting
Entitlement
...

                     │
                Domain Events
                     ▼

            AUTOMATION ENGINE
────────────────────────────────────────
Trigger
Condition
Action
Wait
Edges
Versions
Execution
Retries
Audit