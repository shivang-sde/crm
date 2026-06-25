# Sprint 5 - Accounts & Contacts Module Architecture and Implementation Guide

## Project Context

Current CRM Status:

### Completed Modules

* Authentication
* Multi-Tenant Architecture
* RBAC System
* Tenant Management
* User Management
* Invitations
* Lead Management
* Lead Statuses
* Lead Sources
* Dynamic Lead Fields
* Lead Activities
* Entity Notes
* Lead Kanban
* Lead Assignment
* Entity History

### Tech Stack

Backend

* Java 21
* Spring Boot
* PostgreSQL
* JWT Authentication
* MapStruct
* Spring Data JPA

Frontend

* Next.js
* TypeScript
* TanStack Query
* React Hook Form
* Zod
* Shadcn UI

---

# Core CRM Business Model

CRM follows:

Lead
↓ Convert
Account (Company)
↓
Contact (Person)
↓
Deal (Revenue Opportunity)

Never skip Account.

Never merge Contact and Account.

Never treat Lead as Contact.

---

# Business Definitions

## Account

Represents a company or organization.

Examples:

* Google
* Infosys
* TCS
* Local Business

Account stores company-level information.

One Account can have:

* Many Contacts
* Many Deals
* Many Activities

---

## Contact

Represents a person working for an Account.

Examples:

* John Smith
* Amit Sharma
* Priya Singh

Each Contact belongs to one Account.

Many Contacts can belong to the same Account.

---

## Owner Concept

Every Account and Contact must have:

```text
owner_user_id
```

Owner means:

"The CRM user responsible for managing that record."

Used for:

* Accountability
* Reporting
* Assignment
* RBAC OWN scope

Examples:

Lead Owner

Rahul

↓

Account Owner

Rahul

↓

Contact Owner

Rahul

---

# Database Design

## Accounts Table

```sql
accounts
```

Fields:

```sql
id UUID PK

tenant_id UUID

name VARCHAR(255) NOT NULL

website VARCHAR(255)

industry VARCHAR(100)

phone VARCHAR(50)

email VARCHAR(255)

annual_revenue NUMERIC(18,2)

employee_count INTEGER

description TEXT

country VARCHAR(100)

state VARCHAR(100)

city VARCHAR(100)

address_line1 VARCHAR(255)

postal_code VARCHAR(20)

owner_user_id UUID

lead_id UUID NULL

custom_data JSONB DEFAULT '{}'

is_active BOOLEAN DEFAULT TRUE

created_by UUID
updated_by UUID

created_at
updated_at
```

Indexes:

```sql
tenant_id
owner_user_id
name
industry
created_at
```

---

# Account Custom Fields

Use same strategy as Leads.

Table:

```sql
account_custom_fields
```

Fields:

```sql
id
tenant_id

field_key
field_label

field_type

is_required
is_active

display_order

options_json

created_at
updated_at
```

Account values stored in:

```sql
accounts.custom_data JSONB
```

Do NOT create EAV tables.

---

# Contacts Table

```sql
contacts
```

Fields:

```sql
id UUID PK

tenant_id UUID

account_id UUID NOT NULL

first_name VARCHAR(100)
last_name VARCHAR(100)

email VARCHAR(255)

phone VARCHAR(50)

mobile VARCHAR(50)

job_title VARCHAR(150)

department VARCHAR(100)

owner_user_id UUID

lead_id UUID NULL

custom_data JSONB DEFAULT '{}'

is_primary BOOLEAN DEFAULT FALSE

is_active BOOLEAN DEFAULT TRUE

created_by UUID
updated_by UUID

created_at
updated_at
```

Indexes:

```sql
tenant_id
account_id
owner_user_id
email
phone
```

Relationship:

```text
Account
   ↓
Many Contacts
```

---

# Contact Custom Fields

Table:

```sql
contact_custom_fields
```

Values stored in:

```sql
contacts.custom_data JSONB
```

Same renderer used for Lead dynamic fields.

Build reusable architecture.

---

# Activities Module

Do NOT make activities lead-specific anymore.
Current lead activities should evolve into:

```sql
activities
```

Fields:

```sql
id

tenant_id

entity_type
entity_id

activity_type

subject

description

performed_by

metadata JSONB

created_at
```

entity_type:

```text
LEAD
ACCOUNT
CONTACT
DEAL
```

