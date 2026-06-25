# AI_PROJECT_CONTEXT.md

## Project Overview

Project Type: Multi-Tenant CRM SaaS

Architecture:

* Backend: Spring Boot 4.x
* Java: 21
* Database: PostgreSQL
* ORM: Spring Data JPA + Hibernate
* Migration: Flyway
* Security: JWT Authentication
* Messaging: RabbitMQ
* Cache: Redis
* Build Tool: Maven

Frontend:

* Next.js 16.2.6
* TypeScript
* Tailwind CSS
* shadcn/ui
* TanStack Table
* React Hook Form
* Zod Validation
* React Query (@tanstack/react-query)
* App Router

---

## Development Philosophy

This project is developed module-by-module.

Backend is always completed before frontend.

AI must understand existing modules and relationships before generating new code.

Before creating a new module:

1. Analyze existing entities.
2. Analyze existing migrations.
3. Analyze existing relationships.
4. Identify dependencies.
5. Generate code that integrates with the existing architecture.
6. Never generate isolated modules without understanding existing modules.

## Architecture Evolution Rule

This project is actively evolving.

Existing modules may use older patterns that are already deployed and tested.

AI agents must:

- Respect existing implementations.
- Avoid unnecessary refactors.
- Prefer backward-compatible enhancements.
- Introduce improved architecture only for new modules unless explicitly instructed to refactor existing modules.

Development velocity is preferred over large-scale redesigns.

---

## Completed Modules

* Authentication
* Users
* Roles
* Permissions
* Leads
* Accounts
* Contacts
* Activities

---

## Planned Modules

* Deal / Opportunity
* Task / Ticket
* Calendar
* Workflow
* Integrations
* Notifications
* Dashboard
* Reports

---

## RBAC Model

All tenant modules follow RBAC.

Permission Pattern:

module:read
module:write
module:delete

Scopes:

OWN
TEAM
ALL

AI must always implement RBAC support when generating tenant-owned modules.

---

## Entity Standards

Base Entity:

All entities requiring audit information extend BaseEntity.

BaseEntity contains:

* id (UUID)
* createdAt
* updatedAt
* deleted
* deletedAt
* deletedBy

Soft delete support exists.

---

Tenant Owned Entity:

Business entities such as:

* Lead
* Account
* Contact
* Deal
* Opportunity
* Ticket

extend TenantOwnedEntity.

TenantOwnedEntity contains:

* tenantId
* ownerId
* createdBy

All repository queries must respect tenant isolation.

---

## Backend Module Structure

module
├── controller
├── service
│ └── impl
├── repository
├── specification
├── mapper
├── dto
├── entity
└── ModuleNameModule.java

Shared Structure:

shared
├── dto
├── mapper
├── security
├── response
├── exception

AI should follow this structure unless explicitly instructed otherwise.

## CRM Metadata & Dynamic Configuration Strategy

### Current State

The Lead module currently contains dedicated entities:

* LeadStatus
* LeadSource
* LeadCustomField

These are production-ready and must not be redesigned or refactored unless explicitly requested.

Future work must remain compatible with the existing Lead implementation.

---

### Future Metadata Strategy

For future modules (Deal, Task, Ticket, Workflow, etc.), avoid creating separate entities such as:

* DealStage
* DealType
* TaskStatus
* TicketPriority
* TicketStatus
* AccountType

unless there is a strong business reason.

Instead prefer a generic metadata system.

#### MetadataDefinition

Represents a configurable metadata category.

Examples:

| Module  | Key      |
| ------- | -------- |
| DEAL    | STAGE    |
| DEAL    | TYPE     |
| TASK    | STATUS   |
| TASK    | PRIORITY |
| TICKET  | STATUS   |
| ACCOUNT | TYPE     |

#### MetadataValue

Represents values belonging to a metadata definition.

Examples:

DEAL → STAGE

* Prospecting
* Qualification
* Proposal
* Negotiation
* Won
* Lost

TASK → PRIORITY

* Low
* Medium
* High
* Urgent

This allows new modules to support tenant-configurable statuses, stages, priorities, categories, and types without creating additional tables.

---

### Dynamic Custom Fields

All business modules should support extensibility using JSONB custom data.

