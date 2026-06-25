# BACKEND_CONTEXT.md

## Backend Stack

* Spring Boot 4.x
* Java 21
* PostgreSQL
* Spring Data JPA
* Hibernate
* Flyway
* Redis
* RabbitMQ
* JWT Authentication
* Maven

---

# Development Philosophy

Backend is always completed before frontend.

When creating a new module:

1. Analyze existing entities.
2. Analyze existing migrations.
3. Analyze existing module relationships.
4. Identify dependencies with existing modules.
5. Reuse existing patterns.
6. Maintain tenant isolation.
7. Maintain RBAC support.
8. Prefer consistency over introducing new architecture.

Avoid unnecessary refactoring of existing production-ready modules.

---

# Completed Modules

* Authentication
* Users
* Roles
* Permissions
* Leads
* Accounts
* Contacts
* Activities
* Deals

---

# Planned Modules

* Tasks / Tickets
* Call
* Meetings
* Calendar
* Workflow
* Integrations
* Notifications
* Dashboard
* Reports

---

# Base Entity Standards

All entities requiring auditing extend BaseEntity.

BaseEntity contains:

* id (UUID)
* createdAt
* updatedAt
* deleted
* deletedAt
* deletedBy

Soft delete is supported.

---

# Tenant Entity Standards

Business entities extend TenantOwnedEntity.

Examples:

* Lead
* Account
* Contact
* Deal
* Opportunity
* Ticket
* Task

TenantOwnedEntity contains:

* tenantId
* ownerId
* createdBy

All business queries must enforce tenant isolation.

---

# RBAC Standards

Every tenant-owned module must support:

Permissions:

* module:read
* module:write
* module:delete

Scopes:

* OWN
* TEAM
* ALL

RBAC must be enforced in service and query layers.

---

# Metadata Strategy

For new modules, avoid creating dedicated tables such as:

* DealStage
* DealType
* TicketStatus
* TaskPriority
* TaskStatus

Prefer reusable metadata entities:

## MetadataDefinition

Examples:

DEAL → STAGE

DEAL → TYPE

TASK → STATUS

TASK → PRIORITY

TICKET → STATUS

ACCOUNT → TYPE

## MetadataValue

Stores configurable tenant values.

Example:

DEAL/STAGE

* Prospecting
* Qualification
* Proposal
* Negotiation
* Won
* Lost

---

# Dynamic Custom Fields

Business modules should support JSONB custom fields.

Pattern:

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "custom_data", columnDefinition = "jsonb")
private Map<String, Object> customData;

---

# Lead Module Exception

Lead module already contains:

* LeadStatus
* LeadSource
* LeadCustomField

These are production-ready and must remain unchanged unless explicitly instructed.

Do not refactor Leads into MetadataDefinition or CustomFieldDefinition automatically.

---

# Shared Custom Field Strategy

Future modules should use:

CustomFieldDefinition

Examples:

DEAL → expected_close_date

TASK → estimated_hours

ACCOUNT → gst_number

CONTACT → linkedin_url

Definitions control UI rendering.

Values remain stored in customData JSONB.

---

# Module Structure

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

Shared:

shared
├── dto
├── mapper
├── security
├── response
└── exception

---

# API Standards

Never return entities directly.

Always return DTOs.

Always wrap responses using ApiResponse.

Examples:

ApiResponse.success(data)

ApiResponse.success(data, meta)

ApiResponse.error(code, message)

---

# DTO Standards

Create:

* CreateRequest
* UpdateRequest
* Response
* DetailResponse

DetailResponse may contain:

* activities
* timeline
* notes
* related entities

---

# Repository Standards

Always use:

* JpaRepository
* JpaSpecificationExecutor

Avoid native queries unless absolutely necessary.

Apply tenant filtering in all business queries.

---

# Specification Standards

All major modules must support:

* Search
* Filtering
* Pagination

Common filters:

* ownerId
* status
* createdAt
* searchTerm
* custom filters

Use Specification Builder pattern.

---

# Mapper Standards

Use MapStruct.

Rules:

* Entity → Response DTO
* Request DTO → Entity
* UpdateRequest → Existing Entity

Ignore:

* Audit fields
* Tenant fields
* System-managed fields

unless explicitly set by service layer.

---

# Service Standards

Business logic belongs in services.

Responsibilities:

* Validation
* Tenant Isolation
* Relationship Validation
* Business Rules
* Activity Logging
* Conversion Logic

Controllers must remain thin.

---

# Controller Standards

Controllers only:

* Receive Request
* Validate Request
* Call Service
* Return ApiResponse

No business logic.

---

# Flyway Standards

Every module begins with Flyway migration.

Order:

1. Table Creation
2. Constraints
3. Foreign Keys
4. Indexes
5. Seed Data

Recommended indexes:

* tenant_id
* owner_id
* created_at
* status_id

Plus module-specific search indexes.

---

# Backend Development Workflow

For every new module:

1. Flyway Migration
2. Entity
3. Repository
4. Specification
5. DTOs
6. Mapper
7. Service
8. Controller

Backend must be completed and validated before frontend development begins.