Examples:

Call Logged

Meeting Scheduled

Email Sent

Note Added

Status Changed

Owner Changed

Converted

This design becomes reusable across CRM.

---

# Notes Module

Same approach.

```sql
notes
```

Fields:

```sql
id

tenant_id

entity_type
entity_id

note

created_by

created_at
updated_at
```

Entity Types:

```text
LEAD
ACCOUNT
CONTACT
DEAL
```

Avoid separate notes table per module.

---

# Lead Conversion Enhancement

Current lead conversion must be extended.

API:

```http
POST /api/v1/leads/{id}/convert
```

Conversion Process:

1. Create Account
2. Create Primary Contact
3. Mark Lead Converted
4. Create Activity
5. Store Relationships

Lead:

```text
ABC Pvt Ltd
Amit Sharma
```

Creates:

Account

```text
ABC Pvt Ltd
```

Contact

```text
Amit Sharma
```

Owner should be inherited from Lead.

---

# Backend APIs

## Accounts

Create

```http
POST /api/v1/accounts
```

List

```http
GET /api/v1/accounts
```

Detail

```http
GET /api/v1/accounts/{id}
```

Update

```http
PUT /api/v1/accounts/{id}
```

Delete

```http
DELETE /api/v1/accounts/{id}
```

Contacts Under Account

```http
GET /api/v1/accounts/{id}/contacts
```

Activities

```http
GET /api/v1/accounts/{id}/activities
```

Notes

```http
GET /api/v1/accounts/{id}/notes
```

---

## Contacts

Create

```http
POST /api/v1/contacts
```

List

```http
GET /api/v1/contacts
```

Detail

```http
GET /api/v1/contacts/{id}
```

Update

```http
PUT /api/v1/contacts/{id}
```

Delete

```http
DELETE /api/v1/contacts/{id}
```

Activities

```http
GET /api/v1/contacts/{id}/activities
```

Notes

```http
GET /api/v1/contacts/{id}/notes
```

---

# Search / Filters

Accounts

Support:

```text
name
industry
owner
city
country
active
```

Contacts

Support:

```text
name
email
phone
account
owner
```

Use Spring Specifications.

Do not create repository explosion.

---

# Frontend Architecture

## Accounts Module

Route:

```text
/accounts
```

Pages:

### Account List

Columns:

* Company Name
* Industry
* Owner
* Contacts Count
* Created Date

Features:

* Search
* Filters
* Pagination
* Sorting

---

### Account Detail

Sections:

Company Profile

Contacts

Activities

Notes

Related Deals (future)

Timeline

---

### Account Form

Create

Edit

Dynamic custom fields

Owner assignment

---

# Contacts Module

Route:

```text
/contacts
```

Pages:

### Contact List

Columns:

* Name
* Account
* Email
* Phone
* Owner

---

### Contact Detail

Sections:

Profile

Account Information

Activities

Notes

Timeline

Future Deals

---

### Contact Form

Create

Edit

Link to Account

Owner Assignment

Dynamic Fields

---

# Permissions

Accounts

```text
account.read
account.write
account.delete
account.assign
account.export
```

Contacts

```text
contact.read
contact.write
contact.delete
contact.assign
contact.export
```

Apply to:

* APIs
* Navigation
* Buttons
* Actions
* Detail Pages

---

# Future Compatibility

This architecture must support:

* Deals Module
* Opportunities Pipeline
* Email Integration
* WhatsApp Integration
* Cloud Telephony
* Workflows
* Reporting
* Dashboards
* Webhooks

No future redesign should be required.

---

# Sprint Deliverables

Phase 1

✅ Account Schema

✅ Contact Schema

✅ CRUD APIs

✅ Search

✅ Filters

✅ Pagination

✅ Sorting

✅ Ownership

---

Phase 2

✅ Account Detail Page

✅ Contact Detail Page

✅ Relationship Mapping

✅ Dynamic Fields

✅ Activities

✅ Notes

---

Phase 3

✅ Lead Conversion Enhancement

✅ Account ↔ Contact Linking

✅ Activity Timeline

✅ RBAC Enforcement

---

Important Rule

Build Accounts and Contacts as first-class CRM entities, not as simple lookup tables.

Everything in future (Deals, Activities, Reporting, Automation, Integrations) will be built on top of these entities.