Entity pattern:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "custom_data", columnDefinition = "jsonb")
private Map<String, Object> customData;
```

---

### Lead Module Exception

The Lead module already uses:

* LeadCustomField
* LeadStatus
* LeadSource

These should remain unchanged.

Future migration into generic metadata/custom field infrastructure may occur later but should not be assumed during development.

---

### CustomFieldDefinition (Future Shared Model)

For future modules prefer a shared definition entity instead of module-specific field tables.

Examples:

module = DEAL
field = expected_close_date

module = TASK
field = estimated_hours

module = ACCOUNT
field = gst_number

module = CONTACT
field = linkedin_url

This definition controls form rendering.

Actual values remain stored inside each module's customData JSONB column.

---

### AI Agent Rule

Before creating any new Status, Stage, Type, Source, Category, Priority, or Classification entity:

1. Check whether MetadataDefinition and MetadataValue should be used.
2. Check whether a shared CustomFieldDefinition can satisfy the requirement.
3. Do not automatically create module-specific configuration tables.
4. Do not refactor existing Lead entities unless explicitly requested.
5. Prefer reusable metadata architecture for all new modules starting from Deal onward.

---

## API Standards

Never return entities directly.

Always use DTOs.

Always wrap responses using ApiResponse.

Example:

ApiResponse.success(data)

ApiResponse.success(data, meta)

ApiResponse.error(code, message)

---

## DTO Standards

Create:

* CreateRequest
* UpdateRequest
* Response
* DetailResponse

DetailResponse may contain:

* timeline
* activities
* notes
* related entities

when applicable.

---

## Repository Standards

Always use:

JpaRepository

and

JpaSpecificationExecutor

for searchable modules.

Avoid native queries unless absolutely necessary.

Tenant filtering must be applied.

---

## Specification Standards

All major business modules must support:

* Search
* Filtering
* Pagination

Specification Builder should be implemented.

Typical Filters:

* ownerId
* status
* createdAt
* searchTerm
* custom filters

---

## Mapper Standards

Use MapStruct whenever possible.

Never expose entity directly to API.

Map:

Entity → Response DTO

Request DTO → Entity

UpdateRequest → Existing Entity

Ignore audit fields.

Ignore tenant fields unless explicitly set by service layer.

---

## Service Standards

Business logic belongs in Service layer.

Controller must remain thin.

Service responsibilities:

* Validation
* Tenant Isolation
* Relationship Validation
* Business Rules
* Activity Logging
* Conversion Logic

---

## Controller Standards

Controllers only:

* Receive Request
* Validate Request
* Call Service
* Return ApiResponse

No business logic inside controllers.

---

## Flyway Standards

Every new module begins with migration.

Migration order:

1. Table Creation
2. Constraints
3. Foreign Keys
4. Indexes
5. Seed Data (if required)

Indexes should be added for:

* tenant_id
* owner_id
* status_id
* created_at

and search fields.

---

## Frontend Structure

src

app
components
hooks
lib
constants
providers
store
types

---

## Frontend API Layer

All API calls live inside:

lib/

Examples:

* accounts.ts
* contacts.ts
* leads.ts

API functions return unwrapped ApiResponse data.

---

## React Query Standards

Each module contains hooks.

Examples:

useAccounts()

useCreateAccount()

useUpdateAccount()

useDeleteAccount()

useAccount()

AI should generate hooks for every CRUD operation.

---

## List Page Standards

All list pages must support:

* Server Side Pagination
* Search
* Filtering
* Sorting
* Bulk Selection
* Bulk Actions
* Permission Based Actions

Table Technology:

TanStack Table

---

## Permission Standards

Actions displayed in UI must respect permissions.

Examples:

module:read
module:write
module:delete

Buttons should not render if permission is missing.

Scope must be respected.

---

## Detail Page Standards

Each detail page should contain:

* Main Information
* Related Records
* Activity Timeline
* Notes
* Audit Information

when applicable.

---

## Phase-Based Development Workflow

Phase 1 Backend

1. Flyway Migration
2. Entity
3. Repository
4. Specification
5. DTOs
6. Mapper
7. Service
8. Controller

Phase 2 Frontend

9. Types
10. API Layer
11. React Query Hooks
12. List Page
13. Create Dialog
14. Edit Dialog
15. Detail Page

---

## AI Instructions

Before generating any module:

1. Review existing entities.
2. Review existing migrations.
3. Review existing relationships.
4. Review existing permissions.
5. Identify dependencies with existing modules.
6. Reuse existing patterns.
7. Maintain consistency with existing codebase.

Never introduce a new architectural pattern unless explicitly requested.

Consistency is preferred over innovation.

The goal is maintainable enterprise CRM code.
